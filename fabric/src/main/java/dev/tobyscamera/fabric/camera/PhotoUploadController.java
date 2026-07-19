package dev.tobyscamera.fabric.camera;

import dev.tobyscamera.common.protocol.CameraPacket;
import com.mojang.logging.LogUtils;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.net.CameraPayload;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;

public final class PhotoUploadController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNKS_PER_TICK = 8;
    private final Consumer<CameraPacket> sender;
    private final LongSupplier clock;
    private MapTileEncoder.EncodedPhoto pendingPhoto;
    private byte[] previewPixels;
    private UUID token;
    private int previewOffset, tile, offset, completedChunks, totalChunks;
    private boolean finishSent;
    private ChunkTokenBucket allowance;

    public PhotoUploadController() { this(PhotoUploadController::send, System::currentTimeMillis); }

    PhotoUploadController(Consumer<CameraPacket> sender) { this(sender, System::currentTimeMillis); }

    PhotoUploadController(Consumer<CameraPacket> sender, LongSupplier clock) { this.sender = sender; this.clock = clock; }

    public void requestCapture() { sender.accept(new Packets.CaptureIntent()); }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.UploadGranted grant) startUpload(grant);
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated) clearPending();
    }

    public boolean confirm(MapTileEncoder.EncodedPhoto photo, byte[] preview) {
        if (pendingPhoto != null || photo == null || photo.gridWidth() < 1 || photo.gridHeight() < 1 || photo.tiles().size() != photo.gridWidth() * photo.gridHeight()) return false;
        if (preview == null || preview.length != 16_384) return false;
        pendingPhoto = photo;
        previewPixels = preview.clone();
        totalChunks = photo.tiles().size() * 2 + 2;
        completedChunks = previewOffset = tile = offset = 0;
        finishSent = false;
        sender.accept(new Packets.UploadBegin(photo.gridWidth(), photo.gridHeight()));
        return true;
    }

    private void startUpload(Packets.UploadGranted grant) {
        if (pendingPhoto == null || clock.getAsLong() >= grant.expiresAtEpochMillis() || grant.maxChunksPerSecond() < 1) { clearPending(); return; }
        token = grant.token();
        allowance = new ChunkTokenBucket(grant.maxChunksPerSecond(), clock.getAsLong());
    }

    public void tick() {
        if (pendingPhoto == null || token == null || allowance == null || finishSent) return;
        for (int count = allowance.takeAvailable(clock.getAsLong(), CHUNKS_PER_TICK); count > 0 && (previewOffset < 16_384 || tile < pendingPhoto.tiles().size()); count--) {
            if (previewOffset < 16_384) {
                int length = Math.min(8_192, previewPixels.length - previewOffset);
                sender.accept(new Packets.UploadPreviewChunk(token, previewOffset,
                        java.util.Arrays.copyOfRange(previewPixels, previewOffset, previewOffset + length)));
                completedChunks++;
                previewOffset += length;
                continue;
            }
            byte[] pixels = pendingPhoto.tiles().get(tile);
            int length = Math.min(8_192, pixels.length - offset);
            sender.accept(new Packets.UploadTileChunk(token, tile % pendingPhoto.gridWidth(), tile / pendingPhoto.gridWidth(), offset,
                    java.util.Arrays.copyOfRange(pixels, offset, offset + length)));
            completedChunks++;
            offset += length;
            if (offset == pixels.length) { offset = 0; tile++; }
        }
        if (previewOffset == 16_384 && tile == pendingPhoto.tiles().size()) { sender.accept(new Packets.UploadFinish(token)); finishSent = true; }
    }

    public UploadProgress progress() { return new UploadProgress(completedChunks, totalChunks); }
    public void clearPending() { pendingPhoto = null; previewPixels = null; token = null; allowance = null; previewOffset = tile = offset = completedChunks = totalChunks = 0; finishSent = false; }

    private static void send(CameraPacket packet) {
        LOGGER.info("Sending camera packet {}.", packet.getClass().getSimpleName());
        ClientPlayNetworking.send(new CameraPayload(dev.tobyscamera.common.protocol.PacketCodec.encode(packet)));
    }
}
