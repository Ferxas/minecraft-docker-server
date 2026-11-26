package top.steve3184.mapbrowser;

import de.pianoman911.mapengine.api.MapEngineApi;
import de.pianoman911.mapengine.api.clientside.IMapDisplay;
import de.pianoman911.mapengine.api.drawing.IDrawingSpace;
import de.pianoman911.mapengine.api.util.Converter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.cef.browser.MapBrowserInstance;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service class for managing the lifecycle and interactions of MapBrowser displays.
 */
public class DisplayService {

    private final MapBrowser plugin;
    private final MapEngineApi mapEngine;
    private final JCEFManager jcefManager;
    private final Logger logger;

    /** Last screen each player clicked, so commands/menu/chat know which one you mean. */
    private final Map<UUID, Integer> focusedDisplay = new ConcurrentHashMap<>();
    /** When each player last saw the controls hint for a display (key = uuid + ":" + id). */
    private final Map<String, Long> hintShown = new ConcurrentHashMap<>();

    public DisplayService(MapBrowser plugin, MapEngineApi mapEngine, JCEFManager jcefManager) {
        this.plugin = plugin;
        this.mapEngine = mapEngine;
        this.jcefManager = jcefManager;
        this.logger = plugin.getLogger();
    }

    // ------------------------------------------------------------------
    // Creation / removal
    // ------------------------------------------------------------------

    /**
     * Creates, initializes, and stores a new browser display.
     *
     * @param fixedId  id to reuse (restoring from disk), or -1 for a new one.
     * @param location top-left wall block of the screen.
     * @param facing   horizontal direction the screen faces (towards the viewer).
     * @param creator  optional player that sees it immediately; others get it via the proximity checker.
     */
    public MapBrowserDisplay createAndInitializeDisplay(int fixedId, @Nullable Player creator, String url,
                                                        Location location, BlockFace facing, int width, int height, int scale) {
        int id;
        if (fixedId > 0) {
            id = fixedId;
            plugin.getNextId().accumulateAndGet(fixedId + 1, Math::max);
        } else {
            id = plugin.getNextId().getAndIncrement();
        }
        MapBrowserDisplay displayInfo = new MapBrowserDisplay(id, url, location, facing, width, height);
        displayInfo.setScale(Math.max(1, scale));
        calculateAndStoreGeometry(displayInfo);

        if (jcefManager != null) {
            final int browserWidth = width * 128 * displayInfo.getScale();
            final int browserHeight = height * 128 * displayInfo.getScale();

            MapBrowserInstance browser = jcefManager.createBrowser(displayInfo.getUrl());
            if (browser == null) {
                logger.severe("Failed to create browser instance for display #" + id);
                return null;
            }
            displayInfo.setBrowser(browser);

            SwingUtilities.invokeLater(() -> {
                try {
                    browser.createImmediately();
                    browser.resize(browserWidth, browserHeight);
                    startRenderLoop(displayInfo);
                    logger.info("Successfully created browser and render loop for display #" + displayInfo.getId());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }

        if (creator != null && creator.isOnline()) {
            synchronized (displayInfo.getViewerDisplays()) {
                addViewer(creator, displayInfo);
            }
            focusedDisplay.put(creator.getUniqueId(), id);
        }

        plugin.getActiveDisplays().put(id, displayInfo);
        return displayInfo;
    }

    /**
     * Removes a display and cleans up all its associated resources.
     * @return true if a display was found and removed, false otherwise.
     */
    public boolean removeDisplay(int id) {
        MapBrowserDisplay displayInfo = plugin.getActiveDisplays().remove(id);
        if (displayInfo == null) {
            return false;
        }
        synchronized (displayInfo.getViewerDisplays()) {
            displayInfo.getViewerDisplays().values().forEach(playerDisplay ->
                    plugin.getDisplayLookup().remove(playerDisplay.mapDisplay())
            );
        }
        displayInfo.cleanup();
        focusedDisplay.values().removeIf(v -> v == id);
        return true;
    }

    // ------------------------------------------------------------------
    // Viewers
    // ------------------------------------------------------------------

    private void addViewer(Player player, MapBrowserDisplay display) {
        PlayerDisplay pd = createDisplayForPlayer(player, display);
        display.getViewerDisplays().put(player.getUniqueId(), pd);
        plugin.getDisplayLookup().put(pd.mapDisplay(), display);
    }

    private void removeViewer(UUID uuid, MapBrowserDisplay display) {
        PlayerDisplay old = display.getViewerDisplays().remove(uuid);
        if (old != null) {
            plugin.getDisplayLookup().remove(old.mapDisplay());
            old.destroy(Bukkit.getPlayer(uuid));
        }
    }

    /**
     * Starts a repeating task that shows/hides screens as players come and go
     * (also handles players changing worlds, which the original version leaked).
     */
    public BukkitTask startProximityChecker() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getActiveDisplays().isEmpty()) return;
                double range = plugin.getSettings().viewDistance();
                double rangeSq = range * range;

                for (MapBrowserDisplay display : plugin.getActiveDisplays().values()) {
                    Location center = display.getCenter();
                    synchronized (display.getViewerDisplays()) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            UUID uuid = player.getUniqueId();
                            boolean viewing = display.getViewerDisplays().containsKey(uuid);
                            boolean inRange = player.getWorld().equals(center.getWorld())
                                    && player.getLocation().distanceSquared(center) <= rangeSq;
                            if (inRange && !viewing) {
                                addViewer(player, display);
                                maybeShowHint(player, display);
                            } else if (!inRange && viewing) {
                                removeViewer(uuid, display);
                            }
                        }
                        // Players that went offline without a quit event reaching us.
                        display.getViewerDisplays().keySet().removeIf(uuid -> {
                            if (Bukkit.getPlayer(uuid) != null) return false;
                            PlayerDisplay pd = display.getViewerDisplays().get(uuid);
                            if (pd != null) plugin.getDisplayLookup().remove(pd.mapDisplay());
                            return true;
                        });
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    private void maybeShowHint(Player player, MapBrowserDisplay display) {
        if (!plugin.getSettings().showHints() || !player.hasPermission("mapbrowser.use")) return;
        String key = player.getUniqueId() + ":" + display.getId();
        long now = System.currentTimeMillis();
        Long last = hintShown.get(key);
        if (last != null && now - last < 10 * 60 * 1000L) return;
        hintShown.put(key, now);
        Msg.actionBar(player, Msg.HINT_CONTROLS);
    }

    public void forgetPlayer(UUID uuid) {
        focusedDisplay.remove(uuid);
        hintShown.keySet().removeIf(k -> k.startsWith(uuid + ":"));
    }

    // ------------------------------------------------------------------
    // Which screen does a player mean?
    // ------------------------------------------------------------------

    public void setFocus(Player player, MapBrowserDisplay display) {
        focusedDisplay.put(player.getUniqueId(), display.getId());
    }

    /**
     * The screen the player is looking at; otherwise the one they last clicked (if still close);
     * otherwise the nearest one within view distance. Null if none.
     */
    @Nullable
    public MapBrowserDisplay resolveTarget(Player player) {
        double range = plugin.getSettings().viewDistance();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        // 1) Looking at one.
        MapBrowserDisplay looked = null;
        double bestDist = Double.MAX_VALUE;
        for (MapBrowserDisplay d : plugin.getActiveDisplays().values()) {
            if (!d.getLocation().getWorld().equals(player.getWorld())) continue;
            RayTraceResult hit = screenBox(d).rayTrace(eye.toVector(), dir, range);
            if (hit != null) {
                double dist = hit.getHitPosition().distanceSquared(eye.toVector());
                if (dist < bestDist) {
                    bestDist = dist;
                    looked = d;
                }
            }
        }
        if (looked != null) return looked;

        // 2) Last clicked.
        Integer focused = focusedDisplay.get(player.getUniqueId());
        if (focused != null) {
            MapBrowserDisplay d = plugin.getActiveDisplays().get(focused);
            if (d != null && d.getLocation().getWorld().equals(player.getWorld())
                    && d.getCenter().distanceSquared(player.getLocation()) <= range * range) {
                return d;
            }
        }

        // 3) Nearest.
        return plugin.getActiveDisplays().values().stream()
                .filter(d -> d.getLocation().getWorld().equals(player.getWorld()))
                .filter(d -> d.getCenter().distanceSquared(player.getLocation()) <= range * range)
                .min(Comparator.comparingDouble(d -> d.getCenter().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    /** Box covering the wall blocks plus the frame layer in front of them. */
    private BoundingBox screenBox(MapBrowserDisplay d) {
        BlockVector a = d.getCornerA();
        BlockVector b = d.getCornerB();
        BoundingBox box = BoundingBox.of(
                new Vector(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new Vector(Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1));
        Vector f = d.getFacing().getDirection();
        return box.expand(f.getX(), f.getY(), f.getZ(), 0.1).expand(0.05);
    }

    // ------------------------------------------------------------------
    // Move / resize / scale
    // ------------------------------------------------------------------

    public void moveDisplay(Player player, MapBrowserDisplay display, Location newLocation, BlockFace newFacing) {
        synchronized (display.getViewerDisplays()) {
            cleanupPlayerVisuals(display);
            if (display.getRenderTask() != null) display.getRenderTask().cancel();

            display.setLocation(newLocation);
            if (newFacing != null) display.setFacing(newFacing);
            calculateAndStoreGeometry(display);

            startRenderLoop(display);
            addViewer(player, display);
        }
    }

    public void resizeDisplay(Player player, MapBrowserDisplay display, int newWidth, int newHeight) {
        synchronized (display.getViewerDisplays()) {
            final MapBrowserInstance browser = display.getBrowser();
            if (browser == null) return;

            cleanupPlayerVisuals(display);
            if (display.getRenderTask() != null) display.getRenderTask().cancel();

            display.setWidth(newWidth);
            display.setHeight(newHeight);
            display.setLastPixelData(null);
            calculateAndStoreGeometry(display);

            final int newPixelWidth = newWidth * 128 * display.getScale();
            final int newPixelHeight = newHeight * 128 * display.getScale();
            SwingUtilities.invokeLater(() -> browser.resize(newPixelWidth, newPixelHeight));

            startRenderLoop(display);
            addViewer(player, display);
        }
    }

    public void scaleDisplay(MapBrowserDisplay display, int newScale) {
        synchronized (display.getViewerDisplays()) {
            final MapBrowserInstance browser = display.getBrowser();
            if (browser == null) return;
            display.setScale(newScale);
            SwingUtilities.invokeLater(() -> browser.resize(display.getWidth() * 128 * newScale, display.getHeight() * 128 * newScale));
        }
    }

    private void cleanupPlayerVisuals(MapBrowserDisplay display) {
        display.getViewerDisplays().forEach((uuid, playerDisplay) -> {
            plugin.getDisplayLookup().remove(playerDisplay.mapDisplay());
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) playerDisplay.destroy(viewer);
        });
        display.getViewerDisplays().clear();
    }

    public void cleanupAllDisplays() {
        plugin.getActiveDisplays().values().forEach(this::cleanupPlayerVisuals);
        plugin.getActiveDisplays().values().forEach(MapBrowserDisplay::cleanup);
        plugin.getActiveDisplays().clear();
        plugin.getDisplayLookup().clear();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Scales down pixel data by a given integer factor using nearest-neighbor.
     */
    public static int[] scalePixelData(int[] originalPixelData, int originalWidth, int originalHeight, int scale) {
        if (scale < 1) {
            throw new IllegalArgumentException("Scale factor must be 1 or greater.");
        }
        if (originalPixelData.length != originalWidth * originalHeight) {
            throw new IllegalArgumentException("Original pixel data array length does not match the provided dimensions.");
        }
        if (scale == 1) {
            return originalPixelData;
        }
        int newWidth = originalWidth / scale;
        int newHeight = originalHeight / scale;

        int[] scaledPixelData = new int[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                scaledPixelData[y * newWidth + x] = originalPixelData[(y * scale) * originalWidth + (x * scale)];
            }
        }
        return scaledPixelData;
    }

    private void startRenderLoop(MapBrowserDisplay displayInfo) {
        final MapBrowserInstance browser = displayInfo.getBrowser();
        if (browser == null) {
            logger.warning("Cannot start render loop for display #" + displayInfo.getId() + ", browser is missing.");
            return;
        }
        BukkitRunnable renderLoop = new BukkitRunnable() {
            @Override
            public void run() {
                if (displayInfo.getBrowser() == null) { this.cancel(); return; }
                // Nobody looking: leave the pending frame unconsumed so the next viewer gets it, and save CPU.
                if (displayInfo.getViewerDisplays().isEmpty()) return;
                int[] tmpPixelData = browser.getAndUpdatePixelData();
                if (tmpPixelData == null) return;
                int expected = displayInfo.getWidth() * 128 * displayInfo.getScale() * displayInfo.getHeight() * 128 * displayInfo.getScale();
                if (tmpPixelData.length != expected) return; // mid-resize frame
                int[] pixelData = scalePixelData(tmpPixelData, browser.getPixelWidth(), browser.getPixelHeight(), displayInfo.getScale());
                displayInfo.setLastPixelData(pixelData);
                synchronized (displayInfo.getViewerDisplays()) {
                    for (PlayerDisplay playerDisplay : displayInfo.getViewerDisplays().values()) {
                        IDrawingSpace drawingSpace = playerDisplay.drawingSpace();
                        if (drawingSpace != null) {
                            try {
                                drawingSpace.pixels(pixelData, 0, 0, displayInfo.getWidth() * 128, displayInfo.getHeight() * 128);
                                drawingSpace.flush();
                            } catch (ArrayIndexOutOfBoundsException ignored) {
                            }
                        }
                    }
                }
            }
        };
        displayInfo.setRenderTask(renderLoop.runTaskTimerAsynchronously(plugin, 0L, 2L));
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    /** "Right" as seen by someone standing in front of a screen that faces {@code facing}. */
    public static BlockVector rightOf(BlockFace facing) {
        Vector viewerDir = facing.getOppositeFace().getDirection();
        Vector right = viewerDir.getCrossProduct(new Vector(0, 1, 0));
        return new BlockVector(Math.round(right.getX()), Math.round(right.getY()), Math.round(right.getZ()));
    }

    /** Works out the two corners from the top-left anchor, size and facing. No player needed. */
    public void calculateAndStoreGeometry(MapBrowserDisplay displayInfo) {
        BlockVector rightStep = rightOf(displayInfo.getFacing());
        BlockVector downStep = new BlockVector(0, -1, 0);
        BlockVector cornerA = displayInfo.getLocation().toVector().toBlockVector();
        BlockVector totalOffset = rightStep.clone().multiply(displayInfo.getWidth() - 1)
                .add(downStep.clone().multiply(displayInfo.getHeight() - 1)).toBlockVector();
        BlockVector cornerB = cornerA.clone().add(totalOffset).toBlockVector();
        displayInfo.setCornerA(cornerA);
        displayInfo.setCornerB(cornerB);
    }

    public PlayerDisplay createDisplayForPlayer(Player player, MapBrowserDisplay sourceDisplay) {
        IMapDisplay display = mapEngine.displayProvider().createBasic(sourceDisplay.getCornerA(), sourceDisplay.getCornerB(), sourceDisplay.getFacing());
        IDrawingSpace drawingSpace = mapEngine.pipeline().createDrawingSpace(display);
        drawingSpace.ctx().receivers().add(player);
        drawingSpace.ctx().buffering(true);
        drawingSpace.ctx().converter(Converter.FLOYD_STEINBERG);

        int[] lastFrame = sourceDisplay.getLastPixelData();
        if (lastFrame != null && sourceDisplay.getBrowser() != null && lastFrame.length == sourceDisplay.getWidth() * sourceDisplay.getHeight() * 128 * 128) {
            drawingSpace.pixels(lastFrame, 0, 0, sourceDisplay.getWidth() * 128, sourceDisplay.getHeight() * 128);
            drawingSpace.flush();
        }

        display.spawn(player, 0);

        return new PlayerDisplay(display, drawingSpace);
    }
}
