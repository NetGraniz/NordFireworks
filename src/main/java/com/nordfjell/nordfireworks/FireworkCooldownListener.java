package com.nordfjell.nordfireworks;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** All player access is synchronous in the player's owning event context on Paper/Folia. */
public final class FireworkCooldownListener implements Listener {
    private volatile int cooldownTicks;

    public FireworkCooldownListener(int ticks) { setCooldownTicks(ticks); }

    public void setCooldownTicks(int ticks) {
        if (ticks < 0 || ticks > 12000) throw new IllegalArgumentException("cooldown-ticks must be 0..12000");
        cooldownTicks = ticks;
    }

    private boolean limited(Player player) {
        return cooldownTicks > 0 && !player.hasPermission("nordfireworks.bypass");
    }

    private boolean coolingDown(Player player, ItemStack item) {
        return player.hasCooldown(Material.FIREWORK_ROCKET) || player.hasCooldown(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.useItemInHand() == Event.Result.DENY) return;
        if (limited(event.getPlayer()) && coolingDown(event.getPlayer(), item)) {
            // Do not cancel an unrelated block interaction or re-enable a denied action.
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        if (limited(event.getPlayer()) && coolingDown(event.getPlayer(), event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (event.getItemStack().getType() == Material.FIREWORK_ROCKET
                && limited(event.getPlayer()) && coolingDown(event.getPlayer(), event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBoost(PlayerElytraBoostEvent event) {
        if (!event.isCancelled()) apply(event.getPlayer(), event.getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterLaunch(PlayerLaunchProjectileEvent event) {
        if (!event.isCancelled() && event.getItemStack().getType() == Material.FIREWORK_ROCKET) {
            apply(event.getPlayer(), event.getItemStack());
        }
    }

    private void apply(Player player, ItemStack item) {
        int ticks = cooldownTicks;
        if (ticks == 0 || player.hasPermission("nordfireworks.bypass")) return;
        // Global material group prevents bypass by switching stacks/hands/custom rocket groups.
        // Keep any longer cooldown set by another plugin.
        if (player.getCooldown(Material.FIREWORK_ROCKET) < ticks) {
            player.setCooldown(Material.FIREWORK_ROCKET, ticks);
        }
        if (player.getCooldown(item) < ticks) player.setCooldown(item, ticks);
    }
}
