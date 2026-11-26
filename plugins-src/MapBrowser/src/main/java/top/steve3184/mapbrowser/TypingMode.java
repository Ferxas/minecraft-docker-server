package top.steve3184.mapbrowser;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Escribir" mode: while on, what the player types in chat goes to the screen instead of public chat.
 * <ul>
 *   <li>Normal message → typed + Enter (search boxes just work).</li>
 *   <li>Message starting with {@code +} → typed without Enter.</li>
 *   <li>{@code <} → Backspace, {@code <<<} → three Backspaces, {@code !enter} → Enter only.</li>
 *   <li>{@code !salir} / {@code /mb escribir} → leave.</li>
 * </ul>
 * Turns itself off after a while without typing or if the player walks away.
 */
public class TypingMode implements Listener {

    private record Session(int displayId, long lastUse) {
    }

    private final MapBrowser plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public TypingMode(MapBrowser plugin) {
        this.plugin = plugin;
    }

    public boolean isTyping(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public void start(Player p, MapBrowserDisplay d) {
        sessions.put(p.getUniqueId(), new Session(d.getId(), System.currentTimeMillis()));
        Msg.send(p, "<aqua>✎ Modo escribir</aqua> <gray>en la pantalla <white>#<arg0></white>. Haz clic primero en el campo de texto.", d.getId());
        Msg.info(p, "Lo que escribas en el chat irá a la pantalla<arg0>. <white>+texto</white> = sin Enter · <white>\\<</white> = borrar · <white>!salir</white> = terminar.",
                plugin.getSettings().chatPressesEnter() ? " y pulsará Enter" : "");
        startTicker();
    }

    public void stop(Player p, boolean notify) {
        if (sessions.remove(p.getUniqueId()) != null && notify) {
            Msg.info(p, "Modo escribir desactivado. El chat vuelve a ser normal.");
        }
    }

    private void startTicker() {
        if (ticker != null && !ticker.isCancelled()) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (sessions.isEmpty()) {
                ticker.cancel();
                ticker = null;
                return;
            }
            long now = System.currentTimeMillis();
            long timeout = plugin.getSettings().typingTimeoutSeconds() * 1000L;
            double range = plugin.getSettings().viewDistance();
            sessions.forEach((uuid, s) -> {
                Player p = Bukkit.getPlayer(uuid);
                MapBrowserDisplay d = plugin.getActiveDisplays().get(s.displayId());
                if (p == null) {
                    sessions.remove(uuid);
                    return;
                }
                if (d == null || !p.getWorld().equals(d.getLocation().getWorld())
                        || p.getLocation().distanceSquared(d.getCenter()) > range * range) {
                    sessions.remove(uuid);
                    Msg.info(p, "Te alejaste de la pantalla: modo escribir desactivado.");
                    return;
                }
                if (now - s.lastUse() > timeout) {
                    sessions.remove(uuid);
                    Msg.info(p, "Modo escribir desactivado por inactividad.");
                    return;
                }
                Msg.actionBar(p, "<aqua>✎</aqua> <white>Escribiendo en la pantalla #<arg0></white> <dark_gray>·</dark_gray> <gray>escribe en el chat · <white>!salir</white> para terminar", d.getId());
            });
        }, 20L, 30L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        Bukkit.getScheduler().runTask(plugin, () -> {
            MapBrowserDisplay d = plugin.getActiveDisplays().get(s.displayId());
            if (d == null || d.getBrowser() == null) {
                stop(p, true);
                return;
            }
            sessions.put(p.getUniqueId(), new Session(s.displayId(), System.currentTimeMillis()));
            String msg = raw;
            String lower = msg.trim().toLowerCase();
            if (lower.equals("!salir") || lower.equals("!exit")) {
                stop(p, true);
                return;
            }
            if (lower.equals("!enter")) {
                BrowserActions.enter(d);
                Msg.actionBar(p, "<aqua>⏎</aqua> <gray>Enter");
                return;
            }
            if (!msg.isEmpty() && msg.chars().allMatch(ch -> ch == '<')) {
                for (int i = 0; i < msg.length(); i++) BrowserActions.backspace(d);
                Msg.actionBar(p, "<aqua>⌫</aqua> <gray>Borrado ×<arg0>", msg.length());
                return;
            }
            boolean enter = plugin.getSettings().chatPressesEnter();
            if (msg.startsWith("+")) {
                msg = msg.substring(1);
                enter = false;
            }
            BrowserActions.type(d, msg, enter);
            Msg.actionBar(p, "<aqua>✎</aqua> <white><arg0></white><gray><arg1>", msg, enter ? "  ⏎" : "");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
