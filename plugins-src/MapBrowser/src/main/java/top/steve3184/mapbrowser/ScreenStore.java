package top.steve3184.mapbrowser;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * Saves screens to plugins/MapBrowser/screens.yml so they survive restarts.
 * The original plugin kept them only in RAM.
 */
public class ScreenStore {

    public record Saved(int id, String world, int x, int y, int z, BlockFace facing,
                        int width, int height, int scale, String url, String home) {
    }

    private final MapBrowser plugin;
    private final File file;

    public ScreenStore(MapBrowser plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "screens.yml");
    }

    public List<Saved> load() {
        List<Saved> out = new ArrayList<>();
        if (!file.exists()) return out;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection screens = yml.getConfigurationSection("screens");
        if (screens == null) return out; // old format (list) from the skript era is ignored
        for (String key : screens.getKeys(false)) {
            ConfigurationSection s = screens.getConfigurationSection(key);
            if (s == null) continue;
            try {
                int id = Integer.parseInt(key);
                BlockFace facing = BlockFace.valueOf(s.getString("facing", "NORTH").toUpperCase());
                String url = s.getString("url", plugin.getSettings().defaultUrl());
                out.add(new Saved(id, s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"), facing,
                        Math.max(1, s.getInt("width", 1)), Math.max(1, s.getInt("height", 1)), Math.max(1, s.getInt("scale", 1)),
                        url, s.getString("home", url)));
            } catch (Exception e) {
                plugin.getLogger().warning("screens.yml: entrada '" + key + "' inválida, se ignora (" + e.getMessage() + ")");
            }
        }
        out.sort(Comparator.comparingInt(Saved::id));
        return out;
    }

    /** Writes every active display. Safe to call from any thread (writes are serialized). */
    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of(
                "Pantallas web de MapBrowser. Las guarda el plugin solo (al crear, borrar o cambiar una pantalla",
                "y cada pocos minutos). Puedes editarlo con el servidor apagado."));
        for (MapBrowserDisplay d : plugin.getActiveDisplays().values()) {
            Location l = d.getLocation();
            World w = l.getWorld();
            if (w == null) continue;
            String p = "screens." + d.getId() + ".";
            yml.set(p + "world", w.getName());
            yml.set(p + "x", l.getBlockX());
            yml.set(p + "y", l.getBlockY());
            yml.set(p + "z", l.getBlockZ());
            yml.set(p + "facing", d.getFacing().name());
            yml.set(p + "width", d.getWidth());
            yml.set(p + "height", d.getHeight());
            yml.set(p + "scale", d.getScale());
            yml.set(p + "url", d.getCurrentUrl());
            yml.set(p + "home", d.getHomeUrl());
        }
        try {
            File tmp = new File(file.getParentFile(), "screens.yml.tmp");
            yml.save(tmp);
            if (!tmp.renameTo(file)) {
                yml.save(file);
                tmp.delete();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar screens.yml", e);
        }
    }

    public void saveAsync() {
        if (!plugin.isEnabled()) {
            save();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
