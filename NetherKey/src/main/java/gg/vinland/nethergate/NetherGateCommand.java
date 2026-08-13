package gg.vinland.nethergate;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class NetherGateCommand implements CommandExecutor, TabCompleter {
    private final VinlandNetherGatePlugin plugin;
    private final MessageService messages;
    private final PlayerUnlockRepository unlocks;
    private final RiftKeyService keys;
    private final RitualManager rituals;

    public NetherGateCommand(VinlandNetherGatePlugin plugin, MessageService messages,
                             PlayerUnlockRepository unlocks, RiftKeyService keys,
                             RitualManager rituals) {
        this.plugin = plugin;
        this.messages = messages;
        this.unlocks = unlocks;
        this.keys = keys;
        this.rituals = rituals;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vinlandnethergate.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                sender.sendMessage("§5§lVinlandNetherGate Status");
                sender.sendMessage("§7Unlocked players: §f" + unlocks.count());
                sender.sendMessage("§7Active rituals: §f" + rituals.activeCount());
                sender.sendMessage("§7Recipe key: §f" + keys.getRecipeKey());
                return true;

            case "reload":
                if (!plugin.reloadRuntimeState()) {
                    sender.sendMessage("§cReloaded with errors - check console");
                    return true;
                }
                messages.send(sender, "reload-complete");
                return true;

            case "givekey":
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /nethergate givekey <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "player-not-found");
                    return true;
                }
                ItemStack key = keys.createKey();
                Map<Integer, ItemStack> leftovers = target.getInventory().addItem(key);
                leftovers.values().forEach(item ->
                        target.getWorld().dropItemNaturally(target.getLocation(), item)
                );
                messages.send(sender, "key-given", Map.of("player", target.getName()));
                return true;

            case "reset":
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /nethergate reset <player>");
                    return true;
                }
                OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (offline == null) {
                    messages.send(sender, "player-not-found");
                    return true;
                }
                String name = offline.getName() != null ? offline.getName() : offline.getUniqueId().toString();
                if (!unlocks.isUnlocked(offline.getUniqueId())) {
                    messages.send(sender, "reset-not-unlocked", Map.of("player", name));
                    return true;
                }
                rituals.cancel(offline.getUniqueId(), true);
                if (!unlocks.setUnlocked(offline.getUniqueId(), false)) {
                    sender.sendMessage("§cCould not save players.yml");
                    return true;
                }
                messages.send(sender, "reset-complete", Map.of("player", name));
                return true;

            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§5§lVinlandNetherGate Commands");
        sender.sendMessage("§d/nethergate status §7- Check plugin status");
        sender.sendMessage("§d/nethergate reload §7- Reload config");
        sender.sendMessage("§d/nethergate givekey <player> §7- Give a Rift Key");
        sender.sendMessage("§d/nethergate reset <player> §7- Remove Nether access");
        sender.sendMessage("§d/nethergate help §7- Show this help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("vinlandnethergate.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("status", "reload", "givekey", "reset", "help");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("givekey") || args[0].equalsIgnoreCase("reset"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }

        return Collections.emptyList();
    }
}