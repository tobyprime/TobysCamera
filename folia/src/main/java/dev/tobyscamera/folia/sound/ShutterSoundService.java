package dev.tobyscamera.folia.sound;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ShutterSoundService {
    void playFor(Player player);
}
