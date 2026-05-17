package dev.smartcracked.managers;

import dev.smartcracked.SmartCrackedAccounts;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

public class AuthManager {

    private final SmartCrackedAccounts plugin;

    private final Set<UUID> pendingAuth    = new HashSet<>();
    private final Set<UUID> authenticated  = new HashSet<>();
    private final Map<UUID, Integer> wrongAttempts = new HashMap<>();

    // FIX 4: نحفظ الـ inventory في Map مضمونة
    private final Map<UUID, ItemStack[]> savedInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor     = new HashMap<>();

    private final Map<UUID, BukkitTask> reminderTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks  = new HashMap<>();

    private File passwordsFile;
    private FileConfiguration passwordsConfig;

    public AuthManager(SmartCrackedAccounts plugin) {
        this.plugin = plugin;
        setupPasswordsFile();
    }

    // ==========================================
    // ملف كلمات السر
    // ==========================================

    private void setupPasswordsFile() {
        passwordsFile = new File(plugin.getDataFolder(), "passwords.yml");
        if (!passwordsFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                passwordsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("فشل في عمل passwords.yml: " + e.getMessage());
            }
        }
        passwordsConfig = YamlConfiguration.loadConfiguration(passwordsFile);
    }

    private void savePasswordsFile() {
        try {
            passwordsConfig.save(passwordsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("فشل في حفظ passwords.yml: " + e.getMessage());
        }
    }

    // ==========================================
    // تشفير (SHA-256 + Salt)
    // ==========================================

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 مش متاح!", e);
        }
    }

    // ==========================================
    // Register
    // ==========================================

    public RegisterResult register(Player player, String password, String confirmPassword) {
        String name = player.getName().toLowerCase();

        if (passwordsConfig.contains(name + ".hash")) return RegisterResult.ALREADY_REGISTERED;
        if (!password.equals(confirmPassword))        return RegisterResult.PASSWORD_MISMATCH;
        if (password.length() < plugin.getConfigManager().getMinPasswordLength())
            return RegisterResult.TOO_SHORT;

        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        passwordsConfig.set(name + ".hash", hash);
        passwordsConfig.set(name + ".salt", salt);
        savePasswordsFile();

        // FIX 3 & 4: نمسح من pending بس منرجعش الـ inventory هنا
        // الـ AuthListener هو اللي هيرجعها على الـ main thread
        markAuthenticated(player);
        return RegisterResult.SUCCESS;
    }

    // ==========================================
    // Login
    // ==========================================

    public LoginResult login(Player player, String password) {
        String name = player.getName().toLowerCase();

        if (!passwordsConfig.contains(name + ".hash")) return LoginResult.NOT_REGISTERED;
        if (authenticated.contains(player.getUniqueId())) return LoginResult.ALREADY_LOGGED_IN;

        String salt       = passwordsConfig.getString(name + ".salt");
        String storedHash = passwordsConfig.getString(name + ".hash");
        String inputHash  = hashPassword(password, salt);

        if (!inputHash.equals(storedHash)) {
            int attempts = wrongAttempts.getOrDefault(player.getUniqueId(), 0) + 1;
            wrongAttempts.put(player.getUniqueId(), attempts);
            if (attempts >= plugin.getConfigManager().getMaxWrongAttempts())
                return LoginResult.TOO_MANY_ATTEMPTS;
            return LoginResult.WRONG_PASSWORD;
        }

        // FIX 3 & 4: نمسح من pending بس منرجعش الـ inventory هنا
        markAuthenticated(player);
        return LoginResult.SUCCESS;
    }

    // ==========================================
    // إدارة الحالة
    // ==========================================

    public void addPending(Player player) {
        pendingAuth.add(player.getUniqueId());

        ConfigManager cfg = plugin.getConfigManager();
        int intervalTicks = cfg.getReminderInterval() * 20;
        boolean isRegistered = passwordsConfig.contains(player.getName().toLowerCase() + ".hash");
        String reminderKey = isRegistered ? "reminder-login" : "reminder-register";

        BukkitTask reminder = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && isPending(player))
                player.sendMessage(cfg.getMessage(reminderKey));
        }, intervalTicks, intervalTicks);
        reminderTasks.put(player.getUniqueId(), reminder);

        int timeoutTicks = cfg.getLoginTimeout() * 20;
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isPending(player))
                player.kickPlayer(cfg.getMessage("login-timeout"));
        }, timeoutTicks);
        timeoutTasks.put(player.getUniqueId(), timeout);
    }

    // FIX 3 & 4: markAuthenticated بتمسح من pending وتوقف التايمرز بس
    // مش بترجع الـ inventory (ده شغل الـ AuthListener على main thread)
    private void markAuthenticated(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAuth.remove(uuid);
        authenticated.add(uuid);
        wrongAttempts.remove(uuid);
        cancelTasks(uuid);
    }

    public void removePending(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAuth.remove(uuid);
        authenticated.remove(uuid);
        wrongAttempts.remove(uuid);
        // FIX 4: لو اللاعب خرج قبل اللوجين نرجع أغراضه عليه (مش نضيعها)
        restoreInventoryPublic(player);
        cancelTasks(uuid);
    }

    private void cancelTasks(UUID uuid) {
        BukkitTask r = reminderTasks.remove(uuid);
        if (r != null) r.cancel();
        BukkitTask t = timeoutTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    // ==========================================
    // حفظ واسترجاع الـ Inventory
    // ==========================================

    public void saveAndClearInventory(Player player) {
        if (!plugin.getConfigManager().isProtectInventory()) return;

        // FIX 4: نحفظ نسخة من الأغراض في الـ Map
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor    = player.getInventory().getArmorContents();

        // نعمل deep copy عشان مش يتأثر لو Bukkit غيّر الـ array
        savedInventory.put(player.getUniqueId(), Arrays.copyOf(contents, contents.length));
        savedArmor.put(player.getUniqueId(), Arrays.copyOf(armor, armor.length));

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    // FIX 4: public عشان الـ AuthListener يقدر يستدعيها على الـ main thread
    public void restoreInventoryPublic(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] contents = savedInventory.remove(uuid);
        ItemStack[] armor    = savedArmor.remove(uuid);

        if (contents != null) player.getInventory().setContents(contents);
        if (armor != null)    player.getInventory().setArmorContents(armor);

        player.updateInventory(); // FIX 4: نجبر Client يتحدث
    }

    // ==========================================
    // Getters
    // ==========================================

    public boolean isPending(Player player)       { return pendingAuth.contains(player.getUniqueId()); }
    public boolean isAuthenticated(Player player) { return authenticated.contains(player.getUniqueId()); }
    public boolean isRegistered(String name)      { return passwordsConfig.contains(name.toLowerCase() + ".hash"); }
    public void saveAll()                         { savePasswordsFile(); }

    // ==========================================
    // Enums
    // ==========================================

    public enum RegisterResult { SUCCESS, ALREADY_REGISTERED, PASSWORD_MISMATCH, TOO_SHORT }
    public enum LoginResult    { SUCCESS, NOT_REGISTERED, ALREADY_LOGGED_IN, WRONG_PASSWORD, TOO_MANY_ATTEMPTS }
}
