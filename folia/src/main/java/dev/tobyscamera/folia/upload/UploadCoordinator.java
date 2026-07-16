package dev.tobyscamera.folia.upload;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.common.upload.RateLimit;
import dev.tobyscamera.common.upload.SlidingWindowRateLimiter;
import dev.tobyscamera.common.upload.UploadFailure;
import dev.tobyscamera.common.upload.UploadGrant;
import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.camera.CameraFilmService;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.sound.ShutterSoundService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UploadCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadCoordinator.class);
    private final PluginSettings settings;
    private final CameraFilmService films;
    private final PluginPayloadGatewaySender sender;
    private final CompletedUploadHandler completionHandler;
    private final ShutterSoundService shutterSound;
    private final SlidingWindowRateLimiter rateLimiter;
    private final Map<UUID, UploadGrant> grants = new HashMap<>();
    private final Map<UUID, UploadSession> sessions = new HashMap<>();
    private final Map<UUID, PhotoMetadata> capturedMetadata = new HashMap<>();
    private final Map<UUID, PhotoMetadata> uploadMetadata = new HashMap<>();

    public UploadCoordinator(PluginSettings settings, CameraFilmService films,
            PluginPayloadGatewaySender sender, CompletedUploadHandler completionHandler, ShutterSoundService shutterSound) {
        this.settings = settings;
        this.films = films;
        this.sender = sender;
        this.completionHandler = completionHandler;
        this.shutterSound = shutterSound;
        this.rateLimiter = new SlidingWindowRateLimiter(new RateLimit(settings.perSecond(), settings.perMinute()));
    }

    public void handle(Player player, CameraPacket packet) {
        LOGGER.info("Handling camera packet {} for {}.", packet.getClass().getSimpleName(), player.getName());
        switch (packet) {
            case Packets.CaptureIntent ignored -> capture(player);
            case Packets.UploadBegin begin -> begin(player, begin);
            case Packets.UploadTileChunk chunk -> append(player, chunk);
            case Packets.UploadFinish finish -> finish(player, finish);
            default -> { }
        }
    }

    private void capture(Player player) {
        if (films.heldCamera(player) == null) {
            LOGGER.info("Rejected capture intent for {} because no tagged camera is held.", player.getName());
            sender.send(player, new Packets.UploadRejected("A tagged camera must be held"));
            return;
        }
        shutterSound.playFor(player);
        capturedMetadata.put(player.getUniqueId(), PhotoMetadata.capture(player));
    }

    private void begin(Player player, Packets.UploadBegin begin) {
        var camera = films.heldCamera(player);
        if (camera == null) {
            sender.send(player, new Packets.UploadRejected("A tagged camera must be held"));
            return;
        }
        Instant now = Instant.now();
        var rateResult = rateLimiter.tryAcquire(player.getUniqueId(), now);
        if (!rateResult.allowed()) {
            sender.send(player, new Packets.RateLimited(rateResult.retryAfterMillis()));
            return;
        }
        int maximum = films.maximumForFilm(camera, settings.maxGridSize());
        if (begin.gridWidth() < 1 || begin.gridHeight() < 1
                || begin.gridWidth() > maximum || begin.gridHeight() > maximum) {
            sender.send(player, new Packets.UploadRejected("Camera does not have enough film for that print size"));
            return;
        }
        int filmCost = Math.multiplyExact(begin.gridWidth(), begin.gridHeight());
        if (!films.consume(camera, filmCost)) {
            sender.send(player, new Packets.UploadRejected("Camera does not have enough film"));
            return;
        }
        UUID token = UUID.randomUUID();
        UploadGrant grant = new UploadGrant(token, player.getUniqueId(), now,
                now.plusSeconds(settings.tokenTtlSeconds()), maximum);
        try {
            grants.put(token, grant);
            sessions.put(token, new UploadSession(grant, begin.gridWidth(), begin.gridHeight()));
            uploadMetadata.put(token, capturedMetadata.remove(player.getUniqueId()));
            sender.send(player, new Packets.UploadGranted(token, grant.expiresAt().toEpochMilli(), UploadSession.TILE_BYTES));
        } catch (UploadFailure exception) {
            grants.remove(token);
            sender.send(player, new Packets.UploadRejected(exception.getMessage()));
        }
    }

    private void append(Player player, Packets.UploadTileChunk chunk) {
        UploadSession session = validSessionOrKick(player, chunk.token());
        if (session == null) return;
        try {
            session.append(player.getUniqueId(), chunk.tileX(), chunk.tileY(), chunk.offset(), chunk.data());
        } catch (UploadFailure exception) {
            sender.send(player, new Packets.UploadRejected(exception.getMessage()));
        }
    }

    private void finish(Player player, Packets.UploadFinish finish) {
        UploadSession session = validSessionOrKick(player, finish.token());
        if (session == null) return;
        if (!session.isComplete()) {
            sender.send(player, new Packets.UploadRejected("Every map tile must be complete"));
            return;
        }
        grants.remove(finish.token());
        sessions.remove(finish.token());
        PhotoMetadata metadata = uploadMetadata.remove(finish.token());
        completionHandler.accept(player, session, metadata == null ? PhotoMetadata.capture(player) : metadata);
    }

    private UploadGrant validGrantOrKick(Player player, UUID token) {
        UploadGrant grant = grants.get(token);
        if (grant == null || !grant.isValidFor(player.getUniqueId(), Instant.now())) {
            kick(player);
            return null;
        }
        return grant;
    }

    private UploadSession validSessionOrKick(Player player, UUID token) {
        UploadGrant grant = validGrantOrKick(player, token);
        if (grant == null) return null;
        UploadSession session = sessions.get(token);
        if (session == null) {
            kick(player);
            return null;
        }
        return session;
    }

    private void kick(Player player) {
        player.kick(Component.text(settings.invalidTokenKickMessage()));
    }

    @FunctionalInterface
    public interface PluginPayloadGatewaySender {
        void send(Player player, CameraPacket packet);
    }
}
