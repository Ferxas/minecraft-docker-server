package legacywarmup;

import org.bukkit.plugin.java.JavaPlugin;

public final class LegacyWarmup extends JavaPlugin {

    @Override
    public void onLoad() {
        // CraftLegacy's static init runs DataFixerUpper over every legacy id; on this machine it takes > 60 s.
        // Triggered lazily by the first legacy API call (QualityArmory: Block#getData when a gun fires),
        // it froze the main thread until the watchdog killed the server. Run it here, off-thread, during boot;
        // any main-thread caller that arrives early just waits for the class init to finish.
        Thread t = new Thread(() -> {
            long start = System.nanoTime();
            try {
                Class.forName("org.bukkit.craftbukkit.legacy.CraftLegacy", true, getServer().getClass().getClassLoader());
                getLogger().info("Legacy material support ready in " + (System.nanoTime() - start) / 1_000_000 + " ms");
            } catch (Throwable e) {
                getLogger().warning("Could not pre-initialize legacy material support: " + e);
            }
        }, "LegacyWarmup");
        t.setDaemon(true);
        t.start();
    }
}
