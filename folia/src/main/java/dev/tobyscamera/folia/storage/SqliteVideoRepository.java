package dev.tobyscamera.folia.storage;

import dev.tobyscamera.common.upload.VideoUploadSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** SQLite video metadata plus independently compressed palette tiles on disk. */
public final class SqliteVideoRepository implements VideoRepository {
    private final Path root;
    private final Path videoDirectory;
    private final Connection connection;

    public SqliteVideoRepository(Path root) throws IOException {
        try {
            this.root = root;
            videoDirectory = Files.createDirectories(root.resolve("videos"));
            connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("videos.db").toAbsolutePath());
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists videos(id text primary key, owner text, created integer, width integer, height integer, fps integer, frames integer)");
                statement.executeUpdate("create table if not exists video_maps(video_id text,x integer,y integer,map_id integer,primary key(video_id,x,y))");
                statement.executeUpdate("create index if not exists video_maps_video_index on video_maps(video_id)");
            }
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    public Path root() { return root; }

    @Override public synchronized void save(VideoRecord record, VideoUploadSession session) throws IOException {
        Path finalDirectory = videoDirectory.resolve(record.videoId().toString());
        Path stagingDirectory = videoDirectory.resolve("." + record.videoId() + ".staging");
        if (Files.exists(finalDirectory) || Files.exists(stagingDirectory)) throw new IOException("video already exists: " + record.videoId());
        try {
            Files.createDirectories(stagingDirectory);
            for (int frame = 0; frame < record.frameCount(); frame++) for (int y = 0; y < record.gridHeight(); y++) for (int x = 0; x < record.gridWidth(); x++) {
                writeTile(stagingDirectory.resolve(tileName(frame, x, y)), session.tile(frame, x, y));
            }
            try (PreparedStatement video = connection.prepareStatement("insert into videos values(?,?,?,?,?,?,?)");
                    PreparedStatement maps = connection.prepareStatement("insert into video_maps values(?,?,?,?)")) {
                video.setString(1, record.videoId().toString()); video.setString(2, record.ownerId().toString()); video.setLong(3, record.createdAt().toEpochMilli());
                video.setInt(4, record.gridWidth()); video.setInt(5, record.gridHeight()); video.setInt(6, record.fps()); video.setInt(7, record.frameCount()); video.executeUpdate();
                for (var entry : record.mapIds().entrySet()) { maps.setString(1, record.videoId().toString()); maps.setInt(2, entry.getKey().x()); maps.setInt(3, entry.getKey().y()); maps.setInt(4, entry.getValue()); maps.addBatch(); }
                maps.executeBatch();
            }
            Files.move(stagingDirectory, finalDirectory);
        } catch (SQLException exception) {
            deleteRecursively(stagingDirectory);
            throw new IOException(exception);
        } catch (IOException exception) {
            deleteRecursively(stagingDirectory);
            throw exception;
        }
    }

    @Override public synchronized List<VideoRecord> loadAll() throws IOException {
        try (var query = connection.createStatement(); ResultSet results = query.executeQuery("select * from videos")) {
            List<VideoRecord> records = new ArrayList<>();
            while (results.next()) {
                UUID videoId = UUID.fromString(results.getString("id"));
                Map<TileCoordinate, Integer> mapIds = new LinkedHashMap<>();
                try (PreparedStatement maps = connection.prepareStatement("select x,y,map_id from video_maps where video_id=?")) {
                    maps.setString(1, videoId.toString()); try (ResultSet rows = maps.executeQuery()) { while (rows.next()) mapIds.put(new TileCoordinate(rows.getInt(1), rows.getInt(2)), rows.getInt(3)); }
                }
                records.add(new VideoRecord(videoId, UUID.fromString(results.getString("owner")), Instant.ofEpochMilli(results.getLong("created")), results.getInt("width"), results.getInt("height"), results.getInt("fps"), results.getInt("frames"), mapIds));
            }
            return records;
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    @Override public byte[] readTile(UUID videoId, int frameIndex, TileCoordinate coordinate) throws IOException {
        Path path = videoDirectory.resolve(videoId.toString()).resolve(tileName(frameIndex, coordinate.x(), coordinate.y()));
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            byte[] tile = input.readAllBytes();
            if (tile.length != 16_384) throw new IOException("invalid stored video tile length");
            return tile;
        }
    }

    @Override public void close() throws IOException { try { connection.close(); } catch (SQLException exception) { throw new IOException(exception); } }

    private static String tileName(int frame, int x, int y) { return frame + "-" + x + "-" + y + ".tile.gz"; }
    private static void writeTile(Path path, byte[] tile) throws IOException { try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) { output.write(tile); } }
    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException exception) { throw new DeleteFailure(exception); } }); }
        catch (DeleteFailure exception) { throw exception.getCause(); }
    }
    private static final class DeleteFailure extends RuntimeException { DeleteFailure(IOException cause) { super(cause); } @Override public IOException getCause() { return (IOException) super.getCause(); } }
}
