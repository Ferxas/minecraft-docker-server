package top.steve3184.mapbrowser;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Cleans up per-player state when someone leaves. Showing screens to players that join or come close
 * is done by the proximity checker in {@link DisplayService} (runs every second).
 */
public class PlayerConnectionListener implements Listener {

    private final MapBrowser plugin;
    private final DisplayService displayService;

    public PlayerConnectionListener(MapBrowser plugin, DisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        for (MapBrowserDisplay display : plugin.getActiveDisplays().values()) {
            synchronized (display.getViewerDisplays()) {
                PlayerDisplay playerDisplay = display.getViewerDisplays().remove(playerUUID);
                if (playerDisplay != null) {
                    // Client-side entities vanish with the connection; only drop our tracking.
                    plugin.getDisplayLookup().remove(playerDisplay.mapDisplay());
                }
            }
        }
        displayService.forgetPlayer(playerUUID);
        plugin.getInteractionListener().forget(playerUUID);
    }
}
