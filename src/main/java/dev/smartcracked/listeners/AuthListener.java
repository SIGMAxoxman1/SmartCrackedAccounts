package dev.smartcracked.listeners;

import dev.smartcracked.SmartCrackedAccounts;
import dev.smartcracked.managers.AuthManager;
import dev.smartcracked.managers.ConfigManager;
import dev.smartcracked.managers.WorldManager;
import org.bukkit.GameMode;
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

    /**
     * الكشف عن نوع اللاعب بدون isOnlineMode() (اتشالت في Paper 1.21)
     *
     * لاعب Premium (أصلي): UUID بتاعه version 4 - جاي من سيرفرات Mojang
     * لاعب Cracked:        UUID بتاعه version 3 - مبني على اسمه (offline hash)
     *
     * ده نفس الأسلوب اللي بيستخدمه FastLogin وغيره من auth plugins
     */
    private boolean isPremium(UUID uuid) {
        return uuid.version() == 4;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        // 1) أصلي (Premium UUID version 4)؟ سيبه يعدي
        if (isPremium(player.getUniqueId())) return;

        // 2) بيدروك (Floodgate)؟ سيبه يعدي
        if (plugin.isFloodgateEnabled() && FloodgateHelper.isBedrockPlayer(player)) return;

        // 3) كراك - نشوف لو اسمه في القائمة
        if (!cfg.isWhitelisted(player.getName())) {
            event.disallow(
                PlayerLoginEvent.Result.KICK_OTHER,
                cfg.getMessage("not-whitelisted")
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // نفس الفحص
        if (isPremium(player.getUniqueId())) return;
        if (plugin.isFloodgateEnabled() && FloodgateHelper.isBedrockPlayer(player)) return;
        if (!cfg.isWhitelisted(player.getName())) return;

        // كراك ومسموح له - نجهّزه للـ auth
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            auth.saveAndClearInventory(player);
            player.teleport(world.getLobbyLocation());
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGameMode(GameMode.ADVENTURE);
            auth.addPending(player);

            if (auth.isRegistered(player.getName())) {
                player.sendMessage(cfg.getMessage("please-login"));
            } else {
                player.sendMessage(cfg.getMessage("please-register"));
            }
        });
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!auth.isPending(player)) return;

        String msg = event.getMessage().trim();

        if (msg.toLowerCase().startsWith("/login")) {
            event.setCancelled(true);
            String[] args = msg.split(" ");
            if (args.length < 2) {
                player.sendMessage(cfg.getMessage("please-login"));
                return;
            }
            AuthManager.LoginResult result = auth.login(player, args[1]);
            switch (result) {
                case SUCCESS        -> finishAuth(player);
                case WRONG_PASSWORD    -> player.sendMessage(cfg.getMessage("wrong-password"));
                case NOT_REGISTERED    -> player.sendMessage(cfg.getMessage("not-registered"));
                case ALREADY_LOGGED_IN -> player.sendMessage(cfg.getMessage("already-logged-in"));
                case TOO_MANY_ATTEMPTS -> player.kickPlayer(cfg.getMessage("too-many-attempts"));
            }
            return;
        }

        if (msg.toLowerCase().startsWith("/register")) {
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

        // أي أمر تاني - امنعه
        event.setCancelled(true);
        player.sendMessage(auth.isRegistered(player.getName())
            ? cfg.getMessage("please-login")
            : cfg.getMessage("please-register"));
    }

    private void finishAuth(Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGameMode(GameMode.SURVIVAL);
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
