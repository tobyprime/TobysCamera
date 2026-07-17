package dev.tobyscamera.common.protocol;

public sealed interface CameraPacket permits Packets.CaptureIntent, Packets.UploadGranted,
        Packets.RateLimited, Packets.UploadBegin, Packets.UploadPreviewChunk, Packets.UploadTileChunk,
        Packets.UploadFinish, Packets.PhotoCreated, Packets.UploadRejected,
        Packets.VideoBegin, Packets.VideoGranted, Packets.VideoPreviewChunk, Packets.VideoTileChunk,
        Packets.VideoFinish, Packets.VideoCreated {
    PacketType type();
}
