package gg.vinland.nethergate;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class VinlandNetherGatePlugin extends JavaPlugin {
    private MessageService messages;
    private PlayerUnlockRepository unlocks;
    private RiftKeyService keys;
    private RitualManager rituals;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        messages = new MessageService(this);
        unlocks = new PlayerUnlockRepository(this);
        keys = new RiftKeyService(this);

        unlocks.load();
        keys.registerRecipe();

        rituals = new RitualManager(this, messages, unlocks, keys);

        Bukkit.getPluginManager().registerEvents(
                new NetherPortalListener(this, messages, unlocks, rituals), this);
        Bukkit.getPluginManager().registerEvents(
                new PortalRedemptionListener(rituals, keys), this);

        PluginCommand cmd = getCommand("nethergate");
        if (cmd != null) {
            NetherGateCommand handler = new NetherGateCommand(this, messages, unlocks, keys, rituals);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        Bukkit.getOnlinePlayers().forEach(keys::discoverRecipe);

        getLogger().info("VinlandNetherGate enabled - " + unlocks.count() + " unlocks loaded");
    }

    @Override
    public void onDisable() {
        if (rituals != null) rituals.cancelAll(false);
        if (unlocks != null) unlocks.save();
        if (keys != null) keys.unregisterRecipe();
    }

    public boolean reloadRuntimeState() {
        rituals.cancelAll(true);
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        boolean ok = unlocks.load();
        keys.registerRecipe();
        Bukkit.getOnlinePlayers().forEach(keys::discoverRecipe);
        return ok;
    }
}