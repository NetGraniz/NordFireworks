package com.nordfjell.nordfireworks.test;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Local synthetic test companion only; never deploy to production. */
public final class FireworksTestProbe extends JavaPlugin implements Listener {
    record Counts(int boosts, int ground, boolean cancel) {}
    private final Map<UUID, Counts> counts = new ConcurrentHashMap<>();

    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void cancelBoost(PlayerElytraBoostEvent event) {
        if (counts.getOrDefault(event.getPlayer().getUniqueId(), new Counts(0, 0, false)).cancel()) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void cancelGround(PlayerLaunchProjectileEvent event) {
        if (event.getItemStack().getType() == Material.FIREWORK_ROCKET
                && counts.getOrDefault(event.getPlayer().getUniqueId(), new Counts(0, 0, false)).cancel()) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countBoost(PlayerElytraBoostEvent event) {
        counts.compute(event.getPlayer().getUniqueId(), (key, old) -> new Counts(old == null ? 1 : old.boosts() + 1, old == null ? 0 : old.ground(), false));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countGround(PlayerLaunchProjectileEvent event) {
        if (event.getItemStack().getType() == Material.FIREWORK_ROCKET)
            counts.compute(event.getPlayer().getUniqueId(), (key, old) -> new Counts(old == null ? 0 : old.boosts(), old == null ? 1 : old.ground() + 1, false));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length < 2) return true;
        Player player = getServer().getPlayerExact(args[1]);
        if (player == null) { getLogger().info("FW_MISSING"); return true; }
        player.getScheduler().run(this, task -> {
            try {
                switch (args[0]) {
                    case "prepare" -> {
                        boolean air = args.length > 2 && args[2].equals("air");
                        player.setGliding(false);
                        player.setGravity(false);
                        player.setCooldown(Material.FIREWORK_ROCKET, 0);
                        player.getInventory().clear();
                        player.getInventory().setHeldItemSlot(0);
                        player.getInventory().setItem(0, ItemStack.of(Material.FIREWORK_ROCKET, 64));
                        player.getInventory().setItem(1, ItemStack.of(Material.FIREWORK_ROCKET, 64));
                        player.getInventory().setItemInOffHand(ItemStack.of(Material.FIREWORK_ROCKET, 64));
                        player.getInventory().setChestplate(ItemStack.of(Material.ELYTRA));
                        counts.put(player.getUniqueId(), new Counts(0, 0, false));
                        if (air) {
                            var target = player.getLocation().add(0, 30, 0);
                            player.teleportAsync(target).thenAccept(ok -> player.getScheduler().run(this, next -> {
                                player.setGliding(true);
                                getLogger().info("FW_PREPARED air " + ok);
                            }, null));
                        } else {
                            var block = player.getLocation().getBlock().getRelative(0, -1, 0);
                            block.setType(Material.STONE);
                            getLogger().info("FW_PREPARED ground " + block.getX() + " " + block.getY() + " " + block.getZ());
                        }
                    }
                    case "state" -> {
                        Counts c = counts.getOrDefault(player.getUniqueId(), new Counts(0, 0, false));
                        getLogger().info("FW_STATE " + player.getCooldown(Material.FIREWORK_ROCKET) + " "
                                + player.getInventory().getItemInMainHand().getAmount() + " "
                                + player.getInventory().getItemInOffHand().getAmount() + " "
                                + c.boosts() + " " + c.ground() + " " + player.isGliding());
                    }
                    case "cancel" -> {
                        counts.put(player.getUniqueId(), new Counts(0, 0, true));
                        player.setCooldown(Material.FIREWORK_ROCKET, 0);
                        getLogger().info("FW_CANCEL_READY");
                    }
                    case "bypass" -> {
                        player.addAttachment(this, "nordfireworks.bypass", true);
                        player.setCooldown(Material.FIREWORK_ROCKET, 0);
                        getLogger().info("FW_BYPASS_READY");
                    }
                }
            } catch (Throwable ex) { getLogger().log(java.util.logging.Level.SEVERE, "FW_PROBE_FAILED", ex); }
        }, null);
        return true;
    }
}
