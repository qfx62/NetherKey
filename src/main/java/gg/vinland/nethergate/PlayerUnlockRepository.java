package gg.vinland.nethergate;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class PlayerUnlockRepository {
    private final File file;
    private final Set<UUID> unlocked = new HashSet<>();
    private boolean ready = false;

    public PlayerUnlockRepository(VinlandNetherGatePlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public boolean load() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            return false;
        }

        if (!file.exists()) {
            unlocked.clear();
            ready = true;
            return save();
        }

        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(file);
            unlocked.clear();
            for (String s : cfg.getStringList("unlocked")) {
                try {
                    unlocked.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {}
            }
            ready = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUnlocked(UUID id) {
        return unlocked.contains(id);
    }

    public boolean setUnlocked(UUID id, boolean unlock) {
        if (!ready) return false;

        boolean was = unlocked.contains(id);
        if (was == unlock) return true;

        if (unlock) unlocked.add(id);
        else unlocked.remove(id);

        if (save()) return true;

        // rollback
        if (unlock) unlocked.remove(id);
        else unlocked.add(id);
        return false;
    }

    public int count() {
        return unlocked.size();
    }

    public boolean save() {
        if (!ready) return false;

        YamlConfiguration cfg = new YamlConfiguration();
        List<String> list = unlocked.stream()
                .map(UUID::toString)
                .sorted()
                .toList();
        cfg.set("unlocked", list);

        try {
            cfg.save(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}