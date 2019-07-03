package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SkillUtils {

    public static final int ENCHANT_SPEED_VAR = 5;

    public static void applyXpGain(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, float xp, XPGainReason xpGainReason) {
        mcMMOPlayer.beginXpGain(skill, xp, xpGainReason, XPGainSource.SELF);
    }

    public static void applyXpGain(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, float xp, XPGainReason xpGainReason, XPGainSource xpGainSource) {
        mcMMOPlayer.beginXpGain(skill, xp, xpGainReason, xpGainSource);
    }

    /**
     * Calculates how long a given ability should last in seconds
     * Does not factor in perks
     * @param mcMMOPlayer target mcMMO Player
     * @param skill target skill
     * @param superAbilityType target Super Ability
     * @return how long an ability should last in seconds
     */
    public static int calculateAbilityLength(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, SuperAbilityType superAbilityType) {
        //These values change depending on whether or not the server is in retro mode
        int abilityLengthVar = pluginRef.getConfigManager().getConfigSuperAbilities().getSuperAbilityStartingSeconds();

        int maxLength = pluginRef.getConfigManager().getConfigSuperAbilities().getMaxLengthForSuper(superAbilityType);

        int skillLevel = mcMMOPlayer.getSkillLevel(skill);

        int ticks;

        //Ability cap of 0 or below means no cap
        if (maxLength > 0) {
            ticks = Math.min(2 + (Math.min(maxLength, skillLevel) / abilityLengthVar), maxLength);
        } else {
            ticks = Math.min(2 + (Math.min(maxLength, skillLevel) / abilityLengthVar), maxLength);
        }

        return ticks;
    }

    /**
     * Calculates how long a given ability should last in seconds
     * Adds in perks if the player has any
     * @param mcMMOPlayer target mcMMO Player
     * @param skill target skill
     * @param superAbilityType target Super Ability
     * @return how long an ability should last in seconds
     */
    public static int calculateAbilityLengthPerks(McMMOPlayer mcMMOPlayer, PrimarySkillType skill, SuperAbilityType superAbilityType) {
        return getEnduranceLength(mcMMOPlayer.getPlayer()) + calculateAbilityLength(mcMMOPlayer, skill, superAbilityType);
    }

    public static int getEnduranceLength(Player player) {
        if (Permissions.twelveSecondActivationBoost(player)) {
            return 12;
        } else if (Permissions.eightSecondActivationBoost(player)) {
            return  8;
        } else if (Permissions.fourSecondActivationBoost(player)) {
            return  4;
        } else {
            return 0;
        }
    }

    public static int handleFoodSkills(Player player, int eventFoodLevel, SubSkillType subSkillType) {
        int curRank = RankUtils.getRank(player, subSkillType);

        int currentFoodLevel = player.getFoodLevel();
        int foodChange = eventFoodLevel - currentFoodLevel;

        foodChange += curRank;

        return currentFoodLevel + foodChange;
    }

    /**
     * Calculate the time remaining until the cooldown expires.
     *
     * @param deactivatedTimeStamp Time of deactivation
     * @param cooldown             The length of the cooldown
     * @param player               The Player to check for cooldown perks
     * @return the number of seconds remaining before the cooldown expires
     */
    public static int calculateTimeLeft(long deactivatedTimeStamp, int cooldown, Player player) {
        return (int) (((deactivatedTimeStamp + (PerksUtils.handleCooldownPerks(player, cooldown) * Misc.TIME_CONVERSION_FACTOR)) - System.currentTimeMillis()) / Misc.TIME_CONVERSION_FACTOR);
    }

    /**
     * Check if the cooldown has expired.
     * This does NOT account for cooldown perks!
     *
     * @param deactivatedTimeStamp Time of deactivation in seconds
     * @param cooldown             The length of the cooldown in seconds
     * @return true if the cooldown is expired
     */
    public static boolean cooldownExpired(long deactivatedTimeStamp, int cooldown) {
        return System.currentTimeMillis() >= (deactivatedTimeStamp + cooldown) * Misc.TIME_CONVERSION_FACTOR;
    }

    /**
     * Checks if the given string represents a valid skill
     *
     * @param skillName The name of the skill to check
     * @return true if this is a valid skill, false otherwise
     */
    public static boolean isSkill(String skillName) {
        return pluginRef.getConfigManager().getConfigLanguage().getTargetLanguage().equalsIgnoreCase("en_US") ? PrimarySkillType.getSkill(skillName) != null : isLocalizedSkill(skillName);
    }

    public static void sendSkillMessage(Player player, NotificationType notificationType, String key) {
        Location location = player.getLocation();

        for (Player otherPlayer : player.getWorld().getPlayers()) {
            if (otherPlayer != player && Misc.isNear(location, otherPlayer.getLocation(), Misc.SKILL_MESSAGE_MAX_SENDING_DISTANCE)) {
                pluginRef.getNotificationManager().sendNearbyPlayersInformation(otherPlayer, notificationType, key, player.getName());
            }
        }
    }

    public static void handleAbilitySpeedIncrease(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return;
        }

        int efficiencyLevel = heldItem.getEnchantmentLevel(Enchantment.DIG_SPEED);
        ItemMeta itemMeta = heldItem.getItemMeta();
        List<String> itemLore = new ArrayList<>();

        if (itemMeta.hasLore()) {
            itemLore = itemMeta.getLore();
        }

        itemLore.add("mcMMO Ability Tool");
        itemMeta.addEnchant(Enchantment.DIG_SPEED, efficiencyLevel + ENCHANT_SPEED_VAR, true);

        itemMeta.setLore(itemLore);
        heldItem.setItemMeta(itemMeta);
        player.updateInventory();

        /*else {
            int duration = 0;
            int amplifier = 0;

            if (player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    if (effect.getType() == PotionEffectType.FAST_DIGGING) {
                        duration = effect.getDuration();
                        amplifier = effect.getAmplifier();
                        break;
                    }
                }
            }

            McMMOPlayer mcMMOPlayer = pluginRef.getUserManager().getPlayer(player);

            //Not Loaded
            if(mcMMOPlayer == null)
                return;

            PrimarySkillType skill = mcMMOPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER) ? PrimarySkillType.MINING : PrimarySkillType.EXCAVATION;

            int abilityLengthVar = AdvancedConfig.getInstance().getAbilityLength();
            int abilityLengthCap = AdvancedConfig.getInstance().getAbilityLengthCap();

            int ticks;

            if(abilityLengthCap > 0)
            {
                ticks = PerksUtils.calculateAbilityLength(player,  Math.min(abilityLengthCap, 2 + (mcMMOPlayer.getSkillLevel(skill) / abilityLengthVar)),
                        skill.getSuperAbility().getMaxLength()) * Misc.TICK_CONVERSION_FACTOR;
            } else {
                ticks = PerksUtils.calculateAbilityLength(player, 2 + ((mcMMOPlayer.getSkillLevel(skill)) / abilityLengthVar),
                        skill.getSuperAbility().getMaxLength()) * Misc.TICK_CONVERSION_FACTOR;
            }

            PotionEffect abilityBuff = new PotionEffect(PotionEffectType.FAST_DIGGING, duration + ticks, amplifier + 10);
            player.addPotionEffect(abilityBuff, true);
        }*/
    }

    public static void handleAbilitySpeedDecrease(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            removeAbilityBuff(item);
        }
    }

    public static void removeAbilityBuff(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || (!ItemUtils.isPickaxe(item) && !ItemUtils.isShovel(item)) || !item.containsEnchantment(Enchantment.DIG_SPEED)) {
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();

        if (itemMeta.hasLore()) {
            List<String> itemLore = itemMeta.getLore();

            if (itemLore.remove("mcMMO Ability Tool")) {
                int efficiencyLevel = item.getEnchantmentLevel(Enchantment.DIG_SPEED);

                if (efficiencyLevel <= ENCHANT_SPEED_VAR) {
                    itemMeta.removeEnchant(Enchantment.DIG_SPEED);
                } else {
                    itemMeta.addEnchant(Enchantment.DIG_SPEED, efficiencyLevel - ENCHANT_SPEED_VAR, true);
                }

                itemMeta.setLore(itemLore);
                item.setItemMeta(itemMeta);
            }
        }
    }

    public static void handleDurabilityChange(ItemStack itemStack, int durabilityModifier) {
        handleDurabilityChange(itemStack, durabilityModifier, 1.0);
    }

    /**
     * Modify the durability of an ItemStack.
     *
     * @param itemStack          The ItemStack which durability should be modified
     * @param durabilityModifier the amount to modify the durability by
     * @param maxDamageModifier  the amount to adjust the max damage by
     */
    public static void handleDurabilityChange(ItemStack itemStack, double durabilityModifier, double maxDamageModifier) {
        if(itemStack.getItemMeta() != null && itemStack.getItemMeta().isUnbreakable()) {
            return;
        }

        Material type = itemStack.getType();
        short maxDurability = pluginRef.getRepairableManager().isRepairable(type) ? pluginRef.getRepairableManager().getRepairable(type).getMaximumDurability() : type.getMaxDurability();
        durabilityModifier = (int) Math.min(durabilityModifier / (itemStack.getEnchantmentLevel(Enchantment.DURABILITY) + 1), maxDurability * maxDamageModifier);

        itemStack.setDurability((short) Math.min(itemStack.getDurability() + durabilityModifier, maxDurability));
    }

    private static boolean isLocalizedSkill(String skillName) {
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (skillName.equalsIgnoreCase(pluginRef.getLocaleManager().getString(StringUtils.getCapitalized(skill.toString()) + ".SkillName"))) {
                return true;
            }
        }

        return false;
    }

    protected static Material getRepairAndSalvageItem(ItemStack inHand) {
        if (ItemUtils.isDiamondTool(inHand) || ItemUtils.isDiamondArmor(inHand)) {
            return Material.DIAMOND;
        } else if (ItemUtils.isGoldTool(inHand) || ItemUtils.isGoldArmor(inHand)) {
            return Material.GOLD_INGOT;
        } else if (ItemUtils.isIronTool(inHand) || ItemUtils.isIronArmor(inHand)) {
            return Material.IRON_INGOT;
        } else if (ItemUtils.isStoneTool(inHand)) {
            return Material.COBBLESTONE;
        } else if (ItemUtils.isWoodTool(inHand)) {
            return Material.OAK_WOOD;
        } else if (ItemUtils.isLeatherArmor(inHand)) {
            return Material.LEATHER;
        } else if (ItemUtils.isStringTool(inHand)) {
            return Material.STRING;
        } else {
            return null;
        }
    }

    public static int getRepairAndSalvageQuantities(ItemStack item) {
        return getRepairAndSalvageQuantities(item.getType(), getRepairAndSalvageItem(item));
    }

    public static int getRepairAndSalvageQuantities(Material itemMaterial, Material recipeMaterial) {
        int quantity = 0;

        for(Iterator<? extends Recipe> recipeIterator = Bukkit.getServer().recipeIterator(); recipeIterator.hasNext();) {
            Recipe bukkitRecipe = recipeIterator.next();

            if(bukkitRecipe.getResult().getType() != itemMaterial)
                continue;

            if(bukkitRecipe instanceof ShapelessRecipe) {
                for (ItemStack ingredient : ((ShapelessRecipe) bukkitRecipe).getIngredientList()) {
                    if (ingredient != null
                            && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                            && (ingredient.getType() == recipeMaterial)) {
                        quantity += ingredient.getAmount();
                    }
                }
            } else if(bukkitRecipe instanceof ShapedRecipe) {
                for (ItemStack ingredient : ((ShapedRecipe) bukkitRecipe).getIngredientMap().values()) {
                    if (ingredient != null
                            && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                            && (ingredient.getType() == recipeMaterial)) {
                        quantity += ingredient.getAmount();
                    }
                }
            }
        }

        return quantity;
    }

    public static int getRepairAndSalvageQuantities(Material itemMaterial, List<Material> recipeMaterials) {
        int quantity = 0;

        for(Iterator<? extends Recipe> recipeIterator = Bukkit.getServer().recipeIterator(); recipeIterator.hasNext();) {
            Recipe bukkitRecipe = recipeIterator.next();

            if(bukkitRecipe.getResult().getType() != itemMaterial)
                continue;

            boolean matchedIngredient = false;

            for(Material recipeMaterial : recipeMaterials) {
                if(matchedIngredient)
                    break;

                if(bukkitRecipe instanceof ShapelessRecipe) {
                    for (ItemStack ingredient : ((ShapelessRecipe) bukkitRecipe).getIngredientList()) {
                        if (ingredient != null
                                && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                                && (ingredient.getType() == recipeMaterial)) {
                            quantity += ingredient.getAmount();
                            matchedIngredient = true;
                        }
                    }
                } else if(bukkitRecipe instanceof ShapedRecipe) {
                    for (ItemStack ingredient : ((ShapedRecipe) bukkitRecipe).getIngredientMap().values()) {
                        if (ingredient != null
                                && (recipeMaterial == null || ingredient.getType() == recipeMaterial)
                                && (ingredient.getType() == recipeMaterial)) {
                            quantity += ingredient.getAmount();
                            matchedIngredient = true;
                        }
                    }
                }
            }
        }

        return quantity;
    }

    /**
     * Determine if a recipe has already been registered
     * @param recipe target recipe
     * @return true if the recipe has already been registered
     */
    public static boolean hasRecipeBeenRegistered(Recipe recipe) {
        for(Iterator<? extends Recipe> recipeIterator = Bukkit.getServer().recipeIterator(); recipeIterator.hasNext();) {
            Recipe bukkitRecipe = recipeIterator.next();

            if(bukkitRecipe.getResult().isSimilar(recipe.getResult())) {
                return true;
            }

        }
        return false;
    }


}
