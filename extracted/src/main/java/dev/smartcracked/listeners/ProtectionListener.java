package dev.smartcracked.listeners;

import dev.smartcracked.SmartCrackedAccounts;
import dev.smartcracked.managers.AuthManager;
import dev.smartcracked.managers.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;

/**
 * بيمنع أي تفاعل قبل اللوجين
 */
public class ProtectionListener implements Listener {

    private final AuthManager auth;
    private final ConfigManager cfg;

    public ProtectionListener(SmartCrackedAccounts plugin) {
        this.auth = plugin.getAuthManager();
        this.cfg  = plugin.getConfigManager();
    }

    // منع الحركة
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!cfg.isPreventMovement()) return;
        Player player = event.getPlayer();
        if (!auth.isPending(player)) return;

        // نسمح بالدوران بس (نظر) - بس مش حركة فعلية
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    // منع الكلام في الشات
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!cfg.isPreventChat()) return;
        Player player = event.getPlayer();
        if (!auth.isPending(player)) return;

        event.setCancelled(true);
        if (auth.isRegistered(player.getName())) {
            player.sendMessage(cfg.getMessage("please-login"));
        } else {
            player.sendMessage(cfg.getMessage("please-register"));
        }
    }

    // منع الضرر
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!cfg.isPreventDamage()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!auth.isPending(player)) return;

        event.setCancelled(true);
    }

    // منع أكل أي حاجة
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!auth.isPending(event.getPlayer())) return;
        event.setCancelled(true);
    }

    // منع إسقاط أي حاجة
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!auth.isPending(event.getPlayer())) return;
        event.setCancelled(true);
    }

    // منع التفاعل مع أي حاجة
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!auth.isPending(event.getPlayer())) return;
        event.setCancelled(true);
    }
}
