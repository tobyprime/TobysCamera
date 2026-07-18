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
    private final MapVideoService videos;
    private final StillMapAttachmentService stills;
    private final Map<String, MediaMapDescriptor.VideoTile> pendingVideos = new ConcurrentHashMap<>();
    private final ChunkFrameViewerTracker frameViewers = new ChunkFrameViewerTracker();
    private final Set<String> frameSources = ConcurrentHashMap.newKeySet();

    public MediaMapActivationListener(Plugin plugin, ServerTaskScheduler scheduler, MapPhotoService photos, MapVideoService videos) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.photos = photos;
        this.videos = videos;
        this.stills = new StillMapAttachmentService(Bukkit::getMap, scheduler::runAsync, scheduler::runGlobal,
                failure -> plugin.getLogger().warning("Could not lazily load camera media: " + failure.getMessage()));
    }

    /** Reconciles a changed frame for only clients whose loaded chunks currently contain it. */
    public void refreshFrame(ItemFrame frame) { refreshFrame(frame, frame.getItem()); }

    public void clear() {
        pendingVideos.clear();
        for (String source : frameSources) detach(source);
        frameSources.clear();
        stills.clear();
        videos.clear();
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { reconcileHandsNextTick(event.getPlayer()); }
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
        reconcile(playerSource(player, "main"), player.getInventory().getItemInMainHand());
        reconcile(playerSource(player, "off"), player.getInventory().getItemInOffHand());
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
            reconcile(source, frame.getItem());
        }
        for (UUID removed : frameViewers.replace(viewer, frames)) {
            String source = frameSource(viewer, removed);
            frameSources.remove(source);
            detach(source);
        }
    }

    private void releaseViewerChunk(ChunkFrameViewerTracker.ViewerChunk viewer) {
        for (UUID frameId : frameViewers.release(viewer)) {
            String source = frameSource(viewer, frameId);
            frameSources.remove(source);
            detach(source);
        }
    }

    private void releasePlayerFrames(UUID playerId) {
        for (var entry : frameViewers.releasePlayer(playerId).entrySet()) {
            for (UUID frameId : entry.getValue()) {
                String source = frameSource(entry.getKey(), frameId);
                frameSources.remove(source);
                detach(source);
            }
        }
    }

    private void refreshFrame(ItemFrame frame, ItemStack item) {
        for (var viewer : frameViewers.viewers(frame.getUniqueId())) {
            String source = frameSource(viewer, frame.getUniqueId());
            frameSources.add(source);
            reconcile(source, item);
        }
    }

    private void reconcile(String source, ItemStack item) {
        var descriptor = MediaMapDescriptor.from(item);
        if (descriptor.isEmpty()) {
            detach(source);
            return;
        }
        switch (descriptor.get()) {
            case MediaMapDescriptor.PhotoTile photo -> { videos.detach(source); pendingVideos.remove(source); stills.attach(source, photo, () -> photos.tilePixels(photo)); }
            case MediaMapDescriptor.VideoTile video -> activateVideo(source, video);
            case MediaMapDescriptor.PhotoBagPreview photo -> { videos.detach(source); pendingVideos.remove(source); stills.attach(source, photo,
                    () -> photos.previewPixels(new PhotoBagData(photo.mediaId(), PhotoBagKind.PHOTO, photo.mapId(), 1, 1))); }
            case MediaMapDescriptor.VideoBagPreview video -> { videos.detach(source); pendingVideos.remove(source); stills.attach(source, video,
                    () -> videos.previewPixels(new PhotoBagData(video.mediaId(), PhotoBagKind.VIDEO, video.mapId(), 1, 1))); }
        }
    }

    private void activateVideo(String source, MediaMapDescriptor.VideoTile tile) {
        if (tile.equals(pendingVideos.get(source))) return;
        stills.detach(source);
        videos.detach(source);
        pendingVideos.put(source, tile);
        scheduler.runAsync(() -> {
            try {
                MapVideoService.LoadedVideo loaded = videos.load(tile);
                scheduler.runGlobal(() -> {
                    if (!tile.equals(pendingVideos.get(source))) return;
                    videos.attach(source, tile, loaded);
                });
            } catch (IOException exception) {
                if (tile.equals(pendingVideos.remove(source))) plugin.getLogger().warning("Could not lazily load video " + tile.mediaId() + ": " + exception.getMessage());
            }
        });
    }

    private void detach(String source) {
        pendingVideos.remove(source);
        stills.detach(source);
        videos.detach(source);
    }

    private static String playerSource(Player player, String hand) { return "player:" + player.getUniqueId() + ':' + hand; }
    private static ChunkFrameViewerTracker.ViewerChunk viewerChunk(Player player, Chunk chunk) {
        return new ChunkFrameViewerTracker.ViewerChunk(player.getUniqueId(), chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
    private static String frameSource(ChunkFrameViewerTracker.ViewerChunk viewer, UUID frameId) {
        return "frame:" + frameId + ":viewer:" + viewer.playerId();
    }
}
