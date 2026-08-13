package gg.vinland.nethergate;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RitualManager {
    private final VinlandNetherGatePlugin plugin;
    private final MessageService messages;
    private final PlayerUnlockRepository unlocks;
    private final RiftKeyService keys;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<PortalId, UUID> portalOwners = new HashMap<>();

    public RitualManager(VinlandNetherGatePlugin plugin, MessageService messages,
                         PlayerUnlockRepository unlocks, RiftKeyService keys) {
        this.plugin = plugin;
        this.messages = messages;
        this.unlocks = unlocks;
        this.keys = keys;
    }

    public void start(Player p, Block portal) {
        if (unlocks.isUnlocked(p.getUniqueId())) {
            messages.send(p, "already-unlocked");
            return;
        }
        if (sessions.containsKey(p.getUniqueId())) {
            messages.send(p, "ritual-in-progress");
            return;
        }
        if (!keys.isRiftKey(p.getInventory().getItemInMainHand())) {
            messages.send(p, "key-required");
            return;
        }

        PortalId id = getPortalId(portal);
        if (portalOwners.containsKey(id)) {
            messages.send(p, "portal-busy");
            return;
        }

        int dur = Math.max(1, Math.min(60, plugin.getConfig().getInt("settings.ritual-duration-seconds", 8))) * 20;
        double dist = Math.max(1, Math.min(32, plugin.getConfig().getDouble("settings.ritual-max-distance", 6.0)));

        Location center = portal.getLocation().add(0.5, 0.5, 0.5);
        BossBar bar = BossBar.bossBar(
                messages.component("ritual-bossbar", Map.of("seconds", String.format("%.1f", dur / 20.0))),
                1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS
        );

        TextDisplay display = null;
        if (plugin.getConfig().getBoolean("settings.show-text-display", true)) {
            Location loc = center.clone().add(0, 2.15, 0);
            display = center.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.text(messages.component("ritual-display"));
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setSeeThrough(true);
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(112, 26, 184));
                d.setInvulnerable(true);
                d.setPersistent(false);
                d.setViewRange(0.8f);
            });
        }

        Session s = new Session(p.getUniqueId(), center.getWorld().getUID(), id, center, dur, dist, bar, display);
        sessions.put(p.getUniqueId(), s);
        portalOwners.put(id, p.getUniqueId());

        p.showBossBar(bar);
        messages.send(p, "ritual-started");
        playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.65f);
        playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 0.8f, 0.6f);

        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(s), 0, 1);
    }

    private void tick(Session s) {
        Player p = Bukkit.getPlayer(s.player);
        if (p == null || !p.isOnline() || p.isDead()) {
            cancel(s.player, true);
            return;
        }
        if (!p.getWorld().getUID().equals(s.world)) {
            cancel(s.player, true);
            return;
        }
        if (p.getLocation().distanceSquared(s.center) > s.maxDist * s.maxDist) {
            cancel(s.player, true);
            return;
        }
        if (s.center.getBlock().getType() != Material.NETHER_PORTAL) {
            cancel(s.player, true);
            return;
        }
        if (!keys.isRiftKey(p.getInventory().getItemInMainHand())) {
            cancel(s.player, true);
            return;
        }

        s.elapsed++;
        spawnParticles(s);
        updateBar(s);

        if (s.elapsed >= s.duration) {
            complete(s, p);
        }
    }

    private void complete(Session s, Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!keys.isRiftKey(held)) {
            cancel(s.player, true);
            return;
        }

        if (!unlocks.setUnlocked(p.getUniqueId(), true)) {
            cleanup(s);
            messages.send(p, "unlock-save-failed");
            return;
        }

        if (held.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
        }

        Location c = s.center.clone();
        cleanup(s);

        c.getWorld().spawnParticle(Particle.REVERSE_PORTAL, c.add(0, 1, 0), 120, 1.2, 1.8, 1.2, 0.25);
        c.getWorld().spawnParticle(Particle.PORTAL, c, 160, 1, 1.6, 1, 0.35);
        c.getWorld().spawnParticle(Particle.ENCHANTED_HIT, c, 60, 1, 1.4, 1, 0.25);
        c.getWorld().spawnParticle(Particle.ASH, c, 70, 1.2, 1.6, 1.2, 0.06);
        c.getWorld().spawnParticle(Particle.DUST, c, 80, 1.1, 1.5, 1.1, 0,
                new Particle.DustOptions(Color.fromRGB(235, 91, 19), 1.7f));

        playSound(c, Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.75f);
        playSound(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.8f);

        messages.send(p, "unlock-title");
        messages.send(p, "unlock-subtitle");
    }

    private void cancel(Session s, boolean notify) {
        Player p = Bukkit.getPlayer(s.player);
        cleanup(s);
        if (notify && p != null && p.isOnline()) {
            messages.send(p, "ritual-cancelled");
        }
    }

    public void cancel(UUID id, boolean notify) {
        Session s = sessions.get(id);
        if (s != null) cancel(s, notify);
    }

    public void cancelAll(boolean notify) {
        new ArrayList<>(sessions.values()).forEach(s -> cancel(s, notify));
    }

    public boolean isActive(UUID id) {
        return sessions.containsKey(id);
    }

    public int activeCount() {
        return sessions.size();
    }

    private void cleanup(Session s) {
        sessions.remove(s.player);
        portalOwners.remove(s.portalId, s.player);

        if (s.task != null) s.task.cancel();

        Player p = Bukkit.getPlayer(s.player);
        if (p != null) p.hideBossBar(s.bar);
        if (s.display != null && s.display.isValid()) s.display.remove();
    }

    private void updateBar(Session s) {
        int left = Math.max(0, s.duration - s.elapsed);
        s.bar.progress(Math.max(0, Math.min(1, left / (float) s.duration)));
        s.bar.name(messages.component("ritual-bossbar", Map.of("seconds", String.format("%.1f", left / 20.0))));
    }

    private void spawnParticles(Session s) {
        if (s.elapsed % 2 != 0) return;

        double t = s.elapsed * 0.16;
        double h = 0.2 + (s.elapsed % 80) / 24.0;

        for (int i = 0; i < 3; i++) {
            double a = t + i * (Math.PI * 2 / 3);
            double r = 0.55 + 0.18 * Math.sin(t * 0.7 + i);
            Location loc = s.center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);

            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 4, 0.08, 0.08, 0.08, 0.18);
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 2, 0.06, 0.06, 0.06, 0.04);
            loc.getWorld().spawnParticle(Particle.ASH, loc, 2, 0.12, 0.12, 0.12, 0.01);

            Color c = i == 0 ? Color.fromRGB(184, 26, 26) :
                       i == 1 ? Color.fromRGB(112, 26, 184) :
                                Color.fromRGB(235, 91, 19);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.04, 0.04, 0.04, 0, new Particle.DustOptions(c, 1.25f));
        }

        if (s.elapsed % 6 == 0) {
            s.center.getWorld().spawnParticle(Particle.ENCHANTED_HIT, s.center.clone().add(0, 1.1, 0), 12, 0.7, 0.8, 0.7, 0.15);
        }

        if (s.elapsed % 40 == 0) {
            playSound(s.center, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.55f);
        }
        if (s.elapsed == s.duration / 2) {
            playSound(s.center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.45f);
        }
    }

    private void playSound(Location loc, Sound sound, float vol, float pitch) {
        double r = Math.max(1, Math.min(64, plugin.getConfig().getDouble("settings.ritual-sound-radius", 24)));
        loc.getWorld().getNearbyPlayers(loc, r).forEach(p ->
                p.playSound(loc, sound, SoundCategory.BLOCKS, vol, pitch)
        );
    }

    private PortalId getPortalId(Block start) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);

        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();

        while (!queue.isEmpty() && visited.size() < 1024) {
            Block b = queue.poll();
            BlockPos pos = new BlockPos(b.getX(), b.getY(), b.getZ());
            if (!visited.add(pos) || b.getType() != Material.NETHER_PORTAL) continue;

            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            minZ = Math.min(minZ, b.getZ());

            for (BlockFace f : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                    BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block rel = b.getRelative(f);
                if (rel.getType() == Material.NETHER_PORTAL) {
                    BlockPos relPos = new BlockPos(rel.getX(), rel.getY(), rel.getZ());
                    if (!visited.contains(relPos)) queue.add(rel);
                }
            }
        }

        return new PortalId(start.getWorld().getUID(), minX, minY, minZ);
    }

    private static class Session {
        final UUID player, world;
        final PortalId portalId;
        final Location center;
        final int duration;
        final double maxDist;
        final BossBar bar;
        final TextDisplay display;
        int elapsed = 0;
        BukkitTask task;

        Session(UUID player, UUID world, PortalId portalId, Location center, int duration, double maxDist,
                BossBar bar, TextDisplay display) {
            this.player = player;
            this.world = world;
            this.portalId = portalId;
            this.center = center;
            this.duration = duration;
            this.maxDist = maxDist;
            this.bar = bar;
            this.display = display;
        }
    }

    private record BlockPos(int x, int y, int z) {}
    private record PortalId(UUID world, int x, int y, int z) {}
}