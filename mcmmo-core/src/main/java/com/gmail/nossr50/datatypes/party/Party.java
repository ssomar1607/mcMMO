package com.gmail.nossr50.datatypes.party;

import com.gmail.nossr50.datatypes.player.BukkitMMOPlayer;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.party.ShareHandler;
import com.gmail.nossr50.util.StringUtils;
import com.gmail.nossr50.util.sounds.SoundType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Party {
    private final mcMMO pluginRef;
    private static final String ONLINE_PLAYER_PREFIX = "⬤";
    private static final String OFFLINE_PLAYER_PREFIX = "○";
    private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
    private final List<Player> onlineMembers = new ArrayList<>();
    private PartyLeader leader;
    private String name;
    private String password;
    private boolean locked;
    private Party ally;
    private int level;
    private double xp;

    private ShareHandler shareHandler;

    private ShareMode xpShareMode = ShareMode.NONE;
    private ShareMode itemShareMode = ShareMode.NONE;

    private boolean shareLootDrops = true;
    private boolean shareMiningDrops = true;
    private boolean shareHerbalismDrops = true;
    private boolean shareWoodcuttingDrops = true;
    private boolean shareMiscDrops = true;

    public Party(String name, mcMMO pluginRef) {
        this.pluginRef = pluginRef;
        this.name = name;
    }

    public Party(PartyLeader leader, String name, mcMMO pluginRef) {
        this.pluginRef = pluginRef;
        this.leader = leader;
        this.name = name;
        this.locked = true;
        this.level = 0;

        initShareHandler();
    }

    public Party(PartyLeader leader, String name, String password, mcMMO pluginRef)
    {
        this.pluginRef = pluginRef;
        this.leader = leader;
        this.name = name;
        this.password = password;
        this.locked = true;
        this.level = 0;

        initShareHandler();
    }

    public Party(PartyLeader leader, String name, String password, boolean locked, mcMMO pluginRef) {
        this.pluginRef = pluginRef;
        this.leader = leader;
        this.name = name;
        this.password = password;
        this.locked = locked;
        this.level = 0;

        initShareHandler();
    }

    private void initShareHandler() {
        shareHandler = new ShareHandler(pluginRef, this);
    }

    public LinkedHashMap<UUID, String> getMembers() {
        return members;
    }

    public List<Player> getOnlineMembers() {
        return onlineMembers;
    }

    public List<Player> getVisibleMembers(Player player) {
        ArrayList<Player> visibleMembers = new ArrayList<>();

        for (Player p : onlineMembers) {
            if (player.canSee(p))
                visibleMembers.add(p);
        }

        return visibleMembers;
    }

    public List<String> getOnlinePlayerNames(CommandSender sender) {
        Player player = sender instanceof Player ? (Player) sender : null;
        List<String> onlinePlayerNames = new ArrayList<>();

        for (Player onlinePlayer : getOnlineMembers()) {
            if (player != null && player.canSee(onlinePlayer)) {
                onlinePlayerNames.add(onlinePlayer.getName());
            }
        }

        return onlinePlayerNames;
    }

    public void addOnlineMember(Player player) {
        onlineMembers.add(player);
    }

    public void removeOnlineMember(Player player) {
        onlineMembers.remove(player);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PartyLeader getLeader() {
        return leader;
    }

    public void setLeader(PartyLeader leader) {
        this.leader = leader;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Party getAlly() {
        return ally;
    }

    public void setAlly(Party ally) {
        this.ally = ally;
    }

    public List<String> getItemShareCategories() {
        List<String> shareCategories = new ArrayList<>();

        for (ItemShareType shareType : ItemShareType.values()) {
            if (sharingDrops(shareType)) {
                shareCategories.add(getShareTypeLocaleString(shareType));
            }
        }

        return shareCategories;
    }

    public String getShareTypeLocaleString(ItemShareType itemShareType) {
        return pluginRef.getLocaleManager().getString("Party.ItemShare.Category." + StringUtils.getCapitalized(itemShareType.toString()));
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public void addXp(double xp) {
        setXp(getXp() + xp);
    }

    protected double levelUp() {
        double xpRemoved = getXpToLevel();

        setLevel(getLevel() + 1);
        setXp(getXp() - xpRemoved);

        return xpRemoved;
    }

    public int getXpToLevel() {
        return pluginRef.getFormulaManager().getXPtoNextLevel(level);
    }

    public String getXpToLevelPercentage() {
        DecimalFormat percent = new DecimalFormat("##0.00%");
        return percent.format(this.getXp() / getXpToLevel());
    }

    /**
     * Applies an experience gain
     *
     * @param xp Experience amount to add
     */
    public void applyXpGain(double xp) {
        if (!pluginRef.getEventManager().handlePartyXpGainEvent(this, xp)) {
            return;
        }

        if (getXp() < getXpToLevel()) {
            return;
        }

        int levelsGained = 0;
        double xpRemoved = 0;

        while (getXp() >= getXpToLevel()) {
            /*if (hasReachedLevelCap()) {
                setXp(0);
                return;
            }*/

            xpRemoved += levelUp();
            levelsGained++;
        }

        if (!pluginRef.getEventManager().handlePartyLevelChangeEvent(this, levelsGained, xpRemoved)) {
            return;
        }

        if (!pluginRef.getConfigManager().getConfigParty().getPartyXP().getPartyLevel().isInformPartyMembersOnLevelup()) {
            Player leader = pluginRef.getServer().getPlayer(this.leader.getUniqueId());

            if (leader != null) {
                leader.sendMessage(pluginRef.getLocaleManager().getString("Party.LevelUp", levelsGained, getLevel()));

                pluginRef.getSoundManager().sendSound(leader, leader.getLocation(), SoundType.LEVEL_UP);
            }
            return;
        }

        pluginRef.getPartyManager().informPartyMembersLevelUp(this, levelsGained, getLevel());
    }

    public ShareMode getXpShareMode() {
        return xpShareMode;
    }

    public void setXpShareMode(ShareMode xpShareMode) {
        this.xpShareMode = xpShareMode;
    }

    public ShareMode getItemShareMode() {
        return itemShareMode;
    }

    public void setItemShareMode(ShareMode itemShareMode) {
        this.itemShareMode = itemShareMode;
    }

    public boolean sharingDrops(ItemShareType shareType) {
        switch (shareType) {
            case HERBALISM:
                return shareHerbalismDrops;

            case LOOT:
                return shareLootDrops;

            case MINING:
                return shareMiningDrops;

            case MISC:
                return shareMiscDrops;

            case WOODCUTTING:
                return shareWoodcuttingDrops;

            default:
                return false;
        }
    }

    public void setSharingDrops(ItemShareType shareType, boolean enabled) {
        switch (shareType) {
            case HERBALISM:
                shareHerbalismDrops = enabled;
                break;

            case LOOT:
                shareLootDrops = enabled;
                break;

            case MINING:
                shareMiningDrops = enabled;
                break;

            case MISC:
                shareMiscDrops = enabled;
                break;

            case WOODCUTTING:
                shareWoodcuttingDrops = enabled;
                break;

            default:
        }
    }

    public boolean hasMember(String memberName) {
        return this.getMembers().values().contains(memberName);
    }

    public boolean hasMember(UUID uuid) {
        return this.getMembers().keySet().contains(uuid);
    }

    /**
     * Makes a formatted list of party members based on the perspective of a target player
     * Players that are hidden will be shown as offline (formatted in the same way)
     * Party leader will be formatted a specific way as well
     *
     * @param player target player to use as POV
     * @return formatted list of party members from the POV of a player
     */
    public String createMembersList(Player player) {
        StringBuilder memberList = new StringBuilder();

        List<UUID> onlineMembers = members.keySet().stream()
                .filter(x -> Bukkit.getOfflinePlayer(x).isOnline())
                .collect(Collectors.toList());

        List<UUID> offlineMembers = members.keySet().stream()
                .filter(x -> !Bukkit.getOfflinePlayer(x).isOnline())
                .collect(Collectors.toList());

        ArrayList<UUID> visiblePartyList = new ArrayList<>();
        boolean isPartyLeaderOfflineOrHidden = false;
        ArrayList<UUID> offlineOrHiddenPartyList = new ArrayList<>();

        for (UUID onlineMember : onlineMembers) {
            Player onlinePlayer = Bukkit.getPlayer(onlineMember);

            if (!isNotSamePerson(player.getUniqueId(), onlineMember) || player.canSee(onlinePlayer)) {
                visiblePartyList.add(onlineMember);
            } else {
                //Party leader and cannot be seen by this player
                if (isNotSamePerson(leader.getUniqueId(), player.getUniqueId()) && onlineMember == leader.getUniqueId())
                    isPartyLeaderOfflineOrHidden = true;

                offlineOrHiddenPartyList.add(onlineMember);
            }
        }

        if (offlineMembers.contains(leader.getUniqueId()))
            isPartyLeaderOfflineOrHidden = true;

        //Add all the actually offline members
        offlineOrHiddenPartyList.addAll(offlineMembers);

        /* BUILD THE PARTY LIST WITH FORMATTING */

        String partyLeaderPrefix =
                /*ChatColor.WHITE
                + "["
                +*/ ChatColor.GOLD
                + "♕"
                /*+ ChatColor.WHITE
                + "]"*/
                + ChatColor.RESET;

        //First add the party leader
        memberList.append(partyLeaderPrefix);

        List<Player> nearbyPlayerList = getNearMembers(pluginRef.getUserManager().getPlayer(player));

        boolean useDisplayNames = pluginRef.getConfigManager().getConfigParty().isPartyDisplayNamesEnabled();

        if (isPartyLeaderOfflineOrHidden) {
            if (isNotSamePerson(player.getUniqueId(), leader.getUniqueId()))
                applyOnlineAndRangeFormatting(memberList, false, false);

            memberList.append(ChatColor.GRAY)
                    .append(leader.getPlayerName());
        } else {
            if (isNotSamePerson(leader.getUniqueId(), player.getUniqueId()))
                applyOnlineAndRangeFormatting(memberList, true, nearbyPlayerList.contains(Bukkit.getPlayer(leader.getUniqueId())));

            if (useDisplayNames) {
                memberList.append(leader.getPlayerName());
            } else {
                memberList.append(ChatColor.GOLD)
                        .append(Bukkit.getOfflinePlayer(leader.getUniqueId()));
            }
        }

        //Space
        memberList.append(" ");

        //Now do online members
        for (UUID onlinePlayerUUID : visiblePartyList) {
            if (onlinePlayerUUID == leader.getUniqueId())
                continue;

            if (isNotSamePerson(onlinePlayerUUID, player.getUniqueId()))
                applyOnlineAndRangeFormatting(memberList, true, nearbyPlayerList.contains(Bukkit.getPlayer(onlinePlayerUUID)));

            if (useDisplayNames) {
                memberList.append(Bukkit.getPlayer(onlinePlayerUUID).getDisplayName());
            } else {
                //Color allies green, players dark aqua
                memberList.append(ChatColor.GREEN)
                        .append(Bukkit.getPlayer(onlinePlayerUUID).getName());
            }

            memberList.append(" ").append(ChatColor.RESET);
        }

        for (UUID offlineOrHiddenPlayer : offlineOrHiddenPartyList) {
            if (offlineOrHiddenPlayer == leader.getUniqueId())
                continue;

            applyOnlineAndRangeFormatting(memberList, false, false);

            memberList.append(ChatColor.GRAY)
                    .append(Bukkit.getOfflinePlayer(offlineOrHiddenPlayer).getName())
                    .append(" ").append(ChatColor.RESET);
        }


//        for (Player otherPlayer : this.getVisibleMembers(player)) {
//            String memberName = otherPlayer.getName();
//
//            if (this.getLeader().getUniqueId().equals(otherPlayer.getUniqueId())) {
//                memberList.append(ChatColor.GOLD);
//
//                if (otherPlayer == null) {
//                    memberName = memberName.substring(0, 1) + ChatColor.GRAY + ChatColor.ITALIC + "" + memberName.substring(1);
//                }
//            }
//            else if (otherPlayer != null) {
//                memberList.append(ChatColor.WHITE);
//            }
//            else {
//                memberList.append(ChatColor.GRAY);
//            }
//
//            if (player.getName().equalsIgnoreCase(otherPlayer.getName())) {
//                memberList.append(ChatColor.ITALIC);
//            }
//
//            memberList.append(memberName).append(ChatColor.RESET).append(" ");
//        }

        return memberList.toString();
    }

    private boolean isNotSamePerson(UUID onlinePlayerUUID, UUID uniqueId) {
        return onlinePlayerUUID != uniqueId;
    }

    private void applyOnlineAndRangeFormatting(StringBuilder stringBuilder, boolean isVisibleOrOnline, boolean isNear) {
        if (isVisibleOrOnline) {
            if (isNear) {
                stringBuilder.append(ChatColor.GREEN);
            } else {
                stringBuilder.append(ChatColor.GRAY);
            }

//            stringBuilder.append(ChatColor.BOLD);
            stringBuilder.append(ONLINE_PLAYER_PREFIX);
        } else {
            stringBuilder.append(ChatColor.GRAY);
            stringBuilder.append(OFFLINE_PLAYER_PREFIX);
        }

        stringBuilder.append(ChatColor.RESET);
    }

    /**
     * Get the near party members.
     *
     * @param mcMMOPlayer The player to check
     * @return the near party members
     */
    public List<Player> getNearMembers(BukkitMMOPlayer mcMMOPlayer) {
        List<Player> nearMembers = new ArrayList<Player>();
        Party party = mcMMOPlayer.getParty();

        if (party != null) {
            Player player = mcMMOPlayer.getPlayer();
            double range = pluginRef.getConfigManager().getConfigParty().getPartyXP().getPartyExperienceSharing().getPartyShareRange();

            for (Player member : party.getOnlineMembers()) {
                if (!player.equals(member) && member.isValid() && pluginRef.getMiscTools().isNear(player.getLocation(), member.getLocation(), range)) {
                    nearMembers.add(member);
                }
            }
        }

        return nearMembers;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof Party)) {
            return false;
        }

        Party other = (Party) obj;

        if ((this.getName() == null) || (other.getName() == null)) {
            return false;
        }

        return this.getName().equals(other.getName());
    }

    public ShareHandler getShareHandler() {
        return shareHandler;
    }
}
