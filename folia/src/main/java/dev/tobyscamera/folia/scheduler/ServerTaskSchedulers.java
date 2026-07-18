package dev.tobyscamera.folia.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Chooses the scheduling model exposed by the running server. */
public final class ServerTaskSchedulers {
    private ServerTaskSchedulers() {
    }

    public static ServerTaskScheduler create(Plugin plugin) {
        return isFolia(Bukkit.getName()) ? new FoliaTaskScheduler(plugin) : new PaperTaskScheduler(plugin);
    }

    static boolean isFolia(String serverName) {
        return "Folia".equalsIgnoreCase(serverName);
    }
}
