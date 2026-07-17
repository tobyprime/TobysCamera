package dev.tobyscamera.fabric.video;

import com.mojang.logging.LogUtils;
import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.UploadProgress;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/** Sends encoded video tiles incrementally after the server's video upload grant. */
public final class VideoUploadController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNKS_PER_TICK = 8;
    private static final Executor FRAME_ENCODER_EXECUTOR = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "TobysCamera video encoder");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final Consumer<CameraPacket> sender;
    private final LongSupplier clock;
    private final Consumer<String> failureHandler;
    private final AsyncVideoFrameEncoder frameEncoder;
    private VideoEncoder encoder;
    private TemporaryVideoRecording recording;
    private UUID token;
    private ChunkTokenBucket allowance;
    private byte[] previewPixels;
    private MapTileEncoder.EncodedPhoto encodedFrame;
    private int encodedFrameIndex = -1;
    private int previewOffset, frame, tile, offset;
    private boolean finishSent;
    private int completedChunks;
    private int totalChunks;

    public VideoUploadController(Consumer<CameraPacket> sender, LongSupplier clock) { this(sender, clock, ignored -> { }); }

    public VideoUploadController(Consumer<CameraPacket> sender, LongSupplier clock, Consumer<String> failureHandler) {
        this(sender, clock, failureHandler, FRAME_ENCODER_EXECUTOR);
    }

    VideoUploadController(Consumer<CameraPacket> sender, LongSupplier clock, Consumer<String> failureHandler, Executor frameExecutor) {
        this.sender = sender;
        this.clock = clock;
        this.failureHandler = failureHandler;
        this.frameEncoder = new AsyncVideoFrameEncoder(frameExecutor);
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
        try {
            int sent = 0;
            while (sent < CHUNKS_PER_TICK && !complete()) {
                if (!frameReady()) break;
                if (allowance.takeAvailable(clock.getAsLong(), 1) == 0) break;
                if (!previewComplete()) sendPreviewChunk(); else sendNextChunk();
                sent++;
            }
            if (complete() && !finishSent) { sender.accept(new Packets.VideoFinish(token)); finishSent = true; }
        } catch (IOException exception) {
            clear();
            failureHandler.accept("Could not encode video upload");
        }
    }

    private boolean complete() { return frame >= encoder.frameCount(); }
    private boolean previewComplete() { return previewOffset == 16_384; }

    private boolean frameReady() throws IOException {
        if (encodedFrameIndex == frame) return true;
        frameEncoder.request(frame, encoder::frame);
        MapTileEncoder.EncodedPhoto completedFrame = frameEncoder.poll(frame);
        if (completedFrame == null) return false;
        encodedFrame = completedFrame;
        encodedFrameIndex = frame;
        return true;
    }

    private void sendPreviewChunk() {
        if (previewPixels == null) previewPixels = new MapTileEncoder().bagPreview(encodedFrame);
        int length = Math.min(8_192, previewPixels.length - previewOffset);
        sender.accept(new Packets.VideoPreviewChunk(token, previewOffset,
                java.util.Arrays.copyOfRange(previewPixels, previewOffset, previewOffset + length)));
        completedChunks++;
        previewOffset += length;
    }

    private void sendNextChunk() {
        byte[] pixels = encodedFrame.tiles().get(tile);
        int length = Math.min(8_192, pixels.length - offset);
        byte[] chunk = java.util.Arrays.copyOfRange(pixels, offset, offset + length);
        int tileX = tile % encoder.gridWidth(), tileY = tile / encoder.gridWidth();
        sender.accept(new Packets.VideoTileChunk(token, frame, tileX, tileY, offset, chunk));
        completedChunks++;
        offset += length;
        if (offset == pixels.length) { offset = 0; tile++; if (tile == encoder.gridWidth() * encoder.gridHeight()) { tile = 0; frame++; } }
    }

    public void clear() {
        frameEncoder.clear();
        encoder = null; token = null; allowance = null; previewPixels = null; encodedFrame = null; encodedFrameIndex = -1;
        previewOffset = frame = tile = offset = 0; finishSent = false; completedChunks = totalChunks = 0;
        if (recording != null) try { recording.close(); } catch (IOException ignored) { }
        recording = null;
    }
    public UploadProgress progress() { return new UploadProgress(completedChunks, totalChunks); }
}
