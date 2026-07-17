package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VideoUploadControllerTest {
    @Test
    void waitsForTheBackgroundFrameBeforeSendingTileChunks() throws Exception {
        List<CameraPacket> sent = new ArrayList<>();
        var queued = new ArrayDeque<Runnable>();
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            VideoTestImages.append(recording, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 0, 1),
                    new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);
            AtomicLong now = new AtomicLong(1_000L);
            VideoUploadController controller = new VideoUploadController(sent::add, now::get, ignored -> { }, queued::add);
            controller.begin(encoder, recording, 10);
            controller.handleServerPacket(new Packets.VideoGranted(UUID.randomUUID(), 2_000L, 16_384, 100));

            controller.tick();

            assertEquals(0, tileChunks(sent));
            queued.remove().run();
            controller.tick();
            assertEquals(2, tileChunks(sent));
        }
    }

    @Test
    void reportsLocalEncodingFailureInsteadOfSilentlyStayingInUploadState() throws Exception {
        List<CameraPacket> sent = new ArrayList<>();
        TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"));
        VideoTestImages.append(recording, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
        VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 0, 1),
                new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);
        AtomicReference<String> failure = new AtomicReference<>();
        VideoUploadController controller = new VideoUploadController(sent::add, () -> 1_000L, failure::set, Runnable::run);
        controller.begin(encoder, recording, 10);
        controller.handleServerPacket(new Packets.VideoGranted(UUID.randomUUID(), 2_000L, 16_384, 2));
        recording.close();

        controller.tick();

        assertEquals("Could not encode video upload", failure.get());
    }

    @Test
    void sendsBeginThenRateLimitedFrameTilesThenFinish() throws Exception {
        List<CameraPacket> sent = new ArrayList<>();
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            VideoTestImages.append(recording, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 0, 1), new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);
            AtomicLong now = new AtomicLong(1_000L);
            VideoUploadController controller = new VideoUploadController(sent::add, now::get, ignored -> { }, Runnable::run);
            controller.begin(encoder, recording, 10);
            controller.handleServerPacket(new Packets.VideoGranted(UUID.randomUUID(), 2_000L, 16_384, 2));
            controller.tick();
            now.addAndGet(1_000L);
            controller.tick();

            assertEquals(Packets.VideoBegin.class, sent.get(0).getClass());
            assertEquals(Packets.VideoPreviewChunk.class, sent.get(1).getClass());
            assertEquals(Packets.VideoPreviewChunk.class, sent.get(2).getClass());
            assertEquals(Packets.VideoTileChunk.class, sent.get(3).getClass());
            assertEquals(Packets.VideoTileChunk.class, sent.get(4).getClass());
            assertEquals(Packets.VideoFinish.class, sent.get(5).getClass());
            assertEquals(4, controller.progress().completedChunks());
            assertEquals(4, controller.progress().totalChunks());
        }
    }

    @Test
    void limitsTheInitialUploadBurstSoTheHudCanRenderIntermediateProgress() throws Exception {
        List<CameraPacket> sent = new ArrayList<>();
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            for (int index = 0; index < 5; index++) {
                VideoTestImages.append(recording, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
            }
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 4, 5),
                    new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);
            VideoUploadController controller = new VideoUploadController(sent::add, () -> 1_000L, ignored -> { }, Runnable::run);

            controller.begin(encoder, recording, 10);
            controller.handleServerPacket(new Packets.VideoGranted(UUID.randomUUID(), 2_000L, 16_384, 100));
            controller.tick();

            assertEquals(6, sent.stream().filter(Packets.VideoTileChunk.class::isInstance).count());
            assertEquals(8, controller.progress().completedChunks());
            assertEquals(12, controller.progress().totalChunks());
        }
    }

    private static long tileChunks(List<CameraPacket> packets) {
        return packets.stream().filter(Packets.VideoTileChunk.class::isInstance).count();
    }
}
