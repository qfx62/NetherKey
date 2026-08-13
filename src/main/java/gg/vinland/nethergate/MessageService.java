package gg.vinland.nethergate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class MessageService {
    private final VinlandNetherGatePlugin plugin;

    public MessageService(VinlandNetherGatePlugin plugin) {
        this.plugin = plugin;
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> placeholders) {
        String msg = plugin.getConfig().getString("messages." + key, "&cMissing: " + key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(component(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }
}