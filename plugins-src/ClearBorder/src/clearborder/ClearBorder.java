package clearborder;

import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fixes client world-border desync after cross-world join/teleport.
 *
 * Paper + Multiverse join-destination: sendLevelInfo can ship the PREVIOUS world's
 * border (e.g. lainWorld @ 172) while the player is already in SakuraSpawn.
 * @see <a href="https://github.com/PaperMC/Paper/issues/13876">Paper#13876</a>
 *
 * Fix: push the current world's WorldBorder to the player after world changes settle.
 */
public class ClearBorder extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("clearborder") != null) {
            getCommand("clearborder").setExecutor(this);
        }
        getLogger().info("ClearBorder enabled (resync WB after cross-world join/tp)");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        // After teleport completes: tick 0 may still be mid-MV; stagger.
        scheduleResync(player, 1L, 5L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleResync(event.getPlayer(), 1L, 5L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Multiverse join-destination often teleports during/after join — cover the race.
        scheduleResync(event.getPlayer(), 5L, 20L, 40L, 80L);
    }

    private void scheduleResync(Player player, long... delays) {
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(this, () -> resync(player), delay);
        }
    }

    /**
     * Force the client to apply this world's border packets.
     * Assigning the world's border (then clearing the personal override) re-sends
     * ClientboundInitializeBorderPacket for the correct dimension.
     */
    private void resync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        WorldBorder worldBorder = player.getWorld().getWorldBorder();
        // Push current world border explicitly (fixes desync from Paper#13876).
        player.setWorldBorder(worldBorder);
        // Drop personal override so future world changes use the world's border naturally.
        player.setWorldBorder(null);
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Resynced WB for " + player.getName()
                    + " in " + player.getWorld().getName()
                    + " size=" + worldBorder.getSize());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found");
                return true;
            }
            resync(target);
            sender.sendMessage("Resynced border for " + target.getName()
                    + " (" + target.getWorld().getName() + " size="
                    + target.getWorld().getWorldBorder().getSize() + ")");
            return true;
        }
        if (sender instanceof Player) {
            Player self = (Player) sender;
            resync(self);
            sender.sendMessage("World border resynced (" + self.getWorld().getName() + ")");
            return true;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            resync(p);
        }
        sender.sendMessage("Resynced all online players");
        return true;
    }
}
