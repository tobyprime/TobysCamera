package dev.tobyscamera.folia.upload;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.common.upload.RateLimit;
import dev.tobyscamera.common.upload.SlidingWindowRateLimiter;
import dev.tobyscamera.common.upload.UploadFailure;
import dev.tobyscamera.common.upload.UploadGrant;
import dev.tobyscamera.common.upload.UploadSession;
import dev.tobyscamera.folia.camera.CameraValidator;
import dev.tobyscamera.folia.config.PluginSettings;
import dev.tobyscamera.folia.net.PluginPayloadGateway;
import dev.tobyscamera.folia.sound.ShutterSoundService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class UploadCoordinator {
    private final PluginSettings settings;
    private final CameraValidator cameraValidator;
    private final PluginPayloadGatewaySender sender;
    private final CompletedUploadHandler completionHandler;
    private final ShutterSoundService shutterSound;
    private final SlidingWindowRateLimiter rateLimiter;
    private final Map<UUID, UploadGrant> grants = new HashMap<>();
    private final Map<UUID, UploadSession> sessions = new HashMap<>();

    public UploadCoordinator(PluginSettings settings, CameraValidator cameraValidator,
            PluginPayloadGatewaySender sender, CompletedUploadHandler completionHandler, ShutterSoundService shutterSound) {
        this.settings = settings;
        this.cameraValidator = cameraValidator;
        this.sender = sender;
        this.completionHandler = completionHandler;
        this.shutterSound = shutterSound;
        this.rateLimiter = new SlidingWindowRateLimiter(new RateLimit(settings.perSecond(), settings.perMinute()));
    }

    public void handle(Player player, CameraPacket packet) {
        switch (packet) {
            case Packets.CaptureIntent ignored -> capture(player);
            case Packets.UploadBegin begin -> begin(player, begin);
            case Packets.UploadTileChunk chunk -> append(player, chunk);
            case Packets.UploadFinish finish -> finish(player, finish);
            default -> { }
        }
    }

    private void capture(Player player) {
        if (!cameraValidator.isHoldingCamera(player)) {
            sender.send(player, new Packets.UploadRejected("A tagged camera must be held"));
            return;
        }
        Instant now = Instant.now();
        var rateResult = rateLimiter.tryAcquire(player.getUniqueId(), now);
        if (!rateResult.allowed()) {
            sender.send(player, new Packets.RateLimited(rateResult.retryAfterMillis()));
            return;
        }
        UUID token = UUID.randomUUID();
        grants.put(token, new UploadGrant(token, player.getUniqueId(), now, now.plusSeconds(settings.tokenTtlSeconds()), settings.maxGridSize()));
        shutterSound.playFor(player);
        sender.send(player, new Packets.UploadGranted(token, now.plusSeconds(settings.tokenTtlSeconds()).toEpochMilli(),
                settings.maxGridSize(), UploadSession.TILE_BYTES));
    }

    private void begin(Player player, Packets.UploadBegin begin) {
        UploadGrant grant = validGrantOrKick(player, begin.token());
        if (grant == null) return;
        if (sessions.containsKey(begin.token())) {
            kick(player);
            return;
        }
        if (!cameraValidator.isHoldingCamera(player)) {
            sender.send(player, new Packets.UploadRejected("A tagged camera must still be held"));
            return;
        }
        try {
            sessions.put(begin.token(), new UploadSession(grant, begin.gridWidth(), begin.gridHeight()));
        } catch (UploadFailure exception) {
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
        completionHandler.accept(player, session);
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
