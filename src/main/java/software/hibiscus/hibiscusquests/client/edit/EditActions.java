/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasImage;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasLabel;
import software.hibiscus.hibiscusquests.book.CanvasElements.QuestLink;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.ChapterInfo;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.ClientLang;
import software.hibiscus.hibiscusquests.edit.BookEdit;
import software.hibiscus.hibiscusquests.edit.Ids;

// Everything the editor can do, as edits for the server and the edits that undo them, worked out from the client's
// copy of the book (the chapters as written, not as cleaned: see QuestBook.sourceChapters). Each action is one
// undoable change in ClientEditor's history.
public final class EditActions {
    private static final String CLIPBOARD_MARK = "hibiscusquests_clipboard";

    // Something on a chapter's canvas the editor selects and moves: a quest or quest link (by id), or an image or label
    // (by its place in the chapter's list; they have no ids).
    public enum ElementKind { QUEST, LINK, IMAGE, LABEL }

    public record Element(ElementKind kind, String key) {
        public static Element quest(String id) {
            return new Element(ElementKind.QUEST, id);
        }

        public static Element link(String id) {
            return new Element(ElementKind.LINK, id);
        }

        public static Element image(int index) {
            return new Element(ElementKind.IMAGE, Integer.toString(index));
        }

        public static Element label(int index) {
            return new Element(ElementKind.LABEL, Integer.toString(index));
        }

        public int index() {
            return Integer.parseInt(key);
        }
    }

    // The edits of one change: forward, and back (applied in reverse order to undo).
    private static final class Change {
        private final List<BookEdit> forward = new ArrayList<>();
        private final List<BookEdit> back = new ArrayList<>();

        void add(BookEdit edit, BookEdit undo) {
            forward.add(edit);
            back.addFirst(undo);
        }

        void submit(String action) {
            // Translations a form changed go in the same change (see withLang).
            forward.addAll(PENDING_LANG);
            back.addAll(0, PENDING_LANG_BACK);
            PENDING_LANG.clear();
            PENDING_LANG_BACK.clear();
            ClientEditor.submit(Component.translatable("hibiscusquests.edit.action." + action), forward, back);
        }
    }

    // Translations to send with the next change: a form that edits translated text saves through withLang.
    private static final List<BookEdit> PENDING_LANG = new ArrayList<>();
    private static final List<BookEdit> PENDING_LANG_BACK = new ArrayList<>();

    // Saves an object together with the translations its form changed (key to text, in the locale the editor shows),
    // as one undoable change. A save that doesn't edit anything else still sends them.
    public static void withLang(Map<String, String> changes, Runnable save) {
        String locale = ClientLang.locale();
        PENDING_LANG.clear();
        PENDING_LANG_BACK.clear();
        changes.forEach((key, text) -> {
            PENDING_LANG.add(new BookEdit.Lang(locale, key, text.isEmpty() ? Optional.empty() : Optional.of(text)));
            PENDING_LANG_BACK.add(new BookEdit.Lang(locale, key, book().translation(locale, key)));
        });
        save.run();
        if (!PENDING_LANG.isEmpty()) {
            new Change().submit("translation");
        }
    }

    // Whether a key is one of the pack's translations (in any locale).
    public static boolean isPackKey(String key) {
        return book().lang().values().stream().anyMatch(entries -> entries.containsKey(key));
    }

    private EditActions() {}

    // ---- the book as the editor sees it ----

    private static QuestBook book() {
        return ClientBook.book();
    }

    public static Optional<Chapter> chapter(String id) {
        return book().sourceChapter(id);
    }

    // The pack's own chapters can be edited; a mod's must be overridden first.
    public static boolean isEditable(String chapterId) {
        return chapter(chapterId).map(chapter -> chapter.source().isEmpty()).orElse(false);
    }

    public static Optional<Quest> quest(String chapterId, String id) {
        return chapter(chapterId).flatMap(chapter -> chapter.quest(id));
    }

    private static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(ClientEditor.ops(), value).getOrThrow();
    }

    public static CompoundTag encodeQuest(Quest quest) {
        return (CompoundTag) encode(Quest.CODEC, quest);
    }

    public static CompoundTag encodeLink(QuestLink link) {
        return (CompoundTag) encode(QuestLink.CODEC, link);
    }

    public static ListTag encodeImages(List<CanvasImage> images) {
        return (ListTag) encode(CanvasImage.CODEC.listOf(), images);
    }

    public static ListTag encodeLabels(List<CanvasLabel> labels) {
        return (ListTag) encode(CanvasLabel.CODEC.listOf(), labels);
    }

    public static CompoundTag encodeInfo(ChapterInfo info) {
        return (CompoundTag) encode(ChapterInfo.MAP_CODEC.codec(), info);
    }

    public static CompoundTag encodeTable(RewardTable table) {
        CompoundTag tag = (CompoundTag) encode(RewardTable.CODEC, table);
        tag.remove("id");
        return tag;
    }

    public static CompoundTag encodeSettings(BookSettings settings) {
        return (CompoundTag) encode(BookSettings.CODEC, settings);
    }

    public static CompoundTag encodeGroup(ChapterGroup group) {
        return (CompoundTag) encode(ChapterGroup.CODEC, group);
    }

    private static ListTag encodeGroups(List<ChapterGroup> groups) {
        return (ListTag) encode(ChapterGroup.CODEC.listOf(), groups);
    }

    // A dependency as a quest in fromChapter writes it: the id alone in the same chapter, else chapter/quest.
    public static String refText(QuestRef target, String fromChapter) {
        return target.toRelativeString(fromChapter);
    }

    private static Set<String> questIds(String chapterId) {
        Set<String> ids = new HashSet<>();
        chapter(chapterId).ifPresent(chapter -> chapter.quests().forEach(quest -> ids.add(quest.id())));
        return ids;
    }

    // ---- single objects (the forms save through these) ----

    public static void putQuest(String chapterId, String id, @Nullable CompoundTag tag, String action) {
        Change change = new Change();
        Optional<CompoundTag> old = quest(chapterId, id).map(EditActions::encodeQuest);
        change.add(new BookEdit.Quest(chapterId, id, Optional.ofNullable(tag)), new BookEdit.Quest(chapterId, id, old));
        change.submit(action);
    }

    public static void putLink(String chapterId, String id, @Nullable CompoundTag tag, String action) {
        Change change = new Change();
        Optional<CompoundTag> old = chapter(chapterId).flatMap(chapter -> chapter.links().stream().filter(link -> link.id().equals(id)).findFirst())
                .map(EditActions::encodeLink);
        change.add(new BookEdit.Link(chapterId, id, Optional.ofNullable(tag)), new BookEdit.Link(chapterId, id, old));
        change.submit(action);
    }

    public static void putImages(String chapterId, ListTag images, String action) {
        chapter(chapterId).ifPresent(chapter -> {
            Change change = new Change();
            change.add(new BookEdit.Images(chapterId, images), new BookEdit.Images(chapterId, encodeImages(chapter.images())));
            change.submit(action);
        });
    }

    public static void putLabels(String chapterId, ListTag labels, String action) {
        chapter(chapterId).ifPresent(chapter -> {
            Change change = new Change();
            change.add(new BookEdit.Labels(chapterId, labels), new BookEdit.Labels(chapterId, encodeLabels(chapter.labels())));
            change.submit(action);
        });
    }

    public static void putInfo(String chapterId, CompoundTag info, String action) {
        Change change = new Change();
        Optional<CompoundTag> old = chapter(chapterId).map(chapter -> encodeInfo(chapter.info()));
        change.add(new BookEdit.ChapterInfo(chapterId, Optional.of(info)), new BookEdit.ChapterInfo(chapterId, old));
        change.submit(action);
    }

    public static void putSettings(CompoundTag settings) {
        Change change = new Change();
        change.add(new BookEdit.Settings(settings), new BookEdit.Settings(encodeSettings(book().settings())));
        change.submit("settings");
    }

    public static void putTable(String id, @Nullable CompoundTag tag, String action) {
        Change change = new Change();
        Optional<CompoundTag> old = book().table(id).map(EditActions::encodeTable);
        change.add(new BookEdit.Table(id, Optional.ofNullable(tag)), new BookEdit.Table(id, old));
        change.submit(action);
    }

    // Copies a mod's chapter into the pack's files so it can be edited. Undoing it deletes the copy (the mod's
    // chapter shows again).
    public static void override(String chapterId) {
        Change change = new Change();
        change.add(new BookEdit.Override(chapterId), new BookEdit.ChapterInfo(chapterId, Optional.empty()));
        change.submit("override");
    }

    // ---- quests on the canvas ----

    // A new quest at a grid point with one task (and a title, when there's an obvious one). Returns its id.
    public static String createQuest(String chapterId, double x, double y, String title, CompoundTag task) {
        String base = title.isBlank() ? task.getStringOr("type", "quest").replace(':', '_') : title;
        String id = Ids.fromTitle(base, "quest", questIds(chapterId));
        CompoundTag quest = new CompoundTag();
        if (!title.isBlank()) {
            quest.putString("title", title);
        }
        quest.putDouble("x", x);
        quest.putDouble("y", y);
        ListTag tasks = new ListTag();
        tasks.add(task);
        quest.put("tasks", tasks);
        Change change = new Change();
        change.add(new BookEdit.Quest(chapterId, id, Optional.of(quest)), new BookEdit.Quest(chapterId, id, Optional.empty()));
        change.submit("new_quest");
        return id;
    }

    public static String createLink(String chapterId, double x, double y, String quest) {
        Set<String> taken = new HashSet<>();
        chapter(chapterId).ifPresent(chapter -> chapter.links().forEach(link -> taken.add(link.id())));
        String id = Ids.fromTitle("link_" + quest.replace('/', '_'), "link", taken);
        CompoundTag link = new CompoundTag();
        link.putString("quest", quest);
        link.putDouble("x", x);
        link.putDouble("y", y);
        Change change = new Change();
        change.add(new BookEdit.Link(chapterId, id, Optional.of(link)), new BookEdit.Link(chapterId, id, Optional.empty()));
        change.submit("new_link");
        return id;
    }

    public static void addImage(String chapterId, CompoundTag image) {
        chapter(chapterId).ifPresent(chapter -> {
            ListTag images = encodeImages(chapter.images());
            images.add(image);
            putImages(chapterId, images, "new_image");
        });
    }

    public static void addLabel(String chapterId, CompoundTag label) {
        chapter(chapterId).ifPresent(chapter -> {
            ListTag labels = encodeLabels(chapter.labels());
            labels.add(label);
            putLabels(chapterId, labels, "new_label");
        });
    }

    public static void setImage(String chapterId, int index, @Nullable CompoundTag image) {
        chapter(chapterId).ifPresent(chapter -> {
            ListTag images = encodeImages(chapter.images());
            if (index < 0 || index >= images.size()) {
                return;
            }
            if (image == null) {
                images.remove(index);
            } else {
                images.set(index, image);
            }
            putImages(chapterId, images, image == null ? "delete" : "edit_image");
        });
    }

    public static void setLabel(String chapterId, int index, @Nullable CompoundTag label) {
        chapter(chapterId).ifPresent(chapter -> {
            ListTag labels = encodeLabels(chapter.labels());
            if (index < 0 || index >= labels.size()) {
                return;
            }
            if (label == null) {
                labels.remove(index);
            } else {
                labels.set(index, label);
            }
            putLabels(chapterId, labels, label == null ? "delete" : "edit_label");
        });
    }

    // Moves elements by whole grid steps (dx, dy), in one change.
    public static void move(String chapterId, Collection<Element> elements, double dx, double dy) {
        Optional<Chapter> found = chapter(chapterId);
        if (found.isEmpty() || elements.isEmpty() || (dx == 0 && dy == 0)) {
            return;
        }
        Chapter chapter = found.get();
        Change change = new Change();
        ListTag images = encodeImages(chapter.images());
        ListTag labels = encodeLabels(chapter.labels());
        boolean imagesMoved = false;
        boolean labelsMoved = false;
        for (Element element : elements) {
            switch (element.kind()) {
                case QUEST -> chapter.quest(element.key()).ifPresent(quest -> {
                    CompoundTag old = encodeQuest(quest);
                    CompoundTag moved = old.copy();
                    moved.putDouble("x", quest.display().x() + dx);
                    moved.putDouble("y", quest.display().y() + dy);
                    change.add(new BookEdit.Quest(chapterId, quest.id(), Optional.of(moved)), new BookEdit.Quest(chapterId, quest.id(), Optional.of(old)));
                });
                case LINK -> chapter.links().stream().filter(link -> link.id().equals(element.key())).findFirst().ifPresent(link -> {
                    CompoundTag old = encodeLink(link);
                    CompoundTag moved = old.copy();
                    moved.putDouble("x", link.x() + dx);
                    moved.putDouble("y", link.y() + dy);
                    change.add(new BookEdit.Link(chapterId, link.id(), Optional.of(moved)), new BookEdit.Link(chapterId, link.id(), Optional.of(old)));
                });
                case IMAGE -> imagesMoved |= shift(images, element.index(), dx, dy);
                case LABEL -> labelsMoved |= shift(labels, element.index(), dx, dy);
            }
        }
        if (imagesMoved) {
            change.add(new BookEdit.Images(chapterId, images), new BookEdit.Images(chapterId, encodeImages(chapter.images())));
        }
        if (labelsMoved) {
            change.add(new BookEdit.Labels(chapterId, labels), new BookEdit.Labels(chapterId, encodeLabels(chapter.labels())));
        }
        change.submit("move");
    }

    private static boolean shift(ListTag list, int index, double dx, double dy) {
        if (index < 0 || index >= list.size() || !(list.get(index) instanceof CompoundTag entry)) {
            return false;
        }
        entry.putDouble("x", entry.getDoubleOr("x", 0) + dx);
        entry.putDouble("y", entry.getDoubleOr("y", 0) + dy);
        return true;
    }

    // Deletes elements, and the dependencies on the quests deleted (in the pack's chapters), in one change.
    public static void delete(String chapterId, Collection<Element> elements) {
        Optional<Chapter> found = chapter(chapterId);
        if (found.isEmpty() || elements.isEmpty()) {
            return;
        }
        Chapter chapter = found.get();
        Change change = new Change();
        Set<QuestRef> gone = new HashSet<>();
        Set<Integer> imagesGone = new HashSet<>();
        Set<Integer> labelsGone = new HashSet<>();
        for (Element element : elements) {
            switch (element.kind()) {
                case QUEST -> chapter.quest(element.key()).ifPresent(quest -> {
                    gone.add(quest.ref());
                    change.add(new BookEdit.Quest(chapterId, quest.id(), Optional.empty()),
                            new BookEdit.Quest(chapterId, quest.id(), Optional.of(encodeQuest(quest))));
                });
                case LINK -> chapter.links().stream().filter(link -> link.id().equals(element.key())).findFirst().ifPresent(link ->
                        change.add(new BookEdit.Link(chapterId, link.id(), Optional.empty()),
                                new BookEdit.Link(chapterId, link.id(), Optional.of(encodeLink(link)))));
                case IMAGE -> imagesGone.add(element.index());
                case LABEL -> labelsGone.add(element.index());
            }
        }
        if (!imagesGone.isEmpty()) {
            ListTag images = new ListTag();
            ListTag all = encodeImages(chapter.images());
            for (int n = 0; n < all.size(); n++) {
                if (!imagesGone.contains(n)) {
                    images.add(all.get(n));
                }
            }
            change.add(new BookEdit.Images(chapterId, images), new BookEdit.Images(chapterId, all));
        }
        if (!labelsGone.isEmpty()) {
            ListTag labels = new ListTag();
            ListTag all = encodeLabels(chapter.labels());
            for (int n = 0; n < all.size(); n++) {
                if (!labelsGone.contains(n)) {
                    labels.add(all.get(n));
                }
            }
            change.add(new BookEdit.Labels(chapterId, labels), new BookEdit.Labels(chapterId, all));
        }
        if (!gone.isEmpty()) {
            for (Chapter other : book().sourceChapters()) {
                if (other.source().isPresent()) {
                    continue;
                }
                for (Quest quest : other.quests()) {
                    if (gone.contains(quest.ref())) {
                        continue;
                    }
                    List<String> kept = quest.rules().dependencies().stream()
                            .filter(text -> QuestRef.parseRelative(text, other.id()).map(ref -> !gone.contains(ref)).orElse(true)).toList();
                    if (kept.size() != quest.rules().dependencies().size()) {
                        CompoundTag old = encodeQuest(quest);
                        CompoundTag updated = withDependencies(old, kept);
                        change.add(new BookEdit.Quest(other.id(), quest.id(), Optional.of(updated)),
                                new BookEdit.Quest(other.id(), quest.id(), Optional.of(old)));
                    }
                }
            }
        }
        change.submit("delete");
    }

    private static CompoundTag withDependencies(CompoundTag quest, List<String> dependencies) {
        CompoundTag updated = quest.copy();
        if (dependencies.isEmpty()) {
            updated.remove("dependencies");
        } else {
            ListTag list = new ListTag();
            dependencies.forEach(dependency -> list.add(StringTag.valueOf(dependency)));
            updated.put("dependencies", list);
        }
        return updated;
    }

    // Adds target as a dependency of the quest, or takes it away if it's one already.
    public static void toggleDependency(String chapterId, String questId, QuestRef target) {
        Optional<Quest> found = quest(chapterId, questId);
        if (found.isEmpty() || found.get().ref().equals(target)) {
            return;
        }
        Quest quest = found.get();
        List<String> dependencies = new ArrayList<>();
        boolean removed = false;
        for (String text : quest.rules().dependencies()) {
            if (QuestRef.parseRelative(text, chapterId).filter(target::equals).isPresent()) {
                removed = true;
            } else {
                dependencies.add(text);
            }
        }
        if (!removed) {
            dependencies.add(refText(target, chapterId));
        }
        CompoundTag old = encodeQuest(quest);
        Change change = new Change();
        change.add(new BookEdit.Quest(chapterId, questId, Optional.of(withDependencies(old, dependencies))),
                new BookEdit.Quest(chapterId, questId, Optional.of(old)));
        change.submit(removed ? "remove_dependency" : "add_dependency");
    }

    // ---- copy and paste ----

    // The elements as clipboard text (SNBT the editor can paste into any chapter, in this world or another one).
    public static String copy(String chapterId, Collection<Element> elements) {
        CompoundTag clip = new CompoundTag();
        clip.putBoolean(CLIPBOARD_MARK, true);
        clip.putString("chapter", chapterId);
        Optional<Chapter> found = chapter(chapterId);
        if (found.isEmpty()) {
            return clip.toString();
        }
        Chapter chapter = found.get();
        ListTag quests = new ListTag();
        ListTag links = new ListTag();
        ListTag images = new ListTag();
        ListTag labels = new ListTag();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        ListTag allImages = encodeImages(chapter.images());
        ListTag allLabels = encodeLabels(chapter.labels());
        for (Element element : elements) {
            switch (element.kind()) {
                case QUEST -> {
                    Optional<Quest> quest = chapter.quest(element.key());
                    if (quest.isPresent()) {
                        quests.add(encodeQuest(quest.get()));
                        minX = Math.min(minX, quest.get().display().x());
                        minY = Math.min(minY, quest.get().display().y());
                    }
                }
                case LINK -> {
                    Optional<QuestLink> link = chapter.links().stream().filter(l -> l.id().equals(element.key())).findFirst();
                    if (link.isPresent()) {
                        links.add(encodeLink(link.get()));
                        minX = Math.min(minX, link.get().x());
                        minY = Math.min(minY, link.get().y());
                    }
                }
                case IMAGE -> {
                    if (element.index() < allImages.size()) {
                        CompoundTag image = (CompoundTag) allImages.get(element.index());
                        images.add(image);
                        minX = Math.min(minX, image.getDoubleOr("x", 0));
                        minY = Math.min(minY, image.getDoubleOr("y", 0));
                    }
                }
                case LABEL -> {
                    if (element.index() < allLabels.size()) {
                        CompoundTag label = (CompoundTag) allLabels.get(element.index());
                        labels.add(label);
                        minX = Math.min(minX, label.getDoubleOr("x", 0));
                        minY = Math.min(minY, label.getDoubleOr("y", 0));
                    }
                }
            }
        }
        clip.put("quests", quests);
        clip.put("quest_links", links);
        clip.put("images", images);
        clip.put("labels", labels);
        clip.putDouble("x", minX == Double.MAX_VALUE ? 0 : minX);
        clip.putDouble("y", minY == Double.MAX_VALUE ? 0 : minY);
        return clip.toString();
    }

    // Whether text is something the editor copied.
    public static Optional<CompoundTag> readClipboard(String text) {
        try {
            CompoundTag clip = TagParser.parseCompoundFully(text.strip());
            return clip.getBooleanOr(CLIPBOARD_MARK, false) ? Optional.of(clip) : Optional.empty();
        } catch (CommandSyntaxException e) {
            return Optional.empty();
        }
    }

    // Pastes copied elements with their top-left at (x, y). Pasted quests get new ids where theirs are taken, and
    // dependencies between them follow; other dependencies keep pointing where they did. Returns the new elements.
    public static List<Element> paste(String chapterId, CompoundTag clip, double x, double y, String action) {
        Optional<Chapter> found = chapter(chapterId);
        if (found.isEmpty()) {
            return List.of();
        }
        Chapter chapter = found.get();
        String from = clip.getStringOr("chapter", chapterId);
        double dx = x - clip.getDoubleOr("x", 0);
        double dy = y - clip.getDoubleOr("y", 0);
        Change change = new Change();
        List<Element> pasted = new ArrayList<>();
        Set<String> taken = questIds(chapterId);
        Map<String, String> renamed = new LinkedHashMap<>();
        List<CompoundTag> quests = new ArrayList<>();
        for (Tag entry : clip.getListOrEmpty("quests")) {
            if (entry instanceof CompoundTag quest) {
                String oldId = quest.getStringOr("id", "quest");
                String newId = Ids.fromTitle(oldId, "quest", taken);
                taken.add(newId);
                renamed.put(oldId, newId);
                quests.add(quest);
            }
        }
        for (CompoundTag quest : quests) {
            String oldId = quest.getStringOr("id", "quest");
            String newId = renamed.get(oldId);
            CompoundTag copy = quest.copy();
            copy.putString("id", newId);
            copy.putDouble("x", quest.getDoubleOr("x", 0) + dx);
            copy.putDouble("y", quest.getDoubleOr("y", 0) + dy);
            List<String> dependencies = new ArrayList<>();
            for (Tag dependency : quest.getListOrEmpty("dependencies")) {
                String text = dependency.asString().orElse("");
                Optional<QuestRef> ref = QuestRef.parseRelative(text, from);
                if (ref.isEmpty()) {
                    continue;
                }
                if (ref.get().chapter().equals(from) && renamed.containsKey(ref.get().quest())) {
                    dependencies.add(renamed.get(ref.get().quest()));
                } else {
                    dependencies.add(refText(ref.get(), chapterId));
                }
            }
            copy = withDependencies(copy, dependencies);
            change.add(new BookEdit.Quest(chapterId, newId, Optional.of(copy)), new BookEdit.Quest(chapterId, newId, Optional.empty()));
            pasted.add(Element.quest(newId));
        }
        Set<String> linkIds = new HashSet<>();
        chapter.links().forEach(link -> linkIds.add(link.id()));
        for (Tag entry : clip.getListOrEmpty("quest_links")) {
            if (entry instanceof CompoundTag link) {
                String newId = Ids.fromTitle(link.getStringOr("id", "link"), "link", linkIds);
                linkIds.add(newId);
                CompoundTag copy = link.copy();
                copy.putString("id", newId);
                copy.putDouble("x", link.getDoubleOr("x", 0) + dx);
                copy.putDouble("y", link.getDoubleOr("y", 0) + dy);
                QuestRef.parseRelative(link.getStringOr("quest", ""), from).ifPresent(ref -> copy.putString("quest", refText(ref, chapterId)));
                change.add(new BookEdit.Link(chapterId, newId, Optional.of(copy)), new BookEdit.Link(chapterId, newId, Optional.empty()));
                pasted.add(Element.link(newId));
            }
        }
        ListTag images = encodeImages(chapter.images());
        ListTag labels = encodeLabels(chapter.labels());
        boolean imagesAdded = false;
        boolean labelsAdded = false;
        for (Tag entry : clip.getListOrEmpty("images")) {
            if (entry instanceof CompoundTag image) {
                CompoundTag copy = image.copy();
                copy.putDouble("x", image.getDoubleOr("x", 0) + dx);
                copy.putDouble("y", image.getDoubleOr("y", 0) + dy);
                pasted.add(Element.image(images.size()));
                images.add(copy);
                imagesAdded = true;
            }
        }
        for (Tag entry : clip.getListOrEmpty("labels")) {
            if (entry instanceof CompoundTag label) {
                CompoundTag copy = label.copy();
                copy.putDouble("x", label.getDoubleOr("x", 0) + dx);
                copy.putDouble("y", label.getDoubleOr("y", 0) + dy);
                pasted.add(Element.label(labels.size()));
                labels.add(copy);
                labelsAdded = true;
            }
        }
        if (imagesAdded) {
            change.add(new BookEdit.Images(chapterId, images), new BookEdit.Images(chapterId, encodeImages(chapter.images())));
        }
        if (labelsAdded) {
            change.add(new BookEdit.Labels(chapterId, labels), new BookEdit.Labels(chapterId, encodeLabels(chapter.labels())));
        }
        change.submit(action);
        return pasted;
    }

    public static List<Element> duplicate(String chapterId, Collection<Element> elements) {
        Optional<CompoundTag> clip = readClipboard(copy(chapterId, elements));
        if (clip.isEmpty()) {
            return List.of();
        }
        return paste(chapterId, clip.get(), clip.get().getDoubleOr("x", 0) + 1, clip.get().getDoubleOr("y", 0) + 1, "duplicate");
    }

    // Moves quests to another chapter (under new ids where theirs are taken there), and points every dependency on
    // them in the pack's chapters at their new place. Their progress stays under their old place.
    public static void moveToChapter(String fromId, Collection<String> questIds, String toId) {
        Optional<Chapter> from = chapter(fromId);
        Optional<Chapter> to = chapter(toId);
        if (from.isEmpty() || to.isEmpty() || fromId.equals(toId)) {
            return;
        }
        Set<String> taken = questIds(toId);
        Map<QuestRef, QuestRef> moved = new HashMap<>();
        for (String id : questIds) {
            if (from.get().quest(id).isPresent()) {
                String newId = Ids.fromTitle(id, "quest", taken);
                taken.add(newId);
                moved.put(new QuestRef(fromId, id), new QuestRef(toId, newId));
            }
        }
        Change change = new Change();
        for (Map.Entry<QuestRef, QuestRef> entry : moved.entrySet()) {
            Quest quest = from.get().quest(entry.getKey().quest()).orElseThrow();
            CompoundTag old = encodeQuest(quest);
            CompoundTag copy = old.copy();
            copy.putString("id", entry.getValue().quest());
            List<String> dependencies = new ArrayList<>();
            for (QuestRef dependency : quest.dependencies()) {
                dependencies.add(refText(moved.getOrDefault(dependency, dependency), toId));
            }
            change.add(new BookEdit.Quest(fromId, quest.id(), Optional.empty()), new BookEdit.Quest(fromId, quest.id(), Optional.of(old)));
            change.add(new BookEdit.Quest(toId, entry.getValue().quest(), Optional.of(withDependencies(copy, dependencies))),
                    new BookEdit.Quest(toId, entry.getValue().quest(), Optional.empty()));
        }
        for (Chapter other : book().sourceChapters()) {
            if (other.source().isPresent()) {
                continue;
            }
            for (Quest quest : other.quests()) {
                if (moved.containsKey(quest.ref())) {
                    continue;
                }
                List<String> dependencies = new ArrayList<>();
                boolean changed = false;
                for (String text : quest.rules().dependencies()) {
                    Optional<QuestRef> ref = QuestRef.parseRelative(text, other.id());
                    if (ref.isPresent() && moved.containsKey(ref.get())) {
                        dependencies.add(refText(moved.get(ref.get()), other.id()));
                        changed = true;
                    } else {
                        dependencies.add(text);
                    }
                }
                if (changed) {
                    CompoundTag old = encodeQuest(quest);
                    change.add(new BookEdit.Quest(other.id(), quest.id(), Optional.of(withDependencies(old, dependencies))),
                            new BookEdit.Quest(other.id(), quest.id(), Optional.of(old)));
                }
            }
        }
        change.submit("move_to_chapter");
    }

    // ---- chapters and groups ----

    // A new, empty chapter from its form (its id from its title), last in its group unless the form says where.
    // Returns its id.
    public static String createChapter(CompoundTag info) {
        String group = info.getStringOr("group", "");
        Set<String> taken = new HashSet<>();
        int order = 0;
        for (Chapter chapter : book().sourceChapters()) {
            taken.add(chapter.id());
            if (chapter.info().group().equals(group)) {
                order = Math.max(order, chapter.info().order() + 10);
            }
        }
        String id = Ids.fromTitle(info.getStringOr("title", ""), "chapter", taken);
        CompoundTag tag = info.copy();
        if (!tag.contains("order") && order != 0) {
            tag.putInt("order", order);
        }
        Change change = new Change();
        change.add(new BookEdit.ChapterInfo(id, Optional.of(tag)), new BookEdit.ChapterInfo(id, Optional.empty()));
        change.submit("new_chapter");
        return id;
    }

    // Deletes a chapter (its file goes to the trash). Undo writes it back whole.
    public static void deleteChapter(String chapterId) {
        Optional<Chapter> found = chapter(chapterId);
        if (found.isEmpty()) {
            return;
        }
        Chapter chapter = found.get();
        List<BookEdit> restore = new ArrayList<>();
        restore.add(new BookEdit.ChapterInfo(chapterId, Optional.of(encodeInfo(chapter.info()))));
        chapter.quests().forEach(quest -> restore.add(new BookEdit.Quest(chapterId, quest.id(), Optional.of(encodeQuest(quest)))));
        chapter.links().forEach(link -> restore.add(new BookEdit.Link(chapterId, link.id(), Optional.of(encodeLink(link)))));
        restore.add(new BookEdit.Images(chapterId, encodeImages(chapter.images())));
        restore.add(new BookEdit.Labels(chapterId, encodeLabels(chapter.labels())));
        ClientEditor.submit(Component.translatable("hibiscusquests.edit.action.delete_chapter"),
                List.of(new BookEdit.ChapterInfo(chapterId, Optional.empty())), restore);
    }

    // The chapters of a group in book order.
    private static List<Chapter> chaptersIn(String group) {
        List<String> groupIds = book().groups().stream().map(ChapterGroup::id).toList();
        return book().sourceChapters().stream().filter(chapter -> group.isEmpty()
                ? !groupIds.contains(chapter.info().group()) : chapter.info().group().equals(group)).toList();
    }

    // Puts a chapter just before another (or last in group when before is null), renumbering the pack's chapters of
    // that group 10, 20, 30... A mod's chapters keep their order numbers.
    public static void placeChapter(String chapterId, @Nullable String before, String group) {
        Optional<Chapter> moving = chapter(chapterId);
        if (moving.isEmpty() || !isEditable(chapterId)) {
            return;
        }
        List<Chapter> order = new ArrayList<>(chaptersIn(group));
        order.removeIf(chapter -> chapter.id().equals(chapterId));
        int index = before == null ? order.size() : order.stream().map(Chapter::id).toList().indexOf(before);
        order.add(index < 0 ? order.size() : index, moving.get());
        Change change = new Change();
        for (int n = 0; n < order.size(); n++) {
            Chapter chapter = order.get(n);
            if (chapter.source().isPresent()) {
                continue;
            }
            int newOrder = (n + 1) * 10;
            boolean regroup = chapter.id().equals(chapterId) && !chapter.info().group().equals(group);
            if (chapter.info().order() != newOrder || regroup) {
                CompoundTag old = encodeInfo(chapter.info());
                CompoundTag info = old.copy();
                info.putInt("order", newOrder);
                if (chapter.id().equals(chapterId)) {
                    if (group.isEmpty()) {
                        info.remove("group");
                    } else {
                        info.putString("group", group);
                    }
                }
                change.add(new BookEdit.ChapterInfo(chapter.id(), Optional.of(info)), new BookEdit.ChapterInfo(chapter.id(), Optional.of(old)));
            }
        }
        change.submit("reorder");
    }

    // Moves a chapter up or down its group.
    public static void stepChapter(String chapterId, int by) {
        Optional<Chapter> found = chapter(chapterId);
        if (found.isEmpty()) {
            return;
        }
        String group = groupOf(found.get());
        List<String> ids = chaptersIn(group).stream().map(Chapter::id).toList();
        int index = ids.indexOf(chapterId);
        int target = index + by;
        if (index < 0 || target < 0 || target >= ids.size()) {
            return;
        }
        String before = by < 0 ? ids.get(target) : target + 1 < ids.size() ? ids.get(target + 1) : null;
        placeChapter(chapterId, before, group);
    }

    // The chapter's group, or "" when it's in none (or one that doesn't exist).
    public static String groupOf(Chapter chapter) {
        String group = chapter.info().group();
        return book().groups().stream().anyMatch(g -> g.id().equals(group)) ? group : "";
    }

    // A new group from its form (its id from its title), last in the list. Returns its id.
    public static String createGroup(CompoundTag form) {
        ListTag groups = encodeGroups(book().groups());
        List<String> taken = book().groups().stream().map(ChapterGroup::id).toList();
        String id = Ids.fromTitle(form.getStringOr("title", ""), "group", taken);
        CompoundTag group = form.copy();
        group.putString("id", id);
        group.putInt("order", book().groups().stream().mapToInt(ChapterGroup::order).max().orElse(0) + 10);
        groups.add(group);
        Change change = new Change();
        change.add(new BookEdit.Groups(groups), new BookEdit.Groups(encodeGroups(book().groups())));
        change.submit("new_group");
        return id;
    }

    public static void saveGroup(String id, CompoundTag tag) {
        ListTag groups = encodeGroups(book().groups());
        for (int n = 0; n < groups.size(); n++) {
            if (groups.get(n) instanceof CompoundTag group && group.getStringOr("id", "").equals(id)) {
                CompoundTag updated = tag.copy();
                updated.putString("id", id);
                groups.set(n, updated);
            }
        }
        Change change = new Change();
        change.add(new BookEdit.Groups(groups), new BookEdit.Groups(encodeGroups(book().groups())));
        change.submit("edit_group");
    }

    // Puts a group just before another, renumbering the groups 10, 20, 30...
    public static void placeGroup(String id, String before) {
        List<ChapterGroup> groups = new ArrayList<>(book().groups());
        Optional<ChapterGroup> moving = groups.stream().filter(group -> group.id().equals(id)).findFirst();
        if (moving.isEmpty()) {
            return;
        }
        groups.remove(moving.get());
        int index = groups.stream().map(ChapterGroup::id).toList().indexOf(before);
        groups.add(index < 0 ? groups.size() : index, moving.get());
        renumberGroups(groups);
    }

    private static void renumberGroups(List<ChapterGroup> groups) {
        List<ChapterGroup> renumbered = new ArrayList<>();
        for (int n = 0; n < groups.size(); n++) {
            ChapterGroup group = groups.get(n);
            renumbered.add(new ChapterGroup(group.id(), group.title(), group.icon(), (n + 1) * 10, group.collapsed()));
        }
        Change change = new Change();
        change.add(new BookEdit.Groups(encodeGroups(renumbered)), new BookEdit.Groups(encodeGroups(book().groups())));
        change.submit("reorder");
    }

    public static void stepGroup(String id, int by) {
        List<ChapterGroup> groups = new ArrayList<>(book().groups());
        int index = groups.stream().map(ChapterGroup::id).toList().indexOf(id);
        int target = index + by;
        if (index < 0 || target < 0 || target >= groups.size()) {
            return;
        }
        groups.add(target, groups.remove(index));
        renumberGroups(groups);
    }

    // Deletes a group; the pack's chapters in it move out of it.
    public static void deleteGroup(String id) {
        List<ChapterGroup> groups = new ArrayList<>(book().groups());
        groups.removeIf(group -> group.id().equals(id));
        Change change = new Change();
        change.add(new BookEdit.Groups(encodeGroups(groups)), new BookEdit.Groups(encodeGroups(book().groups())));
        for (Chapter chapter : book().sourceChapters()) {
            if (chapter.source().isEmpty() && chapter.info().group().equals(id)) {
                CompoundTag old = encodeInfo(chapter.info());
                CompoundTag info = old.copy();
                info.remove("group");
                change.add(new BookEdit.ChapterInfo(chapter.id(), Optional.of(info)), new BookEdit.ChapterInfo(chapter.id(), Optional.of(old)));
            }
        }
        change.submit("delete_group");
    }

    // ---- progress (through the change_progress command, which checks the op's permission) ----

    public static void changeProgress(QuestRef quest, boolean complete) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("hibiscusquests change_progress " + minecraft.player.getGameProfile().name() + " "
                    + (complete ? "complete " : "reset ") + quest);
        }
    }
}
