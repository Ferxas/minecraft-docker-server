package top.steve3184.mapbrowser;

import org.bukkit.entity.Player;
import org.cef.browser.MapBrowserInstance;

import java.awt.event.KeyEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Small navigation helpers shared by commands, the menu, clicks and chat typing.
 */
public final class BrowserActions {

    private static final Pattern HAS_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    private static final Pattern LOOKS_LIKE_HOST = Pattern.compile("^[^\\s/]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$|^localhost(:\\d+)?(/.*)?$");

    private BrowserActions() {
    }

    /**
     * Turns what a player typed into a URL: "youtube.com" → https://youtube.com,
     * "recetas de pizza" → Google search. Returns null for blocked schemes.
     */
    public static String normalizeUrl(String input) {
        String s = input.trim();
        if (s.isEmpty()) return null;
        if (HAS_SCHEME.matcher(s).matches()) {
            String lower = s.toLowerCase();
            if (lower.startsWith("http://") || lower.startsWith("https://")) return s;
            return null; // file://, chrome://, javascript: ... not allowed
        }
        if (s.toLowerCase().startsWith("javascript:") || s.toLowerCase().startsWith("data:")) return null;
        if (LOOKS_LIKE_HOST.matcher(s).matches()) return "https://" + s;
        return "https://www.google.com/search?q=" + URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static MapBrowserInstance browser(Player p, MapBrowserDisplay d) {
        MapBrowserInstance b = d.getBrowser();
        if (b == null) Msg.err(p, "La pantalla #<arg0> todavía está cargando.", d.getId());
        return b;
    }

    public static void go(Player p, MapBrowserDisplay d, String url) {
        MapBrowserInstance b = browser(p, d);
        if (b == null) return;
        b.loadURL(url);
        d.setUrl(url);
        Msg.actionBar(p, "<aqua>↗</aqua> <white><arg0>", Msg.shortUrl(url));
    }

    public static void back(Player p, MapBrowserDisplay d) {
        MapBrowserInstance b = browser(p, d);
        if (b == null) return;
        if (b.canGoBack()) {
            b.goBack();
            Msg.actionBar(p, "<aqua>◀</aqua> <gray>Atrás");
        } else {
            Msg.actionBar(p, "<gray>No hay página anterior");
        }
    }

    public static void forward(Player p, MapBrowserDisplay d) {
        MapBrowserInstance b = browser(p, d);
        if (b == null) return;
        if (b.canGoForward()) {
            b.goForward();
            Msg.actionBar(p, "<aqua>▶</aqua> <gray>Adelante");
        } else {
            Msg.actionBar(p, "<gray>No hay página siguiente");
        }
    }

    public static void reload(Player p, MapBrowserDisplay d) {
        MapBrowserInstance b = browser(p, d);
        if (b == null) return;
        b.reload();
        Msg.actionBar(p, "<aqua>⟳</aqua> <gray>Recargando…");
    }

    public static void home(Player p, MapBrowserDisplay d) {
        go(p, d, d.getHomeUrl());
    }

    /** Scroll at the center of the screen. */
    public static void scroll(Player p, MapBrowserDisplay d, int deltaY) {
        MapBrowserInstance b = browser(p, d);
        if (b == null) return;
        int cx = d.getWidth() * 128 * d.getScale() / 2;
        int cy = d.getHeight() * 128 * d.getScale() / 2;
        b.scrollAt(cx, cy, deltaY);
    }

    public static void type(MapBrowserDisplay d, String text, boolean pressEnter) {
        MapBrowserInstance b = d.getBrowser();
        if (b == null) return;
        b.inputText(text);
        if (pressEnter) b.pressKey(KeyEvent.VK_ENTER, '\r');
    }

    public static void enter(MapBrowserDisplay d) {
        MapBrowserInstance b = d.getBrowser();
        if (b != null) b.pressKey(KeyEvent.VK_ENTER, '\r');
    }

    public static void backspace(MapBrowserDisplay d) {
        MapBrowserInstance b = d.getBrowser();
        if (b != null) b.pressKey(KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED);
    }
}
