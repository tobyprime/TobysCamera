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
    private MapTileEncoder.EncodedPhoto pendingPhoto;
    private UUID token;

    public void requestCapture() { send(new Packets.CaptureIntent()); }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.UploadGranted grant) startUpload(grant);
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated) clearPending();
    }

    public boolean confirm(CapturedFrame frame, int printSize) {
        if (pendingPhoto != null || printSize < 1 || printSize > frame.gridSize()) return false;
        PrintLayout layout = PrintLayout.forMaximumSide(printSize, frame.composition().aspectRatio());
        MapTileEncoder.EncodedPhoto photo = encoder.encode(new PrintCanvasProcessor().process(frame.image(), layout));
        pendingPhoto = photo;
        send(new Packets.UploadBegin(photo.gridWidth(), photo.gridHeight()));
        return true;
    }

    private void startUpload(Packets.UploadGranted grant) {
        if (pendingPhoto == null || System.currentTimeMillis() >= grant.expiresAtEpochMillis()) { clearPending(); return; }
        token = grant.token();
        MapTileEncoder.EncodedPhoto photo = pendingPhoto;
        for (int y = 0; y < photo.gridHeight(); y++) for (int x = 0; x < photo.gridWidth(); x++) {
            byte[] tile = photo.tiles().get(y * photo.gridWidth() + x);
            for (int offset = 0; offset < tile.length; offset += 8_192) {
                int length = Math.min(8_192, tile.length - offset);
                byte[] part = java.util.Arrays.copyOfRange(tile, offset, offset + length);
                send(new Packets.UploadTileChunk(token, x, y, offset, part));
            }
        }
        send(new Packets.UploadFinish(token));
        clearPending();
    }

    public void clearPending() { pendingPhoto = null; token = null; }

    private static void send(CameraPacket packet) {
        LOGGER.info("Sending camera packet {}.", packet.getClass().getSimpleName());
        ClientPlayNetworking.send(new CameraPayload(dev.tobyscamera.common.protocol.PacketCodec.encode(packet)));
    }
}
