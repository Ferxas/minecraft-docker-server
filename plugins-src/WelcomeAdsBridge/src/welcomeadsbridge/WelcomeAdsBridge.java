package welcomeadsbridge;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.World;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens WelcomeAds without {@code Bukkit.getPlayer} console chains and avoids reopen glitches:
 * on close, WelcomeAds schedules {@code restorePlayerInventory} 1 tick later; reopening immediately
 * leaves the welcome GUI open when that restore runs (hotbar + "Inventory" row visible, broken layout).
 */
public final class WelcomeAdsBridge extends JavaPlugin implements CommandExecutor, Listener {

    private static final String META_OPEN = "welcomeads_open";
    private static final String WELCOME_HOLDER = "com.tinysx.welcomeads.WelcomeInventoryHolder";
    private static final int OPEN_DELAY_TICKS = 4;
    private static final Set<String> HUB_MENU_WORLDS = Set.of("SakuraSpawn", "Runnerslobby", "HGLobby");

    private final Map<UUID, BukkitTask> uiGuardTasks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (getCommand("welcomeadspage") != null) {
            getCommand("welcomeadspage").setExecutor(this);
        }
        if (getCommand("welcomeadsopen") != null) {
            getCommand("welcomeadsopen").setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("WelcomeAdsBridge enabled (/welcomeadspage, /welcomeadsopen, UI guard)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("welcomeadspage")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cSolo jugadores.");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("§eUso: /welcomeadspage <contenedor> §7(ej. welcome-1, welcome-2)");
                return true;
            }
            scheduleOpen(player, args[0], 0);
            return true;
        }

        if (command.getName().equalsIgnoreCase("welcomeadsopen")) {
            if (!sender.hasPermission("welcomeads.open")) {
                sender.sendMessage("§cSin permiso welcomeads.open");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§eUso: /welcomeadsopen <contenedor> <jugador>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cJugador no encontrado (en línea): §e" + args[1]);
                return true;
            }
            scheduleOpen(target, args[0], 0);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLegacyWelcomeAdsCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("/welcomeads open ")) {
            return;
        }
        String[] parts = raw.split("\\s+");
        if (parts.length < 4) {
            return;
        }
        event.setCancelled(true);
        Player target = Bukkit.getPlayerExact(parts[3]);
        if (target == null) {
            event.getPlayer().sendMessage("§cJugador no en línea: §e" + parts[3]);
            return;
        }
        if (!event.getPlayer().hasPermission("welcomeads.open")) {
            event.getPlayer().sendMessage("§cUsa §e/welcomeadspage " + parts[2] + "§c o pide permiso welcomeads.open");
            return;
        }
        scheduleOpen(target, parts[2], 0);
    }

    @EventHandler
    public void onWelcomeOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isWelcomeTop(event.getInventory())) {
            return;
        }
        player.setMetadata(META_OPEN, new FixedMetadataValue(this, true));
        stripHubItems(player);
        ensurePlayerInventoryHidden(player);
        polishWelcomeInventory(event.getInventory());
        polishVisiblePlayerItems(player);
        UUID id = player.getUniqueId();
        BukkitTask prev = uiGuardTasks.remove(id);
        if (prev != null) {
            prev.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || !player.hasMetadata(META_OPEN)) {
                BukkitTask t = uiGuardTasks.remove(id);
                if (t != null) {
                    t.cancel();
                }
                return;
            }
            if (!isWelcomeTop(player.getOpenInventory().getTopInventory())) {
                player.removeMetadata(META_OPEN, WelcomeAdsBridge.this);
                BukkitTask t = uiGuardTasks.remove(id);
                if (t != null) {
                    t.cancel();
                }
                return;
            }
            stripHubItems(player);
            ensurePlayerInventoryHidden(player);
            polishWelcomeInventory(player.getOpenInventory().getTopInventory());
            polishVisiblePlayerItems(player);
        }, 1L, 2L);
        uiGuardTasks.put(id, task);
    }

    @EventHandler
    public void onWelcomeClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isWelcomeHolder(event.getInventory().getHolder())) {
            return;
        }
        player.removeMetadata(META_OPEN, this);
        BukkitTask t = uiGuardTasks.remove(player.getUniqueId());
        if (t != null) {
            t.cancel();
        }
        scheduleHubMenuRestore(player);
    }

    void scheduleHubMenuRestore(Player player) {
        for (long delay : new long[] { 3L, 12L, 40L }) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline() || player.hasMetadata(META_OPEN)) {
                    return;
                }
                if (!isHubMenuWorld(player.getWorld())) {
                    return;
                }
                if (isWelcomeTop(player.getOpenInventory().getTopInventory())) {
                    return;
                }
                giveHubMenuClock(player);
            }, delay);
        }
    }

    static boolean isHubMenuWorld(World world) {
        return world != null && HUB_MENU_WORLDS.contains(world.getName());
    }

    static void giveHubMenuClock(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isHubMenuItem(stack)) {
                player.getInventory().remove(stack);
            }
        }
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lMenú");
            meta.setLore(List.of("§7Clic derecho: abrir menú", "§7Minijuegos, spawn, skin..."));
            clock.setItemMeta(meta);
        }
        player.getInventory().setItem(0, clock);
        player.updateInventory();
    }

    static boolean isWelcomeTop(Inventory inv) {
        return isWelcomeHolder(inv.getHolder());
    }

    static boolean isWelcomeHolder(InventoryHolder holder) {
        return holder != null && WELCOME_HOLDER.equals(holder.getClass().getName());
    }

    static void stripHubItems(Player player) {
        removeHubMenuStacks(player.getInventory().getStorageContents(), player);
        removeHubMenuStacks(player.getInventory().getArmorContents(), player);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isHubMenuItem(off)) {
            player.getInventory().setItemInOffHand(null);
        }
        player.updateInventory();
    }

    static void removeHubMenuStacks(ItemStack[] stacks, Player player) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (isHubMenuItem(stack)) {
                player.getInventory().remove(stack);
            }
        }
    }

    static boolean isHubMenuItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName()) {
            return false;
        }
        String name = meta.getDisplayName();
        if (!name.contains("Menú") && !name.contains("Menu")) {
            return false;
        }
        Material type = stack.getType();
        return type == Material.CLOCK
                || type == Material.COMPASS
                || type == Material.RECOVERY_COMPASS;
    }

    static void ensurePlayerInventoryHidden(Player player) {
        try {
            Class<?> safe = Class.forName("com.tinysx.welcomeads.SafeInventoryManager");
            if (!(boolean) safe.getMethod("isHidingEnabled").invoke(null)) {
                return;
            }
            if (!(boolean) safe.getMethod("hasHiddenInventory", Player.class).invoke(null, player)) {
                safe.getMethod("hidePlayerInventory", Player.class).invoke(null, player);
            }
        } catch (ReflectiveOperationException ignored) {
            // WelcomeAds API unchanged
        }
    }

    static void polishVisiblePlayerItems(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            applyNoTooltip(stack);
        }
        player.updateInventory();
    }

    static void applyNoTooltip(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setHideTooltip(true);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_DYE);
        stack.setItemMeta(meta);
    }

    static void polishWelcomeInventory(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            applyNoTooltip(stack);
            inv.setItem(i, stack);
        }
    }

    void scheduleOpen(Player player, String container, int containerIndex) {
        closeWelcomeScreen(player);
        cancelWelcomeTransition(player);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            restoreHiddenInventory(player);
            stripHubItems(player);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!openScreen(player, container, containerIndex)) {
                    player.sendMessage("§cNo se pudo abrir WelcomeAds (contenedor §e" + container + "§c).");
                }
            }, 1L);
        }, OPEN_DELAY_TICKS);
    }

    static void closeWelcomeScreen(Player player) {
        if (isWelcomeHolder(player.getOpenInventory().getTopInventory().getHolder())) {
            player.closeInventory();
        }
    }

    static void cancelWelcomeTransition(Player player) {
        try {
            Class<?> pdm = Class.forName("com.tinysx.welcomeads.PlayerDataManager");
            Object session = pdm.getMethod("get", Player.class).invoke(null, player);
            if (session != null) {
                session.getClass().getMethod("cancelTransitionTask").invoke(session);
            }
        } catch (ReflectiveOperationException ignored) {
            // WelcomeAds API unchanged
        }
    }

    static void restoreHiddenInventory(Player player) {
        try {
            Class<?> safe = Class.forName("com.tinysx.welcomeads.SafeInventoryManager");
            safe.getMethod("restorePlayerInventory", Player.class).invoke(null, player);
        } catch (ReflectiveOperationException ignored) {
            // WelcomeAds API unchanged
        }
    }

    static boolean openScreen(Player player, String container, int containerIndex) {
        try {
            Class<?> screenClass = Class.forName("com.tinysx.welcomeads.Screen");
            Object screen = screenClass
                    .getConstructor(String.class, Integer.class, Player.class)
                    .newInstance(container, containerIndex, player);
            screenClass.getMethod("openTo", Player.class).invoke(screen, player);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
