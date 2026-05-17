package dev.smartcracked;

import dev.smartcracked.commands.CrackListCommand;
import dev.smartcracked.listeners.AuthListener;
import dev.smartcracked.listeners.ProtectionListener;
import dev.smartcracked.managers.AuthManager;
import dev.smartcracked.managers.ConfigManager;
import dev.smartcracked.managers.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SmartCrackedAccounts extends JavaPlugin {

    private static SmartCrackedAccounts instance;
    private ConfigManager configManager;
    private AuthManager authManager;
    private WorldManager worldManager;
    private boolean floodgateEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        // الخطوة 1: نعمل ونحمّل الـ config
        configManager = new ConfigManager(this);
        configManager.setup();

        // الخطوة 2: نعمل عالم اللوبي لو مش موجود
        worldManager = new WorldManager(this);
        worldManager.setupLobbyWorld();

        // الخطوة 3: نشوف لو Floodgate متنصب
        if (getServer().getPluginManager().getPlugin("floodgate") != null
                && configManager.isUseFloodgate()) {
            floodgateEnabled = true;
            getLogger().info("تم الكشف عن Floodgate! لاعبين البيدروك هيعدوا تلقائياً.");
        } else {
            getLogger().info("Floodgate مش موجود أو متطفي - هيتجاهل الكشف عن البيدروك.");
        }

        // الخطوة 4: نشغّل الـ AuthManager
        authManager = new AuthManager(this);

        // الخطوة 5: نسجّل الـ Listeners
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);

        // الخطوة 6: نسجّل الأمر
        getCommand("cracklist").setExecutor(new CrackListCommand(this));

        getLogger().info("SmartCrackedAccounts شغّال بنجاح! ✔");
    }

    @Override
    public void onDisable() {
        // نحفظ أي بيانات معلّقة
        if (authManager != null) {
            authManager.saveAll();
        }
        getLogger().info("SmartCrackedAccounts اتوقف.");
    }

    // ======= Getters =======

    public static SmartCrackedAccounts getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public AuthManager getAuthManager() { return authManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public boolean isFloodgateEnabled() { return floodgateEnabled; }
}
