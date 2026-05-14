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

/**
 * بيدير كل حاجة خاصة بالـ login/register
 * - بيحفظ كلمات السر بشكل آمن (SHA-256 + Salt)
 * - بيحفظ الـ inventory مؤقتاً
 * - بيتابع مين عامل login ومين لسه
 */
public class AuthManager {

    private final SmartCrackedAccounts plugin;

    // اللاعبين اللي محتاجين يعملوا auth (كراك + في القائمة)
    private final Set<UUID> pendingAuth = new HashSet<>();

    // اللاعبين اللي عملوا auth بنجاح
    private final Set<UUID> authenticated = new HashSet<>();

    // عدد المحاولات الخاطئة
    private final Map<UUID, Integer> wrongAttempts = new HashMap<>();

    // الـ inventory المحفوظ مؤقتاً
    private final Map<UUID, ItemStack[]> savedInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();

    // مهام التذكير والـ timeout
    private final Map<UUID, BukkitTask> reminderTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();

    // ملف حفظ كلمات السر
    private File passwordsFile;
    private FileConfiguration passwordsConfig;

    public AuthManager(SmartCrackedAccounts plugin) {
        this.plugin = plugin;
        setupPasswordsFile();
    }

    // ==========================================
    // إعداد ملف كلمات السر
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
    // تشفير كلمة السر (SHA-256 + Salt)
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
            String salted = salt + password;
            byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
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

        // هل مسجّل بالفعل؟
        if (passwordsConfig.contains(name + ".hash")) {
            return RegisterResult.ALREADY_REGISTERED;
        }

        // هل كلمتين السر متطابقتين؟
        if (!password.equals(confirmPassword)) {
            return RegisterResult.PASSWORD_MISMATCH;
        }

        // هل كلمة السر طويلة كفاية؟
        if (password.length() < plugin.getConfigManager().getMinPasswordLength()) {
            return RegisterResult.TOO_SHORT;
        }

        // نحفظ كلمة السر مشفّرة
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        passwordsConfig.set(name + ".hash", hash);
        passwordsConfig.set(name + ".salt", salt);
        savePasswordsFile();

        // نعتبره authenticated
        setAuthenticated(player);
        return RegisterResult.SUCCESS;
    }

    // ==========================================
    // Login
    // ==========================================

    public LoginResult login(Player player, String password) {
        String name = player.getName().toLowerCase();

        // هل مسجّل أصلاً؟
        if (!passwordsConfig.contains(name + ".hash")) {
            return LoginResult.NOT_REGISTERED;
        }

        // هل عامل login بالفعل؟
        if (authenticated.contains(player.getUniqueId())) {
            return LoginResult.ALREADY_LOGGED_IN;
        }

        // نتحقق من كلمة السر
        String salt = passwordsConfig.getString(name + ".salt");
        String storedHash = passwordsConfig.getString(name + ".hash");
        String inputHash = hashPassword(password, salt);

        if (!inputHash.equals(storedHash)) {
            // محاولة خاطئة
            int attempts = wrongAttempts.getOrDefault(player.getUniqueId(), 0) + 1;
            wrongAttempts.put(player.getUniqueId(), attempts);

            if (attempts >= plugin.getConfigManager().getMaxWrongAttempts()) {
                return LoginResult.TOO_MANY_ATTEMPTS;
            }
            return LoginResult.WRONG_PASSWORD;
        }

        // نجح اللوجين
        setAuthenticated(player);
        return LoginResult.SUCCESS;
    }

    // ==========================================
    // إدارة حالة اللاعب
    // ==========================================

    public void addPending(Player player) {
        pendingAuth.add(player.getUniqueId());

        // نشغّل مهمة التذكير
        ConfigManager cfg = plugin.getConfigManager();
        int intervalTicks = cfg.getReminderInterval() * 20;
        boolean isRegistered = passwordsConfig.contains(player.getName().toLowerCase() + ".hash");
        String reminderKey = isRegistered ? "reminder-login" : "reminder-register";

        BukkitTask reminder = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && isPending(player)) {
                player.sendMessage(cfg.getMessage(reminderKey));
            }
        }, intervalTicks, intervalTicks);
        reminderTasks.put(player.getUniqueId(), reminder);

        // نشغّل مهمة الـ timeout
        int timeoutTicks = cfg.getLoginTimeout() * 20;
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isPending(player)) {
                player.kickPlayer(cfg.getMessage("login-timeout"));
            }
        }, timeoutTicks);
        timeoutTasks.put(player.getUniqueId(), timeout);
    }

    private void setAuthenticated(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAuth.remove(uuid);
        authenticated.add(uuid);
        wrongAttempts.remove(uuid);

        // نوقف التذكير والـ timeout
        cancelTasks(uuid);

        // نرجّع الـ inventory
        restoreInventory(player);

        // نرجّع اللاعب لمكانه الأصلي
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // لو في موقع محفوظ، نرجّعه له - غير كده يفضل في الـ spawn
            player.sendMessage(plugin.getConfigManager().getMessage("login-success"));
        });
    }

    public void removePending(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAuth.remove(uuid);
        authenticated.remove(uuid);
        wrongAttempts.remove(uuid);
        savedInventory.remove(uuid);
        savedArmor.remove(uuid);
        cancelTasks(uuid);
    }

    private void cancelTasks(UUID uuid) {
        BukkitTask reminder = reminderTasks.remove(uuid);
        if (reminder != null) reminder.cancel();
        BukkitTask timeout = timeoutTasks.remove(uuid);
        if (timeout != null) timeout.cancel();
    }

    // ==========================================
    // حفظ واسترجاع الـ Inventory
    // ==========================================

    public void saveAndClearInventory(Player player) {
        if (!plugin.getConfigManager().isProtectInventory()) return;

        // نحفظ الأغراض
        savedInventory.put(player.getUniqueId(),
                player.getInventory().getContents().clone());
        savedArmor.put(player.getUniqueId(),
                player.getInventory().getArmorContents().clone());

        // نفضيه تماماً
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] contents = savedInventory.remove(uuid);
        ItemStack[] armor    = savedArmor.remove(uuid);

        if (contents != null) player.getInventory().setContents(contents);
        if (armor != null)    player.getInventory().setArmorContents(armor);
    }

    // ==========================================
    // Getters
    // ==========================================

    public boolean isPending(Player player) {
        return pendingAuth.contains(player.getUniqueId());
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    public boolean isRegistered(String name) {
        return passwordsConfig.contains(name.toLowerCase() + ".hash");
    }

    public void saveAll() {
        savePasswordsFile();
    }

    // ==========================================
    // Enums للنتائج
    // ==========================================

    public enum RegisterResult {
        SUCCESS, ALREADY_REGISTERED, PASSWORD_MISMATCH, TOO_SHORT
    }

    public enum LoginResult {
        SUCCESS, NOT_REGISTERED, ALREADY_LOGGED_IN, WRONG_PASSWORD, TOO_MANY_ATTEMPTS
    }
}
