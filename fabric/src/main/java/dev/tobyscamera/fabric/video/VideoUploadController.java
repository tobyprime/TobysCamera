package dev.tobyscamera.fabric.video;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Sends encoded video tiles incrementally after the server's video upload grant. */
public final class VideoUploadController {
    private final Consumer<CameraPacket> sender;
    private final LongSupplier clock;
    private VideoEncoder encoder;
    private TemporaryVideoRecording recording;
    private UUID token;
    private ChunkTokenBucket allowance;
    private int frame, tile, offset;
    private boolean finishSent;

    public VideoUploadController(Consumer<CameraPacket> sender, LongSupplier clock) { this.sender = sender; this.clock = clock; }

    public boolean begin(VideoEncoder encoder, TemporaryVideoRecording recording, int fps) {
        if (this.encoder != null || encoder == null || recording == null || fps < 1) return false;
        this.encoder = encoder; this.recording = recording;
        sender.accept(new Packets.VideoBegin(encoder.gridWidth(), encoder.gridHeight(), fps, encoder.frameCount()));
        return true;
    }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.VideoGranted grant) {
            if (encoder == null || clock.getAsLong() >= grant.expiresAtEpochMillis() || grant.tileBytes() != 16_384) { clear(); return; }
            token = grant.token(); allowance = new ChunkTokenBucket(grant.maxChunksPerSecond(), clock.getAsLong());
        } else if (packet instanceof Packets.UploadRejected || packet instanceof Packets.VideoCreated) clear();
    }

    public void tick() {
        if (encoder == null || token == null || allowance == null || finishSent) return;
        int available = allowance.takeAvailable(clock.getAsLong(), Integer.MAX_VALUE);
        try {
            while (available-- > 0 && !complete()) sendNextChunk();
            if (complete() && !finishSent) { sender.accept(new Packets.VideoFinish(token)); finishSent = true; }
        } catch (IOException exception) { clear(); }
    }

    private boolean complete() { return frame >= encoder.frameCount(); }

    private void sendNextChunk() throws IOException {
        byte[] pixels = encoder.frame(frame).tiles().get(tile);
        int length = Math.min(8_192, pixels.length - offset);
        byte[] chunk = java.util.Arrays.copyOfRange(pixels, offset, offset + length);
        int tileX = tile % encoder.gridWidth(), tileY = tile / encoder.gridWidth();
        sender.accept(new Packets.VideoTileChunk(token, frame, tileX, tileY, offset, chunk));
        offset += length;
        if (offset == pixels.length) { offset = 0; tile++; if (tile == encoder.gridWidth() * encoder.gridHeight()) { tile = 0; frame++; } }
    }

    public void clear() {
        encoder = null; token = null; allowance = null; frame = tile = offset = 0; finishSent = false;
        if (recording != null) try { recording.close(); } catch (IOException ignored) { }
        recording = null;
    }
}
