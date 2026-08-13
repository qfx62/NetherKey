package gg.vinland.nethergate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.List;

public class PortalRedemptionListener implements Listener {
    private final RitualManager rituals;
    private final RiftKeyService keys;

    public PortalRedemptionListener(RitualManager rituals, RiftKeyService keys) {
        this.rituals = rituals;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) return;

        Player p = e.getPlayer();
        if (!keys.isRiftKey(p.getInventory().getItemInMainHand())) return;

        Block portal = findPortal(e);
        if (portal == null) return;

        e.setCancelled(true);
        rituals.start(p, portal);
    }

    private Block findPortal(PlayerInteractEvent e) {
        Block clicked = e.getClickedBlock();
        if (clicked != null) {
            if (clicked.getType() == Material.NETHER_PORTAL) return clicked;
            for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                    BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block rel = clicked.getRelative(face);
                if (rel.getType() == Material.NETHER_PORTAL) return rel;
            }
        }

        // raycast
        Location eye = e.getPlayer().getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Block last = null;
        for (double d = 0; d <= 6.0; d += 0.2) {
            Location loc = eye.clone().add(dir.clone().multiply(d));
            Block b = loc.getBlock();
            if (b.equals(last)) continue;
            last = b;
            if (b.getType() == Material.NETHER_PORTAL) return b;
            if (!b.isPassable() && !b.getType().isAir()) {
                for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                        BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                    Block rel = b.getRelative(face);
                    if (rel.getType() == Material.NETHER_PORTAL) return rel;
                }
                break;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        keys.discoverRecipe(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        rituals.cancel(e.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        rituals.cancel(e.getPlayer().getUniqueId(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        rituals.cancel(e.getPlayer().getUniqueId(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (rituals.isActive(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (rituals.isActive(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlot(PlayerItemHeldEvent e) {
        if (rituals.isActive(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (rituals.isActive(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent e) {
        if (rituals.isActive(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
    }
}