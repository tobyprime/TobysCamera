package dev.tobyscamera.fabric.video;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class VideoCaptureServiceTest { @Test void schedulesFramesAtSelectedFpsAndStopsCleanly(){var service=new VideoCaptureService();service.start(10,1_000);assertTrue(service.captureDue(1_000));assertFalse(service.captureDue(1_050));assertTrue(service.captureDue(1_100));service.stop();assertFalse(service.captureDue(2_000));} }
