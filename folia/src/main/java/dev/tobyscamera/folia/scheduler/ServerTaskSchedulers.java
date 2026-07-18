package dev.tobyscamera.folia.scheduler;

import org.bukkit.plugin.Plugin;

/** Creates the Paper scheduler API adapter shared by Paper and Folia. */
public final class ServerTaskSchedulers {
    private ServerTaskSchedulers() {
    }

    public static ServerTaskScheduler create(Plugin plugin) {
        return new FoliaTaskScheduler(plugin);
    }
}
