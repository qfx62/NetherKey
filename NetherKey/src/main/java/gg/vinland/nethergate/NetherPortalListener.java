package gg.vinland.nethergate;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetherPortalListener implements Listener {
    private final VinlandNetherGatePlugin plugin;
    private final MessageService messages;
    private final PlayerUnlockRepository unlocks;
    private final RitualManager rituals;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public NetherPortalListener(VinlandNetherGatePlugin plugin, MessageService messages,
                                PlayerUnlockRepository unlocks, RitualManager rituals) {
        this.plugin = plugin;
        this.messages = messages;
        this.unlocks = unlocks;
        this.rituals = rituals;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;

        World from = e.getFrom().getWorld();
        World to = e.getTo().getWorld();
        if (from == null || to == null) return;
        if (from.getEnvironment() != World.Environment.NORMAL) return;
        if (to.getEnvironment() != World.Environment.NETHER) return;

        Player p = e.getPlayer();
        if (p.hasPermission("vinlandnethergate.bypass") || unlocks.isUnlocked(p.getUniqueId())) return;

        e.setCancelled(true);
        if (rituals.isActive(p.getUniqueId())) return;

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long cd = plugin.getConfig().getLong("settings.portal-denial-cooldown-seconds", 3) * 1000L;

        if (now - last >= cd) {
            p.sendActionBar(messages.component("portal-denied"));
            cooldowns.put(p.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }
}