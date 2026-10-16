/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.Fluid;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.ListWidget;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscuslib.client.gui.widget.TextInput;
import software.hibiscus.hibiscusquests.api.editor.FieldHint.Kind;

// A searchable list to pick one thing from: an item, a registry id or tag, a task or reward type, a quest, a chapter
// or a reward table. Type to filter, double-click or Choose to pick. Registries come from the server (ClientEditor
// asks once), so the list may show "Loading" for a moment.
public final class PickerPopup extends Popup {
    // One thing in the list: the value picked, what's shown, and a muted second line.
    public record Entry(String value, Component label, @Nullable Component detail, Icon icon) {}

    // An extra button beside Choose (the item in your hand).
    public record Extra(Component label, Runnable action) {}

    private final Component title;
    private final Consumer<String> onPick;
    private @Nullable List<Entry> entries;
    private @Nullable Extra extra;
    private String query = "";
    private @Nullable Entry selected;
    private @Nullable ListWidget<Entry> list;

    public PickerPopup(Component title, @Nullable List<Entry> entries, Consumer<String> onPick) {
        super(width(), height());
        this.title = title;
        this.entries = entries == null ? null : List.copyOf(entries);
        this.onPick = onPick;
    }

    private static int width() {
        return Math.min(Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16, 300);
    }

    private static int height() {
        return Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 16, 260);
    }

    public PickerPopup extra(Component label, Runnable action) {
        this.extra = new Extra(label, action);
        return this;
    }

    // Fills in a list that was loading.
    public void setEntries(List<Entry> loaded) {
        this.entries = List.copyOf(loaded);
        filter();
    }

    @Override
    protected void init() {
        int x = getX() + 6;
        int innerWidth = getWidth() - 12;
        TextInput search = new TextInput(x, getY() + 20, innerWidth, 18, Component.translatable("hibiscusquests.picker.search"));
        search.placeholder(Component.translatable("hibiscusquests.picker.search_hint"));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            filter();
        });
        add(search);
        list = new ListWidget<>(x, getY() + 42, innerWidth, getHeight() - 42 - 30,
                new ListWidget.RowRenderer<>(Entry::icon, Entry::label, Entry::detail));
        list.onSelect(entry -> selected = entry).onActivate(this::pick);
        list.emptyText(Component.translatable(entries == null ? "hibiscusquests.picker.loading" : "hibiscusquests.picker.none"));
        add(list);
        filter();
        int buttonY = getY() + getHeight() - 24;
        add(new TextButton(x, buttonY, 64, 18, Component.translatable("gui.cancel"), button -> cancel()));
        TextButton choose = new TextButton(getX() + getWidth() - 6 - 70, buttonY, 70, 18, Component.translatable("hibiscusquests.picker.choose"),
                button -> {
                    if (selected != null) {
                        pick(selected);
                    }
                }).accent();
        add(choose);
        if (extra != null) {
            Extra button = extra;
            int width = Math.min(innerWidth - 150, Minecraft.getInstance().font.width(button.label()) + 12);
            add(new TextButton(getX() + getWidth() - 6 - 70 - 4 - width, buttonY, width, 18, button.label(), pressed -> {
                close();
                button.action().run();
            }));
        }
        setFocused(search);
        search.setFocused(true);
    }

    private void filter() {
        if (list == null) {
            return;
        }
        if (entries == null) {
            list.setRows(List.of());
            return;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<Entry> shown = new ArrayList<>();
        for (Entry entry : entries) {
            if (needle.isEmpty() || entry.value().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(entry);
            }
        }
        list.setRows(shown);
        list.emptyText(Component.translatable("hibiscusquests.picker.none"));
        if (selected != null && shown.contains(selected)) {
            list.select(selected);
        }
    }

    private void pick(Entry entry) {
        close();
        onPick.accept(entry.value());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        graphics.text(Minecraft.getInstance().font, title, getX() + 6, getY() + 7, HibiscusTheme.current().text(), false);
    }

    // ---- the usual lists ----

    // Every item, with its icon; Use held item picks the stack in the player's hand (components and all).
    public static PickerPopup items(Component title, Consumer<ItemStack> onPick) {
        List<Entry> entries = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            entries.add(new Entry(id.toString(), stack.getHoverName(), Component.literal(id.toString()), Icon.item(stack)));
        }
        PickerPopup popup = new PickerPopup(title, entries, value -> {
            Identifier id = Identifier.tryParse(value);
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> onPick.accept(new ItemStack(item)));
            }
        });
        return popup.extra(Component.translatable("hibiscusquests.picker.held_item"), () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                onPick.accept(player.getMainHandItem().copy());
            }
        });
    }

    // A registry's ids, tags or both (as the field writes them: tags with # for ID_OR_TAG, without for TAG).
    public static PickerPopup registry(Component title, ResourceKey<? extends Registry<?>> registry, Kind kind, Consumer<String> onPick) {
        PickerPopup popup = new PickerPopup(title, null, onPick);
        ClientEditor.registry(registry.identifier().toString(), data -> {
            List<Entry> entries = new ArrayList<>();
            if (kind != Kind.TAG) {
                for (String id : data.ids()) {
                    entries.add(new Entry(id, Component.literal(id), null, icon(registry, id)));
                }
            }
            if (kind != Kind.ID) {
                for (String tag : data.tags()) {
                    String value = kind == Kind.TAG ? tag : "#" + tag;
                    entries.add(new Entry(value, Component.literal("#" + tag), Component.translatable("hibiscusquests.picker.tag"), Icon.NONE));
                }
            }
            popup.setEntries(entries);
        });
        return popup;
    }

    // An item to show beside a registry id, where there's an obvious one.
    private static Icon icon(ResourceKey<? extends Registry<?>> registry, String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            return Icon.NONE;
        }
        if (registry.equals(Registries.ITEM)) {
            return BuiltInRegistries.ITEM.getOptional(id).map(item -> Icon.item(new ItemStack(item))).orElse(Icon.NONE);
        }
        if (registry.equals(Registries.BLOCK)) {
            return BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.asItem())
                    .filter(item -> item != Items.AIR).map(item -> Icon.item(new ItemStack(item))).orElse(Icon.NONE);
        }
        if (registry.equals(Registries.ENTITY_TYPE)) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(id).flatMap(PickerPopup::spawnEgg)
                    .map(item -> Icon.item(new ItemStack(item))).orElse(Icon.NONE);
        }
        if (registry.equals(Registries.FLUID)) {
            return BuiltInRegistries.FLUID.getOptional(id).map(Fluid::getBucket)
                    .filter(item -> item != Items.AIR).map(item -> Icon.item(new ItemStack(item))).orElse(Icon.NONE);
        }
        return Icon.NONE;
    }

    private static Optional<Item> spawnEgg(EntityType<?> type) {
        return SpawnEggItem.byId(type).map(holder -> holder.value());
    }

    // Fixed values with labels (types, quests, chapters, tables).
    public static PickerPopup of(Component title, List<Entry> entries, Consumer<String> onPick) {
        return new PickerPopup(title, entries, onPick);
    }
}
