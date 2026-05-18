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
            // 1) نحفظ مكانه الأصلي الأول قبل أي حاجة
            auth.saveLocation(player);

            // 2) نحفظ ونفضي الـ inventory
            auth.saveAndClearInventory(player);

            // 3) ننقله للوبي الفارغ بـ SPECTATOR (مش بيقع)
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(world.getLobbyLocation());

            // 4) نضيفه للـ pending
            auth.addPending(player);

            // 5) الرسالة المناسبة
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

        String msg   = event.getMessage().trim();
        String lower = msg.toLowerCase();

        if (lower.startsWith("/login") || lower.startsWith("/l ") || lower.equals("/l")) {
            event.setCancelled(true);
            String[] args = msg.split(" ");
            if (args.length < 2) { player.sendMessage(cfg.getMessage("please-login")); return; }

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
            if (args.length < 3) { player.sendMessage(cfg.getMessage("please-register")); return; }

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
        // الترتيب الصح:
        // 1) نرجّع GameMode الطبيعي
        player.setGameMode(GameMode.SURVIVAL);

        // 2) نرجّعه لمكانه الأصلي أولاً (قبل الأغراض)
        Location savedLoc = auth.getSavedLocation(player);
        if (savedLoc != null) {
            player.teleport(savedLoc);
        }

        // 3) بعد الـ teleport بـ tick واحد نرجّع الأغراض
        //    عشان نضمن إنه وصل للعالم الجديد قبل ما الأغراض ترجع
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                auth.restoreInventoryPublic(player);
                player.sendMessage(cfg.getMessage("login-success"));
            }
        }, 2L); // tick تانيين كافيين للـ teleport يكمل
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
