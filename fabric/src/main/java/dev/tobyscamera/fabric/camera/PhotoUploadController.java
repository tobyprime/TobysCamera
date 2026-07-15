package dev.tobyscamera.fabric.camera;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.net.CameraPayload;
import java.awt.image.BufferedImage;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class PhotoUploadController {
    private final MapTileEncoder encoder = new MapTileEncoder();
    private UUID token;
    private long expiresAt;
    private BufferedImage preview;

    public void requestCapture() { send(new Packets.CaptureIntent()); }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.UploadGranted grant) { token = grant.token(); expiresAt = grant.expiresAtEpochMillis(); }
        if (packet instanceof Packets.RateLimited || packet instanceof Packets.UploadRejected || packet instanceof Packets.PhotoCreated) token = null;
    }

    public boolean confirm(BufferedImage image) {
        if (token == null || System.currentTimeMillis() >= expiresAt) { token = null; return false; }
        MapTileEncoder.EncodedPhoto photo = encoder.encode(image);
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
        token = null;
        return true;
    }

    public void setPreview(BufferedImage image) { preview = image; }
    public boolean confirmPreview() { return preview != null && confirm(preview); }
    public boolean awaitingPreview() { return token != null && preview == null; }

    private static void send(CameraPacket packet) { ClientPlayNetworking.send(new CameraPayload(dev.tobyscamera.common.protocol.PacketCodec.encode(packet))); }
}
