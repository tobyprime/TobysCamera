package dev.tobyscamera.common.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PacketCodec {
    public static final byte VERSION = 2;
    public static final int MAX_CHUNK_BYTES = 8_192;
    private static final int MAX_STRING_BYTES = 512;

    private PacketCodec() {
    }

    public static byte[] encode(CameraPacket packet) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(packet.type().id());
            switch (packet) {
                case Packets.CaptureIntent ignored -> { }
                case Packets.UploadGranted value -> {
                    writeUuid(out, value.token());
                    out.writeLong(value.expiresAtEpochMillis());
                    out.writeInt(value.tileBytes());
                    out.writeInt(value.maxChunksPerSecond());
                }
                case Packets.RateLimited value -> out.writeLong(value.retryAfterMillis());
                case Packets.UploadBegin value -> {
                    out.writeInt(value.gridWidth());
                    out.writeInt(value.gridHeight());
                }
                case Packets.UploadPreviewChunk value -> writePreviewChunk(out, value.token(), value.offset(), value.data());
                case Packets.UploadTileChunk value -> {
                    if (value.data().length > MAX_CHUNK_BYTES) throw new ProtocolException("chunk exceeds limit");
                    writeUuid(out, value.token());
                    out.writeInt(value.tileX()); out.writeInt(value.tileY()); out.writeInt(value.offset());
                    out.writeInt(value.data().length); out.write(value.data());
                }
                case Packets.UploadFinish value -> writeUuid(out, value.token());
                case Packets.PhotoCreated value -> {
                    writeUuid(out, value.photoId()); out.writeInt(value.gridWidth()); out.writeInt(value.gridHeight());
                    out.writeInt(value.mapIds().size());
                    for (int id : value.mapIds()) out.writeInt(id);
                }
                case Packets.UploadRejected value -> writeString(out, value.reason());
                case Packets.VideoBegin value -> { out.writeInt(value.gridWidth()); out.writeInt(value.gridHeight()); out.writeInt(value.fps()); out.writeInt(value.frameCount()); }
                case Packets.VideoGranted value -> { writeUuid(out, value.token()); out.writeLong(value.expiresAtEpochMillis()); out.writeInt(value.tileBytes()); out.writeInt(value.maxChunksPerSecond()); }
                case Packets.VideoPreviewChunk value -> writePreviewChunk(out, value.token(), value.offset(), value.data());
                case Packets.VideoTileChunk value -> { if (value.data().length > MAX_CHUNK_BYTES) throw new ProtocolException("chunk exceeds limit"); writeUuid(out, value.token()); out.writeInt(value.frameIndex()); out.writeInt(value.tileX()); out.writeInt(value.tileY()); out.writeInt(value.offset()); out.writeInt(value.data().length); out.write(value.data()); }
                case Packets.VideoFinish value -> writeUuid(out, value.token());
                case Packets.VideoCreated value -> { writeUuid(out, value.videoId()); out.writeInt(value.gridWidth()); out.writeInt(value.gridHeight()); out.writeInt(value.fps()); out.writeInt(value.frameCount()); out.writeInt(value.mapIds().size()); for (int id : value.mapIds()) out.writeInt(id); }
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static CameraPacket decode(byte[] bytes) {
        return decode(ByteBuffer.wrap(bytes));
    }

    public static CameraPacket decode(ByteBuffer in) {
        try {
            if (in.get() != VERSION) throw new ProtocolException("unsupported protocol version");
            CameraPacket packet = switch (PacketType.fromId(in.get())) {
                case CAPTURE_INTENT -> new Packets.CaptureIntent();
                case UPLOAD_GRANTED -> new Packets.UploadGranted(readUuid(in), in.getLong(), in.getInt(), in.getInt());
                case RATE_LIMITED -> new Packets.RateLimited(in.getLong());
                case UPLOAD_BEGIN -> new Packets.UploadBegin(in.getInt(), in.getInt());
                case UPLOAD_PREVIEW_CHUNK -> readUploadPreviewChunk(in);
                case UPLOAD_TILE_CHUNK -> readChunk(in);
                case UPLOAD_FINISH -> new Packets.UploadFinish(readUuid(in));
                case PHOTO_CREATED -> readPhotoCreated(in);
                case UPLOAD_REJECTED -> new Packets.UploadRejected(readString(in));
                case VIDEO_BEGIN -> new Packets.VideoBegin(in.getInt(), in.getInt(), in.getInt(), in.getInt());
                case VIDEO_GRANTED -> new Packets.VideoGranted(readUuid(in), in.getLong(), in.getInt(), in.getInt());
                case VIDEO_PREVIEW_CHUNK -> readVideoPreviewChunk(in);
                case VIDEO_TILE_CHUNK -> readVideoChunk(in);
                case VIDEO_FINISH -> new Packets.VideoFinish(readUuid(in));
                case VIDEO_CREATED -> readVideoCreated(in);
            };
            if (in.hasRemaining()) throw new ProtocolException("trailing packet bytes");
            return packet;
        } catch (BufferUnderflowException exception) {
            throw new ProtocolException("truncated packet");
        }
    }

    private static Packets.UploadTileChunk readChunk(ByteBuffer in) {
        UUID token = readUuid(in); int x = in.getInt(); int y = in.getInt(); int offset = in.getInt();
        int length = in.getInt();
        if (length < 0 || length > MAX_CHUNK_BYTES || length > in.remaining()) throw new ProtocolException("invalid chunk length");
        byte[] data = new byte[length]; in.get(data);
        return new Packets.UploadTileChunk(token, x, y, offset, data);
    }
    private static Packets.UploadPreviewChunk readUploadPreviewChunk(ByteBuffer in) {
        UUID token = readUuid(in); int offset = in.getInt();
        return new Packets.UploadPreviewChunk(token, offset, readChunkData(in));
    }
    private static Packets.VideoTileChunk readVideoChunk(ByteBuffer in) {
        UUID token = readUuid(in); int frame = in.getInt(), x = in.getInt(), y = in.getInt(), offset = in.getInt(), length = in.getInt();
        if (length < 0 || length > MAX_CHUNK_BYTES || length > in.remaining()) throw new ProtocolException("invalid chunk length");
        byte[] data = new byte[length]; in.get(data); return new Packets.VideoTileChunk(token, frame, x, y, offset, data);
    }
    private static Packets.VideoPreviewChunk readVideoPreviewChunk(ByteBuffer in) {
        UUID token = readUuid(in); int offset = in.getInt();
        return new Packets.VideoPreviewChunk(token, offset, readChunkData(in));
    }

    private static void writePreviewChunk(DataOutputStream out, UUID token, int offset, byte[] data) throws IOException {
        if (data.length > MAX_CHUNK_BYTES) throw new ProtocolException("chunk exceeds limit");
        writeUuid(out, token); out.writeInt(offset); out.writeInt(data.length); out.write(data);
    }

    private static byte[] readChunkData(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > MAX_CHUNK_BYTES || length > in.remaining()) throw new ProtocolException("invalid chunk length");
        byte[] data = new byte[length]; in.get(data);
        return data;
    }

    private static Packets.PhotoCreated readPhotoCreated(ByteBuffer in) {
        UUID photoId = readUuid(in); int width = in.getInt(); int height = in.getInt(); int count = in.getInt();
        if (count < 0 || count > in.remaining() / Integer.BYTES) throw new ProtocolException("invalid map id count");
        List<Integer> mapIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) mapIds.add(in.getInt());
        return new Packets.PhotoCreated(photoId, mapIds, width, height);
    }
    private static Packets.VideoCreated readVideoCreated(ByteBuffer in) {
        UUID id = readUuid(in); int width = in.getInt(), height = in.getInt(), fps = in.getInt(), frames = in.getInt(), count = in.getInt();
        if (count < 0 || count > in.remaining() / Integer.BYTES) throw new ProtocolException("invalid map id count");
        List<Integer> mapIds = new ArrayList<>(count); for (int i = 0; i < count; i++) mapIds.add(in.getInt());
        return new Packets.VideoCreated(id, mapIds, width, height, fps, frames);
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer in) { return new UUID(in.getLong(), in.getLong()); }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new ProtocolException("string exceeds limit");
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static String readString(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > in.remaining()) throw new ProtocolException("invalid string length");
        byte[] bytes = new byte[length]; in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
