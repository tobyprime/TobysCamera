package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.fabric.camera.AspectRatio;
import dev.tobyscamera.fabric.camera.MapTileEncoder;
import dev.tobyscamera.fabric.camera.PrintLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VideoUploadControllerTest {
    @Test
    void sendsBeginThenRateLimitedFrameTilesThenFinish() throws Exception {
        List<CameraPacket> sent = new ArrayList<>();
        try (TemporaryVideoRecording recording = TemporaryVideoRecording.create(Files.createTempDirectory("camera-video"))) {
            recording.append(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
            VideoEncoder encoder = new VideoEncoder(recording, new VideoFrameRange(0, 0, 1), new PrintLayout(1, 1, new AspectRatio(1, 1)), MapTileEncoder.DitheringMode.OFF);
            VideoUploadController controller = new VideoUploadController(sent::add, () -> 1_000L);
            controller.begin(encoder, recording, 10);
            controller.handleServerPacket(new Packets.VideoGranted(UUID.randomUUID(), 2_000L, 16_384, 2));
            controller.tick();

            assertEquals(Packets.VideoBegin.class, sent.get(0).getClass());
            assertEquals(Packets.VideoTileChunk.class, sent.get(1).getClass());
            assertEquals(Packets.VideoTileChunk.class, sent.get(2).getClass());
            assertEquals(Packets.VideoFinish.class, sent.get(3).getClass());
        }
    }
}
