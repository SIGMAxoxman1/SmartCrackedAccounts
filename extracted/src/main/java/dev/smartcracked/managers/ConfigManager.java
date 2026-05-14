package dev.smartcracked.managers;

import dev.smartcracked.SmartCrackedAccounts;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * بيعمل ويحمّل ويحفظ الـ config.yml تلقائياً
 * لو الملف مش موجود، بيعمله من الـ default اللي جوه الـ JAR
 */
public class ConfigManager {

    private final SmartCrackedAccounts plugin;
    private FileConfiguration config;

    public ConfigManager(SmartCrackedAccounts plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        // saveDefaultConfig بتعمل الملف لو مش موجود، ومش بتمسحه لو موجود
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.getLogger().info("config.yml اتحمّل بنجاح.");
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    // =========== Whitelist ===========

    public List<String> getWhitelistedPlayers() {
        return config.getStringList("whitelisted-players");
    }

    public boolean isWhitelisted(String name) {
        return getWhitelistedPlayers().stream()
                .anyMatch(n -> n.equalsIgnoreCase(name));
    }

    public void addToWhitelist(String name) {
        List<String> list = getWhitelistedPlayers();
        list.add(name);
        config.set("whitelisted-players", list);
        plugin.saveConfig();
    }

    public void removeFromWhitelist(String name) {
        List<String> list = getWhitelistedPlayers();
        list.removeIf(n -> n.equalsIgnoreCase(name));
        config.set("whitelisted-players", list);
        plugin.saveConfig();
    }

    // =========== Lobby ===========

    public String getLobbyWorld()   { return config.getString("lobby-world", "auth_lobby"); }
    public double getLobbyX()       { return config.getDouble("lobby-location.x", 0.5); }
    public double getLobbyY()       { return config.getDouble("lobby-location.y", 64.0); }
    public double getLobbyZ()       { return config.getDouble("lobby-location.z", 0.5); }
    public float  getLobbyYaw()     { return (float) config.getDouble("lobby-location.yaw", 0.0); }
    public float  getLobbyPitch()   { return (float) config.getDouble("lobby-location.pitch", 0.0); }

    // =========== Security ===========

    public int     getLoginTimeout()      { return config.getInt("login-timeout", 60); }
    public int     getMinPasswordLength() { return config.getInt("min-password-length", 6); }
    public int     getMaxWrongAttempts()  { return config.getInt("max-wrong-attempts", 3); }
    public boolean isProtectInventory()   { return config.getBoolean("protect-inventory", true); }
    public boolean isPreventMovement()    { return config.getBoolean("prevent-movement", true); }
    public boolean isPreventChat()        { return config.getBoolean("prevent-chat", true); }
    public boolean isPreventDamage()      { return config.getBoolean("prevent-damage", true); }
    public boolean isUseFloodgate()       { return config.getBoolean("use-floodgate", true); }
    public int     getReminderInterval()  { return config.getInt("messages.reminder-interval", 15); }

    // =========== Messages ===========

    public String getMessage(String key) {
        String msg = config.getString("messages." + key, "&cرسالة غير موجودة: " + key);
        return colorize(msg);
    }

    public String getMessage(String key, String playerName) {
        return getMessage(key).replace("%player%", playerName);
    }

    public String getMessage(String key, String playerName, int minLength) {
        return getMessage(key, playerName).replace("%min%", String.valueOf(minLength));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
