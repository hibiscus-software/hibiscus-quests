/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import software.hibiscus.hibiscusquests.api.QuestsApi;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.config.QuestsConfig;

// item: the team gets N matching items. Matching is by item (a stack with components must match them too), by tag, or
// by an item predicate, or several of these at once (all must match). In detect mode (the default) holding them is
// enough: the inventory is checked every itemScanInterval ticks, and only counted when it has changed. In consume
// mode the Submit button takes them. only_from_crafting counts items as they're crafted instead.
//   {type: "item", item: "minecraft:iron_ingot", count: 16L}
//   {type: "item", tag: "c:ingots/steel", count: 4L, mode: "consume"}
public record ItemTask(Optional<ItemStack> item, Optional<TagKey<Item>> tag, Optional<ItemPredicate> predicate, long count,
        Mode mode, boolean onlyFromCrafting) implements Task {
    public enum Mode implements StringRepresentable {
        DETECT("detect"),
        CONSUME("consume");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // An item id, or a whole stack ({id: ..., components: {...}}).
    static final Codec<ItemStack> ITEM = Codec.either(Identifier.CODEC, ItemStack.CODEC).comapFlatMap(
            either -> either.map(
                    id -> BuiltInRegistries.ITEM.getOptional(id).map(item -> DataResult.success(new ItemStack(item)))
                            .orElseGet(() -> DataResult.error(() -> "Unknown item " + id)),
                    DataResult::success),
            stack -> stack.getCount() == 1 && stack.getComponentsPatch().isEmpty()
                    ? Either.left(BuiltInRegistries.ITEM.getKey(stack.getItem())) : Either.right(stack));

    public static final MapCodec<ItemTask> CODEC = RecordCodecBuilder.<ItemTask>mapCodec(i -> i.group(
            ITEM.optionalFieldOf("item").forGetter(ItemTask::item),
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(ItemTask::tag),
            ItemPredicate.CODEC.optionalFieldOf("predicate").forGetter(ItemTask::predicate),
            Codec.LONG.validate(n -> n > 0 ? DataResult.success(n) : DataResult.error(() -> "count must be at least 1"))
                    .optionalFieldOf("count", 1L).forGetter(ItemTask::count),
            Mode.CODEC.optionalFieldOf("mode", Mode.DETECT).forGetter(ItemTask::mode),
            Codec.BOOL.optionalFieldOf("only_from_crafting", false).forGetter(ItemTask::onlyFromCrafting))
            .apply(i, ItemTask::new))
            .validate(task -> task.item.isPresent() || task.tag.isPresent() || task.predicate.isPresent()
                    ? DataResult.success(task) : DataResult.error(() -> "An item task needs an item, a tag or a predicate"));

    public static final Type TYPE = new Type();

    public ItemTask {
        item = item.map(ItemStack::copy);
    }

    @Override
    public TaskType<?> type() {
        return TYPE;
    }

    @Override
    public long target() {
        return count;
    }

    // Detected items count while the quest is locked, so having them already completes it on unlock.
    @Override
    public boolean countsWhileLocked() {
        return mode == Mode.DETECT && !onlyFromCrafting;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (item.isPresent()) {
            ItemStack wanted = item.get();
            boolean same = wanted.getComponentsPatch().isEmpty() ? ItemStack.isSameItem(stack, wanted) : ItemStack.isSameItemSameComponents(stack, wanted);
            if (!same) {
                return false;
            }
        }
        if (tag.isPresent() && !stack.is(tag.get())) {
            return false;
        }
        return predicate.isEmpty() || predicate.get().test(stack);
    }

    @Override
    public ItemStack icon() {
        if (item.isPresent()) {
            return item.get().copyWithCount(1);
        }
        if (tag.isPresent()) {
            Iterator<Holder<Item>> items = BuiltInRegistries.ITEM.getTagOrEmpty(tag.get()).iterator();
            if (items.hasNext()) {
                return new ItemStack(items.next());
            }
        }
        return predicate.flatMap(ItemPredicate::items).flatMap(set -> set.stream().findFirst()).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public Component title() {
        Component name;
        if (item.isPresent()) {
            name = item.get().getHoverName();
        } else if (tag.isPresent()) {
            Identifier id = tag.get().location();
            name = Component.translatableWithFallback("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'), humanize(id.getPath()));
        } else {
            name = icon().isEmpty() ? Component.translatable("hibiscusquests.task.item.any") : icon().getHoverName();
        }
        String key = onlyFromCrafting ? "hibiscusquests.task.item.craft" : mode == Mode.CONSUME ? "hibiscusquests.task.item.submit" : "hibiscusquests.task.item";
        return Component.translatable(key, count, name);
    }

    // A tag's path as words, for tags without a translation: "logs" is "Logs", "ingots/steel" is "Steel Ingots".
    static String humanize(String path) {
        String[] parts = path.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            for (String word : parts[i].split("_")) {
                if (!word.isEmpty()) {
                    if (!out.isEmpty()) {
                        out.append(' ');
                    }
                    out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }
        }
        return out.toString();
    }

    // How many matching items the player holds (main inventory, armour and off hand).
    public long countIn(Inventory inventory) {
        long held = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack)) {
                held += stack.getCount();
            }
        }
        return held;
    }

    // Tracks item tasks: polling for detect, Submit for consume, crafting for only_from_crafting.
    public static final class Type implements TaskType<ItemTask> {
        // The inventory's fingerprint at each task's last check, so an unchanged inventory isn't counted again.
        private final Map<ActiveTask<ItemTask>, Long> lastChecked = new IdentityHashMap<>();
        // Each player's fingerprint this tick, so several detect tasks share one look at the inventory.
        private final Map<ServerPlayer, long[]> fingerprints = new WeakHashMap<>();

        private Type() {}

        @Override
        public MapCodec<ItemTask> codec() {
            return CODEC;
        }

        @Override
        public int pollInterval() {
            return QuestsConfig.get(QuestsConfig.ITEM_SCAN_INTERVAL);
        }

        @Override
        public void poll(ActiveTask<ItemTask> task) {
            ItemTask item = task.task();
            if (item.mode != Mode.DETECT || item.onlyFromCrafting) {
                return;
            }
            Inventory inventory = task.player().getInventory();
            long fingerprint = fingerprint(task.player());
            Long last = lastChecked.put(task, fingerprint);
            if (last != null && last == fingerprint) {
                return;
            }
            long held = Math.min(item.count, item.countIn(inventory));
            if (held > task.progress()) {
                task.setProgress(held);
            }
        }

        @Override
        public void onActivated(ActiveTask<ItemTask> task) {
            poll(task);
        }

        // A hash of what's in every slot. Vanilla's own change counter only moves for changes made through a menu, so
        // it misses items added straight to the inventory (pickups, other mods, commands); this doesn't.
        private long fingerprint(ServerPlayer player) {
            long tick = player.level().getGameTime();
            long[] cached = fingerprints.get(player);
            if (cached != null && cached[0] == tick) {
                return cached[1];
            }
            Inventory inventory = player.getInventory();
            long hash = 1;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                hash = hash * 31 + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 31L + stack.getCount());
            }
            fingerprints.put(player, new long[] {tick, hash});
            return hash;
        }

        @Override
        public void onDeactivated(ActiveTask<ItemTask> task) {
            lastChecked.remove(task);
        }

        @Override
        public boolean canSubmit(ItemTask task) {
            return task.mode == Mode.CONSUME && !task.onlyFromCrafting;
        }

        // Takes as many matching items as the task still needs.
        @Override
        public void submit(ActiveTask<ItemTask> task) {
            ItemTask item = task.task();
            long needed = item.count - task.progress();
            Inventory inventory = task.player().getInventory();
            long taken = 0;
            for (int slot = 0; slot < inventory.getContainerSize() && taken < needed; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (item.matches(stack)) {
                    int take = (int) Math.min(stack.getCount(), needed - taken);
                    inventory.removeItem(slot, take);
                    taken += take;
                }
            }
            if (taken > 0) {
                task.addProgress(taken);
            }
        }

        public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !event.getCrafting().isEmpty()) {
                ItemStack crafted = event.getCrafting();
                for (ActiveTask<ItemTask> task : QuestsApi.get().activeTasks(player, this)) {
                    if (task.task().onlyFromCrafting && task.task().matches(crafted)) {
                        task.addProgress(crafted.getCount());
                    }
                }
            }
        }
    }
}
