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
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * بيستمع لأحداث الدخول والخروج والأوامر
 */
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

    // ==========================================
    // لما لاعب بيدخل السيرفر (قبل ما يظهر للناس)
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();

        // 1) أصلي (Premium)؟ سيبه يعدي
        if (player.isOnlineMode()) return;

        // 2) بيدروك (Floodgate)؟ سيبه يعدي
        if (plugin.isFloodgateEnabled()
                && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            return;
        }

        // 3) كراك - نشوف لو اسمه في القائمة
        if (!cfg.isWhitelisted(player.getName())) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    cfg.getMessage("not-whitelisted")
            );
        }
        // لو في القائمة نسيبه يكمل - الـ onPlayerJoin هيتولى الباقي
    }

    // ==========================================
    // لما لاعب بيدخل الـ world (بعد Login event)
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // نفس الفحص - لو مش كراك في القائمة نسيبه
        if (player.isOnlineMode()) return;
        if (plugin.isFloodgateEnabled()
                && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            return;
        }
        if (!cfg.isWhitelisted(player.getName())) return;

        // اللاعب كراك ومسموح له - نجهّزه للـ auth
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // 1) نحفظ ونفضي الـ inventory
            auth.saveAndClearInventory(player);

            // 2) ننقله للوبي الفارغ - في الجو عشان مفيش أرض
            player.teleport(world.getLobbyLocation());

            // 3) نخليه يطير في مكانه (مش يقع)
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGameMode(GameMode.ADVENTURE); // مش يكسر بلوكات

            // 4) نضيفه لـ pendingAuth ونبدأ التذكير والـ timeout
            auth.addPending(player);

            // 5) نبعتله الرسالة المناسبة
            if (auth.isRegistered(player.getName())) {
                player.sendMessage(cfg.getMessage("please-login"));
            } else {
                player.sendMessage(cfg.getMessage("please-register"));
            }
        });
    }

    // ==========================================
    // لما لاعب بيكتب /login
    // ==========================================
    @EventHandler
    public void onLogin(PlayerCommandPreprocessEvent event) {
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

            String password = args[1];
            AuthManager.LoginResult result = auth.login(player, password);

            switch (result) {
                case SUCCESS -> {
                    // نرجّعه لوضعه الطبيعي
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    player.setGameMode(GameMode.SURVIVAL);
                    // الرسالة بتتبعت جوه auth.login → setAuthenticated
                }
                case WRONG_PASSWORD     -> player.sendMessage(cfg.getMessage("wrong-password"));
                case NOT_REGISTERED     -> player.sendMessage(cfg.getMessage("not-registered"));
                case ALREADY_LOGGED_IN  -> player.sendMessage(cfg.getMessage("already-logged-in"));
                case TOO_MANY_ATTEMPTS  -> player.kickPlayer(cfg.getMessage("too-many-attempts"));
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

            String password = args[1];
            String confirm  = args[2];
            AuthManager.RegisterResult result = auth.register(player, password, confirm);

            switch (result) {
                case SUCCESS -> {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    player.setGameMode(GameMode.SURVIVAL);
                }
                case PASSWORD_MISMATCH   -> player.sendMessage(cfg.getMessage("password-mismatch"));
                case ALREADY_REGISTERED  -> player.sendMessage(cfg.getMessage("already-registered"));
                case TOO_SHORT           -> player.sendMessage(
                        cfg.getMessage("password-too-short", player.getName(),
                                cfg.getMinPasswordLength()));
            }
            return;
        }

        // أي أمر تاني - نمنعه
        event.setCancelled(true);
        if (auth.isRegistered(player.getName())) {
            player.sendMessage(cfg.getMessage("please-login"));
        } else {
            player.sendMessage(cfg.getMessage("please-register"));
        }
    }

    // ==========================================
    // لما لاعب بيخرج - نمسح بياناته المؤقتة
    // ==========================================
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        auth.removePending(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        auth.removePending(event.getPlayer());
    }
}
