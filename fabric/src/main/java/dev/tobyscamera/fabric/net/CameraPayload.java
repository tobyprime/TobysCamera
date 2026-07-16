package dev.tobyscamera.fabric.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CameraPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<CameraPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tobyscamera", "main"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                if (payload.data.length > 8_256) throw new IllegalArgumentException("payload exceeds 8256 bytes");
                buffer.writeBytes(payload.data);
            },
            buffer -> {
                if (buffer.readableBytes() > 8_256) throw new IllegalArgumentException("payload exceeds 8256 bytes");
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                return new CameraPayload(data);
            });

    public CameraPayload { data = data.clone(); }
    @Override public byte[] data() { return data.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
