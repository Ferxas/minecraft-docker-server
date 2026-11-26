package top.steve3184.mapbrowser;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gameplay settings (everything outside the jcef/browser sections). Reloadable with /mb reload.
 */
public record Settings(
        double viewDistance,
        boolean showHints,
        String defaultUrl,
        int defaultWidth,
        int defaultHeight,
        int maxWidth,
        int maxHeight,
        int scrollPixels,
        boolean chatPressesEnter,
        int typingTimeoutSeconds,
        List<Favorite> favorites
) {

    public record Favorite(String name, Material icon, String url) {
    }

    public static Settings load(FileConfiguration c) {
        List<Favorite> favs = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("favorites")) {
            Object name = m.get("name");
            Object url = m.get("url");
            if (name == null || url == null) continue;
            Material icon = Material.matchMaterial(String.valueOf(m.containsKey("icon") ? m.get("icon") : "PAPER"));
            favs.add(new Favorite(String.valueOf(name), icon == null ? Material.PAPER : icon, String.valueOf(url)));
            if (favs.size() >= 9) break; // one menu row
        }
        ConfigurationSection d = c.getConfigurationSection("defaults");
        return new Settings(
                Math.max(8, c.getDouble("view-distance", 32)),
                c.getBoolean("show-hints", true),
                d != null ? d.getString("url", "https://www.google.com") : "https://www.google.com",
                d != null ? Math.max(1, d.getInt("width", 3)) : 3,
                d != null ? Math.max(1, d.getInt("height", 2)) : 2,
                d != null ? Math.max(1, d.getInt("max-width", 6)) : 6,
                d != null ? Math.max(1, d.getInt("max-height", 4)) : 4,
                Math.max(40, c.getInt("controls.scroll-pixels", 300)),
                c.getBoolean("controls.chat-presses-enter", true),
                Math.max(10, c.getInt("controls.typing-timeout-seconds", 120)),
                favs
        );
    }
}
