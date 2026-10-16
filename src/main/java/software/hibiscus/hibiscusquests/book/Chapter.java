/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import software.hibiscus.hibiscusquests.api.book.ChapterView;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasImage;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasLabel;
import software.hibiscus.hibiscusquests.book.CanvasElements.QuestLink;

// A chapter: one file, chapters/<id>.snbt, with its settings and its canvas (quests, links to other chapters' quests,
// images, labels). source is the namespace of the mod or data pack that shipped it (empty for the pack's own files);
// it isn't written to the file.
public record Chapter(String id, ChapterInfo info, List<Quest> quests, List<QuestLink> links, List<CanvasImage> images,
        List<CanvasLabel> labels, Optional<String> source) implements ChapterView {
    // The file form. id may be left out (the file name is used).
    public static final Codec<Chapter> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(Chapter::id),
            ChapterInfo.MAP_CODEC.forGetter(Chapter::info),
            BookCodecs.list("quests", Quest.CODEC).forGetter(Chapter::quests),
            BookCodecs.list("quest_links", QuestLink.CODEC).forGetter(Chapter::links),
            BookCodecs.list("images", CanvasImage.CODEC).forGetter(Chapter::images),
            BookCodecs.list("labels", CanvasLabel.CODEC).forGetter(Chapter::labels))
            .apply(i, (id, info, quests, links, images, labels) -> new Chapter(id, info, quests, links, images, labels, Optional.empty())));

    // The network form, which keeps the source (the book shows a lock on mod-provided chapters).
    public static final Codec<Chapter> NETWORK_CODEC = RecordCodecBuilder.create(i -> i.group(
            CODEC.fieldOf("chapter").forGetter(chapter -> chapter),
            Codec.STRING.optionalFieldOf("source").forGetter(Chapter::source))
            .apply(i, (chapter, source) -> chapter.built(chapter.id(), source)));

    public Chapter {
        quests = List.copyOf(quests);
        links = List.copyOf(links);
        images = List.copyOf(images);
        labels = List.copyOf(labels);
    }

    // The chapter under its final id, with each quest told which chapter it's in.
    public Chapter built(String chapterId, Optional<String> fromSource) {
        return new Chapter(chapterId, info, quests.stream().map(quest -> quest.inChapter(chapterId)).toList(), links, images,
                labels, fromSource);
    }

    public Chapter withSource(Optional<String> newSource) {
        return new Chapter(id, info, quests, links, images, labels, newSource);
    }

    public Chapter withQuests(List<Quest> newQuests) {
        return new Chapter(id, info, newQuests, links, images, labels, source);
    }

    @Override
    public String title() {
        return info.title();
    }

    public Optional<Quest> quest(String questId) {
        return quests.stream().filter(quest -> quest.id().equals(questId)).findFirst();
    }
}
