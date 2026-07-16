package dev.tobyscamera.fabric.camera;

import dev.tobyscamera.common.protocol.CameraPacket;
import com.mojang.logging.LogUtils;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.net.CameraPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;

public final class PhotoUploadController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MapTileEncoder encoder = new MapTileEncoder();
    private UUID token;
    private long expiresAt;
    private int gridSize;

    public void requestCapture() { send(new Packets.CaptureIntent()); }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.UploadGranted grant) { token = grant.token(); expiresAt = grant.expiresAtEpochMillis(); gridSize = grant.gridSize(); }
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated) clearGrant();
    }

    public boolean confirm(CapturedFrame frame) {
        if (token == null || System.currentTimeMillis() >= expiresAt || frame.gridSize() != gridSize) { clearGrant(); return false; }
        MapTileEncoder.EncodedPhoto photo = encoder.encode(frame);
        send(new Packets.UploadBegin(token, photo.gridWidth(), photo.gridHeight()));
        for (int y = 0; y < photo.gridHeight(); y++) for (int x = 0; x < photo.gridWidth(); x++) {
            byte[] tile = photo.tiles().get(y * photo.gridWidth() + x);
            for (int offset = 0; offset < tile.length; offset += 8_192) {
                int length = Math.min(8_192, tile.length - offset);
                byte[] part = java.util.Arrays.copyOfRange(tile, offset, offset + length);
                send(new Packets.UploadTileChunk(token, x, y, offset, part));
            }
        }
        send(new Packets.UploadFinish(token));
        clearGrant();
        return true;
    }

    public boolean hasValidGrant() { return token != null && System.currentTimeMillis() < expiresAt; }
    public int gridSize() { return gridSize; }
    public void clearGrant() { token = null; expiresAt = 0; gridSize = 0; }

    private static void send(CameraPacket packet) {
        LOGGER.info("Sending camera packet {}.", packet.getClass().getSimpleName());
        ClientPlayNetworking.send(new CameraPayload(dev.tobyscamera.common.protocol.PacketCodec.encode(packet)));
    }
}
