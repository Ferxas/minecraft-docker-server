package top.steve3184.mapbrowser;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockVector;
import org.cef.browser.MapBrowserInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A data class that holds all components and information for a single browser display instance.
 * This includes the browser itself, its in-game representation, and its properties.
 */
public class MapBrowserDisplay {

    // --- Core Properties ---
    private final int id; // The unique identifier for this display.
    private String url; // The URL last set by a command/menu (the browser may have navigated since).
    private String homeUrl; // The "home" page the Inicio button goes back to.
    private Location location; // The top-left anchor block (the wall block the frames sit on).
    private int width; // The width of the display in blocks/maps.
    private int height; // The height of the display in blocks/maps.

    // --- Browser & Rendering Components ---
    private volatile MapBrowserInstance browser; // The underlying CEF browser instance.
    private BukkitTask renderTask; // The scheduled task that copies pixels from the browser to the map.

    // --- Player-Specific Visuals ---
    // A map of client-side displays, one for each player currently viewing.
    private final Map<UUID, PlayerDisplay> viewerDisplays = new ConcurrentHashMap<>();

    // --- Geometric Properties ---
    private BlockVector cornerA; // The first corner block of the display area.
    private BlockVector cornerB; // The second corner block of the display area.
    private BlockFace facing; // The direction the item frames are facing (towards the viewer).

    private volatile int[] lastPixelData = null; // The last pixel frame data
    private int scale;

    public MapBrowserDisplay(int id, String url, Location location, BlockFace facing, int width, int height) {
        this.id = id;
        this.url = url;
        this.homeUrl = url;
        this.location = location;
        this.facing = facing;
        this.width = width;
        this.height = height;
        this.scale = 1;
    }

    public int getId() { return id; }
    public String getUrl() { return url; }
    public String getHomeUrl() { return homeUrl; }
    public Location getLocation() { return location; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getScale() { return scale; }
    public MapBrowserInstance getBrowser() { return browser; }

    public BukkitTask getRenderTask() { return this.renderTask; }
    public Map<UUID, PlayerDisplay> getViewerDisplays() { return viewerDisplays; }
    public BlockVector getCornerA() { return cornerA; }
    public BlockVector getCornerB() { return cornerB; }
    public BlockFace getFacing() { return facing; }
    public int[] getLastPixelData() { return lastPixelData; }

    /** The page the browser is really showing (it changes when people click links). */
    public String getCurrentUrl() {
        MapBrowserInstance b = browser;
        if (b != null) {
            try {
                String current = b.getURL();
                if (current != null && !current.isBlank() && !current.startsWith("about:")) return current;
            } catch (Throwable ignored) {
            }
        }
        return url;
    }

    /** Center of the screen surface, used for distance checks. */
    public Location getCenter() {
        if (cornerA == null || cornerB == null) return location;
        return new Location(location.getWorld(),
                (cornerA.getX() + cornerB.getX()) / 2.0 + 0.5,
                (cornerA.getY() + cornerB.getY()) / 2.0 + 0.5,
                (cornerA.getZ() + cornerB.getZ()) / 2.0 + 0.5);
    }

    public void setUrl(String url) { this.url = url; }
    public void setHomeUrl(String homeUrl) { this.homeUrl = homeUrl; }
    public void setLocation(Location location) { this.location = location; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setScale(int scale) { this.scale = scale; }
    public void setBrowser(MapBrowserInstance browser) { this.browser = browser; }
    public void setRenderTask(BukkitTask renderTask) { this.renderTask = renderTask; }
    public void setCornerA(BlockVector cornerA) { this.cornerA = cornerA; }
    public void setCornerB(BlockVector cornerB) { this.cornerB = cornerB; }
    public void setFacing(BlockFace facing) { this.facing = facing; }
    public void setLastPixelData(int[] lastPixelData) { this.lastPixelData = lastPixelData; }


    /**
     * Performs a full cleanup, destroying all visual components and the browser instance.
     * This is used when the display is permanently removed.
     */
    public void cleanup() {
        // First, clean up all in-game visuals.
        if (renderTask != null && !renderTask.isCancelled()) {
            renderTask.cancel();
        }
        viewerDisplays.forEach((uuid, playerDisplay) -> playerDisplay.destroy(Bukkit.getPlayer(uuid)));
        viewerDisplays.clear();

        // Then, close the underlying browser instance.
        MapBrowserInstance b = browser;
        browser = null;
        if (b != null) {
            b.loadURL("about:blank");
            b.close(true);
        }
        lastPixelData = null;
    }
}
