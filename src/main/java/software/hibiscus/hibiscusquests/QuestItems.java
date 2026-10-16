/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import software.hibiscus.hibiscusquests.item.LootCrateItem;
import software.hibiscus.hibiscusquests.item.QuestBookItem;

// Hibiscus Quests' items (the Quest Book, the loot crate), the loot crate's data component (which reward table it
// opens) and the player attachment that remembers the Quest Book was handed out on first join.
public final class QuestItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HibiscusQuests.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HibiscusQuests.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HibiscusQuests.MODID);

    // The reward table a loot crate opens.
    public static final Supplier<DataComponentType<String>> LOOT_TABLE = COMPONENTS.registerComponentType("loot_table",
            builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredItem<QuestBookItem> QUEST_BOOK = ITEMS.registerItem("quest_book", QuestBookItem::new,
            properties -> properties.stacksTo(1));
    public static final DeferredItem<LootCrateItem> LOOT_CRATE = ITEMS.registerItem("loot_crate", LootCrateItem::new);

    // Whether the player has been given the Quest Book on joining (giveBookOnFirstJoin).
    public static final Supplier<AttachmentType<Boolean>> BOOK_GIVEN = ATTACHMENTS.register("book_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("given")).copyOnDeath().build());

    private QuestItems() {}

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(QUEST_BOOK.get());
            }
        });
    }

    public static Item lootCrate() {
        return LOOT_CRATE.get();
    }
}
