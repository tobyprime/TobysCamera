package dev.tobyscamera.folia.bag;

import dev.tobyscamera.folia.delivery.MapItemDelivery;
import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.map.MapVideoService;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.TileCoordinate;
import dev.tobyscamera.folia.storage.VideoRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Expands a bag onto a validated item-frame rectangle and turns any member break back into one bag. */
public final class PhotoBagPlacementListener implements Listener {
    private static final long UNPACK_DELAY_TICKS = 20L;
    private final Plugin plugin;
    private final MapPhotoService photos;
    private final MapVideoService videos;
    private final Map<UUID, PhotoBagData> pendingUnpacks = new ConcurrentHashMap<>();
    private final Set<Integer> restoredPreviewMaps = ConcurrentHashMap.newKeySet();
    private final Set<Integer> restoringPreviewMaps = ConcurrentHashMap.newKeySet();
    private volatile Consumer<ItemFrame> frameRefresher = ignored -> { };

    public PhotoBagPlacementListener(Plugin plugin, MapPhotoService photos, MapVideoService videos) {
        this.plugin = plugin;
        this.photos = photos;
        this.videos = videos;
    }

    /** Connects direct item-frame changes to the video playback index. */
    public void setFrameRefresher(Consumer<ItemFrame> frameRefresher) { this.frameRefresher = frameRefresher; }

    /**
     * The bag is a server-only item: Fabric does not inspect its components or send an
     * unpack packet.  A normal use begins a one-second server-side hold window; the
     * scheduled check only unpacks when the player still holds that exact bag.
     */
    @EventHandler(ignoreCancelled = true)
    public void onUseBag(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack held = event.getItem();
        if (!PhotoBagFactory.isBag(held)) return;
        restorePreview(held);
        final PhotoBagData started;
        try { started = PhotoBagFactory.read(held); }
        catch (IllegalArgumentException ignored) { return; }
        Player player = event.getPlayer();
        pendingUnpacks.put(player.getUniqueId(), started);
        player.getScheduler().runDelayed(plugin, ignored -> {
            if (!pendingUnpacks.remove(player.getUniqueId(), started)) return;
            ItemStack current = player.getInventory().getItemInMainHand();
            try {
                if (PhotoBagFactory.isBag(current) && PhotoBagFactory.read(current).equals(started)) unpack(player);
            } catch (IllegalArgumentException ignored1) {
                // The item was replaced or its server metadata was invalidated while waiting.
            }
        }, () -> { }, UNPACK_DELAY_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) restorePreview(item);
        restorePreview(event.getPlayer().getInventory().getItemInOffHand());
    }

    @EventHandler
    public void onSelectHotbarSlot(PlayerItemHeldEvent event) {
        restorePreview(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    /** Cancels a pending unpack when Paper reports that the native item use ended too early. */
    @EventHandler(ignoreCancelled = true)
    public void onStopUsingBag(PlayerStopUsingItemEvent event) {
        if (event.getTicksHeldFor() >= UNPACK_DELAY_TICKS || !PhotoBagFactory.isBag(event.getItem())) return;
        try { pendingUnpacks.remove(event.getPlayer().getUniqueId(), PhotoBagFactory.read(event.getItem())); }
        catch (IllegalArgumentException ignored) { }
    }

    @EventHandler
    public void onUseBagOnFrame(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof ItemFrame origin)) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!PhotoBagFactory.isBag(held) || !isEmpty(origin)) return;
        restorePreview(held);
        PhotoBagData bag;
        try { bag = PhotoBagFactory.read(held); } catch (IllegalArgumentException ignored) { return; }
        List<ItemFrame> frames = findEmptyPlacement(origin, bag);
        if (frames == null) return;
        List<ItemStack> maps;
        try { maps = memberMaps(bag); } catch (IOException | IllegalArgumentException ignored) { return; }
        if (maps.size() != frames.size()) return;
        event.setCancelled(true);
        consumeOne(player, held);
        UUID placementId = UUID.randomUUID();
        for (int index = 0; index < frames.size(); index++) {
            int x = index % bag.gridWidth();
            int y = index / bag.gridWidth();
            ItemFrame frame = frames.get(index);
            frame.setItem(PhotoBagFactory.markPlaced(maps.get(index), bag, placementId, x, y), false);
            frameRefresher.accept(frame);
        }
    }

    /** Expands a held bag into ordinary maps after the Fabric client has confirmed a continuous hold. */
    public void unpack(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!PhotoBagFactory.isBag(held)) return;
        PhotoBagData bag;
        List<ItemStack> maps;
        try {
            bag = PhotoBagFactory.read(held);
            maps = memberMaps(bag);
        } catch (IOException | IllegalArgumentException ignored) {
            return;
        }
        consumeOne(player, held);
        MapItemDelivery.deliver(maps, player.getInventory()::addItem,
                item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    @EventHandler
    public void onBreakMember(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame broken)) return;
        PhotoBagFactory.PlacedMember member = PhotoBagFactory.readPlaced(broken.getItem());
        if (member == null) return;
        if (recover(broken, member)) event.setCancelled(true);
    }

    /** Hand attacks happen before vanilla turns the item frame into an independent map drop. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageMember(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame broken)) return;
        PhotoBagFactory.PlacedMember member = PhotoBagFactory.readPlaced(broken.getItem());
        if (member != null && recover(broken, member)) event.setCancelled(true);
    }

    private boolean recover(ItemFrame broken, PhotoBagFactory.PlacedMember member) {
        ItemFrame origin = origin(broken, member);
        if (origin == null) return false;
        List<ItemFrame> group = frames(origin, member.bag(), false);
        if (group == null) return false;
        for (ItemFrame frame : group) {
            PhotoBagFactory.PlacedMember candidate = PhotoBagFactory.readPlaced(frame.getItem());
            if (candidate == null || !candidate.placementId().equals(member.placementId())) return false;
        }
        for (ItemFrame frame : group) {
            frame.setItem(new ItemStack(Material.AIR), false);
            frameRefresher.accept(frame);
        }
        ItemStack bag = PhotoBagFactory.create(member.bag());
        broken.getWorld().dropItemNaturally(broken.getLocation(), bag);
        return true;
    }

    private List<ItemStack> memberMaps(PhotoBagData bag) throws IOException {
        List<ItemStack> maps = new ArrayList<>(bag.gridWidth() * bag.gridHeight());
        if (bag.kind() == PhotoBagKind.PHOTO) {
            PhotoRecord record = photos.record(bag.mediaId());
            if (record == null || record.gridWidth() != bag.gridWidth() || record.gridHeight() != bag.gridHeight()) throw new IllegalArgumentException("photo bag does not match stored photo");
            for (int y = 0; y < bag.gridHeight(); y++) for (int x = 0; x < bag.gridWidth(); x++) maps.add(photos.mapItem(record, new TileCoordinate(x, y), bag.metadata()));
        } else {
            VideoRecord record = videos.record(bag.mediaId());
            if (record == null || record.gridWidth() != bag.gridWidth() || record.gridHeight() != bag.gridHeight()) throw new IllegalArgumentException("video bag does not match stored video");
            for (int y = 0; y < bag.gridHeight(); y++) for (int x = 0; x < bag.gridWidth(); x++) maps.add(videos.mapItem(record, new TileCoordinate(x, y), bag.metadata()));
        }
        return maps;
    }

    private static List<ItemFrame> frames(ItemFrame origin, PhotoBagData bag, boolean requireEmpty) {
        ItemFrameGrid grid = ItemFrameGrid.forFace(origin.getFacing());
        List<ItemFrame> result = new ArrayList<>(bag.gridWidth() * bag.gridHeight());
        for (int y = 0; y < bag.gridHeight(); y++) for (int x = 0; x < bag.gridWidth(); x++) {
            ItemFrame frame = findFrame(origin, grid.offset(x, y));
            if (frame == null || (requireEmpty && !isEmpty(frame))) return null;
            result.add(frame);
        }
        return result;
    }

    /** Finds an empty rectangle that contains the clicked frame at any of its local coordinates. */
    private static List<ItemFrame> findEmptyPlacement(ItemFrame clicked, PhotoBagData bag) {
        ItemFrameGrid grid = ItemFrameGrid.forFace(clicked.getFacing());
        for (int y = 0; y < bag.gridHeight(); y++) {
            for (int x = 0; x < bag.gridWidth(); x++) {
                ItemFrame anchor = findFrame(clicked, grid.anchorOffsetForMember(x, y));
                if (anchor == null) continue;
                List<ItemFrame> candidate = frames(anchor, bag, true);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private static ItemFrame origin(ItemFrame member, PhotoBagFactory.PlacedMember placed) {
        ItemFrameGrid grid = ItemFrameGrid.forFace(member.getFacing());
        GridVector reverse = grid.anchorOffsetForMember(placed.tileX(), placed.tileY());
        return findFrame(member, reverse);
    }

    private static ItemFrame findFrame(ItemFrame anchor, GridVector offset) {
        Location target = anchor.getLocation().clone().add(offset.x(), offset.y(), offset.z());
        for (Entity entity : target.getWorld().getNearbyEntities(target, 0.2, 0.2, 0.2)) {
            if (entity instanceof ItemFrame frame && frame.getFacing() == anchor.getFacing()) return frame;
        }
        return null;
    }

    private static boolean isEmpty(ItemFrame frame) { return frame.getItem().getType() == Material.AIR; }

    /** Lazily restores dynamic map rendering for bags that survived a server restart. */
    private void restorePreview(ItemStack item) {
        if (!PhotoBagFactory.isBag(item)) return;
        final PhotoBagData bag;
        try { bag = PhotoBagFactory.read(item); }
        catch (IllegalArgumentException ignored) { return; }
        int mapId = bag.previewMapId();
        if (restoredPreviewMaps.contains(mapId) || !restoringPreviewMaps.add(mapId)) return;
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> {
            try {
                byte[] pixels = bag.kind() == PhotoBagKind.PHOTO ? photos.previewPixels(bag) : videos.previewPixels(bag);
                plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored1 -> {
                    try {
                        org.bukkit.map.MapView preview = Bukkit.getMap(mapId);
                        if (preview != null) {
                            PhotoBagFactory.restorePreview(preview, pixels);
                            restoredPreviewMaps.add(mapId);
                        }
                    } finally {
                        restoringPreviewMaps.remove(mapId);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                restoringPreviewMaps.remove(mapId);
                plugin.getLogger().warning("Could not restore photo bag preview " + mapId + ": " + exception.getMessage());
            }
        });
    }

    private static void consumeOne(Player player, ItemStack held) {
        if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
        else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }
}
