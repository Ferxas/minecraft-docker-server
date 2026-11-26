package top.steve3184.mapbrowser;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.StringUtil;
import org.cef.browser.MapBrowserInstance;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /mb (alias /web, /mapbrowser). Spanish subcommands; the original English ones still work.
 * Commands that act on "a screen" use the one you are looking at, else the last one you clicked,
 * else the nearest. Add {@code #id} to pick one explicitly.
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final MapBrowser plugin;
    private final DisplayService displayService;

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        String[][] pairs = {
                {"create", "crear"}, {"remove", "quitar"}, {"borrar", "quitar"}, {"list", "lista"}, {"near", "cerca"},
                {"go", "ir"}, {"url", "ir"}, {"back", "atras"}, {"atrás", "atras"}, {"forward", "adelante"},
                {"refresh", "recargar"}, {"home", "inicio"}, {"up", "subir"}, {"down", "bajar"},
                {"type", "escribir"}, {"keyboard", "teclado"}, {"size", "tamaño"}, {"tamano", "tamaño"},
                {"move", "mover"}, {"scale", "escala"}, {"help", "ayuda"}, {"reload", "config"},
        };
        for (String[] p : pairs) ALIASES.put(p[0], p[1]);
    }

    private static final List<String> USER_SUBS = List.of("menu", "atras", "adelante", "recargar", "inicio", "subir", "bajar", "escribir", "teclado", "ayuda");
    private static final List<String> ADMIN_SUBS = List.of("crear", "quitar", "lista", "cerca", "ir", "tamaño", "mover", "escala", "config",
            "modify", "input", "keys", "executeJs");

    public CommandHandler(MapBrowser plugin, DisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }

    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("mapbrowser.use")) {
                MapBrowserDisplay d = displayService.resolveTarget(p);
                if (d != null) {
                    plugin.getBrowserMenu().open(p, d);
                    return true;
                }
            }
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        sub = ALIASES.getOrDefault(sub, sub);

        // Console-capable (take an explicit id).
        switch (sub) {
            case "input" -> { handleInput(sender, args); return true; }
            case "keys" -> { handleKeys(sender, args); return true; }
            case "executejs" -> { handleExecuteJs(sender, args); return true; }
            case "config" -> {
                if (!need(sender, "mapbrowser.admin")) return true;
                plugin.reloadSettings();
                Msg.ok(sender, "Configuración recargada (favoritos, controles, tamaños).");
                return true;
            }
            case "ayuda" -> { sendHelp(sender); return true; }
            default -> { }
        }

        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Ese comando solo lo puede usar un jugador.");
            return true;
        }

        switch (sub) {
            // --- everyone (mapbrowser.use) ---
            case "menu" -> withTarget(player, args, 1, "mapbrowser.use", d -> plugin.getBrowserMenu().open(player, d));
            case "atras" -> withTarget(player, args, 1, "mapbrowser.use", d -> BrowserActions.back(player, d));
            case "adelante" -> withTarget(player, args, 1, "mapbrowser.use", d -> BrowserActions.forward(player, d));
            case "recargar" -> withTarget(player, args, 1, "mapbrowser.use", d -> BrowserActions.reload(player, d));
            case "subir" -> withTarget(player, args, 1, "mapbrowser.use", d -> BrowserActions.scroll(player, d, -plugin.getSettings().scrollPixels()));
            case "bajar" -> withTarget(player, args, 1, "mapbrowser.use", d -> BrowserActions.scroll(player, d, plugin.getSettings().scrollPixels()));
            case "inicio" -> handleHome(player, args);
            case "escribir" -> {
                if (!need(player, "mapbrowser.use")) return true;
                if (plugin.getTypingMode().isTyping(player)) {
                    plugin.getTypingMode().stop(player, true);
                } else {
                    withTarget(player, args, 1, "mapbrowser.use", d -> plugin.getTypingMode().start(player, d));
                }
            }
            case "teclado" -> withTarget(player, args, 1, "mapbrowser.use", d -> runSnippet(player, d, "vKeyboard.js"));

            // --- admin ---
            case "crear" -> handleCreate(player, args);
            case "quitar" -> handleRemove(player, args);
            case "lista" -> handleList(player);
            case "cerca" -> handleNear(player);
            case "ir" -> handleGo(player, args);
            case "tamaño" -> handleSize(player, args);
            case "mover" -> handleMove(player, args);
            case "escala" -> handleScale(player, args);
            case "modify" -> handleLegacyModify(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        Msg.err(s, "No tienes permiso para eso.");
        return false;
    }

    private static boolean isHashId(String s) {
        return s.matches("#\\d+");
    }

    /** Finds "#id" anywhere from index on; otherwise the target screen. Returns null (and tells the player) if none. */
    private MapBrowserDisplay pick(Player p, String[] args, int from) {
        for (int i = from; i < args.length; i++) {
            if (isHashId(args[i])) {
                int id = Integer.parseInt(args[i].substring(1));
                MapBrowserDisplay d = plugin.getActiveDisplays().get(id);
                if (d == null) Msg.err(p, "No existe la pantalla #<arg0>.", id);
                return d;
            }
        }
        MapBrowserDisplay d = displayService.resolveTarget(p);
        if (d == null) Msg.err(p, "No hay ninguna pantalla cerca. Acércate o mírala.");
        return d;
    }

    private static String[] withoutHashIds(String[] args, int from) {
        List<String> out = new ArrayList<>();
        for (int i = from; i < args.length; i++) if (!isHashId(args[i])) out.add(args[i]);
        return out.toArray(new String[0]);
    }

    private void withTarget(Player p, String[] args, int from, String perm, java.util.function.Consumer<MapBrowserDisplay> action) {
        if (!need(p, perm)) return;
        MapBrowserDisplay d = pick(p, args, from);
        if (d == null) return;
        displayService.setFocus(p, d);
        action.accept(d);
    }

    private void runSnippet(Player p, MapBrowserDisplay d, String name) {
        MapBrowserInstance b = d.getBrowser();
        if (b == null) {
            Msg.err(p, "La pantalla todavía está cargando.");
            return;
        }
        File f = new File(new File(plugin.getDataFolder(), "snippets"), name);
        try {
            b.executeJavaScript(Files.readString(f.toPath()), null, 0);
            Msg.info(p, "Teclado en pantalla abierto. Púlsalo con <white>clic izquierdo</white>.");
        } catch (IOException e) {
            Msg.err(p, "No encuentro el script <arg0>.", name);
        }
    }

    /** Where a new/moved screen goes: the wall block you look at, centered on it. */
    private record Placement(Location topLeft, BlockFace facing) {
    }

    private Placement placementFromLook(Player p, int width, int height) {
        Block target = p.getTargetBlockExact(24);
        BlockFace face = p.getTargetBlockFace(24);
        if (target == null || face == null) {
            Msg.err(p, "Mira a una pared (a menos de 24 bloques) para poner la pantalla.");
            return null;
        }
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            Msg.err(p, "Las pantallas van en paredes, no en el suelo ni el techo. Mira a una pared.");
            return null;
        }
        BlockVector right = DisplayService.rightOf(face);
        Location topLeft = target.getLocation()
                .subtract(right.getX() * ((width - 1) / 2), 0, right.getZ() * ((width - 1) / 2))
                .add(0, height / 2, 0);
        return new Placement(topLeft, face);
    }

    private int[] clampSize(Player p, int w, int h) {
        Settings s = plugin.getSettings();
        if (!p.hasPermission("mapbrowser.admin") && (w > s.maxWidth() || h > s.maxHeight())) {
            Msg.info(p, "Tamaño máximo: <arg0>×<arg1>. Lo ajusto.", s.maxWidth(), s.maxHeight());
            w = Math.min(w, s.maxWidth());
            h = Math.min(h, s.maxHeight());
        }
        if (w * h > 16) {
            Msg.info(p, "<yellow>Aviso:</yellow> <arg0>×<arg1> mapas es pesado para el servidor; 3×2 o 4×3 van mucho mejor.", w, h);
        }
        return new int[]{w, h};
    }

    // ------------------------------------------------------------------
    // Admin commands
    // ------------------------------------------------------------------

    /**
     * /mb crear [url] [ancho] [alto]           — on the wall you look at
     * /mb crear &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;url&gt; [w] [h]  — old style, still works
     */
    private void handleCreate(Player p, String[] args) {
        if (!need(p, "mapbrowser.command.create")) return;
        Settings s = plugin.getSettings();

        Location topLeft;
        BlockFace facing;
        String url;
        int w, h;
        try {
            boolean legacy = args.length >= 5 && isNumber(args[1]) && isNumber(args[2]) && isNumber(args[3]);
            if (legacy) {
                topLeft = new Location(p.getWorld(), Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                facing = p.getFacing().getOppositeFace();
                url = args[4];
                w = args.length > 5 ? Integer.parseInt(args[5]) : s.defaultWidth();
                h = args.length > 6 ? Integer.parseInt(args[6]) : s.defaultHeight();
            } else {
                // Accept "/mb crear 4 3" (size only), "/mb crear youtube.com", "/mb crear youtube.com 4 3".
                int i = 1;
                url = s.defaultUrl();
                if (args.length > 1 && !isNumber(args[1])) {
                    url = args[1];
                    i = 2;
                }
                w = args.length > i ? Integer.parseInt(args[i]) : s.defaultWidth();
                h = args.length > i + 1 ? Integer.parseInt(args[i + 1]) : s.defaultHeight();
                topLeft = null;
                facing = null;
            }
        } catch (NumberFormatException e) {
            Msg.err(p, "Uso: <white>/mb crear [web] [ancho] [alto]</white>  (mirando a una pared)");
            return;
        }
        if (w <= 0 || h <= 0) {
            Msg.err(p, "El ancho y el alto tienen que ser mayores que 0.");
            return;
        }
        int[] size = clampSize(p, w, h);
        w = size[0];
        h = size[1];
        if (topLeft == null) {
            Placement pl = placementFromLook(p, w, h);
            if (pl == null) return;
            topLeft = pl.topLeft();
            facing = pl.facing();
        }
        String normalized = BrowserActions.normalizeUrl(url);
        if (normalized == null) {
            Msg.err(p, "Esa dirección no está permitida (solo http/https).");
            return;
        }

        final Location fTopLeft = topLeft;
        final BlockFace fFacing = facing;
        final int fw = w, fh = h;
        Msg.info(p, "Creando pantalla <white><arg0>×<arg1></white> con <aqua><arg2></aqua>…", w, h, Msg.shortUrl(normalized));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MapBrowserDisplay d = displayService.createAndInitializeDisplay(-1, p, normalized, fTopLeft, fFacing, fw, fh, 1);
            if (d != null) {
                plugin.getStore().saveAsync();
                Msg.ok(p, "Pantalla <white>#<arg0></white> creada. Se guarda sola y vuelve tras los reinicios.", d.getId());
                Msg.actionBar(p, Msg.HINT_CONTROLS);
            } else {
                Msg.err(p, "No se pudo crear la pantalla (mira la consola).");
            }
        });
    }

    private static boolean isNumber(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    private void handleRemove(Player p, String[] args) {
        if (!need(p, "mapbrowser.command.remove")) return;
        MapBrowserDisplay d;
        if (args.length > 1 && args[1].matches("#?\\d+")) {
            int id = Integer.parseInt(args[1].replace("#", ""));
            d = plugin.getActiveDisplays().get(id);
            if (d == null) {
                Msg.err(p, "No existe la pantalla #<arg0>.", id);
                return;
            }
        } else {
            d = pick(p, args, 1);
            if (d == null) return;
        }
        displayService.removeDisplay(d.getId());
        plugin.getStore().saveAsync();
        Msg.ok(p, "Pantalla #<arg0> quitada.", d.getId());
    }

    private void handleList(Player p) {
        if (!need(p, "mapbrowser.command.list")) return;
        Map<Integer, MapBrowserDisplay> all = plugin.getActiveDisplays();
        if (all.isEmpty()) {
            Msg.info(p, "No hay pantallas. Crea una mirando a una pared: <white>/mb crear</white>");
            return;
        }
        Msg.send(p, "<gold>Pantallas web (<arg0>):", all.size());
        all.values().stream().sorted(Comparator.comparingInt(MapBrowserDisplay::getId)).forEach(d -> {
            Location l = d.getLocation();
            p.sendMessage(Msg.c(" <yellow>#<arg0></yellow> <white><arg1></white> <gray>· <arg2> <arg3>,<arg4>,<arg5> · <aqua><arg6>×<arg7></aqua> · <arg8> mirando",
                    d.getId(), Msg.shortUrl(d.getCurrentUrl()), l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                    d.getWidth(), d.getHeight(), d.getViewerDisplays().size()));
        });
    }

    private void handleNear(Player p) {
        if (!need(p, "mapbrowser.command.near")) return;
        MapBrowserDisplay d = plugin.getActiveDisplays().values().stream()
                .filter(x -> x.getLocation().getWorld().equals(p.getWorld()))
                .min(Comparator.comparingDouble(x -> x.getCenter().distanceSquared(p.getLocation())))
                .orElse(null);
        if (d == null) {
            Msg.info(p, "No hay pantallas en este mundo.");
            return;
        }
        Msg.send(p, "<green>La más cercana es la <white>#<arg0></white> <gray>(<arg1> bloques) · <arg2>",
                d.getId(), String.format("%.1f", Math.sqrt(d.getCenter().distanceSquared(p.getLocation()))), Msg.shortUrl(d.getCurrentUrl()));
    }

    /** /mb ir <web o búsqueda> [#id] */
    private void handleGo(Player p, String[] args) {
        if (!need(p, "mapbrowser.navigate")) return;
        String[] rest = withoutHashIds(args, 1);
        if (rest.length == 0) {
            Msg.err(p, "Uso: <white>/mb ir \\<web o búsqueda></white>  ej. <white>/mb ir youtube.com</white>");
            return;
        }
        String url = BrowserActions.normalizeUrl(String.join(" ", rest));
        if (url == null) {
            Msg.err(p, "Esa dirección no está permitida (solo http/https).");
            return;
        }
        MapBrowserDisplay d = pick(p, args, 1);
        if (d == null) return;
        displayService.setFocus(p, d);
        BrowserActions.go(p, d, url);
        plugin.getStore().saveAsync();
    }

    /** /mb inicio → go home; /mb inicio fijar → current page becomes home (admin). */
    private void handleHome(Player p, String[] args) {
        if (args.length > 1 && (args[1].equalsIgnoreCase("fijar") || args[1].equalsIgnoreCase("set"))) {
            if (!need(p, "mapbrowser.command.modify")) return;
            MapBrowserDisplay d = pick(p, args, 2);
            if (d == null) return;
            d.setHomeUrl(d.getCurrentUrl());
            plugin.getStore().saveAsync();
            Msg.ok(p, "Inicio de la pantalla #<arg0>: <white><arg1>", d.getId(), Msg.shortUrl(d.getHomeUrl()));
            return;
        }
        withTarget(p, args, 1, "mapbrowser.use", d -> BrowserActions.home(p, d));
    }

    private void handleSize(Player p, String[] args) {
        if (!need(p, "mapbrowser.command.modify")) return;
        String[] rest = withoutHashIds(args, 1);
        if (rest.length < 2 || !isNumber(rest[0]) || !isNumber(rest[1])) {
            Msg.err(p, "Uso: <white>/mb tamaño \\<ancho> \\<alto></white>");
            return;
        }
        MapBrowserDisplay d = pick(p, args, 1);
        if (d == null) return;
        int[] size = clampSize(p, Integer.parseInt(rest[0]), Integer.parseInt(rest[1]));
        if (size[0] <= 0 || size[1] <= 0) {
            Msg.err(p, "El ancho y el alto tienen que ser mayores que 0.");
            return;
        }
        displayService.resizeDisplay(p, d, size[0], size[1]);
        plugin.getStore().saveAsync();
        Msg.ok(p, "Pantalla #<arg0> ahora mide <white><arg1>×<arg2></white>.", d.getId(), size[0], size[1]);
    }

    /** /mb mover [#id] → to the wall you look at. */
    private void handleMove(Player p, String[] args) {
        if (!need(p, "mapbrowser.command.modify")) return;
        MapBrowserDisplay d;
        // Use explicit id or the last focused one — not "the one you look at", since you are looking at the new spot.
        String[] ids = Arrays.stream(args).skip(1).filter(CommandHandler::isHashId).toArray(String[]::new);
        if (ids.length > 0) {
            d = plugin.getActiveDisplays().get(Integer.parseInt(ids[0].substring(1)));
        } else {
            d = plugin.getActiveDisplays().values().stream()
                    .filter(x -> x.getLocation().getWorld().equals(p.getWorld()))
                    .min(Comparator.comparingDouble(x -> x.getCenter().distanceSquared(p.getLocation())))
                    .orElse(null);
        }
        if (d == null) {
            Msg.err(p, "No encuentro la pantalla. Usa <white>/mb mover #id</white>.");
            return;
        }
        Placement pl = placementFromLook(p, d.getWidth(), d.getHeight());
        if (pl == null) return;
        displayService.moveDisplay(p, d, pl.topLeft(), pl.facing());
        plugin.getStore().saveAsync();
        Msg.ok(p, "Pantalla #<arg0> movida.", d.getId());
    }

    private void handleScale(Player p, String[] args) {
        if (!need(p, "mapbrowser.command.modify")) return;
        String[] rest = withoutHashIds(args, 1);
        if (rest.length < 1 || !rest[0].matches("\\d+") || Integer.parseInt(rest[0]) < 1 || Integer.parseInt(rest[0]) > 4) {
            Msg.err(p, "Uso: <white>/mb escala \\<1-4></white> <gray>(más = la web se ve más pequeña, cabe más contenido)");
            return;
        }
        MapBrowserDisplay d = pick(p, args, 1);
        if (d == null) return;
        displayService.scaleDisplay(d, Integer.parseInt(rest[0]));
        plugin.getStore().saveAsync();
        Msg.ok(p, "Escala de la pantalla #<arg0>: <white><arg1>", d.getId(), rest[0]);
    }

    /** Original syntax: /mb modify <id> url|refresh|pos|size|scale ... */
    private void handleLegacyModify(Player p, String[] args) {
        if (!need(p, "mapbrowser.command.modify")) return;
        if (args.length < 3) {
            Msg.err(p, "Uso: /mb modify \\<id> \\<url|refresh|pos|size|scale> [valores]");
            return;
        }
        MapBrowserDisplay d = getById(args[1], p);
        if (d == null) return;
        String prop = args[2].toLowerCase(Locale.ROOT);
        String id = "#" + d.getId();
        switch (prop) {
            case "refresh" -> BrowserActions.reload(p, d);
            case "url" -> {
                if (args.length < 4) { Msg.err(p, "Uso: /mb modify \\<id> url \\<url>"); return; }
                handleGo(p, new String[]{"ir", id, args[3]});
            }
            case "size" -> {
                if (args.length < 5) { Msg.err(p, "Uso: /mb modify \\<id> size \\<ancho> \\<alto>"); return; }
                handleSize(p, new String[]{"tamaño", id, args[3], args[4]});
            }
            case "scale" -> {
                if (args.length < 4) { Msg.err(p, "Uso: /mb modify \\<id> scale \\<n>"); return; }
                handleScale(p, new String[]{"escala", id, args[3]});
            }
            case "pos" -> {
                if (args.length < 6) { Msg.err(p, "Uso: /mb modify \\<id> pos \\<x> \\<y> \\<z>"); return; }
                try {
                    Location loc = new Location(p.getWorld(), Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                    displayService.moveDisplay(p, d, loc, p.getFacing().getOppositeFace());
                    plugin.getStore().saveAsync();
                    Msg.ok(p, "Pantalla <arg0> movida.", id);
                } catch (NumberFormatException e) {
                    Msg.err(p, "Coordenadas no válidas.");
                }
            }
            case "devtools" -> Msg.err(p, "DevTools no funciona aquí; usa browser.remote-debugging en config.yml.");
            default -> Msg.err(p, "Propiedad desconocida. Usa: url, refresh, pos, size, scale.");
        }
    }

    // ------------------------------------------------------------------
    // Console-capable (original) commands
    // ------------------------------------------------------------------

    private MapBrowserDisplay getById(String idString, CommandSender s) {
        try {
            int id = Integer.parseInt(idString.replace("#", ""));
            MapBrowserDisplay d = plugin.getActiveDisplays().get(id);
            if (d == null) Msg.err(s, "No existe la pantalla #<arg0>.", id);
            return d;
        } catch (NumberFormatException e) {
            Msg.err(s, "El id tiene que ser un número.");
            return null;
        }
    }

    private void handleInput(CommandSender s, String[] args) {
        if (!need(s, "mapbrowser.command.input")) return;
        if (args.length < 3) {
            Msg.err(s, "Uso: /mb input \\<id> \\<texto…>  <gray>(más cómodo: /mb escribir)");
            return;
        }
        MapBrowserDisplay d = getById(args[1], s);
        if (d == null || d.getBrowser() == null) return;
        d.getBrowser().inputText(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        Msg.ok(s, "Texto enviado a la pantalla #<arg0>.", d.getId());
    }

    private void handleKeys(CommandSender s, String[] args) {
        if (!need(s, "mapbrowser.command.keys")) return;
        if (args.length != 4) {
            Msg.err(s, "Uso: /mb keys \\<id> \\<tecla> \\<pressDown|pressUp|click>");
            return;
        }
        MapBrowserDisplay d = getById(args[1], s);
        if (d == null || d.getBrowser() == null) return;
        Integer keyCode = KeyMap.getKeyCode(args[2]);
        if (keyCode == null) {
            Msg.err(s, "Tecla desconocida: <arg0> (usa el tabulador para ver la lista).", args[2]);
            return;
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        if (!List.of("pressdown", "pressup", "click").contains(action)) {
            Msg.err(s, "Acción no válida. Usa: pressDown, pressUp o click.");
            return;
        }
        d.getBrowser().handleKeyAction(keyCode, action);
        Msg.ok(s, "Tecla <arg0> enviada a la pantalla #<arg1>.", args[2].toUpperCase(Locale.ROOT), d.getId());
    }

    private void handleExecuteJs(CommandSender s, String[] args) {
        if (!need(s, "mapbrowser.command.executejs")) return;
        if (args.length < 3) {
            Msg.err(s, "Uso: /mb executeJs \\<id> \\<código|archivo.js>");
            return;
        }
        MapBrowserDisplay d = getById(args[1], s);
        if (d == null) return;
        MapBrowserInstance b = d.getBrowser();
        if (b == null) {
            Msg.err(s, "La pantalla #<arg0> todavía está cargando.", d.getId());
            return;
        }
        String joined = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String script;
        if (joined.endsWith(".js") && !joined.contains("..") && !joined.startsWith("/") && !joined.startsWith("\\")) {
            File f = new File(new File(plugin.getDataFolder(), "snippets"), joined);
            if (!f.exists()) {
                Msg.err(s, "No existe snippets/<arg0>.", joined);
                return;
            }
            try {
                script = Files.readString(f.toPath());
            } catch (IOException e) {
                Msg.err(s, "No se pudo leer <arg0>: <arg1>", joined, e.getMessage());
                return;
            }
        } else {
            script = joined;
        }
        b.executeJavaScript(script, null, 0);
        Msg.ok(s, "JavaScript ejecutado en la pantalla #<arg0>.", d.getId());
    }

    // ------------------------------------------------------------------
    // Help / tab completion
    // ------------------------------------------------------------------

    private void sendHelp(CommandSender s) {
        Msg.send(s, "<gold>Pantallas web — cómo se usan");
        s.sendMessage(Msg.c(" <white>En la pantalla:</white> <gray>clic izq. = pulsar · clic der. = bajar · Shift+der. = subir · Shift+izq. = menú"));
        s.sendMessage(Msg.c(" <aqua>/mb</aqua> <gray>o</gray> <aqua>/mb menu</aqua> <gray>— mando de la pantalla más cercana"));
        s.sendMessage(Msg.c(" <aqua>/mb escribir</aqua> <gray>— lo que escribas en el chat va a la web (otra vez para salir)"));
        s.sendMessage(Msg.c(" <aqua>/mb atras · adelante · recargar · inicio · subir · bajar · teclado"));
        if (s.hasPermission("mapbrowser.command.create")) {
            s.sendMessage(Msg.c(" <gold>Admin:</gold>"));
            s.sendMessage(Msg.c(" <aqua>/mb crear [web] [ancho] [alto]</aqua> <gray>— en la pared que miras (por defecto <arg0>×<arg1>)",
                    plugin.getSettings().defaultWidth(), plugin.getSettings().defaultHeight()));
            s.sendMessage(Msg.c(" <aqua>/mb ir \\<web o búsqueda></aqua> <gray>— ej. youtube.com o \"recetas de pizza\""));
            s.sendMessage(Msg.c(" <aqua>/mb tamaño \\<ancho> \\<alto> · mover · escala \\<1-4> · quitar · lista · cerca"));
            s.sendMessage(Msg.c(" <aqua>/mb inicio fijar</aqua> <gray>— la página actual pasa a ser la de inicio"));
            s.sendMessage(Msg.c(" <gray>Añade <white>#id</white> para elegir otra pantalla (ej. <white>/mb ir #2 google.com</white>). Se guardan solas."));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("mapbrowser.use")) subs.addAll(USER_SUBS);
            if (sender.hasPermission("mapbrowser.command.create")) subs.addAll(ADMIN_SUBS);
            return StringUtil.copyPartialMatches(args[0], subs, out);
        }
        String sub = ALIASES.getOrDefault(args[0].toLowerCase(Locale.ROOT), args[0].toLowerCase(Locale.ROOT));
        String last = args[args.length - 1];
        List<String> hashIds = plugin.getActiveDisplays().keySet().stream().sorted().map(i -> "#" + i).collect(Collectors.toList());
        List<String> plainIds = plugin.getActiveDisplays().keySet().stream().sorted().map(String::valueOf).collect(Collectors.toList());

        switch (sub) {
            case "crear" -> {
                if (args.length == 2) {
                    List<String> opts = new ArrayList<>(List.of("google.com", "youtube.com", "wikipedia.org"));
                    plugin.getSettings().favorites().forEach(f -> opts.add(f.url().replaceFirst("^https?://(www\\.)?", "").replaceAll("/$", "")));
                    return StringUtil.copyPartialMatches(last, opts.stream().distinct().toList(), out);
                }
                if (args.length == 3) return StringUtil.copyPartialMatches(last, List.of("2", "3", "4"), out);
                if (args.length == 4) return StringUtil.copyPartialMatches(last, List.of("2", "3"), out);
            }
            case "ir" -> {
                if (args.length == 2) {
                    List<String> opts = new ArrayList<>(hashIds);
                    plugin.getSettings().favorites().forEach(f -> opts.add(f.url().replaceFirst("^https?://(www\\.)?", "").replaceAll("/$", "")));
                    return StringUtil.copyPartialMatches(last, opts, out);
                }
            }
            case "quitar", "input", "keys", "executejs", "modify" -> {
                if (args.length == 2) return StringUtil.copyPartialMatches(last, sub.equals("quitar") ? hashIds : plainIds, out);
                if (sub.equals("keys") && args.length == 3) return StringUtil.copyPartialMatches(last, KeyMap.getKeyNames(), out);
                if (sub.equals("keys") && args.length == 4) return StringUtil.copyPartialMatches(last, List.of("click", "pressDown", "pressUp"), out);
                if (sub.equals("modify") && args.length == 3) return StringUtil.copyPartialMatches(last, List.of("url", "refresh", "pos", "size", "scale"), out);
                if (sub.equals("executejs") && args.length == 3) return StringUtil.copyPartialMatches(last, List.of("vKeyboard.js"), out);
            }
            case "tamaño" -> {
                if (args.length == 2) return StringUtil.copyPartialMatches(last, List.of("2", "3", "4"), out);
                if (args.length == 3) return StringUtil.copyPartialMatches(last, List.of("2", "3"), out);
                if (args.length == 4) return StringUtil.copyPartialMatches(last, hashIds, out);
            }
            case "escala" -> {
                if (args.length == 2) return StringUtil.copyPartialMatches(last, List.of("1", "2", "3"), out);
                if (args.length == 3) return StringUtil.copyPartialMatches(last, hashIds, out);
            }
            case "inicio" -> {
                if (args.length == 2) {
                    List<String> opts = new ArrayList<>(hashIds);
                    if (sender.hasPermission("mapbrowser.command.modify")) opts.add("fijar");
                    return StringUtil.copyPartialMatches(last, opts, out);
                }
            }
            default -> {
                if (args.length == 2) return StringUtil.copyPartialMatches(last, hashIds, out);
            }
        }
        return Collections.emptyList();
    }
}
