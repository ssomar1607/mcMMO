package com.gmail.nossr50.commands.party.alliance;

import com.gmail.nossr50.datatypes.party.Party;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.mcMMO;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyAllianceDisbandCommand implements CommandExecutor {

    private mcMMO pluginRef;

    public PartyAllianceDisbandCommand(mcMMO pluginRef) {
        this.pluginRef = pluginRef;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (args.length) {
            case 2:
                if (pluginRef.getUserManager().getPlayer((Player) sender) == null) {
                    sender.sendMessage(pluginRef.getLocaleManager().getString("Profile.PendingLoad"));
                    return true;
                }
                Player player = (Player) sender;
                McMMOPlayer mcMMOPlayer = pluginRef.getUserManager().getPlayer(player);
                Party party = mcMMOPlayer.getParty();

                if (party.getAlly() == null) {
                    sender.sendMessage(pluginRef.getLocaleManager().getString("Commands.Party.Alliance.None"));
                    return true;
                }

                pluginRef.getPartyManager().disbandAlliance(player, party, party.getAlly());
                return true;

            default:
                sender.sendMessage(pluginRef.getLocaleManager().getString("Commands.Usage.2", "party", "alliance", "disband"));
                return true;
        }
    }
}
