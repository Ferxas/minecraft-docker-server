package top.steve3184.mapbrowser;

import de.pianoman911.mapengine.api.MapEngineApi;
import de.pianoman911.mapengine.api.clientside.IMapDisplay;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main plugin class for MapBrowser.
 * Manages plugin lifecycle and holds core components and data.
 */
public final class MapBrowser extends JavaPlugin {

    private JCEFManager jcefManager;
    private DisplayService displayService;
    private BukkitTask proximityCheckTask;
    private BukkitTask autosaveTask;
    private Settings settings;
    private ScreenStore store;
    private BrowserMenu browserMenu;
    private TypingMode typingMode;
    private MapInteractionListener interactionListener;

    // Core data structures, managed by DisplayService but owned by the main plugin class.
    private final Map<Integer, MapBrowserDisplay> activeDisplays = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<IMapDisplay, MapBrowserDisplay> displayLookup = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        MapEngineApi mapEngine = Bukkit.getServicesManager().load(MapEngineApi.class);
        if (mapEngine == null) {
            getLogger().severe("MapEngine API not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        getConfig().options().copyDefaults(true); // add new keys (favorites, controls...) to old configs
        saveConfig();
        PluginConfig pluginConfig = new PluginConfig(this.getConfig());
        this.settings = Settings.load(getConfig());
        getLogger().info("Configuration loaded.");
        saveDefaultScripts();
        this.jcefManager = new JCEFManager();
        try {
            jcefManager.initialize(pluginConfig, getDataFolder());
        } catch (UnsupportedPlatformException e) {
            getLogger().severe("----------------------------------------------------");
            getLogger().severe("Unsupported Platform: " + e.getMessage());
            getLogger().severe("MapBrowser will now be disabled.");
            getLogger().severe("----------------------------------------------------");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            getLogger().severe("Failed to initialize JCEF! The browser feature will not work.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.store = new ScreenStore(this);
        this.displayService = new DisplayService(this, mapEngine, jcefManager);
        this.browserMenu = new BrowserMenu(this);
        this.typingMode = new TypingMode(this);
        this.interactionListener = new MapInteractionListener(this);
        CommandHandler commandHandler = new CommandHandler(this, displayService);

        Objects.requireNonNull(getCommand("mapbrowser")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("mapbrowser")).setTabCompleter(commandHandler);
        var pm = getServer().getPluginManager();
        pm.registerEvents(interactionListener, this);
        pm.registerEvents(new PlayerConnectionListener(this, displayService), this);
        pm.registerEvents(browserMenu, this);
        pm.registerEvents(typingMode, this);

        this.proximityCheckTask = displayService.startProximityChecker();
        // Keeps the "last page" of every screen on disk (people click around).
        this.autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!activeDisplays.isEmpty()) store.save();
        }, 20L * 300, 20L * 300);

        // Bring saved screens back once worlds are loaded (Multiverse loads them after us).
        Bukkit.getScheduler().runTaskLater(this, this::restoreScreens, 20L * 10);

        getLogger().info("MapBrowser has been enabled!");
    }

    private void restoreScreens() {
        List<ScreenStore.Saved> saved = store.load();
        if (saved.isEmpty()) return;
        getLogger().info("Restaurando " + saved.size() + " pantalla(s) web…");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int ok = 0;
            for (ScreenStore.Saved s : saved) {
                World w = Bukkit.getWorld(s.world());
                if (w == null) {
                    getLogger().warning("Pantalla #" + s.id() + ": el mundo '" + s.world() + "' no está cargado, se omite (sigue guardada).");
                    continue;
                }
                MapBrowserDisplay d = displayService.createAndInitializeDisplay(s.id(), null, s.url(),
                        new Location(w, s.x(), s.y(), s.z()), s.facing(), s.width(), s.height(), s.scale());
                if (d != null) {
                    d.setHomeUrl(s.home());
                    ok++;
                }
                try {
                    Thread.sleep(1500); // don't spin up every Chromium tab at once
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            getLogger().info("Pantallas web restauradas: " + ok + "/" + saved.size());
        });
    }

    public void reloadSettings() {
        reloadConfig();
        this.settings = Settings.load(getConfig());
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling MapBrowser... Cleaning up displays...");
        if (this.proximityCheckTask != null && !this.proximityCheckTask.isCancelled()) {
            this.proximityCheckTask.cancel();
        }
        if (this.autosaveTask != null && !this.autosaveTask.isCancelled()) {
            this.autosaveTask.cancel();
        }
        // Save before tearing browsers down (we record the page each one is on).
        if (store != null && !activeDisplays.isEmpty()) {
            store.save();
        }
        if (displayService != null) {
            displayService.cleanupAllDisplays();
        }
        if (jcefManager != null) {
            jcefManager.shutdown();
        }
        getLogger().info("MapBrowser has been disabled and all displays have been cleaned up.");
    }

    /**
     * Copies default JavaScript snippets from the JAR to the plugin's data folder.
     */
    public void saveDefaultScripts() {
        File snippetsDir = new File(getDataFolder(), "snippets");
        if (!snippetsDir.exists() && !snippetsDir.mkdirs()) {
            getLogger().severe("Could not create snippets directory.");
            return;
        }
        String[] defaultScripts = {"vKeyboard.js"};
        for (String scriptName : defaultScripts) {
            String resourcePath = "snippets/" + scriptName;
            File scriptFile = new File(snippetsDir, scriptName);
            if (!scriptFile.exists()) {
                try {
                    saveResource(resourcePath, false);
                    getLogger().info("Saved default script: " + scriptName);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Could not save default script '" + scriptName + "'. It was not found in the JAR at path: " + resourcePath);
                }
            }
        }
    }

    // Getters
    public Map<Integer, MapBrowserDisplay> getActiveDisplays() { return activeDisplays; }
    public AtomicInteger getNextId() { return nextId; }
    public Map<IMapDisplay, MapBrowserDisplay> getDisplayLookup() { return displayLookup; }
    public Settings getSettings() { return settings; }
    public ScreenStore getStore() { return store; }
    public DisplayService getDisplayService() { return displayService; }
    public BrowserMenu getBrowserMenu() { return browserMenu; }
    public TypingMode getTypingMode() { return typingMode; }
    public MapInteractionListener getInteractionListener() { return interactionListener; }
}
