package dev.tobyscamera.folia.storage;

import dev.tobyscamera.common.protocol.PhotoPresentation;
import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SqlitePhotoRepository implements PhotoRepository {
    private final Path photosDirectory;
    private final Path temporaryDirectory;
    private final Connection connection;

    public SqlitePhotoRepository(Path dataDirectory) throws IOException {
        try {
            Files.createDirectories(dataDirectory);
            photosDirectory = dataDirectory.resolve("photos");
            temporaryDirectory = dataDirectory.resolve("upload-tmp");
            Files.createDirectories(photosDirectory);
            Files.createDirectories(temporaryDirectory);
            try (var paths = Files.list(temporaryDirectory)) { paths.forEach(this::deleteQuietly); }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataDirectory.resolve("photos.db").toAbsolutePath());
            initialize();
        } catch (SQLException exception) {
            throw new IOException("could not open photo database", exception);
        }
    }

    @Override
    public synchronized void save(PhotoRecord record, Map<TileCoordinate, byte[]> tiles, byte[] previewPixels) throws IOException {
        validateTiles(record, tiles, previewPixels);
        Path staging = temporaryDirectory.resolve(record.photoId() + ".tbc");
        Path destination = ShardedMediaLayout.container(photosDirectory, record.photoId());
        try {
            Files.createDirectories(destination.getParent());
            Map<String, byte[]> contents = new LinkedHashMap<>();
            for (var entry : tiles.entrySet()) contents.put(tileKey(entry.getKey()), entry.getValue());
            Map<String, TileContainer.Range> ranges = TileContainer.write(staging, contents);
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            connection.setAutoCommit(false);
            try (PreparedStatement photo = connection.prepareStatement("insert into photos(id, owner, owner_name, created, width, height, preview) values(?,?,?,?,?,?,?)");
                 PreparedStatement tile = connection.prepareStatement("insert into tiles(photo_id, x, y, map_id) values(?,?,?,?)");
                 PreparedStatement data = connection.prepareStatement("insert into photo_tile_data(photo_id,x,y,offset,length) values(?,?,?,?,?)");
                 PreparedStatement metadata = connection.prepareStatement("insert into photo_metadata(photo_id, photographer, world, x, y, z, captured_at, name, description, public_address, public_photographer, public_captured_time) values(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                photo.setString(1, record.photoId().toString()); photo.setString(2, record.ownerId().toString());
                photo.setString(3, record.ownerName()); photo.setLong(4, record.createdAt().toEpochMilli()); photo.setInt(5, record.gridWidth()); photo.setInt(6, record.gridHeight()); photo.setBytes(7, previewPixels); photo.executeUpdate();
                for (var entry : record.mapIds().entrySet()) {
                    tile.setString(1, record.photoId().toString()); tile.setInt(2, entry.getKey().x()); tile.setInt(3, entry.getKey().y()); tile.setInt(4, entry.getValue()); tile.addBatch();
                }
                tile.executeBatch();
                for (var entry : record.mapIds().entrySet()) {
                    TileContainer.Range range = ranges.get(tileKey(entry.getKey()));
                    data.setString(1, record.photoId().toString()); data.setInt(2, entry.getKey().x()); data.setInt(3, entry.getKey().y()); data.setLong(4, range.offset()); data.setInt(5, range.length()); data.addBatch();
                }
                data.executeBatch();
                saveMetadata(metadata, record.photoId(), record.metadata());
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback(); deleteQuietly(destination); throw exception;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IOException("could not save photo", exception); }
        catch (IOException exception) { deleteQuietly(staging); throw exception; }
    }

    @Override
    public synchronized List<PhotoRecord> loadAll() throws IOException {
        try (Statement statement = connection.createStatement(); ResultSet photos = statement.executeQuery(photoSelect("order by p.created, p.id"))) {
            List<PhotoRecord> result = new ArrayList<>();
            while (photos.next()) result.add(readRecord(photos));
            return result;
        } catch (SQLException exception) { throw new IOException("could not load photos", exception); }
    }

    @Override
    public synchronized PhotoPage findPage(PhotoQuery query) throws IOException {
        String order = query.sort() == PhotoQuery.Sort.NEWEST ? "desc" : "asc";
        String sql = photoSelect("where lower(coalesce(p.owner_name, '')) like ? escape '!' or lower(p.owner) like ? escape '!' or lower(p.id) like ? escape '!' "
                + "order by p.created " + order + ", p.id " + order + " limit ? offset ?");
        String namePattern = containsLikePattern(query.term());
        String uuidPrefixPattern = uuidPrefixLikePattern(query.term());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namePattern); statement.setString(2, uuidPrefixPattern); statement.setString(3, uuidPrefixPattern);
            statement.setInt(4, query.pageSize() + 1); statement.setLong(5, (long) query.page() * query.pageSize());
            try (ResultSet rows = statement.executeQuery()) {
                List<PhotoRecord> records = new ArrayList<>();
                while (rows.next() && records.size() <= query.pageSize()) records.add(readRecord(rows));
                boolean hasNext = records.size() > query.pageSize();
                if (hasNext) records.removeLast();
                return new PhotoPage(records, hasNext);
            }
        } catch (SQLException exception) { throw new IOException("could not query photos", exception); }
    }

    @Override
    public synchronized PhotoStorageStats stats() throws IOException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select count(*), coalesce(sum(width * height), 0) from photos")) {
            if (!result.next()) return new PhotoStorageStats(0, 0);
            return new PhotoStorageStats(result.getLong(1), result.getLong(2));
        } catch (SQLException exception) {
            throw new IOException("could not count stored photos", exception);
        }
    }

    @Override
    public synchronized PhotoRecord find(UUID photoId) throws IOException {
        try (PreparedStatement photo = connection.prepareStatement(photoSelect("where p.id=?"))) {
            photo.setString(1, photoId.toString());
            try (ResultSet result = photo.executeQuery()) {
                if (!result.next()) return null;
                return readRecord(result);
            }
        } catch (SQLException exception) {
            throw new IOException("could not find photo", exception);
        }
    }

    @Override
    public synchronized byte[] readTile(UUID photoId, TileCoordinate coordinate) throws IOException {
        try (PreparedStatement query = connection.prepareStatement("select offset,length from photo_tile_data where photo_id=? and x=? and y=?")) {
            query.setString(1, photoId.toString()); query.setInt(2, coordinate.x()); query.setInt(3, coordinate.y());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IOException("missing photo tile index");
                return TileContainer.read(ShardedMediaLayout.container(photosDirectory, photoId), new TileContainer.Range(result.getLong(1), result.getInt(2)));
            }
        } catch (SQLException exception) { throw new IOException("could not read photo tile index", exception); }
    }

    @Override
    public synchronized byte[] readPreview(UUID photoId) throws IOException {
        try (PreparedStatement query = connection.prepareStatement("select preview from photos where id=?")) {
            query.setString(1, photoId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IOException("missing photo preview");
                return result.getBytes(1);
            }
        } catch (SQLException exception) { throw new IOException("could not read photo preview", exception); }
    }

    @Override
    public synchronized boolean isBlocked(UUID playerId) throws IOException {
        try (PreparedStatement statement = connection.prepareStatement("select 1 from upload_blocks where player_id=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw new IOException("could not check upload block", exception); }
    }

    @Override
    public synchronized void block(UploadBlock block) throws IOException {
        try (PreparedStatement statement = connection.prepareStatement("insert into upload_blocks(player_id, admin_id, blocked_at) values(?,?,?) on conflict(player_id) do update set admin_id=excluded.admin_id, blocked_at=excluded.blocked_at")) {
            statement.setString(1, block.playerId().toString()); statement.setString(2, block.adminId().toString());
            statement.setLong(3, block.blockedAt().toEpochMilli()); statement.executeUpdate();
        } catch (SQLException exception) { throw new IOException("could not block uploads", exception); }
    }

    @Override
    public synchronized void unblock(UUID playerId) throws IOException {
        try (PreparedStatement statement = connection.prepareStatement("delete from upload_blocks where player_id=?")) {
            statement.setString(1, playerId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new IOException("could not unblock uploads", exception); }
    }

    @Override
    public synchronized void delete(UUID photoId) throws IOException {
        Path destination = ShardedMediaLayout.container(photosDirectory, photoId);
        Path staged = temporaryDirectory.resolve("delete-" + photoId + ".tbc");
        boolean moved = false;
        boolean transactionStarted = false;
        boolean databaseDeleted = false;
        try {
            if (Files.exists(destination)) { Files.move(destination, staged, StandardCopyOption.ATOMIC_MOVE); moved = true; }
            connection.setAutoCommit(false);
            transactionStarted = true;
            if (!hasPhoto(photoId)) return;
            deleteRows("photo_metadata", photoId); deleteRows("photo_tile_data", photoId); deleteRows("tiles", photoId); deleteRows("photos", photoId);
            connection.commit();
            databaseDeleted = true;
            if (moved) Files.deleteIfExists(staged);
        } catch (SQLException exception) { throw new IOException("could not delete photo", exception); }
        catch (IOException exception) { throw new IOException("could not delete photo", exception); }
        finally {
            if (transactionStarted && !databaseDeleted) {
                try { connection.rollback(); } catch (SQLException ignored) { }
            }
            if (moved && !databaseDeleted && Files.exists(staged)) {
                try { Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE); } catch (IOException ignored) { }
            }
            if (transactionStarted) {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
            }
        }
    }

    @Override public synchronized void close() throws IOException { try { connection.close(); } catch (SQLException exception) { throw new IOException(exception); } }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists photos (id text primary key, owner text not null, created integer not null, width integer not null, height integer not null, preview blob)");
            statement.executeUpdate("create table if not exists tiles (photo_id text not null, x integer not null, y integer not null, map_id integer not null, primary key(photo_id,x,y))");
            statement.executeUpdate("create table if not exists photo_tile_data (photo_id text not null, x integer not null, y integer not null, offset integer not null, length integer not null, primary key(photo_id,x,y))");
            statement.executeUpdate("create table if not exists photo_metadata (photo_id text primary key, photographer text not null, world text not null, x integer not null, y integer not null, z integer not null, captured_at integer not null, name text not null, description text not null, public_address integer not null, public_photographer integer not null, public_captured_time integer not null)");
            statement.executeUpdate("create table if not exists upload_blocks (player_id text primary key, admin_id text not null, blocked_at integer not null)");
        }
        ensurePreviewColumn();
        ensureOwnerNameColumn();
    }

    private void ensurePreviewColumn() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("pragma table_info(photos)")) {
            while (columns.next()) if ("preview".equals(columns.getString("name"))) return;
        }
        try (Statement statement = connection.createStatement()) { statement.executeUpdate("alter table photos add column preview blob"); }
    }

    private void ensureOwnerNameColumn() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("pragma table_info(photos)")) {
            while (columns.next()) if ("owner_name".equals(columns.getString("name"))) return;
        }
        try (Statement statement = connection.createStatement()) { statement.executeUpdate("alter table photos add column owner_name text"); }
    }

    private static String photoSelect(String suffix) {
        return "select p.id, p.owner, p.owner_name, p.created, p.width, p.height, m.photo_id as metadata_photo_id, "
                + "m.photographer, m.world, m.x, m.y, m.z, m.captured_at, m.name, m.description, "
                + "m.public_address, m.public_photographer, m.public_captured_time from photos p "
                + "left join photo_metadata m on m.photo_id=p.id " + suffix;
    }

    private PhotoRecord readRecord(ResultSet row) throws SQLException {
        UUID photoId = UUID.fromString(row.getString("id"));
        PhotoMetadata metadata = null;
        if (row.getString("metadata_photo_id") != null) {
            metadata = new PhotoMetadata(row.getString("photographer"), row.getString("world"), row.getInt("x"),
                    row.getInt("y"), row.getInt("z"), Instant.ofEpochMilli(row.getLong("captured_at")),
                    new PhotoPresentation(row.getString("name"), row.getString("description"), row.getBoolean("public_address"),
                            row.getBoolean("public_photographer"), row.getBoolean("public_captured_time")));
        }
        return new PhotoRecord(photoId, UUID.fromString(row.getString("owner")), row.getString("owner_name"),
                Instant.ofEpochMilli(row.getLong("created")), row.getInt("width"), row.getInt("height"), loadMapIds(photoId), metadata);
    }

    private Map<TileCoordinate, Integer> loadMapIds(UUID photoId) throws SQLException {
        Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
        try (PreparedStatement tiles = connection.prepareStatement("select x, y, map_id from tiles where photo_id=? order by y,x")) {
            tiles.setString(1, photoId.toString());
            try (ResultSet rows = tiles.executeQuery()) {
                while (rows.next()) maps.put(new TileCoordinate(rows.getInt(1), rows.getInt(2)), rows.getInt(3));
            }
        }
        return maps;
    }

    private static void saveMetadata(PreparedStatement statement, UUID photoId, PhotoMetadata metadata) throws SQLException {
        if (metadata == null) return;
        statement.setString(1, photoId.toString()); statement.setString(2, metadata.photographer()); statement.setString(3, metadata.world());
        statement.setInt(4, metadata.x()); statement.setInt(5, metadata.y()); statement.setInt(6, metadata.z());
        statement.setLong(7, metadata.capturedAt().toEpochMilli()); statement.setString(8, metadata.presentation().name());
        statement.setString(9, metadata.presentation().description()); statement.setBoolean(10, metadata.presentation().publicAddress());
        statement.setBoolean(11, metadata.presentation().publicPhotographer()); statement.setBoolean(12, metadata.presentation().publicCapturedTime());
        statement.executeUpdate();
    }

    private int deleteRows(String table, UUID photoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from " + table + " where " + ("photos".equals(table) ? "id" : "photo_id") + "=?")) {
            statement.setString(1, photoId.toString()); return statement.executeUpdate();
        }
    }

    private boolean hasPhoto(UUID photoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select 1 from photos where id=?")) {
            statement.setString(1, photoId.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static String containsLikePattern(String term) {
        return "%" + escapeLikeTerm(term) + "%";
    }

    private static String uuidPrefixLikePattern(String term) {
        return escapeLikeTerm(term) + "%";
    }

    private static String escapeLikeTerm(String term) {
        return term.toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String tileKey(TileCoordinate coordinate) { return coordinate.x() + "-" + coordinate.y(); }

    private static void validateTiles(PhotoRecord record, Map<TileCoordinate, byte[]> tiles, byte[] previewPixels) {
        if (!tiles.keySet().equals(record.mapIds().keySet())) throw new IllegalArgumentException("tile keys must match map ids");
        for (byte[] tile : tiles.values()) if (tile.length != UploadSession.TILE_BYTES) throw new IllegalArgumentException("tile must be 16384 bytes");
        if (previewPixels == null || previewPixels.length != UploadSession.TILE_BYTES) throw new IllegalArgumentException("preview must be 16384 bytes");
    }

    private void deleteQuietly(Path path) { try { if (Files.isDirectory(path)) { try (var children = Files.list(path)) { children.forEach(this::deleteQuietly); } } Files.deleteIfExists(path); } catch (IOException ignored) { } }
}
