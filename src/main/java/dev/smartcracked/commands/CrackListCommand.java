package dev.smartcracked.commands;

import dev.smartcracked.SmartCrackedAccounts;
import dev.smartcracked.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * أمر /cracklist للأدمن
 * /cracklist add <name>
 * /cracklist remove <name>
 * /cracklist list
 */
public class CrackListCommand implements CommandExecutor {

    private final ConfigManager cfg;

    public CrackListCommand(SmartCrackedAccounts plugin) {
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // فحص الصلاحية
        if (!sender.hasPermission("sca.admin")) {
            sender.sendMessage(cfg.getMessage("admin-no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(cfg.getMessage("admin-usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(cfg.getMessage("admin-usage"));
                    return true;
                }
                String name = args[1];
                if (cfg.isWhitelisted(name)) {
                    sender.sendMessage(cfg.getMessage("admin-already-exists", name));
                } else {
                    cfg.addToWhitelist(name);
                    sender.sendMessage(cfg.getMessage("admin-added", name));
                }
            }

            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(cfg.getMessage("admin-usage"));
                    return true;
                }
                String name = args[1];
                if (!cfg.isWhitelisted(name)) {
                    sender.sendMessage(cfg.getMessage("admin-not-found", name));
                } else {
                    cfg.removeFromWhitelist(name);
                    sender.sendMessage(cfg.getMessage("admin-removed", name));
                }
            }

            case "list" -> {
                List<String> players = cfg.getWhitelistedPlayers();
                sender.sendMessage(cfg.getMessage("admin-list-header"));
                if (players.isEmpty()) {
                    sender.sendMessage(cfg.getMessage("admin-list-empty"));
                } else {
                    for (String p : players) {
                        sender.sendMessage(cfg.getMessage("admin-list-entry", p));
                    }
                    sender.sendMessage(cfg.getMessage("admin-list-footer", 
                            String.valueOf(players.size()), 0));
                }
            }

            default -> sender.sendMessage(cfg.getMessage("admin-usage"));
        }

        return true;
    }
}
