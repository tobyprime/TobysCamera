package dev.tobyscamera.folia.storage;

import dev.tobyscamera.common.upload.UploadSession;
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
            try (PreparedStatement photo = connection.prepareStatement("insert into photos(id, owner, created, width, height, preview) values(?,?,?,?,?,?)");
                 PreparedStatement tile = connection.prepareStatement("insert into tiles(photo_id, x, y, map_id) values(?,?,?,?)");
                 PreparedStatement data = connection.prepareStatement("insert into photo_tile_data(photo_id,x,y,offset,length) values(?,?,?,?,?)")) {
                photo.setString(1, record.photoId().toString()); photo.setString(2, record.ownerId().toString());
                photo.setLong(3, record.createdAt().toEpochMilli()); photo.setInt(4, record.gridWidth()); photo.setInt(5, record.gridHeight()); photo.setBytes(6, previewPixels); photo.executeUpdate();
                for (var entry : record.mapIds().entrySet()) {
                    tile.setString(1, record.photoId().toString()); tile.setInt(2, entry.getKey().x()); tile.setInt(3, entry.getKey().y()); tile.setInt(4, entry.getValue()); tile.addBatch();
                }
                tile.executeBatch();
                for (var entry : record.mapIds().entrySet()) {
                    TileContainer.Range range = ranges.get(tileKey(entry.getKey()));
                    data.setString(1, record.photoId().toString()); data.setInt(2, entry.getKey().x()); data.setInt(3, entry.getKey().y()); data.setLong(4, range.offset()); data.setInt(5, range.length()); data.addBatch();
                }
                data.executeBatch(); connection.commit();
            } catch (SQLException exception) {
                connection.rollback(); deleteQuietly(destination); throw exception;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IOException("could not save photo", exception); }
        catch (IOException exception) { deleteQuietly(staging); throw exception; }
    }

    @Override
    public synchronized List<PhotoRecord> loadAll() throws IOException {
        try (Statement statement = connection.createStatement(); ResultSet photos = statement.executeQuery("select id, owner, created, width, height from photos order by created")) {
            List<PhotoRecord> result = new ArrayList<>();
            while (photos.next()) {
                UUID id = UUID.fromString(photos.getString(1));
                Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
                try (PreparedStatement tiles = connection.prepareStatement("select x, y, map_id from tiles where photo_id=? order by y,x")) {
                    tiles.setString(1, id.toString()); try (ResultSet rows = tiles.executeQuery()) { while (rows.next()) maps.put(new TileCoordinate(rows.getInt(1), rows.getInt(2)), rows.getInt(3)); }
                }
                result.add(new PhotoRecord(id, UUID.fromString(photos.getString(2)), Instant.ofEpochMilli(photos.getLong(3)),
                        photos.getInt(4), photos.getInt(5), maps));
            }
            return result;
        } catch (SQLException exception) { throw new IOException("could not load photos", exception); }
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
        try (PreparedStatement photo = connection.prepareStatement("select id, owner, created, width, height from photos where id=?")) {
            photo.setString(1, photoId.toString());
            try (ResultSet result = photo.executeQuery()) {
                if (!result.next()) return null;
                Map<TileCoordinate, Integer> maps = new LinkedHashMap<>();
                try (PreparedStatement tiles = connection.prepareStatement("select x, y, map_id from tiles where photo_id=? order by y,x")) {
                    tiles.setString(1, photoId.toString());
                    try (ResultSet rows = tiles.executeQuery()) {
                        while (rows.next()) maps.put(new TileCoordinate(rows.getInt(1), rows.getInt(2)), rows.getInt(3));
                    }
                }
                return new PhotoRecord(UUID.fromString(result.getString(1)), UUID.fromString(result.getString(2)),
                        Instant.ofEpochMilli(result.getLong(3)), result.getInt(4), result.getInt(5), maps);
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

    @Override public synchronized void close() throws IOException { try { connection.close(); } catch (SQLException exception) { throw new IOException(exception); } }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists photos (id text primary key, owner text not null, created integer not null, width integer not null, height integer not null, preview blob)");
            statement.executeUpdate("create table if not exists tiles (photo_id text not null, x integer not null, y integer not null, map_id integer not null, primary key(photo_id,x,y))");
            statement.executeUpdate("create table if not exists photo_tile_data (photo_id text not null, x integer not null, y integer not null, offset integer not null, length integer not null, primary key(photo_id,x,y))");
        }
        ensurePreviewColumn();
    }

    private void ensurePreviewColumn() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("pragma table_info(photos)")) {
            while (columns.next()) if ("preview".equals(columns.getString("name"))) return;
        }
        try (Statement statement = connection.createStatement()) { statement.executeUpdate("alter table photos add column preview blob"); }
    }

    private static String tileKey(TileCoordinate coordinate) { return coordinate.x() + "-" + coordinate.y(); }

    private static void validateTiles(PhotoRecord record, Map<TileCoordinate, byte[]> tiles, byte[] previewPixels) {
        if (!tiles.keySet().equals(record.mapIds().keySet())) throw new IllegalArgumentException("tile keys must match map ids");
        for (byte[] tile : tiles.values()) if (tile.length != UploadSession.TILE_BYTES) throw new IllegalArgumentException("tile must be 16384 bytes");
        if (previewPixels == null || previewPixels.length != UploadSession.TILE_BYTES) throw new IllegalArgumentException("preview must be 16384 bytes");
    }

    private void deleteQuietly(Path path) { try { if (Files.isDirectory(path)) { try (var children = Files.list(path)) { children.forEach(this::deleteQuietly); } } Files.deleteIfExists(path); } catch (IOException ignored) { } }
}
