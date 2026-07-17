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
    private static final int CHUNKS_PER_TICK = 8;
    private MapTileEncoder.EncodedPhoto pendingPhoto;
    private UUID token;
    private int tile, offset, completedChunks, totalChunks;
    private boolean finishSent;

    public void requestCapture() { send(new Packets.CaptureIntent()); }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.UploadGranted grant) startUpload(grant);
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated) clearPending();
    }

    public boolean confirm(MapTileEncoder.EncodedPhoto photo) {
        if (pendingPhoto != null || photo == null || photo.gridWidth() < 1 || photo.gridHeight() < 1 || photo.tiles().size() != photo.gridWidth() * photo.gridHeight()) return false;
        pendingPhoto = photo;
        totalChunks = photo.tiles().size() * 2;
        completedChunks = tile = offset = 0;
        finishSent = false;
        send(new Packets.UploadBegin(photo.gridWidth(), photo.gridHeight()));
        return true;
    }

    private void startUpload(Packets.UploadGranted grant) {
        if (pendingPhoto == null || System.currentTimeMillis() >= grant.expiresAtEpochMillis()) { clearPending(); return; }
        token = grant.token();
    }

    public void tick() {
        if (pendingPhoto == null || token == null || finishSent) return;
        for (int count = 0; count < CHUNKS_PER_TICK && tile < pendingPhoto.tiles().size(); count++) {
            byte[] pixels = pendingPhoto.tiles().get(tile);
            int length = Math.min(8_192, pixels.length - offset);
            send(new Packets.UploadTileChunk(token, tile % pendingPhoto.gridWidth(), tile / pendingPhoto.gridWidth(), offset,
                    java.util.Arrays.copyOfRange(pixels, offset, offset + length)));
            completedChunks++;
            offset += length;
            if (offset == pixels.length) { offset = 0; tile++; }
        }
        if (tile == pendingPhoto.tiles().size()) { send(new Packets.UploadFinish(token)); finishSent = true; }
    }

    public UploadProgress progress() { return new UploadProgress(completedChunks, totalChunks); }
    public void clearPending() { pendingPhoto = null; token = null; tile = offset = completedChunks = totalChunks = 0; finishSent = false; }

    private static void send(CameraPacket packet) {
        LOGGER.info("Sending camera packet {}.", packet.getClass().getSimpleName());
        ClientPlayNetworking.send(new CameraPayload(dev.tobyscamera.common.protocol.PacketCodec.encode(packet)));
    }
}
