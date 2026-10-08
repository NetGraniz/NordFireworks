package com.nordfjell.nordfireworks;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireworkCooldownTest {
    // No running Bukkit server is needed for these handler-level tests.
    static final class Item extends ItemStack {
        final Material type;
        Item(Material type) { super(); this.type = type; }
        @Override public Material getType() { return type; }
    }

    static final class User {
        final Map<Object, Integer> cooldowns = new IdentityHashMap<>();
        boolean bypass;
        boolean customGroup;
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return bypass;
                    if (method.getName().equals("getName")) return "SyntheticPlayer";
                    if (method.getName().equals("getCooldown") || method.getName().equals("hasCooldown")) {
                        int remaining = cooldowns.getOrDefault(group(args[0]), 0);
                        return method.getName().equals("hasCooldown") ? remaining > 0 : (Object) remaining;
                    }
                    if (method.getName().equals("setCooldown")) { cooldowns.put(group(args[0]), (Integer) args[1]); return null; }
                    throw new UnsupportedOperationException(method.getName());
                });
        Object group(Object item) {
            return item instanceof Item stack ? (customGroup ? stack : stack.getType()) : item;
        }
        PlayerElytraBoostEvent boost(ItemStack item, EquipmentSlot hand) {
            Firework firework = (Firework) Proxy.newProxyInstance(Firework.class.getClassLoader(), new Class<?>[]{Firework.class},
                    (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            return new PlayerElytraBoostEvent(player, item, firework, hand);
        }
    }

    @Test void boostAppliesVanillaCooldownAndBlocksOtherHandAndStack() {
        var user = new User();
        var listener = new FireworkCooldownListener(40);
        var first = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND);
        listener.onBoost(first);
        assertFalse(first.isCancelled());
        listener.afterBoost(first);
        assertEquals(40, user.cooldowns.get(Material.FIREWORK_ROCKET));
        var second = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.OFF_HAND);
        listener.onBoost(second);
        assertTrue(second.isCancelled());
        user.cooldowns.clear();
        var third = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND);
        listener.onBoost(third);
        assertFalse(third.isCancelled());
    }

    @Test void cancelledLaunchDoesNotApplyCooldown() {
        var user = new User();
        var listener = new FireworkCooldownListener(40);
        var event = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND);
        event.setCancelled(true);
        listener.afterBoost(event);
        assertTrue(user.cooldowns.isEmpty());
    }

    @Test void groundLaunchSharesCooldownWithFlight() {
        var user = new User();
        var listener = new FireworkCooldownListener(40);
        var boost = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND);
        var launch = new PlayerLaunchProjectileEvent(user.player, boost.getItemStack(), boost.getFirework());
        listener.onLaunch(launch);
        assertFalse(launch.isCancelled());
        listener.afterLaunch(launch);
        var next = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.OFF_HAND);
        listener.onBoost(next);
        assertTrue(next.isCancelled());
        launch.setCancelled(true);
        user.cooldowns.clear();
        listener.afterLaunch(launch);
        assertTrue(user.cooldowns.isEmpty());
    }

    @Test void unrelatedProjectilesAreNotLimited() {
        var user = new User();
        var listener = new FireworkCooldownListener(40);
        user.cooldowns.put(Material.FIREWORK_ROCKET, 40);
        var firework = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND).getFirework();
        var event = new PlayerLaunchProjectileEvent(user.player, new Item(Material.SNOWBALL), firework);
        listener.onLaunch(event);
        assertFalse(event.isCancelled());
        listener.afterLaunch(event);
        assertEquals(1, user.cooldowns.size());
    }

    @Test void customGroupCannotBypassSharedCooldownAndLongerCooldownIsPreserved() {
        var user = new User();
        user.customGroup = true;
        var item = new Item(Material.FIREWORK_ROCKET);
        user.cooldowns.put(item, 80);
        user.cooldowns.put(Material.FIREWORK_ROCKET, 100);
        var listener = new FireworkCooldownListener(40);
        listener.afterBoost(user.boost(item, EquipmentSlot.HAND));
        assertEquals(80, user.cooldowns.get(item));
        assertEquals(100, user.cooldowns.get(Material.FIREWORK_ROCKET));
        var other = user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.OFF_HAND);
        listener.onBoost(other);
        assertTrue(other.isCancelled());
        user.cooldowns.clear();
        listener.afterBoost(user.boost(item, EquipmentSlot.HAND));
        assertEquals(40, user.cooldowns.get(item));
        assertEquals(40, user.cooldowns.get(Material.FIREWORK_ROCKET));
    }

    @Test void interactionDeniesOnlyItemAndPreservesExistingDenial() {
        var user = new User();
        user.cooldowns.put(Material.FIREWORK_ROCKET, 40);
        var listener = new FireworkCooldownListener(40);
        var event = new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_AIR, new Item(Material.FIREWORK_ROCKET), null, BlockFace.SELF);
        event.setUseItemInHand(Event.Result.ALLOW);
        Event.Result blockResult = event.useInteractedBlock();
        listener.onInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(blockResult, event.useInteractedBlock());
        user.cooldowns.clear();
        listener.onInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }

    @Test void disabledAndExplicitBypassDoNotApplyNewCooldowns() {
        var user = new User();
        var listener = new FireworkCooldownListener(0);
        listener.afterBoost(user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND));
        assertTrue(user.cooldowns.isEmpty());
        listener.setCooldownTicks(40);
        user.bypass = true;
        listener.afterBoost(user.boost(new Item(Material.FIREWORK_ROCKET), EquipmentSlot.HAND));
        assertTrue(user.cooldowns.isEmpty());
    }

    @Test void configurationRejectsInvalidValues() {
        for (Object value : new Object[]{-1, 12001, 1.5, "40", true, Long.MAX_VALUE}) {
            var config = new YamlConfiguration();
            config.set("cooldown-ticks", value);
            assertThrows(IllegalArgumentException.class, () -> NordFireworksPlugin.readTicks(config));
        }
        assertThrows(IllegalArgumentException.class, () -> NordFireworksPlugin.readTicks(new YamlConfiguration()));
        for (int value : new int[]{0, 1, 40, 12000}) {
            var config = new YamlConfiguration();
            config.set("cooldown-ticks", value);
            assertEquals(value, NordFireworksPlugin.readTicks(config));
        }
    }
}
