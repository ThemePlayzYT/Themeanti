package com.themesmp.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ThemeCommand implements CommandExecutor, TabCompleter {

    private final ThemeAnticheat plugin;

    public ThemeCommand(ThemeAnticheat plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return plugin.color(plugin.getConfig().getString("prefix", "&4Theme &8» "));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("theme.admin")) {
            sender.sendMessage(plugin.color("&cYou don't have permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(prefix() + plugin.color("&7/theme alerts &8- &7toggle alerts"));
            sender.sendMessage(prefix() + plugin.color("&7/theme vl <player> &8- &7show violations"));
            sender.sendMessage(prefix() + plugin.color("&7/theme reload &8- &7reload config"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(prefix() + plugin.color("&aConfig reloaded."));
            }
            case "alerts" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                boolean on = plugin.toggleAlerts(p);
                sender.sendMessage(prefix() + plugin.color(on ? "&7Alerts &aenabled&7." : "&7Alerts &cdisabled&7."));
            }
            case "vl" -> {
                if (args.length < 2) {
                    sender.sendMessage(prefix() + plugin.color("&cUsage: /theme vl <player>"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(prefix() + plugin.color("&cPlayer not found."));
                    return true;
                }
                Map<String, Double> vl = plugin.data(t).vl;
                if (vl.isEmpty()) {
                    sender.sendMessage(prefix() + plugin.color("&f" + t.getName() + " &7has no violations."));
                } else {
                    sender.sendMessage(prefix() + plugin.color("&7Violations for &f" + t.getName() + "&7:"));
                    vl.forEach((k, v) -> sender.sendMessage(plugin.color(
                            " &8- &f" + k + " &7: &c" + String.format("%.1f", v))));
                }
            }
            default -> sender.sendMessage(prefix() + plugin.color("&cUnknown subcommand."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("alerts", "vl", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("vl")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
