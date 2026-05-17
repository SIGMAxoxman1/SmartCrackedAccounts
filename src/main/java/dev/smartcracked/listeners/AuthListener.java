package dev.smartcracked.listeners;

import dev.smartcracked.SmartCrackedAccounts;
import dev.smartcracked.managers.AuthManager;
import dev.smartcracked.managers.ConfigManager;
import dev.smartcracked.managers.WorldManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.UUID;

public class AuthListener implements Listener {

    private final SmartCrackedAccounts plugin;
    private final ConfigManager cfg;
    private final AuthManager auth;
    private final WorldManager world;

    public AuthListener(SmartCrackedAccounts plugin) {
        this.plugin = plugin;
        this.cfg    = plugin.getConfigManager();
        this.auth   = plugin.getAuthManager();
        this.world  = plugin.getWorldManager();
    }

    private boolean isPremium(UUID uuid) {
        // Premium UUID = version 4 (من Mojang)
        // Cracked UUID = version 3 (offline hash من الاسم)
        return uuid.version() == 4;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (isPremium(player.getUniqueId())) return;
        if (plugin.isFloodgateEnabled() && FloodgateHelper.isBedrockPlayer(player)) return;
        if (!cfg.isWhitelisted(player.getName())) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, cfg.getMessage("not-whitelisted"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isPremium(player.getUniqueId())) return;
        if (plugin.isFloodgateEnabled() && FloodgateHelper.isBedrockPlayer(player)) return;
        if (!cfg.isWhitelisted(player.getName())) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // FIX 3 & 4: نحفظ الـ inventory الأول قبل أي حاجة
            auth.saveAndClearInventory(player);

            // FIX 1: نغير GameMode لـ SPECTATOR بدل الطيران
            // SPECTATOR مش بيقع ومش محتاج allow-flight في server.properties
            player.setGameMode(GameMode.SPECTATOR);

            // ننقله للوبي
            Location lobbyLoc = world.getLobbyLocation();
            player.teleport(lobbyLoc);

            auth.addPending(player);

            if (auth.isRegistered(player.getName())) {
                player.sendMessage(cfg.getMessage("please-login"));
            } else {
                player.sendMessage(cfg.getMessage("please-register"));
            }
        });
    }

    // FIX 2: نضيف handler للـ commands الجديدة في plugin.yml
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!auth.isPending(player)) return;

        String msg = event.getMessage().trim();
        String lower = msg.toLowerCase();

        if (lower.startsWith("/login") || lower.startsWith("/l ") || lower.equals("/l")) {
            event.setCancelled(true);
            String[] args = msg.split(" ");
            if (args.length < 2) {
                player.sendMessage(cfg.getMessage("please-login"));
                return;
            }
            AuthManager.LoginResult result = auth.login(player, args[1]);
            switch (result) {
                case SUCCESS           -> finishAuth(player);
                case WRONG_PASSWORD    -> player.sendMessage(cfg.getMessage("wrong-password"));
                case NOT_REGISTERED    -> player.sendMessage(cfg.getMessage("not-registered"));
                case ALREADY_LOGGED_IN -> player.sendMessage(cfg.getMessage("already-logged-in"));
                case TOO_MANY_ATTEMPTS -> player.kickPlayer(cfg.getMessage("too-many-attempts"));
            }
            return;
        }

        if (lower.startsWith("/register") || lower.startsWith("/reg ") || lower.equals("/reg")) {
            event.setCancelled(true);
            String[] args = msg.split(" ");
            if (args.length < 3) {
                player.sendMessage(cfg.getMessage("please-register"));
                return;
            }
            AuthManager.RegisterResult result = auth.register(player, args[1], args[2]);
            switch (result) {
                case SUCCESS            -> finishAuth(player);
                case PASSWORD_MISMATCH  -> player.sendMessage(cfg.getMessage("password-mismatch"));
                case ALREADY_REGISTERED -> player.sendMessage(cfg.getMessage("already-registered"));
                case TOO_SHORT          -> player.sendMessage(
                    cfg.getMessage("password-too-short", player.getName(), cfg.getMinPasswordLength()));
            }
            return;
        }

        // أي أمر تاني امنعه
        event.setCancelled(true);
        player.sendMessage(auth.isRegistered(player.getName())
            ? cfg.getMessage("please-login")
            : cfg.getMessage("please-register"));
    }

    private void finishAuth(Player player) {
        // FIX 3: نرجع GameMode الأول قبل ما نرجع الـ inventory
        player.setGameMode(GameMode.SURVIVAL);

        // FIX 3 & 4: الـ inventory بترجع جوه AuthManager.setAuthenticated
        // بس لازم نتأكد إنها بترجع على الـ main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            auth.restoreInventoryPublic(player);
            player.sendMessage(cfg.getMessage("login-success"));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        auth.removePending(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        auth.removePending(event.getPlayer());
    }
}
