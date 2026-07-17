package dev.tobyscamera.folia.storage;

import dev.tobyscamera.common.upload.VideoUploadSession;
import java.io.IOException;
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
                statement.executeUpdate("create table if not exists video_tile_data(video_id text,frame integer,x integer,y integer,offset integer,length integer,primary key(video_id,frame,x,y))");
            }
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    public Path root() { return root; }

    @Override public synchronized void save(VideoRecord record, VideoUploadSession session) throws IOException {
        Path finalFile = ShardedMediaLayout.container(videoDirectory, record.videoId());
        Path stagingFile = videoDirectory.resolve("." + record.videoId() + ".staging.tbc");
        if (Files.exists(finalFile) || Files.exists(stagingFile)) throw new IOException("video already exists: " + record.videoId());
        try {
            Files.createDirectories(finalFile.getParent());
            Map<String, byte[]> tiles = new LinkedHashMap<>();
            for (int frame = 0; frame < record.frameCount(); frame++) for (int y = 0; y < record.gridHeight(); y++) for (int x = 0; x < record.gridWidth(); x++) tiles.put(tileKey(frame, x, y), session.tile(frame, x, y));
            Map<String, TileContainer.Range> ranges = TileContainer.write(stagingFile, tiles);
            Files.move(stagingFile, finalFile);
            try (PreparedStatement video = connection.prepareStatement("insert into videos values(?,?,?,?,?,?,?)");
                    PreparedStatement maps = connection.prepareStatement("insert into video_maps values(?,?,?,?)");
                    PreparedStatement data = connection.prepareStatement("insert into video_tile_data values(?,?,?,?,?,?)")) {
                connection.setAutoCommit(false);
                video.setString(1, record.videoId().toString()); video.setString(2, record.ownerId().toString()); video.setLong(3, record.createdAt().toEpochMilli());
                video.setInt(4, record.gridWidth()); video.setInt(5, record.gridHeight()); video.setInt(6, record.fps()); video.setInt(7, record.frameCount()); video.executeUpdate();
                for (var entry : record.mapIds().entrySet()) { maps.setString(1, record.videoId().toString()); maps.setInt(2, entry.getKey().x()); maps.setInt(3, entry.getKey().y()); maps.setInt(4, entry.getValue()); maps.addBatch(); }
                maps.executeBatch();
                for (var entry : ranges.entrySet()) { int[] key = parseTileKey(entry.getKey()); data.setString(1, record.videoId().toString()); data.setInt(2, key[0]); data.setInt(3, key[1]); data.setInt(4, key[2]); data.setLong(5, entry.getValue().offset()); data.setInt(6, entry.getValue().length()); data.addBatch(); }
                data.executeBatch(); connection.commit();
            } catch (SQLException exception) {
                connection.rollback(); Files.deleteIfExists(finalFile); throw exception;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException exception) {
            deleteRecursively(stagingFile);
            throw new IOException(exception);
        } catch (IOException exception) {
            deleteRecursively(stagingFile);
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
        try (PreparedStatement query = connection.prepareStatement("select offset,length from video_tile_data where video_id=? and frame=? and x=? and y=?")) {
            query.setString(1, videoId.toString()); query.setInt(2, frameIndex); query.setInt(3, coordinate.x()); query.setInt(4, coordinate.y());
            try (ResultSet result = query.executeQuery()) { if (!result.next()) throw new IOException("missing video tile index"); return TileContainer.read(ShardedMediaLayout.container(videoDirectory, videoId), new TileContainer.Range(result.getLong(1), result.getInt(2))); }
        } catch (SQLException exception) { throw new IOException("could not read video tile index", exception); }
    }

    @Override public void close() throws IOException { try { connection.close(); } catch (SQLException exception) { throw new IOException(exception); } }

    private static String tileKey(int frame, int x, int y) { return frame + "-" + x + "-" + y; }
    private static int[] parseTileKey(String key) { String[] values = key.split("-", 3); return new int[] {Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])}; }
    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException exception) { throw new DeleteFailure(exception); } }); }
        catch (DeleteFailure exception) { throw exception.getCause(); }
    }
    private static final class DeleteFailure extends RuntimeException { DeleteFailure(IOException cause) { super(cause); } @Override public IOException getCause() { return (IOException) super.getCause(); } }
}
