package dev.tobyscamera.folia.upload;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.common.upload.UploadFailure;
import dev.tobyscamera.common.upload.UploadGrant;
import dev.tobyscamera.common.upload.VideoUploadSession;
import dev.tobyscamera.common.upload.RateLimit;
import dev.tobyscamera.common.upload.SlidingWindowRateLimiter;
import dev.tobyscamera.common.video.VideoFrameRate;
import dev.tobyscamera.folia.camera.CameraFilmService;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

public final class VideoUploadCoordinator {
    private final PluginSettings settings;
    private final CameraFilmService films;
    private final UploadCoordinator.PluginPayloadGatewaySender sender;
    private final CompletedVideoUploadHandler completion;
    private final Consumer<Player> discardPhotoCaptureIntent;
    private final Map<UUID, UploadGrant> grants = new HashMap<>();
    private final Map<UUID, VideoUploadSession> sessions = new HashMap<>();
    private final Map<UUID, SlidingWindowRateLimiter> chunkLimiters = new HashMap<>();
    private final Map<UUID, PhotoMetadata> metadata = new HashMap<>();

    public VideoUploadCoordinator(PluginSettings settings, CameraFilmService films, UploadCoordinator.PluginPayloadGatewaySender sender, CompletedVideoUploadHandler completion, Consumer<Player> discardPhotoCaptureIntent) {
        this.settings = settings; this.films = films; this.sender = sender; this.completion = completion; this.discardPhotoCaptureIntent = discardPhotoCaptureIntent;
    }

    public void handle(Player player, CameraPacket packet) {
        switch (packet) {
            case Packets.VideoBegin begin -> begin(player, begin);
            case Packets.VideoTileChunk chunk -> append(player, chunk);
            case Packets.VideoFinish finish -> finish(player, finish);
            default -> { }
        }
    }

    private void begin(Player player, Packets.VideoBegin begin) {
        discardPhotoCaptureIntent.accept(player);
        var camera = films.heldCamera(player);
        if (camera == null) { sender.send(player, new Packets.UploadRejected("A tagged camera must be held")); return; }
        int maximum = films.maximumForFilm(camera, settings.maxGridSize());
        if (begin.gridWidth() < 1 || begin.gridHeight() < 1 || begin.gridWidth() > maximum || begin.gridHeight() > maximum
                || !VideoFrameRate.isSupported(begin.fps()) || begin.fps() > films.maximumVideoFps(camera, settings.videoMaxFps()) || begin.frameCount() < 1 || begin.frameCount() > settings.videoMaxFrames()) {
            sender.send(player, new Packets.UploadRejected("Video settings exceed camera or server limits")); return;
        }
        int filmCost;
        try { filmCost = Math.multiplyExact(Math.multiplyExact(begin.gridWidth(), begin.gridHeight()), begin.frameCount()); }
        catch (ArithmeticException exception) { sender.send(player, new Packets.UploadRejected("Video is too large")); return; }
        if (!films.consume(camera, filmCost)) { sender.send(player, new Packets.UploadRejected("Camera does not have enough film")); return; }
        Instant now = Instant.now(); UUID token = UUID.randomUUID();
        UploadGrant grant = new UploadGrant(token, player.getUniqueId(), now, now.plusSeconds(settings.tokenTtlSeconds()), maximum);
        try {
            grants.put(token, grant); sessions.put(token, new VideoUploadSession(grant, begin.gridWidth(), begin.gridHeight(), begin.fps(), begin.frameCount())); metadata.put(token, PhotoMetadata.capture(player));
            chunkLimiters.put(token, new SlidingWindowRateLimiter(new RateLimit(settings.videoUploadChunksPerSecond(), Integer.MAX_VALUE)));
            sender.send(player, new Packets.VideoGranted(token, grant.expiresAt().toEpochMilli(), 16_384, settings.videoUploadChunksPerSecond()));
        } catch (UploadFailure exception) { grants.remove(token); sender.send(player, new Packets.UploadRejected(exception.getMessage())); }
    }

    private void append(Player player, Packets.VideoTileChunk chunk) {
        VideoUploadSession session = valid(player, chunk.token()); if (session == null) return;
        if (!chunkLimiters.get(chunk.token()).tryAcquire(player.getUniqueId(), Instant.now()).allowed()) {
            clear(chunk.token());
            sender.send(player, new Packets.UploadRejected("Video upload chunk rate exceeded"));
            return;
        }
        try { session.append(player.getUniqueId(), chunk.frameIndex(), chunk.tileX(), chunk.tileY(), chunk.offset(), chunk.data()); }
        catch (UploadFailure exception) { sender.send(player, new Packets.UploadRejected(exception.getMessage())); }
    }
    private void finish(Player player, Packets.VideoFinish finish) {
        VideoUploadSession session = valid(player, finish.token()); if (session == null) return;
        if (!session.isComplete()) { sender.send(player, new Packets.UploadRejected("Every video tile must be complete")); return; }
        PhotoMetadata captured = metadata.get(finish.token());
        clear(finish.token());
        completion.accept(player, session, captured);
    }
    private VideoUploadSession valid(Player player, UUID token) {
        UploadGrant grant = grants.get(token);
        if (grant == null || !grant.isValidFor(player.getUniqueId(), Instant.now())) { player.kick(net.kyori.adventure.text.Component.text(settings.invalidTokenKickMessage())); return null; }
        VideoUploadSession session = sessions.get(token);
        if (session == null) { player.kick(net.kyori.adventure.text.Component.text(settings.invalidTokenKickMessage())); }
        return session;
    }

    private void clear(UUID token) {
        grants.remove(token);
        sessions.remove(token);
        chunkLimiters.remove(token);
        metadata.remove(token);
    }
    @FunctionalInterface public interface CompletedVideoUploadHandler { void accept(Player player, VideoUploadSession session, PhotoMetadata metadata); }
}
