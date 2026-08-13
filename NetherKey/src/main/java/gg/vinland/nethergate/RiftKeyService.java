package gg.vinland.nethergate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class RiftKeyService {
    private final VinlandNetherGatePlugin plugin;
    private final NamespacedKey marker;
    private final NamespacedKey recipeKey;

    public RiftKeyService(VinlandNetherGatePlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "rift_key");
        this.recipeKey = new NamespacedKey(plugin, "rift_key");
    }

    public ItemStack createKey() {
        Material mat = getMat("recipe.result-material", Material.ECHO_SHARD);
        ItemStack key = new ItemStack(mat);
        ItemMeta meta = key.getItemMeta();

        meta.displayName(Component.text("Rift Key", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("A relic humming with dormant heat.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click any active Nether portal.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        key.setItemMeta(meta);
        return key;
    }

    public boolean isRiftKey(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(marker, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public void registerRecipe() {
        unregisterRecipe();
        Bukkit.addRecipe(makeRecipe());
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    public void discoverRecipe(Player p) {
        p.discoverRecipe(recipeKey);
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    private ShapedRecipe makeRecipe() {
        ShapedRecipe r = new ShapedRecipe(recipeKey, createKey());
        r.shape("DCD", "CEC", "DCD");
        r.setIngredient('D', getMat("recipe.ingredients.diamond", Material.DIAMOND));
        r.setIngredient('C', getMat("recipe.ingredients.obsidian", Material.OBSIDIAN));
        r.setIngredient('E', getMat("recipe.ingredients.echo-shard", Material.ECHO_SHARD));
        return r;
    }

    private Material getMat(String path, Material def) {
        String name = plugin.getConfig().getString(path);
        if (name == null || name.isBlank()) return def;
        Material m = Material.matchMaterial(name.toUpperCase());
        return (m != null && m.isItem()) ? m : def;
    }
}
