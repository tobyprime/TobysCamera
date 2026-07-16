package dev.tobyscamera.folia.camera;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class CameraFilmInventoryListener implements Listener {
    private final CameraFilmService films;

    public CameraFilmInventoryListener(CameraFilmService films) { this.films = films; }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack camera = event.getCurrentItem();
        ItemStack film = event.getCursor();
        if (camera == null || film == null || !films.isCamera(camera) || !films.isFilm(film)) return;
        films.load(camera, film.getAmount());
        event.setCursor(new ItemStack(Material.AIR));
        event.setCancelled(true);
    }
}
