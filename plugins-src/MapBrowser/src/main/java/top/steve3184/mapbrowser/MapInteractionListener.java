package top.steve3184.mapbrowser;

import de.pianoman911.mapengine.api.event.MapClickEvent;
import de.pianoman911.mapengine.api.util.MapClickType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.cef.browser.MapBrowserInstance;

import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clicks on a screen:
 * <ul>
 *   <li>Left click → mouse click at that point.</li>
 *   <li>Right click → scroll down (at that point, so side panels scroll too).</li>
 *   <li>Shift + right click → scroll up.</li>
 *   <li>Shift + left click → remote-control menu.</li>
 * </ul>
 * The original plugin sent right click as a right mouse button, which does nothing (context menus are disabled).
 */
public record MapInteractionListener(MapBrowser plugin, Map<UUID, Long> lastClick) implements Listener {

    public MapInteractionListener(MapBrowser plugin) {
        this(plugin, new ConcurrentHashMap<>());
    }

    @EventHandler
    public void onMapClick(MapClickEvent event) {
        MapBrowserDisplay browserDisplay = plugin.getDisplayLookup().get(event.display());
        if (browserDisplay == null) return;
        Player player = event.player();
        if (player == null || !player.hasPermission("mapbrowser.use")) return;
        MapBrowserInstance browser = browserDisplay.getBrowser();
        if (browser == null) {
            Msg.actionBar(player, "<gray>La pantalla todavía está cargando…");
            return;
        }

        // Some clients send the same click twice in one tick (main hand + off hand).
        long now = System.currentTimeMillis();
        Long prev = lastClick.put(player.getUniqueId(), now);
        if (prev != null && now - prev < 60) return;

        plugin.getDisplayService().setFocus(player, browserDisplay);
        int x = event.x() * browserDisplay.getScale();
        int y = event.y() * browserDisplay.getScale();
        boolean sneaking = player.isSneaking();
        int scroll = plugin.getSettings().scrollPixels();

        if (event.clickType() == MapClickType.RIGHT_CLICK) {
            browser.scrollAt(x, y, sneaking ? -scroll : scroll);
        } else if (sneaking) {
            Runnable open = () -> plugin.getBrowserMenu().open(player, browserDisplay);
            if (Bukkit.isPrimaryThread()) open.run();
            else Bukkit.getScheduler().runTask(plugin, open);
        } else {
            browser.sendMouseClick(x, y, MouseEvent.BUTTON1);
        }
    }

    public void forget(UUID uuid) {
        lastClick.remove(uuid);
    }
}
