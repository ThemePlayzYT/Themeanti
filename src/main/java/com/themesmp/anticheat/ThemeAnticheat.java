package com.themesmp.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ThemeAnticheat extends JavaPlugin {

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new CheckListener(this), this);

        PluginCommand cmd = getCommand("theme");
        if (cmd != null) {
            ThemeCommand handler = new ThemeCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            data(p);
        }

        // Violation decay, once per minute
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double amount = getConfig().getDouble("settings.decay-per-minute", 1.0);
            for (PlayerData d : data.values()) {
                d.vl.replaceAll((k, v) -> Math.max(0.0, v - amount));
            }
        }, 1200L, 1200L);

        getLogger().info("Theme Anticheat enabled.");
    }

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> new PlayerData());
    }

    public void remove(Player p) {
        data.remove(p.getUniqueId());
        alertsOff.remove(p.getUniqueId());
    }

    public boolean isEnabled(String check) {
        return getConfig().getBoolean("checks." + check + ".enabled", true);
    }

    public double cfg(String path, double def) {
        return getConfig().getDouble(path, def);
    }

    public boolean toggleAlerts(Player p) {
        if (alertsOff.remove(p.getUniqueId())) {
            return true;
        }
        alertsOff.add(p.getUniqueId());
        return false;
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String format(String template, Player p, String check, double vl, int max, String info) {
        return color(template
                .replace("{prefix}", getConfig().getString("prefix", ""))
                .replace("{player}", p.getName())
                .replace("{check}", check)
                .replace("{vl}", String.format("%.1f", vl))
                .replace("{max}", String.valueOf(max))
                .replace("{info}", info));
    }

    /**
     * Flags a player. Returns true if the caller should set the player back
     * (cancel the movement / hit).
     */
    public boolean flag(Player p, String check, String info, double weight) {
        if (!isEnabled(check)) {
            return false;
        }
        PlayerData d = data(p);
        double vl = d.vl.merge(check, weight, Double::sum);
        int max = getConfig().getInt("checks." + check + ".max-vl", 10);

        String msg = format(getConfig().getString("alert-format", "{player} failed {check} [{vl}/{max}] {info}"),
                p, check, vl, max, info);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("theme.alerts") && !alertsOff.contains(staff.getUniqueId())) {
                staff.sendMessage(msg);
            }
        }
        getLogger().info(ChatColor.stripColor(msg));

        if (vl >= max) {
            punish(p, check, vl);
            return false;
        }
        return getConfig().getBoolean("setback", true);
    }

    private void punish(Player p, String check, double vl) {
        PlayerData d = data(p);
        d.vl.clear();

        for (String line : getConfig().getStringList("punish-broadcast")) {
            Bukkit.broadcastMessage(format(line, p, check, vl, 0, ""));
        }
        List<String> cmds = getConfig().getStringList("punish-commands");
        for (String c : cmds) {
            String run = c.replace("{player}", p.getName())
                    .replace("{check}", check)
                    .replace("{vl}", String.format("%.0f", vl));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
        }
        getLogger().warning(p.getName() + " was punished for " + check);
    }
}
