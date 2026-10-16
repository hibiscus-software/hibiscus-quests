/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.config.QuestsConfig;

// Loot crates dropped by mobs a player kills: each reward table with a loot_crate gives its crate with the chance its
// drop section sets for the kind of mob (bosses, by the c:bosses tag; hostile mobs; the rest, passive). Off when the
// server config's lootCrateDrops or the book's drop_loot_crates is false.
public final class LootDrops {
    private LootDrops() {}

    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer) || victim instanceof ServerPlayer) {
            return;
        }
        QuestBook book = BookManager.book();
        if (!QuestsConfig.get(QuestsConfig.LOOT_CRATE_DROPS) || !book.settings().dropLootCrates()) {
            return;
        }
        for (RewardTable table : book.tables()) {
            if (table.lootCrate().isEmpty()) {
                continue;
            }
            RewardTable.Drops drops = table.lootCrate().get().drops();
            double chance = victim.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES) ? drops.boss()
                    : victim instanceof Enemy || victim.getType().getCategory() == MobCategory.MONSTER ? drops.hostile()
                    : drops.passive();
            if (chance > 0 && victim.getRandom().nextDouble() < chance) {
                event.getDrops().add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), LootCrateItem.crate(table, 1)));
            }
        }
    }
}
