package top.steve3184.mapbrowser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Remote-control menu for a screen (opened with Shift + left click on the screen or /mb menu).
 *
 * <pre>
 *  ◀ Atrás  ▶ Adelante  ⟳ Recargar  ⌂ Inicio  · ▲ Subir  ▼ Bajar  · ✕
 *  [ favoritos de config.yml ........................................ ]
 *  ✎ Escribir  ⌨ Teclado  ⏎ Enter  ⌫ Borrar  · ℹ Página actual  · ⚙ Admin
 * </pre>
 */
public class BrowserMenu implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final MapBrowser plugin;

    public BrowserMenu(MapBrowser plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        final int displayId;
        Inventory inv;

        Holder(int displayId) {
            this.displayId = displayId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p, MapBrowserDisplay d) {
        Holder h = new Holder(d.getId());
        Inventory inv = Bukkit.createInventory(h, 27, Msg.c("<dark_aqua>Pantalla web #<arg0>", d.getId()));
        h.inv = inv;

        inv.setItem(0, item(Material.ARROW, "<aqua>◀ Atrás", "<gray>Página anterior"));
        inv.setItem(1, item(Material.SPECTRAL_ARROW, "<aqua>▶ Adelante", "<gray>Página siguiente"));
        inv.setItem(2, item(Material.SUNFLOWER, "<yellow>⟳ Recargar", "<gray>Vuelve a cargar la página"));
        inv.setItem(3, item(Material.OAK_DOOR, "<green>⌂ Inicio", "<gray>" + escape(Msg.shortUrl(d.getHomeUrl()))));
        inv.setItem(5, item(Material.FEATHER, "<aqua>▲ Subir", "<gray>También: <white>Shift + clic derecho</white> en la pantalla"));
        inv.setItem(6, item(Material.ANVIL, "<aqua>▼ Bajar", "<gray>También: <white>clic derecho</white> en la pantalla"));
        inv.setItem(8, item(Material.BARRIER, "<red>✕ Cerrar"));

        List<Settings.Favorite> favs = plugin.getSettings().favorites();
        for (int i = 0; i < favs.size(); i++) {
            Settings.Favorite f = favs.get(i);
            ItemStack it = new ItemStack(f.icon());
            ItemMeta m = it.getItemMeta();
            m.displayName(LEGACY.deserialize(f.name()).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Msg.c("<gray><arg0>", Msg.shortUrl(f.url())).decoration(TextDecoration.ITALIC, false),
                    Msg.c("<dark_gray>Clic para abrir").decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(m);
            inv.setItem(9 + i, it);
        }

        inv.setItem(18, item(Material.WRITABLE_BOOK, "<aqua>✎ Escribir desde el chat",
                "<gray>Haz clic en un campo de la web", "<gray>y luego escribe en el chat."));
        inv.setItem(19, item(Material.KNOWLEDGE_BOOK, "<aqua>⌨ Teclado en pantalla",
                "<gray>Muestra un teclado dentro de la web", "<gray>que se pulsa con clic izquierdo."));
        inv.setItem(20, item(Material.LIME_DYE, "<green>⏎ Enter"));
        inv.setItem(21, item(Material.RED_DYE, "<red>⌫ Borrar", "<gray>Borra una letra"));
        inv.setItem(23, item(Material.PAPER, "<white>ℹ Página actual",
                "<gray>" + escape(Msg.shortUrl(d.getCurrentUrl())), "<dark_gray>Clic: enviar el enlace al chat"));
        if (p.hasPermission("mapbrowser.command.modify")) {
            inv.setItem(26, item(Material.COMPARATOR, "<gold>⚙ Admin",
                    "<gray>/mb ir \\<url> <dark_gray>— abrir cualquier web",
                    "<gray>/mb inicio fijar <dark_gray>— esta página como inicio",
                    "<gray>/mb tamaño \\<ancho> \\<alto>",
                    "<gray>/mb mover <dark_gray>— a donde miras",
                    "<gray>/mb quitar"));
        }
        p.openInventory(inv);
    }

    private static String escape(String s) {
        return s.replace("<", "\\<");
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.c(s).decoration(TextDecoration.ITALIC, false));
        if (!l.isEmpty()) m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        MapBrowserDisplay d = plugin.getActiveDisplays().get(h.displayId);
        if (d == null) {
            p.closeInventory();
            Msg.err(p, "Esa pantalla ya no existe.");
            return;
        }
        int scroll = plugin.getSettings().scrollPixels();
        switch (slot) {
            case 0 -> BrowserActions.back(p, d);
            case 1 -> BrowserActions.forward(p, d);
            case 2 -> BrowserActions.reload(p, d);
            case 3 -> {
                BrowserActions.home(p, d);
                p.closeInventory();
            }
            case 5 -> BrowserActions.scroll(p, d, -scroll);
            case 6 -> BrowserActions.scroll(p, d, scroll);
            case 8 -> p.closeInventory();
            case 18 -> {
                p.closeInventory();
                plugin.getTypingMode().start(p, d);
            }
            case 19 -> {
                p.closeInventory();
                runSnippet(p, d, "vKeyboard.js");
            }
            case 20 -> {
                BrowserActions.enter(d);
                Msg.actionBar(p, "<aqua>⏎</aqua> <gray>Enter");
            }
            case 21 -> {
                BrowserActions.backspace(d);
                Msg.actionBar(p, "<aqua>⌫</aqua> <gray>Borrado");
            }
            case 23 -> {
                p.closeInventory();
                String url = d.getCurrentUrl();
                p.sendMessage(Msg.c(Msg.PREFIX + "<gray>Página actual: <aqua><u><arg0></u></aqua> <dark_gray>(clic para abrir)", url)
                        .clickEvent(ClickEvent.openUrl(url)));
            }
            default -> {
                if (slot >= 9 && slot < 18) {
                    List<Settings.Favorite> favs = plugin.getSettings().favorites();
                    int i = slot - 9;
                    if (i < favs.size()) {
                        BrowserActions.go(p, d, favs.get(i).url());
                        p.closeInventory();
                    }
                }
            }
        }
        plugin.getDisplayService().setFocus(p, d);
    }

    private void runSnippet(Player p, MapBrowserDisplay d, String name) {
        if (d.getBrowser() == null) return;
        File f = new File(new File(plugin.getDataFolder(), "snippets"), name);
        try {
            d.getBrowser().executeJavaScript(Files.readString(f.toPath()), null, 0);
            Msg.info(p, "Teclado en pantalla abierto. Púlsalo con <white>clic izquierdo</white>.");
        } catch (IOException ex) {
            Msg.err(p, "No encuentro el script <arg0>.", name);
        }
    }
}
