package dev.tobyscamera.fabric.video;

import com.mojang.logging.LogUtils;
import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.UploadProgress;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/** Sends encoded video tiles incrementally after the server's video upload grant. */
public final class VideoUploadController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNKS_PER_TICK = 8;
    private final Consumer<CameraPacket> sender;
    private final LongSupplier clock;
    private final Consumer<String> failureHandler;
    private VideoEncoder encoder;
    private TemporaryVideoRecording recording;
    private UUID token;
    private ChunkTokenBucket allowance;
    private byte[] previewPixels;
    private int previewOffset, frame, tile, offset;
    private boolean finishSent;
    private int completedChunks;
    private int totalChunks;

    public VideoUploadController(Consumer<CameraPacket> sender, LongSupplier clock) { this(sender, clock, ignored -> { }); }

    public VideoUploadController(Consumer<CameraPacket> sender, LongSupplier clock, Consumer<String> failureHandler) {
        this.sender = sender;
        this.clock = clock;
        this.failureHandler = failureHandler;
    }

    public boolean begin(VideoEncoder encoder, TemporaryVideoRecording recording, int fps) {
        if (this.encoder != null || encoder == null || recording == null || fps < 1) {
            LOGGER.warn("Video upload was not started: active={}, encoderPresent={}, recordingPresent={}, fps={}",
                    this.encoder != null, encoder != null, recording != null, fps);
            return false;
        }
        this.encoder = encoder; this.recording = recording;
        this.completedChunks = 0;
        this.totalChunks = encoder.frameCount() * encoder.gridWidth() * encoder.gridHeight() * 2 + 2;
        LOGGER.info("Sending VideoBegin for {} frame(s), {}x{} map(s) per frame, at {} FPS.",
                encoder.frameCount(), encoder.gridWidth(), encoder.gridHeight(), fps);
        sender.accept(new Packets.VideoBegin(encoder.gridWidth(), encoder.gridHeight(), fps, encoder.frameCount()));
        return true;
    }

    public void handleServerPacket(CameraPacket packet) {
        if (packet instanceof Packets.VideoGranted grant) {
            if (encoder == null || clock.getAsLong() >= grant.expiresAtEpochMillis() || grant.tileBytes() != 16_384) {
                LOGGER.warn("Discarding unusable VideoGranted: active={}, expiresAt={}, tileBytes={}",
                        encoder != null, grant.expiresAtEpochMillis(), grant.tileBytes());
                clear();
                return;
            }
            LOGGER.info("Received VideoGranted; starting tile upload at {} chunks/s.", grant.maxChunksPerSecond());
            token = grant.token(); allowance = new ChunkTokenBucket(grant.maxChunksPerSecond(), clock.getAsLong());
        } else if (packet instanceof Packets.UploadRejected rejection) {
            LOGGER.warn("Video upload rejected by server: {}", rejection.reason());
            clear();
        } else if (packet instanceof Packets.VideoCreated) {
            LOGGER.info("Video upload completed successfully.");
            clear();
        }
    }

    public void tick() {
        if (encoder == null || token == null || allowance == null || finishSent) return;
        int available = allowance.takeAvailable(clock.getAsLong(), CHUNKS_PER_TICK);
        try {
            while (available-- > 0 && !complete()) {
                if (!previewComplete()) sendPreviewChunk(); else sendNextChunk();
            }
            if (complete() && !finishSent) { sender.accept(new Packets.VideoFinish(token)); finishSent = true; }
        } catch (IOException exception) {
            clear();
            failureHandler.accept("Could not encode video upload");
        }
    }

    private boolean complete() { return frame >= encoder.frameCount(); }
    private boolean previewComplete() { return previewOffset == 16_384; }

    private void sendPreviewChunk() throws IOException {
        if (previewPixels == null) previewPixels = new MapTileEncoder().bagPreview(encoder.frame(0));
        int length = Math.min(8_192, previewPixels.length - previewOffset);
        sender.accept(new Packets.VideoPreviewChunk(token, previewOffset,
                java.util.Arrays.copyOfRange(previewPixels, previewOffset, previewOffset + length)));
        completedChunks++;
        previewOffset += length;
    }

    private void sendNextChunk() throws IOException {
        byte[] pixels = encoder.frame(frame).tiles().get(tile);
        int length = Math.min(8_192, pixels.length - offset);
        byte[] chunk = java.util.Arrays.copyOfRange(pixels, offset, offset + length);
        int tileX = tile % encoder.gridWidth(), tileY = tile / encoder.gridWidth();
        sender.accept(new Packets.VideoTileChunk(token, frame, tileX, tileY, offset, chunk));
        completedChunks++;
        offset += length;
        if (offset == pixels.length) { offset = 0; tile++; if (tile == encoder.gridWidth() * encoder.gridHeight()) { tile = 0; frame++; } }
    }

    public void clear() {
        encoder = null; token = null; allowance = null; previewPixels = null; previewOffset = frame = tile = offset = 0; finishSent = false; completedChunks = totalChunks = 0;
        if (recording != null) try { recording.close(); } catch (IOException ignored) { }
        recording = null;
    }
    public UploadProgress progress() { return new UploadProgress(completedChunks, totalChunks); }
}
