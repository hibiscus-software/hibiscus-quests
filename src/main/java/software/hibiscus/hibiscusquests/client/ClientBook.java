/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookIcon;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.HideMode;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestReward;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.progress.QuestClock;
import software.hibiscus.hibiscusquests.progress.QuestLogic;
import software.hibiscus.hibiscusquests.progress.QuestState;
import software.hibiscus.hibiscusquests.progress.TeamProgress;

// The book as this player sees it: each quest's state, which quests and chapters show, and what's waiting to be
// claimed, worked out from the client's copy of the book and its team's progress with the same rules the server uses
// (QuestLogic). Cached until the next sync.
public final class ClientBook {
    private static QuestBook cachedBook = QuestBook.EMPTY;
    private static int cachedVersion = -1;
    private static final Map<QuestRef, NodeState> STATES = new HashMap<>();
    private static final Map<QuestRef, Boolean> VISIBLE = new HashMap<>();

    private ClientBook() {}

    public static QuestBook book() {
        return ClientQuestState.book();
    }

    public static TeamProgress progress() {
        return ClientQuestState.progress();
    }

    public static UUID me() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    // Drops the cache when the book or progress has changed since it was filled.
    private static void refresh() {
        QuestBook book = book();
        int version = ClientQuestState.version();
        if (book != cachedBook || version != cachedVersion) {
            cachedBook = book;
            cachedVersion = version;
            STATES.clear();
            VISIBLE.clear();
        }
    }

    public static NodeState state(Quest quest) {
        refresh();
        NodeState state = STATES.get(quest.ref());
        if (state == null) {
            state = compute(quest);
            STATES.put(quest.ref(), state);
        }
        return state;
    }

    private static NodeState compute(Quest quest) {
        TeamProgress progress = progress();
        if (progress.isCompleted(quest.ref())) {
            return hasUnclaimed(quest) ? NodeState.CLAIMABLE : NodeState.DONE;
        }
        if (!QuestLogic.isUnlocked(book(), progress, quest, QuestClock.now())) {
            return quest.display().secret() ? NodeState.SECRET : NodeState.LOCKED;
        }
        QuestState state = progress.state(quest.ref());
        boolean started = state != null && state.tasks().values().stream().anyMatch(value -> value > 0);
        return started ? NodeState.IN_PROGRESS : NodeState.AVAILABLE;
    }

    // Whether the quest's node shows (its hide mode, invisible_until_tasks), the way players see it.
    public static boolean isVisible(Quest quest) {
        refresh();
        Boolean known = VISIBLE.get(quest.ref());
        if (known != null) {
            return known;
        }
        VISIBLE.put(quest.ref(), false); // breaks dependency cycles
        boolean visible = computeVisible(quest);
        VISIBLE.put(quest.ref(), visible);
        return visible;
    }

    private static boolean computeVisible(Quest quest) {
        NodeState state = state(quest);
        if (state.isComplete()) {
            return true;
        }
        int needed = quest.display().invisibleUntilTasks();
        if (needed > 0 && tasksDone(quest) < needed) {
            return false;
        }
        HideMode hide = quest.display().hide();
        return switch (hide) {
            case NEVER -> true;
            case UNTIL_UNLOCKED -> state.isUnlocked();
            case ALWAYS_UNTIL_COMPLETE -> false;
            case UNTIL_DEPENDENCIES_VISIBLE -> {
                List<QuestRef> dependencies = quest.dependencies();
                if (dependencies.isEmpty()) {
                    yield true;
                }
                boolean any = false;
                for (QuestRef ref : dependencies) {
                    Quest dependency = book().quest(ref).orElse(null);
                    if (dependency != null && isVisible(dependency)) {
                        any = true;
                        break;
                    }
                }
                yield any;
            }
        };
    }

    public static int tasksDone(Quest quest) {
        int done = 0;
        for (QuestTask task : quest.tasks()) {
            if (progress().task(quest.ref(), task.id()) >= task.target()) {
                done++;
            }
        }
        return done;
    }

    public static long taskProgress(Quest quest, QuestTask task) {
        return Math.min(task.target(), progress().task(quest.ref(), task.id()));
    }

    public static double fraction(Quest quest) {
        return QuestLogic.fraction(progress(), quest);
    }

    // Whether this player could claim the reward now.
    public static boolean canClaim(Quest quest, QuestReward reward) {
        QuestState state = progress().state(quest.ref());
        return state != null && state.isCompleted() && !state.isClaimed(reward.id(), reward.teamReward(), me());
    }

    public static boolean isClaimed(Quest quest, QuestReward reward) {
        QuestState state = progress().state(quest.ref());
        return state != null && state.isClaimed(reward.id(), reward.teamReward(), me());
    }

    public static boolean hasUnclaimed(Quest quest) {
        for (QuestReward reward : quest.rewards()) {
            if (canClaim(quest, reward)) {
                return true;
            }
        }
        return false;
    }

    // How many rewards this player can claim, for the inventory button's badge.
    public static int unclaimedCount() {
        int count = 0;
        for (Quest quest : book().quests()) {
            if (progress().isCompleted(quest.ref())) {
                for (QuestReward reward : quest.rewards()) {
                    if (canClaim(quest, reward)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ---- chapters ----

    // The chapters players see in the list (always_invisible ones are only for editors).
    public static List<Chapter> visibleChapters() {
        List<Chapter> chapters = new ArrayList<>();
        for (Chapter chapter : book().chapters()) {
            if (!chapter.info().alwaysInvisible()) {
                chapters.add(chapter);
            }
        }
        return chapters;
    }

    public static ChapterStatus status(Chapter chapter) {
        return QuestLogic.chapterStatus(book(), progress(), chapter.id());
    }

    // Whether the chapter's gate (requires) still keeps it shut.
    public static boolean isLocked(Chapter chapter) {
        for (QuestBook.Requirement requirement : book().requirements(chapter.id())) {
            if (requirement.chapter().isPresent()) {
                if (!QuestLogic.chapterStatus(book(), progress(), requirement.chapter().get()).isCompleted()) {
                    return true;
                }
            } else if (requirement.quest().isPresent() && !progress().isCompleted(requirement.quest().get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUnclaimed(Chapter chapter) {
        for (Quest quest : chapter.quests()) {
            if (state(quest) == NodeState.CLAIMABLE) {
                return true;
            }
        }
        return false;
    }

    // ---- icons ----

    // A quest's icon: its own, else its first task's, else a book.
    public static BookIcon icon(Quest quest) {
        if (quest.display().icon().isPresent()) {
            return quest.display().icon().get();
        }
        for (QuestTask task : quest.tasks()) {
            if (task.icon().isPresent()) {
                return task.icon().get();
            }
            ItemStack stack = task.task().icon();
            if (!stack.isEmpty()) {
                return BookIcon.of(stack);
            }
        }
        return BookIcon.of(new ItemStack(Items.BOOK));
    }

    public static BookIcon icon(QuestTask task) {
        if (task.icon().isPresent()) {
            return task.icon().get();
        }
        ItemStack stack = task.task().icon();
        return BookIcon.of(stack.isEmpty() ? new ItemStack(Items.PAPER) : stack);
    }

    public static BookIcon icon(Chapter chapter) {
        if (chapter.info().icon().isPresent()) {
            return chapter.info().icon().get();
        }
        return chapter.quests().isEmpty() ? BookIcon.of(new ItemStack(Items.BOOK)) : icon(chapter.quests().getFirst());
    }
}
