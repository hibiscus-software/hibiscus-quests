/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.reward;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscuslib.api.team.Team;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.progress.QuestTeams;

// command: runs a command as the server (op level 4), at the claimer's position, with {p} replaced by the claimer's
// name, {uuid} by their UUID, {team} by their team's short name and {quest} by the quest's chapter/quest. silent
// hides its output. Only ops may author these in the editor; a pack file can hold any command, so treat pack files
// as trusted.
//   {type: "command", command: "effect give {p} minecraft:speed 60", title: "Speed for a minute", silent: true}
public record CommandReward(String command, Optional<String> label, boolean silent) implements Reward {
    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("command").forGetter(CommandReward::command),
            Codec.STRING.optionalFieldOf("title").forGetter(CommandReward::label),
            Codec.BOOL.optionalFieldOf("silent", false).forGetter(CommandReward::silent))
            .apply(i, CommandReward::new));

    public static final RewardType<CommandReward> TYPE = new RewardType<>() {
        @Override
        public MapCodec<CommandReward> codec() {
            return CODEC;
        }

        @Override
        public boolean claim(RewardContext context, CommandReward reward) {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            String team = QuestTeams.teams().team(context.teamId()).map(Team::shortName).orElse(player.nameAndId().name());
            String line = reward.command.replace("{p}", player.nameAndId().name()).replace("{team}", team)
                    .replace("{uuid}", player.getUUID().toString()).replace("{quest}", context.quest().toString());
            CommandSourceStack source = server.createCommandSourceStack().withLevel(player.level()).withPosition(player.position());
            if (reward.silent) {
                source = source.withSuppressedOutput();
            }
            server.getCommands().performPrefixedCommand(source, line);
            return true;
        }
    };

    @Override
    public RewardType<?> type() {
        return TYPE;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMMAND_BLOCK);
    }

    @Override
    public Component title() {
        return label.map(BookText::component).orElseGet(() -> Component.translatable("hibiscusquests.reward.command"));
    }
}
