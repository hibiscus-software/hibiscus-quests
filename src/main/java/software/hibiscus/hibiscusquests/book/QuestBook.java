/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.book.BookView;

// The whole book after merging mod-provided chapters with the pack's files: settings, groups and chapters in book
// order, with the indexes the progress logic needs (quests by reference, who depends on whom, one_of branches,
// chapter gates). Immutable; a reload or edit builds a new one. The same class is used on the client, decoded from
// the network form.
public final class QuestBook implements BookView {
    public static final QuestBook EMPTY = build(BookSettings.DEFAULT, List.of(), List.of(), List.of(), warning -> {});

    // The pack's translations: locale to key to text.
    public static final Codec<Map<String, Map<String, String>>> LANG_CODEC = Codec.unboundedMap(Codec.STRING,
            Codec.unboundedMap(Codec.STRING, Codec.STRING));

    // What the server sends: everything, including each chapter's source.
    public static final Codec<QuestBook> NETWORK_CODEC = RecordCodecBuilder.create(i -> i.group(
            BookSettings.CODEC.fieldOf("settings").forGetter(QuestBook::settings),
            BookCodecs.list("groups", ChapterGroup.CODEC).forGetter(QuestBook::groups),
            BookCodecs.list("chapters", Chapter.NETWORK_CODEC).forGetter(QuestBook::sourceChapters),
            BookCodecs.list("reward_tables", RewardTable.NETWORK_CODEC).forGetter(book -> List.copyOf(book.tables.values())),
            LANG_CODEC.optionalFieldOf("lang", Map.of()).forGetter(QuestBook::lang))
            .apply(i, (settings, groups, chapters, tables, lang) -> build(settings, groups, chapters, tables, lang, warning -> {})));

    // A chapter gate entry: a whole chapter, or one quest.
    public record Requirement(Optional<String> chapter, Optional<QuestRef> quest) {}

    private final BookSettings settings;
    private final List<ChapterGroup> groups;
    private final List<Chapter> chapters;
    // The chapters as they were given, before dependencies that point nowhere were dropped: what's sent to clients and
    // what the editor edits, so a dependency on a quest that's missing for now comes back with the quest.
    private final List<Chapter> sourceChapters;
    private final Map<String, Chapter> sourceById;
    private final Map<String, Chapter> chaptersById;
    private final Map<QuestRef, Quest> quests;
    private final Map<QuestRef, List<QuestRef>> dependents;
    private final Map<QuestRef, Set<QuestRef>> oneOfSiblings;
    private final Map<String, List<Requirement>> chapterRequirements;
    private final Map<String, RewardTable> tables;
    private final Map<String, Map<String, String>> lang;

    private QuestBook(BookSettings settings, List<ChapterGroup> groups, List<Chapter> chapters, List<Chapter> sourceChapters,
            Map<QuestRef, Quest> quests, Map<String, List<Requirement>> chapterRequirements, Map<String, RewardTable> tables,
            Map<String, Map<String, String>> lang) {
        Map<String, Map<String, String>> langCopy = new LinkedHashMap<>();
        lang.forEach((locale, entries) -> langCopy.put(locale, Collections.unmodifiableMap(new LinkedHashMap<>(entries))));
        this.lang = Collections.unmodifiableMap(langCopy);
        this.sourceChapters = List.copyOf(sourceChapters);
        Map<String, Chapter> sources = new LinkedHashMap<>();
        sourceChapters.forEach(chapter -> sources.putIfAbsent(chapter.id(), chapter));
        this.sourceById = Collections.unmodifiableMap(sources);
        this.tables = Collections.unmodifiableMap(tables);
        this.settings = settings;
        this.groups = List.copyOf(groups);
        this.chapters = List.copyOf(chapters);
        Map<String, Chapter> byId = new LinkedHashMap<>();
        chapters.forEach(chapter -> byId.put(chapter.id(), chapter));
        this.chaptersById = Collections.unmodifiableMap(byId);
        this.quests = Collections.unmodifiableMap(quests);
        this.chapterRequirements = Map.copyOf(chapterRequirements);

        Map<QuestRef, List<QuestRef>> dependentsOf = new HashMap<>();
        Map<QuestRef, Set<QuestRef>> siblings = new HashMap<>();
        Map<QuestRef, List<QuestRef>> oneOfByDependency = new HashMap<>();
        for (Quest quest : quests.values()) {
            for (QuestRef dependency : quest.dependencies()) {
                dependentsOf.computeIfAbsent(dependency, key -> new ArrayList<>()).add(quest.ref());
                if (quest.rules().dependencyMode() == DependencyMode.ONE_OF) {
                    oneOfByDependency.computeIfAbsent(dependency, key -> new ArrayList<>()).add(quest.ref());
                }
            }
        }
        for (List<QuestRef> branch : oneOfByDependency.values()) {
            for (QuestRef quest : branch) {
                for (QuestRef other : branch) {
                    if (!other.equals(quest)) {
                        siblings.computeIfAbsent(quest, key -> new LinkedHashSet<>()).add(other);
                    }
                }
            }
        }
        dependentsOf.replaceAll((key, list) -> List.copyOf(list));
        siblings.replaceAll((key, set) -> Set.copyOf(set));
        this.dependents = Map.copyOf(dependentsOf);
        this.oneOfSiblings = Map.copyOf(siblings);
    }

    // Builds the book from decoded parts: sorts groups and chapters, drops duplicate quests and dependencies or
    // gates that point nowhere (each reported to warnings), and indexes everything.
    public static QuestBook build(BookSettings settings, List<ChapterGroup> groups, List<Chapter> chapters, List<RewardTable> rewardTables,
            Consumer<String> warnings) {
        return build(settings, groups, chapters, rewardTables, Map.of(), warnings);
    }

    public static QuestBook build(BookSettings settings, List<ChapterGroup> groups, List<Chapter> chapters, List<RewardTable> rewardTables,
            Map<String, Map<String, String>> lang, Consumer<String> warnings) {
        List<ChapterGroup> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator.comparingInt(ChapterGroup::order).thenComparing(ChapterGroup::id));
        Map<String, Integer> groupRank = new HashMap<>();
        for (int n = 0; n < sortedGroups.size(); n++) {
            groupRank.put(sortedGroups.get(n).id(), n);
        }
        List<Chapter> sortedChapters = new ArrayList<>(chapters);
        // Ungrouped chapters (and those naming a group that doesn't exist) come first.
        sortedChapters.sort(Comparator.<Chapter>comparingInt(chapter -> groupRank.getOrDefault(chapter.info().group(), -1))
                .thenComparingInt(chapter -> chapter.info().order())
                .thenComparing(Chapter::id));

        Map<QuestRef, Quest> quests = new LinkedHashMap<>();
        for (Chapter chapter : sortedChapters) {
            for (Quest quest : chapter.quests()) {
                if (quests.putIfAbsent(quest.ref(), quest) != null) {
                    warnings.accept("Chapter " + chapter.id() + " has two quests with the id " + quest.id() + "; the second is ignored");
                }
            }
        }
        // Keep only dependencies that exist, so a typo can't lock a quest forever without a word.
        List<Chapter> cleanedChapters = new ArrayList<>();
        for (Chapter chapter : sortedChapters) {
            List<Quest> cleaned = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Quest quest : chapter.quests()) {
                if (!seen.add(quest.id())) {
                    continue;
                }
                List<String> kept = new ArrayList<>();
                for (String text : quest.rules().dependencies()) {
                    Optional<QuestRef> ref = QuestRef.parseRelative(text, chapter.id());
                    if (ref.isPresent() && quests.containsKey(ref.get()) && !ref.get().equals(quest.ref())) {
                        kept.add(text);
                    } else {
                        warnings.accept("Quest " + quest.ref() + " depends on \"" + text + "\", which isn't in the book; the dependency is ignored");
                    }
                }
                Quest fixed = kept.size() == quest.rules().dependencies().size() ? quest : quest.withDependencies(kept);
                cleaned.add(fixed);
                quests.put(fixed.ref(), fixed);
            }
            cleanedChapters.add(chapter.withQuests(cleaned));
        }

        Map<String, List<Requirement>> requirements = new HashMap<>();
        Set<String> chapterIds = new LinkedHashSet<>();
        cleanedChapters.forEach(chapter -> chapterIds.add(chapter.id()));
        for (Chapter chapter : cleanedChapters) {
            List<Requirement> gate = new ArrayList<>();
            for (String text : chapter.info().requires()) {
                if (text.indexOf('/') >= 0) {
                    Optional<QuestRef> ref = QuestRef.parse(text);
                    if (ref.isPresent() && quests.containsKey(ref.get())) {
                        gate.add(new Requirement(Optional.empty(), ref));
                        continue;
                    }
                } else if (chapterIds.contains(text) && !text.equals(chapter.id())) {
                    gate.add(new Requirement(Optional.of(text), Optional.empty()));
                    continue;
                }
                warnings.accept("Chapter " + chapter.id() + " requires \"" + text + "\", which isn't in the book; the requirement is ignored");
            }
            if (!gate.isEmpty()) {
                requirements.put(chapter.id(), List.copyOf(gate));
            }
        }
        Map<String, RewardTable> tables = new LinkedHashMap<>();
        for (RewardTable table : rewardTables) {
            if (tables.putIfAbsent(table.id(), table) != null) {
                warnings.accept("Two reward tables have the id " + table.id() + "; the second is ignored");
            }
        }
        // Rewards that roll a table that isn't there would give nothing: say so.
        for (Quest quest : quests.values()) {
            for (QuestReward reward : quest.rewards()) {
                if (reward.reward() instanceof UsesRewardTable user && !tables.containsKey(user.table())) {
                    warnings.accept("Quest " + quest.ref() + ", reward " + reward.id() + ": there's no reward table \"" + user.table() + "\"");
                }
            }
        }
        return new QuestBook(settings, sortedGroups, cleanedChapters, sortedChapters, quests, requirements, tables, lang);
    }

    // The pack's translations (lang/<locale>.snbt), locale to key to text.
    public Map<String, Map<String, String>> lang() {
        return lang;
    }

    // A pack translation of a key in a locale.
    public Optional<String> translation(String locale, String key) {
        Map<String, String> entries = lang.get(locale);
        return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(key));
    }

    public Optional<RewardTable> table(String id) {
        return Optional.ofNullable(tables.get(id));
    }

    public Iterable<RewardTable> tables() {
        return tables.values();
    }

    public BookSettings settings() {
        return settings;
    }

    public List<ChapterGroup> groups() {
        return groups;
    }

    @Override
    public List<Chapter> chapters() {
        return chapters;
    }

    // The chapters before cleaning (see sourceChapters), in book order.
    public List<Chapter> sourceChapters() {
        return sourceChapters;
    }

    public Optional<Chapter> sourceChapter(String id) {
        return Optional.ofNullable(sourceById.get(id));
    }

    @Override
    public Optional<Chapter> chapter(String id) {
        return Optional.ofNullable(chaptersById.get(id));
    }

    @Override
    public Optional<Quest> quest(QuestRef quest) {
        return Optional.ofNullable(quests.get(quest));
    }

    // Every quest, in book order.
    public Iterable<Quest> quests() {
        return quests.values();
    }

    public int questCount() {
        return quests.size();
    }

    // The quests that list this one as a dependency.
    public List<QuestRef> dependents(QuestRef quest) {
        return dependents.getOrDefault(quest, List.of());
    }

    // The other one_of quests that share a dependency with this one: completing one locks the rest.
    public Set<QuestRef> oneOfSiblings(QuestRef quest) {
        return oneOfSiblings.getOrDefault(quest, Set.of());
    }

    // What must be complete before the chapter's quests unlock.
    public List<Requirement> requirements(String chapterId) {
        return chapterRequirements.getOrDefault(chapterId, List.of());
    }

    // The quest listed just before this one in its chapter, for require_sequential chapters.
    public Optional<Quest> previousInChapter(Quest quest) {
        Chapter chapter = chaptersById.get(quest.chapter());
        if (chapter == null) {
            return Optional.empty();
        }
        List<Quest> list = chapter.quests();
        for (int n = 1; n < list.size(); n++) {
            if (list.get(n).id().equals(quest.id())) {
                return Optional.of(list.get(n - 1));
            }
        }
        return Optional.empty();
    }
}
