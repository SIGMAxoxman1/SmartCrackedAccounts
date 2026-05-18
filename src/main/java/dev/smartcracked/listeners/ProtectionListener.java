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
import io.papermc.paper.event.player.AsyncChatEvent;

public class ProtectionListener implements Listener {

    private final AuthManager auth;
    private final ConfigManager cfg;

    public ProtectionListener(SmartCrackedAccounts plugin) {
        this.auth = plugin.getAuthManager();
        this.cfg  = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!cfg.isPreventMovement()) return;
        Player player = event.getPlayer();
        if (!auth.isPending(player)) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    // Paper 1.21 بيستخدم AsyncChatEvent بدل AsyncPlayerChatEvent
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!cfg.isPreventChat()) return;
        Player player = event.getPlayer();
        if (!auth.isPending(player)) return;
        event.setCancelled(true);
        player.sendMessage(auth.isRegistered(player.getName())
            ? cfg.getMessage("please-login")
            : cfg.getMessage("please-register"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!cfg.isPreventDamage()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!auth.isPending(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!auth.isPending(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!auth.isPending(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!auth.isPending(event.getPlayer())) return;
        event.setCancelled(true);
    }
}
