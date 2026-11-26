package top.steve3184.mapbrowser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Chat/action bar messages (Spanish), MiniMessage format.
 * Use {@code <arg0>}, {@code <arg1>}... as unparsed placeholders so URLs/user text are never parsed as tags.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<aqua>Web</aqua>]</dark_gray> ";

    public static final String HINT_CONTROLS =
            "<aqua>Pantalla web</aqua> <dark_gray>·</dark_gray> <white>Clic izq.</white><gray> pulsar</gray> <dark_gray>·</dark_gray> "
                    + "<white>Clic der.</white><gray> bajar</gray> <dark_gray>·</dark_gray> <white>Shift+der.</white><gray> subir</gray> "
                    + "<dark_gray>·</dark_gray> <white>Shift+izq.</white><gray> menú</gray>";

    private Msg() {
    }

    private static Component parse(String mini, Object... args) {
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof Component comp) b.resolver(Placeholder.component("arg" + i, comp));
            else b.resolver(Placeholder.unparsed("arg" + i, String.valueOf(a)));
        }
        return MM.deserialize(mini, b.build());
    }

    public static Component c(String mini, Object... args) {
        return parse(mini, args);
    }

    public static void send(CommandSender to, String mini, Object... args) {
        to.sendMessage(parse(PREFIX + mini, args));
    }

    public static void ok(CommandSender to, String mini, Object... args) {
        send(to, "<green>" + mini, args);
    }

    public static void info(CommandSender to, String mini, Object... args) {
        send(to, "<gray>" + mini, args);
    }

    public static void err(CommandSender to, String mini, Object... args) {
        send(to, "<red>" + mini, args);
    }

    public static void actionBar(Player p, String mini, Object... args) {
        p.sendActionBar(parse(mini, args));
    }

    /** Shortens long URLs for chat/lore. */
    public static String shortUrl(String url) {
        if (url == null) return "";
        String u = url.replaceFirst("^https?://(www\\.)?", "");
        return u.length() > 40 ? u.substring(0, 39) + "…" : u;
    }
}
