package dev.tobyscamera.folia.scheduler;

import org.bukkit.plugin.Plugin;

/** Chooses the scheduling model exposed by the running server. */
public final class ServerTaskSchedulers {
    private static final String FOLIA_RUNTIME_MARKER = "io/papermc/paper/threadedregions/RegionizedServer.class";

    private ServerTaskSchedulers() {
    }

    public static ServerTaskScheduler create(Plugin plugin) {
        return isFolia(plugin.getClass().getClassLoader()) ? new FoliaTaskScheduler(plugin) : new PaperTaskScheduler(plugin);
    }

    static boolean isFolia(ClassLoader classLoader) {
        return classLoader.getResource(FOLIA_RUNTIME_MARKER) != null;
    }
}
