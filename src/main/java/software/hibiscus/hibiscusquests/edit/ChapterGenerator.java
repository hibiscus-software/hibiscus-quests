/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscusquests.book.BookManager;

// /hibiscusquests generate_chapter: a chapter with one quest per kind of item, each with an item task for as many as
// there were, laid out on a square-ish grid. The edits go through BookEditor like the book's own.
public final class ChapterGenerator {
    private static final double SPACING = 1.5;

    private ChapterGenerator() {}

    public static List<BookEdit> edits(MinecraftServer server, String chapterId, String title, List<ItemStack> stacks) {
        List<BookEdit> edits = new ArrayList<>();
        CompoundTag info = new CompoundTag();
        info.putString("title", title);
        edits.add(new BookEdit.ChapterInfo(chapterId, Optional.of(info)));
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(stacks.size())));
        Set<String> taken = new HashSet<>();
        for (int n = 0; n < stacks.size(); n++) {
            ItemStack stack = stacks.get(n);
            String name = stack.getHoverName().getString();
            String id = Ids.fromTitle(name, "quest", taken);
            taken.add(id);
            CompoundTag task = new CompoundTag();
            task.putString("type", "item");
            task.put("item", item(server, stack));
            task.putLong("count", stack.getCount());
            ListTag tasks = new ListTag();
            tasks.add(task);
            CompoundTag quest = new CompoundTag();
            quest.putString("title", name);
            quest.putDouble("x", (n % columns) * SPACING);
            quest.putDouble("y", (n / columns) * SPACING);
            quest.put("tasks", tasks);
            edits.add(new BookEdit.Quest(chapterId, id, Optional.of(quest)));
        }
        return edits;
    }

    // An item as an item task writes it: its id, or the whole stack (one of it) when it has components.
    private static Tag item(MinecraftServer server, ItemStack stack) {
        if (stack.getComponentsPatch().isEmpty()) {
            return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ItemStack.CODEC.encodeStart(BookManager.ops(server), stack.copyWithCount(1)).getOrThrow();
    }
}
