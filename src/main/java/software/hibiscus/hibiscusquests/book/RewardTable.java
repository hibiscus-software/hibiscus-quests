/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;
import software.hibiscus.hibiscusquests.api.reward.Reward;
import software.hibiscus.hibiscusquests.reward.ChoiceReward;

// A reward table: weighted entries (any reward but random, loot and choice), rolled rolls times by random
// rewards and loot crates. empty_weight is the weight of rolling nothing. With a loot_crate, the table also has a crate
// item (named and coloured as given) that mobs drop now and then and that's opened with a right click. One file per
// table, reward_tables/<id>.snbt:
//   {title: "Treasure", rolls: 1, entries: [{weight: 10, type: "item", item: "minecraft:diamond"}, {weight: 30, type: "xp", amount: 50}],
//    loot_crate: {name: "Treasure Crate", color: "#e8b04b", drop: {hostile: 0.01, boss: 0.5}}}
public record RewardTable(String id, String title, Optional<BookIcon> icon, int rolls, int emptyWeight, List<Entry> entries,
        Optional<LootCrate> lootCrate, Optional<String> source) {
    // One weighted reward.
    public record Entry(int weight, Reward reward) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.<Entry>mapCodec(i -> i.group(
                Codec.intRange(1, 1_000_000).optionalFieldOf("weight", 1).forGetter(Entry::weight),
                BookCodecs.REWARD.forGetter(Entry::reward))
                .apply(i, Entry::new)).validate(Entry::check).codec();

        // A table rolls rewards to hand out there and then: not ones that roll tables themselves (a table could roll
        // itself forever) or that need someone to choose.
        private static DataResult<Entry> check(Entry entry) {
            return entry.reward() instanceof UsesRewardTable || entry.reward() instanceof ChoiceReward
                    ? DataResult.error(() -> "A reward table can't hold random, loot or choice rewards")
                    : DataResult.success(entry);
        }
    }

    // How likely a mob of each kind is to drop the table's crate when a player kills it (0 to 1).
    public record Drops(double passive, double hostile, double boss) {
        public static final Drops NONE = new Drops(0, 0, 0);
        public static final Codec<Drops> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1).optionalFieldOf("passive", 0.0).forGetter(Drops::passive),
                Codec.doubleRange(0, 1).optionalFieldOf("hostile", 0.0).forGetter(Drops::hostile),
                Codec.doubleRange(0, 1).optionalFieldOf("boss", 0.0).forGetter(Drops::boss))
                .apply(i, Drops::new));
    }

    // The table's crate item.
    public record LootCrate(String name, int color, Drops drops) {
        public static final Codec<LootCrate> CODEC = RecordCodecBuilder.create(i -> i.group(
                BookCodecs.text("name").forGetter(LootCrate::name),
                BookCodecs.COLOR.optionalFieldOf("color", 0xE8B04B).forGetter(LootCrate::color),
                Drops.CODEC.optionalFieldOf("drop", Drops.NONE).forGetter(LootCrate::drops))
                .apply(i, LootCrate::new));
    }

    // The file form. id may be left out (the file name is used).
    public static final Codec<RewardTable> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(RewardTable::id),
            BookCodecs.text("title").forGetter(RewardTable::title),
            BookIcon.CODEC.optionalFieldOf("icon").forGetter(RewardTable::icon),
            Codec.intRange(1, 64).optionalFieldOf("rolls", 1).forGetter(RewardTable::rolls),
            Codec.intRange(0, 1_000_000).optionalFieldOf("empty_weight", 0).forGetter(RewardTable::emptyWeight),
            BookCodecs.list("entries", Entry.CODEC).forGetter(RewardTable::entries),
            LootCrate.CODEC.optionalFieldOf("loot_crate").forGetter(RewardTable::lootCrate))
            .apply(i, (id, title, icon, rolls, empty, entries, crate) -> new RewardTable(id, title, icon, rolls, empty, entries, crate, Optional.empty())));

    // The network form, which keeps the source.
    public static final Codec<RewardTable> NETWORK_CODEC = RecordCodecBuilder.create(i -> i.group(
            CODEC.fieldOf("table").forGetter(table -> table),
            Codec.STRING.optionalFieldOf("source").forGetter(RewardTable::source))
            .apply(i, (table, source) -> table.withSource(source)));

    public RewardTable {
        entries = List.copyOf(entries);
    }

    public RewardTable withId(String newId, Optional<String> newSource) {
        return new RewardTable(newId, title, icon, rolls, emptyWeight, entries, lootCrate, newSource);
    }

    public RewardTable withSource(Optional<String> newSource) {
        return new RewardTable(id, title, icon, rolls, emptyWeight, entries, lootCrate, newSource);
    }

    public int totalWeight() {
        int total = emptyWeight;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    // The chance of one entry on a single roll, 0 to 1.
    public double chance(Entry entry) {
        int total = totalWeight();
        return total == 0 ? 0 : (double) entry.weight() / total;
    }

    // Rolls the table: rolls times, each roll picking one entry by weight (or nothing, by empty_weight).
    public List<Reward> roll(RandomSource random) {
        List<Reward> rolled = new ArrayList<>();
        int total = totalWeight();
        if (total <= 0) {
            return rolled;
        }
        for (int n = 0; n < rolls; n++) {
            int pick = random.nextInt(total);
            for (Entry entry : entries) {
                pick -= entry.weight();
                if (pick < 0) {
                    rolled.add(entry.reward());
                    break;
                }
            }
        }
        return rolled;
    }
}
