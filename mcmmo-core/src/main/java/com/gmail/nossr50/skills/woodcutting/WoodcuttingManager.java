package com.gmail.nossr50.skills.woodcutting;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.BukkitMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.behaviours.WoodcuttingBehaviour;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.skills.SkillActivationType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WoodcuttingManager extends SkillManager {

    private final WoodcuttingBehaviour woodcuttingBehaviour;
    private boolean treeFellerReachedThreshold;

    public WoodcuttingManager(mcMMO pluginRef, BukkitMMOPlayer mcMMOPlayer) {
        super(pluginRef, mcMMOPlayer, PrimarySkillType.WOODCUTTING);
        this.treeFellerReachedThreshold = false;
        this.woodcuttingBehaviour = pluginRef.getDynamicSettingsManager().getSkillBehaviourManager().getWoodcuttingBehaviour();
    }

    public boolean canUseLeafBlower(ItemStack heldItem) {
        return pluginRef.getPermissionTools().isSubSkillEnabled(getPlayer(), SubSkillType.WOODCUTTING_LEAF_BLOWER)
                && pluginRef.getRankTools().hasUnlockedSubskill(getPlayer(), SubSkillType.WOODCUTTING_LEAF_BLOWER)
                && pluginRef.getItemTools().isAxe(heldItem);
    }

    public boolean canUseTreeFeller(ItemStack heldItem) {
        return mcMMOPlayer.getSuperAbilityMode(SuperAbilityType.TREE_FELLER)
                && pluginRef.getItemTools().isAxe(heldItem);
    }

    private boolean canGetDoubleDrops() {
        return pluginRef.getPermissionTools().isSubSkillEnabled(getPlayer(), SubSkillType.WOODCUTTING_HARVEST_LUMBER)
                && pluginRef.getRankTools().hasReachedRank(1, getPlayer(), SubSkillType.WOODCUTTING_HARVEST_LUMBER)
                && pluginRef.getRandomChanceTools().isActivationSuccessful(SkillActivationType.RANDOM_LINEAR_100_SCALE_WITH_CAP, SubSkillType.WOODCUTTING_HARVEST_LUMBER, getPlayer());
    }

    /**
     * Begins Woodcutting
     *
     * @param blockState Block being broken
     */
    public void woodcuttingBlockCheck(BlockState blockState) {
        int xp = woodcuttingBehaviour.getExperienceFromLog(blockState);

        switch (blockState.getType()) {
            case BROWN_MUSHROOM_BLOCK:
            case RED_MUSHROOM_BLOCK:
                break;

            default:
                if (canGetDoubleDrops()) {
                    woodcuttingBehaviour.checkForDoubleDrop(blockState);
                }
        }

        applyXpGain(xp, XPGainReason.PVE);
    }

    /**
     * Begins Tree Feller
     *
     * @param blockState Block being broken
     */
    public void processTreeFeller(BlockState blockState) {
        Player player = getPlayer();
        Set<BlockState> treeFellerBlocks = new HashSet<>();

        treeFellerReachedThreshold = false;

        processTree(blockState, treeFellerBlocks);

        // If the player is trying to break too many blocks
        if (treeFellerReachedThreshold) {
            treeFellerReachedThreshold = false;

            pluginRef.getNotificationManager().sendPlayerInformation(player, NotificationType.SUBSKILL_MESSAGE_FAILED, "SuperAbility.TreeFeller.Threshold");
            return;
        }

        // If the tool can't sustain the durability loss
        if (!handleDurabilityLoss(treeFellerBlocks, player.getInventory().getItemInMainHand())) {
            pluginRef.getNotificationManager().sendPlayerInformation(player, NotificationType.SUBSKILL_MESSAGE_FAILED, "SuperAbility.TreeFeller.Splinter");

            double health = player.getHealth();

            if (health > 1) {
                pluginRef.getCombatTools().dealDamage(player, pluginRef.getMiscTools().getRandom().nextInt((int) (health - 1)));
            }

            return;
        }

        dropTreeFellerLootFromBlocks(treeFellerBlocks);
        treeFellerReachedThreshold = false; // Reset the value after we're done with Tree Feller each time.
    }

    /**
     * Processes Tree Feller in a recursive manner
     *
     * @param blockState       Block being checked
     * @param treeFellerBlocks List of blocks to be removed
     */
    /*
     * Algorithm: An int[][] of X/Z directions is created on class
     * initialization, representing a cylinder with radius of about 2 - the
     * (0,0) center and all (+-2, +-2) corners are omitted.
     *
     * handleBlock() returns a boolean, which is used for the sole purpose of
     * switching between these two behaviors:
     *
     * (Call blockState "this log" for the below explanation.)
     *
     *  [A] There is another log above this log (TRUNK)
     *    Only the flat cylinder in the directions array is searched.
     *  [B] There is not another log above this log (BRANCH AND TOP)
     *    The cylinder in the directions array is extended up and down by 1
     *    block in the Y-axis, and the block below this log is checked as
     *    well. Due to the fact that the directions array will catch all
     *    blocks on a red mushroom, the special method for it is eliminated.
     *
     * This algorithm has been shown to achieve a performance of 2-5
     * milliseconds on regular trees and 10-15 milliseconds on jungle trees
     * once the JIT has optimized the function (use the ability about 4 times
     * before taking measurements).
     */
    public void processTree(BlockState blockState, Set<BlockState> treeFellerBlocks) {
        List<BlockState> futureCenterBlocks = new ArrayList<>();

        // Check the block up and take different behavior (smaller search) if it's a log
        if (handleBlock(blockState.getBlock().getRelative(BlockFace.UP).getState(), futureCenterBlocks, treeFellerBlocks)) {
            for (int[] dir : woodcuttingBehaviour.getDirections()) {
                handleBlock(blockState.getBlock().getRelative(dir[0], 0, dir[1]).getState(), futureCenterBlocks, treeFellerBlocks);

                if (treeFellerReachedThreshold) {
                    return;
                }
            }
        } else {
            // Cover DOWN
            handleBlock(blockState.getBlock().getRelative(BlockFace.DOWN).getState(), futureCenterBlocks, treeFellerBlocks);
            // Search in a cube
            for (int y = -1; y <= 1; y++) {
                for (int[] dir : woodcuttingBehaviour.getDirections()) {
                    handleBlock(blockState.getBlock().getRelative(dir[0], y, dir[1]).getState(), futureCenterBlocks, treeFellerBlocks);

                    if (treeFellerReachedThreshold) {
                        return;
                    }
                }
            }
        }

        // Recursive call for each log found
        for (BlockState futureCenterBlock : futureCenterBlocks) {
            if (treeFellerReachedThreshold) {
                return;
            }

            processTree(futureCenterBlock, treeFellerBlocks);
        }
    }

    /**
     * Handles the durability loss
     *
     * @param treeFellerBlocks List of blocks to be removed
     * @param inHand           tool being used
     * @return True if the tool can sustain the durability loss
     */
    public boolean handleDurabilityLoss(Set<BlockState> treeFellerBlocks, ItemStack inHand) {
        //Treat the NBT tag for unbreakable and the durability enchant differently
        if(inHand.getItemMeta() != null && inHand.getItemMeta().isUnbreakable()) {
            return true;
        }

        short durabilityLoss = 0;
        Material type = inHand.getType();

        for (BlockState blockState : treeFellerBlocks) {
            if (pluginRef.getBlockTools().isLog(blockState)) {
                durabilityLoss += pluginRef.getConfigManager().getConfigSuperAbilities().getSuperAbilityLimits().getToolDurabilityDamage();
            }
        }

        pluginRef.getSkillTools().handleDurabilityChange(inHand, durabilityLoss);
        //TODO: Revert code back to its former implementation after the Repair rewrite? The implementation is strange so idk.
        // (FORMER IMPLEMENTATION) return (inHand.getDurability() < (pluginRef.getRepairableManager().isRepairable(type) ? pluginRef.getRepairableManager().getRepairable(type).getMaximumDurability() : type.getMaxDurability()));
        return (inHand.getDurability() < type.getMaxDurability());
    }

    /**
     * Handle a block addition to the list of blocks to be removed and to the
     * list of blocks used for future recursive calls of
     * 'processTree()'
     *
     * @param blockState         Block to be added
     * @param futureCenterBlocks List of blocks that will be used to call
     *                           'processTree()'
     * @param treeFellerBlocks   List of blocks to be removed
     * @return true if and only if the given blockState was a Log not already
     * in treeFellerBlocks.
     */
    private boolean handleBlock(BlockState blockState, List<BlockState> futureCenterBlocks, Set<BlockState> treeFellerBlocks) {
        if (treeFellerBlocks.contains(blockState) || pluginRef.getPlaceStore().isTrue(blockState)) {
            return false;
        }

        // Without this check Tree Feller propagates through leaves until the threshold is hit
        if (treeFellerBlocks.size() > pluginRef.getConfigManager().getConfigSuperAbilities().getSuperAbilityLimits().getTreeFeller().getTreeFellerLimit()) {
            treeFellerReachedThreshold = true;
        }

        if (pluginRef.getBlockTools().isLog(blockState)) {
            treeFellerBlocks.add(blockState);
            futureCenterBlocks.add(blockState);
            return true;
        } else if (pluginRef.getBlockTools().isLeaves(blockState)) {
            treeFellerBlocks.add(blockState);
            return false;
        }
        return false;
    }

    public enum ExperienceGainMethod {
        DEFAULT,
        TREE_FELLER,
    }

    /**
     * Handles the dropping of blocks
     *
     * @param treeFellerBlocks List of blocks to be dropped
     */
    private void dropTreeFellerLootFromBlocks(Set<BlockState> treeFellerBlocks) {
        Player player = getPlayer();
        int xp = 0;
        int processedLogCount = 0;

        for (BlockState blockState : treeFellerBlocks) {
            int beforeXP = xp;
            Block block = blockState.getBlock();

            if (!pluginRef.getEventManager().simulateBlockBreak(block, player, true)) {
                break; // TODO: Shouldn't we use continue instead?
            }

            Material material = blockState.getType();

            //TODO: Update this to drop the correct items/blocks via NMS
            if (material == Material.BROWN_MUSHROOM_BLOCK || material == Material.RED_MUSHROOM_BLOCK) {
                xp += woodcuttingBehaviour.processTreeFellerXPGains(blockState, processedLogCount);
                pluginRef.getMiscTools().dropItems(pluginRef.getMiscTools().getBlockCenter(blockState), block.getDrops());
            } else {
                if (pluginRef.getBlockTools().isLog(blockState)) {
                    if (canGetDoubleDrops()) {
                        woodcuttingBehaviour.checkForDoubleDrop(blockState);
                    }
                    xp += woodcuttingBehaviour.processTreeFellerXPGains(blockState, processedLogCount);
                    pluginRef.getMiscTools().dropItems(pluginRef.getMiscTools().getBlockCenter(blockState), block.getDrops());
                }
                if (pluginRef.getBlockTools().isLeaves(blockState)) {
                    pluginRef.getMiscTools().dropItems(pluginRef.getMiscTools().getBlockCenter(blockState), block.getDrops());
                }
            }

            blockState.setType(Material.AIR);
            blockState.update(true);

            //Update only when XP changes
            processedLogCount = updateProcessedLogCount(xp, processedLogCount, beforeXP);
        }

        applyXpGain(xp, XPGainReason.PVE);
    }

    private int updateProcessedLogCount(int xp, int processedLogCount, int beforeXP) {
        if(beforeXP != xp)
            processedLogCount+=1;

        return processedLogCount;
    }
}
