/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.IconButton;
import software.hibiscus.hibiscuslib.client.gui.widget.Panel;
import software.hibiscus.hibiscuslib.client.gui.widget.ScrollPanel;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.editor.CustomEditor;
import software.hibiscus.hibiscusquests.api.editor.CustomEditors;
import software.hibiscus.hibiscusquests.api.reward.RewardType;
import software.hibiscus.hibiscusquests.api.reward.RewardTypes;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.api.task.TaskTypes;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestReward;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.edit.Ids;

// Edits a list inside one object: a quest's tasks or rewards, or a reward table's entries. Each row has its icon and
// title, buttons to move it up or down, edit it (the type's form, or its custom editor) and delete it; Add picks a
// type (and for items, the item) first. Every change is saved at once, as one undoable edit of the quest or table.
public final class ListEditorPopup extends Popup {
    public enum Mode { TASKS, REWARDS, ENTRIES }

    private static final int ROW = 22;
    // Types that can't go in a reward table (they roll tables, or need someone to choose).
    private static final Set<Identifier> NOT_IN_TABLES = Set.of(HibiscusQuests.id("random"), HibiscusQuests.id("loot"), HibiscusQuests.id("choice"));

    private final Mode mode;
    private final String chapterId;
    private final String ownerId;
    private double scroll;
    private @Nullable ScrollPanel body;

    public ListEditorPopup(Mode mode, String chapterId, String ownerId) {
        super(Math.min(Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16, 360),
                Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 16, 280));
        this.mode = mode;
        this.chapterId = chapterId;
        this.ownerId = ownerId;
    }

    // ---- the list and its owner ----

    private String key() {
        return switch (mode) {
            case TASKS -> "tasks";
            case REWARDS -> "rewards";
            case ENTRIES -> "entries";
        };
    }

    private Optional<CompoundTag> ownerTag() {
        if (mode == Mode.ENTRIES) {
            return ClientBook.book().table(ownerId).map(EditActions::encodeTable);
        }
        return EditActions.quest(chapterId, ownerId).map(EditActions::encodeQuest);
    }

    private ListTag list() {
        return ownerTag().map(tag -> tag.getListOrEmpty(key())).orElseGet(ListTag::new);
    }

    private void save(ListTag list, String action) {
        Optional<CompoundTag> owner = ownerTag();
        if (owner.isEmpty()) {
            return;
        }
        CompoundTag tag = owner.get().copy();
        if (list.isEmpty()) {
            tag.remove(key());
        } else {
            tag.put(key(), list);
        }
        if (mode == Mode.ENTRIES) {
            EditActions.putTable(ownerId, tag, action);
        } else {
            EditActions.putQuest(chapterId, ownerId, tag, action);
        }
    }

    // The rows: icon and title of each element, from the decoded owner.
    private record Row(Icon icon, Component title) {}

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (mode == Mode.ENTRIES) {
            ClientBook.book().table(ownerId).ifPresent(table -> {
                for (RewardTable.Entry entry : table.entries()) {
                    Component title = Component.translatable("hibiscusquests.list.entry", String.format("%.1f%%", table.chance(entry) * 100),
                            entry.reward().title());
                    rows.add(new Row(itemIcon(entry.reward().icon()), title));
                }
            });
            return rows;
        }
        Optional<Quest> quest = EditActions.quest(chapterId, ownerId);
        if (quest.isEmpty()) {
            return rows;
        }
        if (mode == Mode.TASKS) {
            for (QuestTask task : quest.get().tasks()) {
                Component title = task.title().map(BookText::component).orElseGet(() -> task.task().title());
                rows.add(new Row(QuestDraw.icon(ClientBook.icon(task)), title));
            }
        } else {
            for (QuestReward reward : quest.get().rewards()) {
                rows.add(new Row(itemIcon(reward.reward().icon()), reward.reward().title()));
            }
        }
        return rows;
    }

    private static Icon itemIcon(ItemStack stack) {
        return stack.isEmpty() ? Icon.sprite(QuestDraw.TABLE) : Icon.item(stack);
    }

    // ---- layout ----

    @Override
    protected void init() {
        if (body != null) {
            scroll = body.scrollAmount();
        }
        if (ownerTag().isEmpty()) {
            Minecraft.getInstance().execute(this::close);
            return;
        }
        body = new ScrollPanel(getX() + 4, getY() + 20, getWidth() - 8, getHeight() - 20 - 30, Panel.Style.INSET);
        add(body);
        int width = body.contentWidth() - 4;
        List<Row> rows = rows();
        int size = list().size();
        int y = 2;
        for (int n = 0; n < rows.size() && n < size; n++) {
            int index = n;
            Row row = rows.get(n);
            body.add(new IconButton(0, 0, 18, row.icon(), row.title(), button -> edit(index)).flat(), 2, y);
            body.add(new Caption(width - 20 - 4 * 19 - 4, row.title(), Caption.Style.LABEL), 24, y + 5);
            int x = width - 4 * 19 + 2;
            IconButton up = new IconButton(0, 0, 18, Icon.sprite(QuestDraw.UP), Component.translatable("hibiscusquests.list.up"), button -> swap(index, index - 1));
            up.active = index > 0;
            body.add(up, x, y);
            IconButton down = new IconButton(0, 0, 18, Icon.sprite(QuestDraw.DOWN), Component.translatable("hibiscusquests.list.down"), button -> swap(index, index + 1));
            down.active = index < size - 1;
            body.add(down, x + 19, y);
            body.add(new IconButton(0, 0, 18, Icon.sprite(QuestDraw.EDIT), Component.translatable("hibiscusquests.list.edit"), button -> edit(index)), x + 38, y);
            body.add(new IconButton(0, 0, 18, Icon.sprite(QuestDraw.DELETE), Component.translatable("hibiscusquests.list.delete"), button -> remove(index)),
                    x + 57, y);
            y += ROW;
        }
        if (rows.isEmpty()) {
            body.add(new Caption(width, Component.translatable("hibiscusquests.list.empty"), Caption.Style.MUTED), 4, 6);
        }
        body.setScrollAmount(scroll);
        int buttonY = getY() + getHeight() - 24;
        add(new TextButton(getX() + 6, buttonY, 90, 18, Component.translatable("hibiscusquests.list.add"), button -> add()).icon(Icon.sprite(QuestDraw.ADD)));
        add(new TextButton(getX() + 100, buttonY, 90, 18, Component.translatable("hibiscusquests.list.add_item"), button -> addItem())
                .icon(Icon.sprite(QuestDraw.PICK)));
        add(new TextButton(getX() + getWidth() - 66, buttonY, 60, 18, Component.translatable("gui.done"), button -> close()).accent());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        Component title = Component.translatable("hibiscusquests.list.title." + key(), ownerId);
        graphics.text(Minecraft.getInstance().font, title, getX() + 6, getY() + 7, HibiscusTheme.current().text(), false);
    }

    // ---- actions ----

    private void swap(int from, int to) {
        ListTag list = list();
        if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) {
            return;
        }
        Tag moving = list.remove(from);
        list.add(to, moving);
        save(list, "reorder");
    }

    private void remove(int index) {
        ListTag list = list();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(list, "delete");
        }
    }

    private void edit(int index) {
        ListTag list = list();
        if (index < 0 || index >= list.size() || !(list.get(index) instanceof CompoundTag element)) {
            return;
        }
        openEditor(element, edited -> {
            ListTag current = list();
            if (index < current.size()) {
                current.set(index, edited);
                save(current, "edit_" + (mode == Mode.TASKS ? "task" : "reward"));
            }
        });
    }

    private void append(CompoundTag element) {
        ListTag list = list();
        if (mode != Mode.ENTRIES && !element.contains("id")) {
            Set<String> taken = new HashSet<>();
            list.forEach(tag -> tag.asCompound().ifPresent(c -> taken.add(c.getStringOr("id", ""))));
            String base = typeId(element).map(Identifier::getPath).orElse(mode == Mode.TASKS ? "task" : "reward");
            element.putString("id", Ids.fromTitle(base, mode == Mode.TASKS ? "task" : "reward", taken));
        }
        list.add(element);
        save(list, mode == Mode.TASKS ? "new_task" : "new_reward");
    }

    // Add: pick a type, then fill in its form (items go straight to the item picker).
    private void add() {
        boolean tasks = mode == Mode.TASKS;
        List<PickerPopup.Entry> entries = new ArrayList<>();
        for (Identifier type : tasks ? Forms.taskTypes() : Forms.rewardTypes()) {
            if (mode == Mode.ENTRIES && NOT_IN_TABLES.contains(type)) {
                continue;
            }
            entries.add(new PickerPopup.Entry(type.toString(), Forms.typeName(type, tasks), Component.literal(type.toString()), Icon.NONE));
        }
        open(PickerPopup.of(Component.translatable(tasks ? "hibiscusquests.list.pick_task_type" : "hibiscusquests.list.pick_reward_type"), entries,
                picked -> {
                    Identifier type = Identifier.tryParse(picked);
                    if (type == null) {
                        return;
                    }
                    if (type.equals(HibiscusQuests.id("item"))) {
                        addItem();
                        return;
                    }
                    CompoundTag element = new CompoundTag();
                    element.putString("type", typeText(type));
                    if (mode == Mode.ENTRIES) {
                        element.putInt("weight", 1);
                    }
                    openEditor(element, this::append);
                }));
    }

    // An item task or reward (or table entry) for an item picked from the list or held.
    private void addItem() {
        open(PickerPopup.items(Component.translatable("hibiscusquests.list.pick_item"), stack -> {
            CompoundTag element = new CompoundTag();
            element.putString("type", "item");
            element.put("item", FormPopup.encodeStack(stack));
            if (mode == Mode.ENTRIES) {
                element.putInt("weight", 1);
            }
            append(element);
        }));
    }

    // The form for one element, or its type's custom editor.
    private void openEditor(CompoundTag element, Consumer<CompoundTag> onSave) {
        Optional<Identifier> type = typeId(element);
        if (type.isEmpty()) {
            return;
        }
        boolean tasks = mode == Mode.TASKS;
        Optional<CustomEditor> custom = tasks ? CustomEditors.task(type.get()) : CustomEditors.reward(type.get());
        Codec<?> codec = switch (mode) {
            case TASKS -> QuestTask.CODEC;
            case REWARDS -> QuestReward.CODEC;
            case ENTRIES -> RewardTable.Entry.CODEC;
        };
        if (custom.isPresent()) {
            custom.get().open(element.copy(), edited -> onSave.accept(edited));
            return;
        }
        List<Forms.Field> fields;
        if (tasks) {
            TaskType<?> taskType = TaskTypes.REGISTRY.getValue(type.get());
            if (taskType == null) {
                return;
            }
            fields = Forms.task(type.get(), taskType);
        } else {
            RewardType<?> rewardType = RewardTypes.REGISTRY.getValue(type.get());
            if (rewardType == null) {
                return;
            }
            fields = Forms.reward(type.get(), rewardType, mode == Mode.ENTRIES);
        }
        Component title = Component.translatable(tasks ? "hibiscusquests.list.edit_task" : "hibiscusquests.list.edit_reward", Forms.typeName(type.get(), tasks));
        open(new FormPopup(title, fields, element, codec, chapterId, onSave));
    }

    private static Optional<Identifier> typeId(CompoundTag element) {
        String text = element.getStringOr("type", "");
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(text.indexOf(':') < 0 ? Identifier.tryBuild(HibiscusQuests.MODID, text) : Identifier.tryParse(text));
    }

    private static String typeText(Identifier type) {
        return type.getNamespace().equals(HibiscusQuests.MODID) ? type.getPath() : type.toString();
    }

    private void open(Popup popup) {
        if (screen() != null) {
            screen().openPopup(popup);
        }
    }
}
