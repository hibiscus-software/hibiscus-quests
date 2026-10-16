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

import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.api.reward.RewardContext;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.book.UsesRewardTable;
import software.hibiscus.hibiscusquests.item.LootCrateItem;

// The rewards that use a reward table: random (roll it now) and loot (hand out the table's crates to open later). The
// book shows a preview of the table's entries, with their chances, on hover.
public final class TableRewards {
    private TableRewards() {}

    // The book the server has, or the client's copy: a table's name and icon are needed on both sides.
    private static Optional<RewardTable> findTable(String id) {
        QuestBook book = BookManager.server() != null ? BookManager.book() : ClientBookAccess.book();
        return book.table(id);
    }

    // random: one roll of a reward table (the table's rolls), handed out at once. {type: "random", table: "treasure"}
    public record RandomReward(String table) implements Reward, UsesRewardTable {
        public static final MapCodec<RandomReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("table").forGetter(RandomReward::table))
                .apply(i, RandomReward::new));

        public static final RewardType<RandomReward> TYPE = new RewardType<>() {
            @Override
            public MapCodec<RandomReward> codec() {
                return CODEC;
            }

            @Override
            public boolean claim(RewardContext context, RandomReward reward) {
                Optional<RewardTable> table = BookManager.book().table(reward.table);
                if (table.isEmpty()) {
                    return false;
                }
                for (Reward rolled : table.get().roll(context.player().getRandom())) {
                    Rewards.give(context, rolled);
                }
                return true;
            }
        };

        @Override
        public RewardType<?> type() {
            return TYPE;
        }

        @Override
        public ItemStack icon() {
            return findTable(table).flatMap(RewardTable::icon).map(icon -> icon.item().copy()).filter(stack -> !stack.isEmpty())
                    .orElseGet(() -> new ItemStack(Items.CHEST));
        }

        @Override
        public Component title() {
            Component name = findTable(table).filter(t -> !t.title().isBlank()).map(t -> BookText.component(t.title()))
                    .orElse(Component.literal(table));
            return Component.translatable("hibiscusquests.reward.random", name);
        }
    }

    // loot: count of a table's loot crates, opened later with a right click. {type: "loot", table: "treasure", count: 2}
    public record LootReward(String table, int count) implements Reward, UsesRewardTable {
        public static final MapCodec<LootReward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("table").forGetter(LootReward::table),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(LootReward::count))
                .apply(i, LootReward::new));

        public static final RewardType<LootReward> TYPE = new RewardType<>() {
            @Override
            public MapCodec<LootReward> codec() {
                return CODEC;
            }

            @Override
            public boolean claim(RewardContext context, LootReward reward) {
                Optional<RewardTable> table = BookManager.book().table(reward.table);
                if (table.isEmpty()) {
                    return false;
                }
                context.player().getInventory().placeItemBackInInventory(LootCrateItem.crate(table.get(), reward.count), Prediction.SERVER_ONLY);
                return true;
            }
        };

        @Override
        public RewardType<?> type() {
            return TYPE;
        }

        @Override
        public ItemStack icon() {
            return findTable(table).map(t -> LootCrateItem.crate(t, count)).orElseGet(() -> new ItemStack(Items.CHEST, count));
        }

        @Override
        public Component title() {
            return Component.translatable("hibiscusquests.reward.loot", count, icon().getHoverName());
        }
    }

    // The client's book, reached without loading client classes on a server (set by the client entrypoint).
    public static final class ClientBookAccess {
        private static volatile java.util.function.Supplier<QuestBook> client = () -> QuestBook.EMPTY;

        private ClientBookAccess() {}

        public static void set(java.util.function.Supplier<QuestBook> book) {
            client = book;
        }

        static QuestBook book() {
            return client.get();
        }
    }
}
