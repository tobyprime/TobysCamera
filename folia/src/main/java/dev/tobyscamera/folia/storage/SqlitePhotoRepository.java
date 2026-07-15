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
    public synchronized void save(PhotoRecord record, Map<TileCoordinate, byte[]> tiles) throws IOException {
        validateTiles(record, tiles);
        Path staging = temporaryDirectory.resolve(record.photoId().toString());
        Path destination = photosDirectory.resolve(record.photoId().toString());
        try {
            Files.createDirectories(staging);
            for (var entry : tiles.entrySet()) {
                Files.write(staging.resolve(entry.getKey().x() + "-" + entry.getKey().y() + ".tile"), entry.getValue());
            }
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            connection.setAutoCommit(false);
            try (PreparedStatement photo = connection.prepareStatement("insert into photos(id, owner, created, width, height) values(?,?,?,?,?)");
                 PreparedStatement tile = connection.prepareStatement("insert into tiles(photo_id, x, y, map_id) values(?,?,?,?)")) {
                photo.setString(1, record.photoId().toString()); photo.setString(2, record.ownerId().toString());
                photo.setLong(3, record.createdAt().toEpochMilli()); photo.setInt(4, record.gridWidth()); photo.setInt(5, record.gridHeight()); photo.executeUpdate();
                for (var entry : record.mapIds().entrySet()) {
                    tile.setString(1, record.photoId().toString()); tile.setInt(2, entry.getKey().x()); tile.setInt(3, entry.getKey().y()); tile.setInt(4, entry.getValue()); tile.addBatch();
                }
                tile.executeBatch(); connection.commit();
            } catch (SQLException exception) {
                connection.rollback(); deleteQuietly(destination); throw exception;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IOException("could not save photo", exception); }
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
                result.add(new PhotoRecord(id, UUID.fromString(photos.getString(2)), Instant.ofEpochMilli(photos.getLong(3)), photos.getInt(4), photos.getInt(5), maps));
            }
            return result;
        } catch (SQLException exception) { throw new IOException("could not load photos", exception); }
    }

    @Override
    public synchronized PhotoRecord find(UUID photoId) throws IOException {
        for (PhotoRecord record : loadAll()) if (record.photoId().equals(photoId)) return record;
        return null;
    }

    @Override
    public byte[] readTile(UUID photoId, TileCoordinate coordinate) throws IOException {
        return Files.readAllBytes(photosDirectory.resolve(photoId.toString()).resolve(coordinate.x() + "-" + coordinate.y() + ".tile"));
    }

    @Override public void close() throws IOException { try { connection.close(); } catch (SQLException exception) { throw new IOException(exception); } }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists photos (id text primary key, owner text not null, created integer not null, width integer not null, height integer not null)");
            statement.executeUpdate("create table if not exists tiles (photo_id text not null, x integer not null, y integer not null, map_id integer not null, primary key(photo_id,x,y))");
        }
    }

    private static void validateTiles(PhotoRecord record, Map<TileCoordinate, byte[]> tiles) {
        if (!tiles.keySet().equals(record.mapIds().keySet())) throw new IllegalArgumentException("tile keys must match map ids");
        for (byte[] tile : tiles.values()) if (tile.length != UploadSession.TILE_BYTES) throw new IllegalArgumentException("tile must be 16384 bytes");
    }

    private void deleteQuietly(Path path) { try { if (Files.isDirectory(path)) { try (var children = Files.list(path)) { children.forEach(this::deleteQuietly); } } Files.deleteIfExists(path); } catch (IOException ignored) { } }
}
