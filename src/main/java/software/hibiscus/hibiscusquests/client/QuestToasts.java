/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import software.hibiscus.hibiscuslib.client.gui.HibiscusToasts;
import software.hibiscus.hibiscusquests.QuestSounds;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.AutoClaim;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestReward;
import software.hibiscus.hibiscusquests.progress.QuestState;
import software.hibiscus.hibiscusquests.progress.TeamProgress;

// The toasts and sounds that follow progress: a quest completed (unless it says disable_toast), a chapter completed, a
// reward that arrived by itself (auto_claim enabled), and the claim sound. Worked out by comparing the progress before
// and after each change the server sends; the first, full sync after joining never announces anything.
final class QuestToasts {
    // What was true before a change: completed chapters, and the rewards this player had.
    record Before(Set<String> chapters, Set<String> claims) {}

    private QuestToasts() {}

    static Before snapshot(QuestBook book, TeamProgress progress, UUID me) {
        return new Before(new HashSet<>(progress.completedChapters()), claims(book, progress, me));
    }

    // "chapter/quest#reward" for every reward this player has (theirs, or the team's).
    private static Set<String> claims(QuestBook book, TeamProgress progress, UUID me) {
        Set<String> claims = new HashSet<>();
        for (Quest quest : book.quests()) {
            QuestState state = progress.state(quest.ref());
            if (state == null) {
                continue;
            }
            for (QuestReward reward : quest.rewards()) {
                if (state.isClaimed(reward.id(), reward.teamReward(), me)) {
                    claims.add(quest.ref() + "#" + reward.id());
                }
            }
        }
        return claims;
    }

    static void afterDelta(QuestBook book, TeamProgress progress, UUID me, Before before, List<String> completedNow) {
        boolean toasts = QuestsClientConfig.get(QuestsClientConfig.TOASTS);
        boolean questSound = false;
        for (String key : completedNow) {
            Quest quest = QuestRef.parse(key).flatMap(book::quest).orElse(null);
            if (quest == null || quest.display().disableToast()) {
                continue;
            }
            questSound = true;
            if (toasts) {
                HibiscusToasts.show(Component.translatable("hibiscusquests.toast.quest_complete"),
                        BookText.titleOr(quest.title(), quest.id()), QuestDraw.icon(ClientBook.icon(quest)));
            }
        }
        boolean chapterSound = false;
        for (String chapterId : progress.completedChapters()) {
            if (before.chapters().contains(chapterId)) {
                continue;
            }
            Chapter chapter = book.chapter(chapterId).orElse(null);
            if (chapter == null) {
                continue;
            }
            chapterSound = true;
            if (toasts) {
                HibiscusToasts.show(Component.translatable("hibiscusquests.toast.chapter_complete"),
                        BookText.titleOr(chapter.title(), chapter.id()), QuestDraw.icon(ClientBook.icon(chapter)));
            }
        }
        boolean claimSound = false;
        for (Quest quest : book.quests()) {
            QuestState state = progress.state(quest.ref());
            if (state == null) {
                continue;
            }
            for (QuestReward reward : quest.rewards()) {
                String key = quest.ref() + "#" + reward.id();
                if (before.claims().contains(key) || !state.isClaimed(reward.id(), reward.teamReward(), me)) {
                    continue;
                }
                claimSound = true;
                if (toasts && reward.autoClaim() == AutoClaim.ENABLED) {
                    HibiscusToasts.show(Component.translatable("hibiscusquests.toast.reward"), reward.reward().title(),
                            reward.reward().icon().isEmpty() ? QuestDraw.icon(ClientBook.icon(quest))
                                    : software.hibiscus.hibiscuslib.client.gui.Icon.item(reward.reward().icon()));
                }
            }
        }
        if (chapterSound) {
            play(QuestSounds.CHAPTER_COMPLETE.get());
        } else if (questSound) {
            play(QuestSounds.QUEST_COMPLETE.get());
        } else if (claimSound) {
            play(QuestSounds.REWARD_CLAIM.get());
        }
    }

    private static void play(SoundEvent sound) {
        if (QuestsClientConfig.get(QuestsClientConfig.SOUNDS)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
        }
    }
}
