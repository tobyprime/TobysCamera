package dev.tobyscamera.fabric.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class CameraPayloadTest {
    @Test
    void writesPacketCodecBytesWithoutALengthPrefix() {
        byte[] packet = {1, 0};
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

        CameraPayload.CODEC.encode(buffer, new CameraPayload(packet));

        assertArrayEquals(packet, ByteBufUtil.getBytes(buffer));
    }
}
