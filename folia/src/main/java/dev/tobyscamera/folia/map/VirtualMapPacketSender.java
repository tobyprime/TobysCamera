package dev.tobyscamera.folia.map;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Sends ordinary vanilla map packets for plugin-owned map IDs. */
public class VirtualMapPacketSender {
    public void sendFull(Player player, int mapId, byte[] pixels) {
        if (pixels.length != 16_384) throw new IllegalArgumentException("map tile must contain 16384 bytes");
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send(new ClientboundMapItemDataPacket(new MapId(mapId), (byte) 0, true, List.of(),
                new MapPatch(0, 0, 128, 128, pixels)));
    }
}
