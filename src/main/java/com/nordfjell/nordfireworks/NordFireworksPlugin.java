package com.nordfjell.nordfireworks;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class NordFireworksPlugin extends JavaPlugin {
    private FireworkCooldownListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            listener = new FireworkCooldownListener(readTicks(getConfig()));
        } catch (IllegalArgumentException ex) {
            getLogger().severe(ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(listener, this);
        Objects.requireNonNull(getCommand("nordfireworks")).setExecutor(this);
    }

    static int readTicks(FileConfiguration config) {
        Object value = config.get("cooldown-ticks");
        if (!(value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() < 0 || ((Number) value).longValue() > 12000) {
            throw new IllegalArgumentException("cooldown-ticks must be an integer from 0 to 12000");
        }
        return ((Number) value).intValue();
    }

    @Override
    public synchronized boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nordfireworks.admin")) {
            sender.sendMessage("You do not have permission to reload NordFireworks.");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;
        reloadConfig();
        try {
            int ticks = readTicks(getConfig());
            listener.setCooldownTicks(ticks);
            sender.sendMessage("NordFireworks reloaded: " + ticks + " ticks.");
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Reload rejected; previous cooldown remains active. " + ex.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.hasPermission("nordfireworks.admin") && args.length == 1
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
    }
}
