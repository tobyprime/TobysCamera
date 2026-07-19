package dev.tobyscamera.folia.map;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Activates maps only for player hands and item frames in chunks delivered to that player. */
public final class MediaMapActivationListener implements Listener {
    private final Plugin plugin;
    private final ServerTaskScheduler scheduler;
    private final MapPhotoService photos;
    private final VirtualStillMapService stills;
    private final ChunkFrameViewerTracker frameViewers = new ChunkFrameViewerTracker();
    private final Set<String> frameSources = ConcurrentHashMap.newKeySet();
    private final Map<ChunkFrameViewerTracker.ViewerChunk, Long> frameDistances = new ConcurrentHashMap<>();

    public MediaMapActivationListener(Plugin plugin, ServerTaskScheduler scheduler, MapPhotoService photos) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.photos = photos;
        this.stills = new VirtualStillMapService(scheduler::runAsync, scheduler::runGlobal,
                failure -> plugin.getLogger().warning("Could not lazily load camera media: " + failure.getMessage()),
                new VirtualMapPacketSender());
    }

    /** Reconciles a changed frame for only clients whose loaded chunks currently contain it. */
    public void refreshFrame(ItemFrame frame) { refreshFrame(frame, frame.getItem()); }

    /** Schedules a hand recheck after a server-side inventory mutation. */
    public void refreshHeldMaps(Player player) { reconcileHandsNextTick(player); }

    public VirtualStillMapService.Status status() { return stills.status(); }

    public void setDeliveryLimits(VirtualMapDeliveryScheduler.Limits limits) { stills.setLimits(limits); }

    public void tickDelivery() { stills.tick(); }

    /** Seeds frame sources for chunks the client had already received before this listener was enabled. */
    public void refreshVisibleFrames(Player player) {
        scheduler.runEntityDelayed(player, 1L, () -> {
            for (Chunk chunk : player.getSentChunks()) {
                World world = chunk.getWorld();
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                var viewer = viewerChunk(player, chunk);
                long distanceSquared = chunkDistanceSquared(player, chunk);
                frameDistances.put(viewer, distanceSquared);
                scheduler.runRegion(world, chunkX, chunkZ, () -> reconcileViewerChunk(viewer, world, chunkX, chunkZ));
            }
        }, () -> detachHands(player));
    }

    public void clear() {
        for (String source : frameSources) detach(source);
        frameSources.clear();
        frameDistances.clear();
        stills.clear();
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        reconcileHandsNextTick(event.getPlayer());
        refreshVisibleFrames(event.getPlayer());
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { detachHands(event.getPlayer()); releasePlayerFrames(event.getPlayer().getUniqueId()); }
    @EventHandler public void onPlayerItemHeld(PlayerItemHeldEvent event) { reconcileHandsNextTick(event.getPlayer()); }
    @EventHandler public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) { reconcileHandsNextTick(event.getPlayer()); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) reconcileHandsNextTick(player); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player) reconcileHandsNextTick(player); }
    @EventHandler public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        for (var viewer : frameViewers.removeFrame(frame.getUniqueId())) {
            String source = frameSource(viewer, frame.getUniqueId());
            frameSources.remove(source);
            detach(source);
        }
    }

    @EventHandler public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        var viewer = viewerChunk(event.getPlayer(), chunk);
        frameDistances.put(viewer, chunkDistanceSquared(event.getPlayer(), chunk));
        scheduler.runRegion(chunk.getWorld(), chunk.getX(), chunk.getZ(),
                () -> reconcileViewerChunk(viewer, chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }

    @EventHandler public void onPlayerChunkUnload(PlayerChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        var viewer = viewerChunk(event.getPlayer(), chunk);
        scheduler.runRegion(chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> releaseViewerChunk(viewer));
    }

    @EventHandler public void onItemFrameChanged(PlayerItemFrameChangeEvent event) {
        if (event.isCancelled()) return;
        refreshFrame(event.getItemFrame(), event.getItemStack());
    }

    private void reconcileHandsNextTick(Player player) {
        scheduler.runEntityDelayed(player, 1L, () -> reconcileHands(player), () -> detachHands(player));
    }

    private void reconcileHands(Player player) {
        reconcile(playerSource(player, "main"), player, player.getInventory().getItemInMainHand(),
                VirtualMapDeliveryScheduler.Priority.MAIN_HAND, 0L);
        reconcile(playerSource(player, "off"), player, player.getInventory().getItemInOffHand(),
                VirtualMapDeliveryScheduler.Priority.OFF_HAND, 0L);
    }

    private void detachHands(Player player) {
        detach(playerSource(player, "main"));
        detach(playerSource(player, "off"));
    }

    private void reconcileViewerChunk(ChunkFrameViewerTracker.ViewerChunk viewer, World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            releaseViewerChunk(viewer);
            return;
        }
        Set<UUID> frames = new HashSet<>();
        for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            if (!(entity instanceof ItemFrame frame)) continue;
            UUID frameId = frame.getUniqueId();
            frames.add(frameId);
            String source = frameSource(viewer, frameId);
            frameSources.add(source);
            Player player = Bukkit.getPlayer(viewer.playerId());
            if (player != null) reconcile(source, player, frame.getItem(), VirtualMapDeliveryScheduler.Priority.FRAME,
                    frameDistances.getOrDefault(viewer, Long.MAX_VALUE));
        }
        for (UUID removed : frameViewers.replace(viewer, frames)) {
            String source = frameSource(viewer, removed);
            frameSources.remove(source);
            detach(source);
        }
    }

    private void releaseViewerChunk(ChunkFrameViewerTracker.ViewerChunk viewer) {
        frameDistances.remove(viewer);
        for (UUID frameId : frameViewers.release(viewer)) {
            String source = frameSource(viewer, frameId);
            frameSources.remove(source);
            detach(source);
        }
    }

    private void releasePlayerFrames(UUID playerId) {
        for (var entry : frameViewers.releasePlayer(playerId).entrySet()) {
            frameDistances.remove(entry.getKey());
            for (UUID frameId : entry.getValue()) {
                String source = frameSource(entry.getKey(), frameId);
                frameSources.remove(source);
                detach(source);
            }
        }
    }

    private void refreshFrame(ItemFrame frame, ItemStack item) {
        Chunk chunk = frame.getChunk();
        for (var viewer : frameViewers.viewersIn(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            String source = frameSource(viewer, frame.getUniqueId());
            frameSources.add(source);
            frameViewers.trackFrame(viewer, frame.getUniqueId());
            Player player = Bukkit.getPlayer(viewer.playerId());
            if (player != null) reconcile(source, player, item, VirtualMapDeliveryScheduler.Priority.FRAME,
                    frameDistances.getOrDefault(viewer, Long.MAX_VALUE));
        }
    }

    private void reconcile(String source, Player player, ItemStack item, VirtualMapDeliveryScheduler.Priority priority,
            long distanceSquared) {
        var descriptor = MediaMapDescriptor.from(item);
        if (descriptor.isEmpty()) {
            detach(source);
            return;
        }
        switch (descriptor.get()) {
            case MediaMapDescriptor.PhotoTile photo -> stills.attach(source, player, photo, priority, distanceSquared,
                    () -> photos.tilePixels(photo));
            case MediaMapDescriptor.PhotoBagPreview photo -> stills.attach(source, player, photo,
                    priority, distanceSquared,
                    () -> photos.previewPixels(new PhotoBagData(photo.mediaId(), PhotoBagKind.PHOTO, photo.mapId(), 1, 1)));
        }
    }

    private void detach(String source) {
        stills.detach(source);
    }

    private static String playerSource(Player player, String hand) { return "player:" + player.getUniqueId() + ':' + hand; }
    private static ChunkFrameViewerTracker.ViewerChunk viewerChunk(Player player, Chunk chunk) {
        return new ChunkFrameViewerTracker.ViewerChunk(player.getUniqueId(), chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
    private static long chunkDistanceSquared(Player player, Chunk chunk) {
        long playerChunkX = player.getLocation().getBlockX() >> 4;
        long playerChunkZ = player.getLocation().getBlockZ() >> 4;
        long deltaX = playerChunkX - chunk.getX();
        long deltaZ = playerChunkZ - chunk.getZ();
        return deltaX * deltaX + deltaZ * deltaZ;
    }
    private static String frameSource(ChunkFrameViewerTracker.ViewerChunk viewer, UUID frameId) {
        return "frame:" + frameId + ":viewer:" + viewer.playerId();
    }
}
