/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.ConfirmDialog;
import software.hibiscus.hibiscuslib.client.gui.popup.ContextMenu;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.api.task.TaskTypes;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasImage;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasLabel;
import software.hibiscus.hibiscusquests.book.CanvasElements.QuestLink;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.ChapterInfo;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.client.edit.ClientEditor;
import software.hibiscus.hibiscusquests.client.edit.EditActions;
import software.hibiscus.hibiscusquests.client.edit.EditActions.Element;
import software.hibiscus.hibiscusquests.client.edit.EditActions.ElementKind;
import software.hibiscus.hibiscusquests.client.edit.FormPopup;
import software.hibiscus.hibiscusquests.client.edit.Forms;
import software.hibiscus.hibiscusquests.client.edit.ListEditorPopup;
import software.hibiscus.hibiscusquests.client.edit.PickerPopup;
import software.hibiscus.hibiscusquests.client.edit.TablesPopup;
import software.hibiscus.hibiscusquests.edit.Ids;

// The editor's menus and dialogs in the book: right-click menus on the map (empty space, quests, links, images,
// labels, a selection) and in the chapter list (chapters, groups), the forms they open, and the editor's keys on the
// map (Delete, Ctrl+C/X/V/D/A, Ctrl+Z/Y, the arrow keys). Each action is an EditActions change.
final class EditorMenus {
    private final QuestBookScreen screen;
    private final BookCanvas canvas;

    EditorMenus(QuestBookScreen screen, BookCanvas canvas) {
        this.screen = screen;
        this.canvas = canvas;
    }

    private void open(Popup popup) {
        screen.openPopup(popup);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("hibiscusquests.edit.menu." + key, args);
    }

    private static Icon sprite(Identifier sprite) {
        return Icon.sprite(sprite);
    }

    // ---- the map ----

    void canvasMenu(Chapter chapter, @Nullable Element element, int mx, int my, double gx, double gy) {
        ContextMenu.Builder menu = ContextMenu.builder();
        if (!EditActions.isEditable(chapter.id())) {
            menu.add(t("override"), sprite(QuestDraw.EDIT), () -> EditActions.override(chapter.id()));
            menu.add(t("copy_id"), () -> copyText(chapter.id()));
            open(menu.build(mx, my));
            return;
        }
        Set<Element> selection = canvas.selection();
        if (element == null) {
            menu.add(t("new_quest"), sprite(QuestDraw.ADD), () -> newQuest(chapter.id(), gx, gy));
            menu.add(t("new_link"), sprite(QuestDraw.LINK), () -> newLink(chapter.id(), gx, gy));
            menu.add(t("new_image"), () -> newImage(chapter.id(), gx, gy));
            menu.add(t("new_label"), () -> newLabel(chapter.id(), gx, gy));
            Optional<CompoundTag> clip = clipboard();
            if (clip.isPresent()) {
                menu.add(t("paste"), () -> canvas.select(EditActions.paste(chapter.id(), clip.get(), gx, gy, "paste")));
            } else {
                menu.addDisabled(t("paste"), Icon.NONE);
            }
            menu.separator();
            menu.add(t("edit_chapter"), sprite(QuestDraw.EDIT), () -> editChapter(chapter));
            if (ClientEditor.canUndo()) {
                menu.add(t("undo"), sprite(QuestDraw.UNDO), ClientEditor::undo);
            }
            if (ClientEditor.canRedo()) {
                menu.add(t("redo"), sprite(QuestDraw.REDO), ClientEditor::redo);
            }
        } else if (selection.size() > 1 && selection.contains(element)) {
            List<Element> chosen = List.copyOf(selection);
            menu.add(t("duplicate"), () -> canvas.select(EditActions.duplicate(chapter.id(), chosen)));
            menu.add(t("copy"), () -> copyText(EditActions.copy(chapter.id(), chosen)));
            List<String> quests = chosen.stream().filter(e -> e.kind() == ElementKind.QUEST).map(Element::key).toList();
            if (!quests.isEmpty()) {
                menu.add(t("move_to_chapter"), () -> moveToChapter(chapter.id(), quests));
            }
            menu.separator();
            menu.addDanger(t("delete_selected", chosen.size()), sprite(QuestDraw.DELETE), () -> confirmDelete(chapter.id(), chosen));
        } else {
            switch (element.kind()) {
                case QUEST -> chapter.quest(element.key()).ifPresent(quest -> questMenu(menu, chapter, quest));
                case LINK -> chapter.links().stream().filter(link -> link.id().equals(element.key())).findFirst().ifPresent(link -> {
                    menu.add(t("edit_link"), sprite(QuestDraw.EDIT), () -> editLink(chapter.id(), link));
                    QuestRef.parseRelative(link.quest(), chapter.id()).ifPresent(ref -> menu.add(t("open_quest"), () -> screen.openQuest(ref)));
                    menu.add(t("copy_id"), () -> copyText(link.id()));
                    menu.separator();
                    menu.addDanger(t("delete"), sprite(QuestDraw.DELETE), () -> EditActions.delete(chapter.id(), List.of(element)));
                });
                case IMAGE, LABEL -> {
                    menu.add(t(element.kind() == ElementKind.IMAGE ? "edit_image" : "edit_label"), sprite(QuestDraw.EDIT),
                            () -> edit(chapter, element));
                    menu.add(t("duplicate"), () -> canvas.select(EditActions.duplicate(chapter.id(), List.of(element))));
                    menu.separator();
                    menu.addDanger(t("delete"), sprite(QuestDraw.DELETE), () -> EditActions.delete(chapter.id(), List.of(element)));
                }
            }
        }
        open(menu.build(mx, my));
    }

    private void questMenu(ContextMenu.Builder menu, Chapter chapter, Quest quest) {
        String chapterId = chapter.id();
        menu.add(t("edit_quest"), sprite(QuestDraw.EDIT), () -> editQuest(chapterId, quest.id()));
        menu.add(t("edit_tasks"), () -> open(new ListEditorPopup(ListEditorPopup.Mode.TASKS, chapterId, quest.id())));
        menu.add(t("edit_rewards"), () -> open(new ListEditorPopup(ListEditorPopup.Mode.REWARDS, chapterId, quest.id())));
        menu.add(t("shape"), () -> editShape(chapterId, quest.id()));
        menu.add(t("move_to_chapter"), () -> moveToChapter(chapterId, List.of(quest.id())));
        menu.add(t("copy_id"), () -> copyText(quest.ref().toString()));
        menu.add(t("duplicate"), () -> canvas.select(EditActions.duplicate(chapterId, List.of(Element.quest(quest.id())))));
        menu.separator();
        menu.add(t("reset_progress"), () -> EditActions.changeProgress(quest.ref(), false));
        menu.add(t("complete"), () -> EditActions.changeProgress(quest.ref(), true));
        menu.separator();
        menu.addDanger(t("delete"), sprite(QuestDraw.DELETE), () -> confirmDelete(chapterId, List.of(Element.quest(quest.id()))));
    }

    // Double-clicked image or label: its form.
    void edit(Chapter chapter, Element element) {
        if (!EditActions.isEditable(chapter.id())) {
            return;
        }
        Optional<Chapter> source = EditActions.chapter(chapter.id());
        if (source.isEmpty()) {
            return;
        }
        if (element.kind() == ElementKind.IMAGE && element.index() < source.get().images().size()) {
            CanvasImage image = source.get().images().get(element.index());
            CompoundTag tag = (CompoundTag) EditActions.encodeImages(List.of(image)).getFirst();
            open(new FormPopup(t("edit_image"), Forms.image(), tag, CanvasImage.CODEC, chapter.id(),
                    edited -> EditActions.setImage(chapter.id(), element.index(), edited)));
        } else if (element.kind() == ElementKind.LABEL && element.index() < source.get().labels().size()) {
            CanvasLabel label = source.get().labels().get(element.index());
            CompoundTag tag = (CompoundTag) EditActions.encodeLabels(List.of(label)).getFirst();
            open(new FormPopup(t("edit_label"), Forms.label(), tag, CanvasLabel.CODEC, chapter.id(),
                    edited -> EditActions.setLabel(chapter.id(), element.index(), edited)));
        }
    }

    void editQuest(String chapterId, String questId) {
        EditActions.quest(chapterId, questId).ifPresent(quest -> open(new FormPopup(t("quest_title", quest.ref().toString()), Forms.quest(),
                EditActions.encodeQuest(quest), Quest.CODEC, chapterId, tag -> EditActions.putQuest(chapterId, questId, tag, "edit_quest"))));
    }

    private void editShape(String chapterId, String questId) {
        EditActions.quest(chapterId, questId).ifPresent(quest -> open(new FormPopup(t("shape"), Forms.questShape(),
                EditActions.encodeQuest(quest), Quest.CODEC, chapterId, tag -> EditActions.putQuest(chapterId, questId, tag, "edit_quest"))));
    }

    private void editLink(String chapterId, QuestLink link) {
        open(new FormPopup(t("edit_link"), Forms.link(), EditActions.encodeLink(link), QuestLink.CODEC, chapterId,
                tag -> EditActions.putLink(chapterId, link.id(), tag, "edit_link")));
    }

    // New quest: pick its first task's type, then (for an item task) the item, else the task's form.
    void newQuest(String chapterId, double gx, double gy) {
        List<PickerPopup.Entry> entries = new ArrayList<>();
        for (Identifier type : Forms.taskTypes()) {
            entries.add(new PickerPopup.Entry(type.toString(), Forms.typeName(type, true), Component.literal(type.toString()), Icon.NONE));
        }
        open(PickerPopup.of(t("pick_task_type"), entries, picked -> {
            Identifier type = Identifier.tryParse(picked);
            if (type == null) {
                return;
            }
            String typeText = type.getNamespace().equals(HibiscusQuests.MODID) ? type.getPath() : type.toString();
            if (type.equals(HibiscusQuests.id("item"))) {
                open(PickerPopup.items(t("pick_item"), stack -> {
                    CompoundTag task = new CompoundTag();
                    task.putString("type", "item");
                    task.put("item", FormPopup.encodeStack(stack));
                    task.putString("id", Ids.fromTitle(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), "task", List.of()));
                    String id = EditActions.createQuest(chapterId, gx, gy, stack.getHoverName().getString(), task);
                    canvas.select(List.of(Element.quest(id)));
                }));
                return;
            }
            TaskType<?> taskType = TaskTypes.REGISTRY.getValue(type);
            if (taskType == null) {
                return;
            }
            CompoundTag start = new CompoundTag();
            start.putString("type", typeText);
            start.putString("id", type.getPath());
            open(new FormPopup(Component.translatable("hibiscusquests.list.edit_task", Forms.typeName(type, true)), Forms.task(type, taskType), start,
                    QuestTask.CODEC, chapterId, task -> {
                        String id = EditActions.createQuest(chapterId, gx, gy, "", task);
                        canvas.select(List.of(Element.quest(id)));
                    }));
        }));
    }

    private void newLink(String chapterId, double gx, double gy) {
        List<PickerPopup.Entry> entries = new ArrayList<>();
        for (Quest quest : ClientBook.book().quests()) {
            if (!quest.chapter().equals(chapterId)) {
                entries.add(new PickerPopup.Entry(quest.ref().toString(), BookText.titleOr(quest.title(), quest.id()),
                        Component.literal(quest.ref().toString()), QuestDraw.icon(ClientBook.icon(quest))));
            }
        }
        open(PickerPopup.of(t("pick_quest"), entries, picked -> {
            String id = EditActions.createLink(chapterId, gx, gy, picked);
            canvas.select(List.of(Element.link(id)));
        }));
    }

    private void newImage(String chapterId, double gx, double gy) {
        CompoundTag start = new CompoundTag();
        start.putString("image", "minecraft:painting");
        start.putDouble("x", gx);
        start.putDouble("y", gy);
        start.putDouble("width", 2);
        start.putDouble("height", 2);
        open(new FormPopup(t("new_image"), Forms.image(), start, CanvasImage.CODEC, chapterId, tag -> EditActions.addImage(chapterId, tag)));
    }

    private void newLabel(String chapterId, double gx, double gy) {
        CompoundTag start = new CompoundTag();
        start.putString("text", t("label_text").getString());
        start.putDouble("x", gx);
        start.putDouble("y", gy);
        open(new FormPopup(t("new_label"), Forms.label(), start, CanvasLabel.CODEC, chapterId, tag -> EditActions.addLabel(chapterId, tag)));
    }

    // Moving quests to another chapter starts their progress over (it's kept under their old place): ask first.
    private void moveToChapter(String fromId, List<String> quests) {
        List<PickerPopup.Entry> entries = new ArrayList<>();
        for (Chapter other : ClientBook.book().sourceChapters()) {
            if (!other.id().equals(fromId) && EditActions.isEditable(other.id())) {
                entries.add(new PickerPopup.Entry(other.id(), BookText.titleOr(other.title(), other.id()), Component.literal(other.id()),
                        QuestDraw.icon(ClientBook.icon(other))));
            }
        }
        open(PickerPopup.of(t("pick_chapter"), entries, picked -> open(new ConfirmDialog(t("move_title"), t("move_message", quests.size()),
                confirmed -> {
                    if (confirmed) {
                        EditActions.moveToChapter(fromId, quests, picked);
                        canvas.select(List.of());
                    }
                }))));
    }

    private void confirmDelete(String chapterId, List<Element> elements) {
        long quests = elements.stream().filter(e -> e.kind() == ElementKind.QUEST).count();
        Runnable delete = () -> {
            EditActions.delete(chapterId, elements);
            canvas.select(List.of());
        };
        if (quests == 0 && elements.size() == 1) {
            delete.run();
            return;
        }
        open(new ConfirmDialog(t("delete_title"), t("delete_message", elements.size()), confirmed -> {
            if (confirmed) {
                delete.run();
            }
        }).dangerous());
    }

    // ---- the editor's keys on the map ----

    boolean keyPressed(Chapter chapter, KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();
        if (ctrl && key == InputConstants.KEY_Z) {
            if (event.hasShiftDown()) {
                ClientEditor.redo();
            } else {
                ClientEditor.undo();
            }
            return true;
        }
        if (ctrl && key == InputConstants.KEY_Y) {
            ClientEditor.redo();
            return true;
        }
        boolean editable = EditActions.isEditable(chapter.id());
        List<Element> selection = List.copyOf(canvas.selection());
        if (ctrl && key == InputConstants.KEY_A) {
            canvas.selectAll();
            return true;
        }
        if (ctrl && key == InputConstants.KEY_C && !selection.isEmpty()) {
            copyText(EditActions.copy(chapter.id(), selection));
            return true;
        }
        if (!editable) {
            return false;
        }
        if (ctrl && key == InputConstants.KEY_X && !selection.isEmpty()) {
            copyText(EditActions.copy(chapter.id(), selection));
            EditActions.delete(chapter.id(), selection);
            canvas.select(List.of());
            return true;
        }
        if (ctrl && key == InputConstants.KEY_V) {
            Optional<CompoundTag> clip = clipboard();
            if (clip.isPresent()) {
                double[] at = canvas.gridAtMouse();
                canvas.select(EditActions.paste(chapter.id(), clip.get(), at[0], at[1], "paste"));
            }
            return true;
        }
        if (ctrl && key == InputConstants.KEY_D && !selection.isEmpty()) {
            canvas.select(EditActions.duplicate(chapter.id(), selection));
            return true;
        }
        if ((key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) && !selection.isEmpty()) {
            confirmDelete(chapter.id(), selection);
            return true;
        }
        if (!selection.isEmpty() && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
                || key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN)) {
            double step = event.hasShiftDown() ? 1.0 : 0.5;
            double dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0;
            double dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0;
            EditActions.move(chapter.id(), selection, dx, dy);
            canvas.movedBy(dx, dy);
            return true;
        }
        if (event.isEscape() && !selection.isEmpty()) {
            canvas.select(List.of());
            return true;
        }
        return false;
    }

    // ---- the chapter list ----

    void chapterMenu(Chapter chapter, int mx, int my) {
        ContextMenu.Builder menu = ContextMenu.builder();
        String id = chapter.id();
        if (!EditActions.isEditable(id)) {
            menu.add(t("override"), sprite(QuestDraw.EDIT), () -> EditActions.override(id));
            menu.add(t("copy_id"), () -> copyText(id));
            open(menu.build(mx, my));
            return;
        }
        menu.add(t("edit_chapter"), sprite(QuestDraw.EDIT), () -> editChapter(chapter));
        menu.add(t("move_up"), sprite(QuestDraw.UP), () -> EditActions.stepChapter(id, -1));
        menu.add(t("move_down"), sprite(QuestDraw.DOWN), () -> EditActions.stepChapter(id, 1));
        menu.add(t("move_to_group"), () -> moveToGroup(chapter));
        menu.add(t("copy_id"), () -> copyText(id));
        menu.separator();
        menu.addDanger(t("delete_chapter"), sprite(QuestDraw.DELETE), () -> open(new ConfirmDialog(t("delete_chapter_title"),
                t("delete_chapter_message", BookText.titleOr(chapter.title(), id)), confirmed -> {
                    if (confirmed) {
                        EditActions.deleteChapter(id);
                    }
                }).dangerous().requireTyping(id)));
        open(menu.build(mx, my));
    }

    void groupMenu(ChapterGroup group, int mx, int my) {
        ContextMenu.Builder menu = ContextMenu.builder();
        menu.add(t("edit_group"), sprite(QuestDraw.EDIT), () -> open(new FormPopup(t("edit_group"), Forms.group(), EditActions.encodeGroup(group),
                ChapterGroup.CODEC, "", tag -> EditActions.saveGroup(group.id(), tag))));
        menu.add(t("new_chapter_here"), sprite(QuestDraw.ADD), () -> newChapter(group.id()));
        menu.add(t("move_up"), sprite(QuestDraw.UP), () -> EditActions.stepGroup(group.id(), -1));
        menu.add(t("move_down"), sprite(QuestDraw.DOWN), () -> EditActions.stepGroup(group.id(), 1));
        menu.add(t("copy_id"), () -> copyText(group.id()));
        menu.separator();
        menu.addDanger(t("delete_group"), sprite(QuestDraw.DELETE), () -> open(new ConfirmDialog(t("delete_group_title"),
                t("delete_group_message", BookText.titleOr(group.title(), group.id())), confirmed -> {
                    if (confirmed) {
                        EditActions.deleteGroup(group.id());
                    }
                }).dangerous()));
        open(menu.build(mx, my));
    }

    private List<String> groupIds() {
        return ClientBook.book().groups().stream().map(ChapterGroup::id).collect(Collectors.toList());
    }

    void editChapter(Chapter chapter) {
        open(new FormPopup(t("chapter_title", chapter.id()), Forms.chapter(groupIds()), EditActions.encodeInfo(chapter.info()),
                ChapterInfo.MAP_CODEC.codec(), chapter.id(), tag -> EditActions.putInfo(chapter.id(), tag, "edit_chapter")));
    }

    void newChapter(String group) {
        CompoundTag start = new CompoundTag();
        start.putString("title", t("new_chapter_title").getString());
        if (!group.isEmpty()) {
            start.putString("group", group);
        }
        open(new FormPopup(t("new_chapter"), Forms.chapter(groupIds()), start, ChapterInfo.MAP_CODEC.codec(), "",
                tag -> screen.showChapter(EditActions.createChapter(tag))));
    }

    void newGroup() {
        CompoundTag start = new CompoundTag();
        start.putString("id", "new_group");
        start.putString("title", t("new_group_title").getString());
        open(new FormPopup(t("new_group"), Forms.group(), start, ChapterGroup.CODEC, "", EditActions::createGroup));
    }

    private void moveToGroup(Chapter chapter) {
        List<PickerPopup.Entry> entries = new ArrayList<>();
        entries.add(new PickerPopup.Entry("", t("no_group"), null, Icon.NONE));
        for (ChapterGroup group : ClientBook.book().groups()) {
            entries.add(new PickerPopup.Entry(group.id(), BookText.titleOr(group.title(), group.id()), Component.literal(group.id()),
                    group.icon().map(QuestDraw::icon).orElse(Icon.NONE)));
        }
        open(PickerPopup.of(t("pick_group"), entries, picked -> EditActions.placeChapter(chapter.id(), null, picked)));
    }

    void settings() {
        BookSettings settings = ClientBook.book().settings();
        open(new FormPopup(t("settings"), Forms.settings(), EditActions.encodeSettings(settings), BookSettings.CODEC, "", EditActions::putSettings));
    }

    void tables() {
        open(new TablesPopup());
    }

    // ---- the clipboard ----

    private static void copyText(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private static Optional<CompoundTag> clipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        return text.isBlank() ? Optional.empty() : EditActions.readClipboard(text);
    }
}
