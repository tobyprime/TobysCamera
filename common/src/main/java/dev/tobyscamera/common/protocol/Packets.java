package dev.tobyscamera.common.protocol;

import java.util.List;
import java.util.UUID;

public final class Packets {
    private Packets() {
    }

    public record CaptureIntent() implements CameraPacket {
        @Override public PacketType type() { return PacketType.CAPTURE_INTENT; }
    }

    /** Sent only after the server has accepted and charged an UploadBegin request. */
    public record UploadGranted(UUID token, long expiresAtEpochMillis, int tileBytes, int maxChunksPerSecond)
            implements CameraPacket {
        @Override public PacketType type() { return PacketType.UPLOAD_GRANTED; }
    }

    public record RateLimited(long retryAfterMillis) implements CameraPacket {
        @Override public PacketType type() { return PacketType.RATE_LIMITED; }
    }

    /** Requests an upload session. It deliberately has no token: the server issues one after charging film. */
    public record UploadBegin(int gridWidth, int gridHeight, PhotoPresentation presentation) implements CameraPacket {
        public UploadBegin(int gridWidth, int gridHeight) { this(gridWidth, gridHeight, PhotoPresentation.DEFAULT); }
        @Override public PacketType type() { return PacketType.UPLOAD_BEGIN; }
    }

    public record UploadPreviewChunk(UUID token, int offset, byte[] data) implements CameraPacket {
        public UploadPreviewChunk {
            data = data.clone();
        }

        @Override public byte[] data() { return data.clone(); }
        @Override public PacketType type() { return PacketType.UPLOAD_PREVIEW_CHUNK; }
    }

    public record UploadTileChunk(UUID token, int tileX, int tileY, int offset, byte[] data)
            implements CameraPacket {
        public UploadTileChunk {
            data = data.clone();
        }

        @Override public byte[] data() { return data.clone(); }
        @Override public PacketType type() { return PacketType.UPLOAD_TILE_CHUNK; }
    }

    public record UploadFinish(UUID token) implements CameraPacket {
        @Override public PacketType type() { return PacketType.UPLOAD_FINISH; }
    }

    public record PhotoCreated(UUID photoId, List<Integer> mapIds, int gridWidth, int gridHeight)
            implements CameraPacket {
        public PhotoCreated {
            mapIds = List.copyOf(mapIds);
        }

        @Override public PacketType type() { return PacketType.PHOTO_CREATED; }
    }

    public record UploadRejected(String reason) implements CameraPacket {
        @Override public PacketType type() { return PacketType.UPLOAD_REJECTED; }
    }

}
