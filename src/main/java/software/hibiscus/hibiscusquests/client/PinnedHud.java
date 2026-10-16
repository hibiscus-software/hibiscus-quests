/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestTask;

// The pinned quests on screen: a compact list in a corner (hudCorner, at hudScale) with each pinned quest's title and
// its unfinished tasks' progress. Hidden while a screen is open or the HUD is hidden (F1), and when the player turns it
// off from the book's top bar. Pins are kept per player with the team's progress, so they follow you between computers.
public final class PinnedHud {
    private static final int MIN_WIDTH = 110;
    private static final int MAX_WIDTH = 200;
    private static final int MARGIN = 4;
    private static final int MAX_TASKS = 3;

    private PinnedHud() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null || !BookUiState.hudVisible()) {
            return;
        }
        List<Quest> quests = new ArrayList<>();
        for (String key : ClientBook.progress().pinned(ClientBook.me())) {
            QuestRef.parse(key).flatMap(ref -> ClientBook.book().quest(ref)).filter(ClientBook::isVisible).ifPresent(quests::add);
        }
        if (quests.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        HibiscusTheme theme = HibiscusTheme.current();
        List<Line> lines = new ArrayList<>();
        for (Quest quest : quests) {
            NodeState state = ClientBook.state(quest);
            Component title = BookText.titleOr(quest.title(), quest.id());
            lines.add(new Line(title, null, state.isComplete() ? theme.color(HibiscusTheme.POSITIVE) : theme.text(), true, -1));
            if (state.isComplete()) {
                lines.add(new Line(Component.translatable(state == NodeState.CLAIMABLE ? "hibiscusquests.hud.claim" : "hibiscusquests.state.done"),
                        null, theme.color(state == NodeState.CLAIMABLE ? HibiscusTheme.GOLD : HibiscusTheme.POSITIVE), false, -1));
                continue;
            }
            int shown = 0;
            for (QuestTask task : quest.tasks()) {
                long value = ClientBook.taskProgress(quest, task);
                if (value >= task.target()) {
                    continue;
                }
                if (shown++ == MAX_TASKS) {
                    break;
                }
                Component name = task.title().map(BookText::component).orElseGet(() -> task.task().title());
                Component count = task.target() > 1 ? Component.translatable("hibiscusquests.hud.task", value, task.target()) : null;
                lines.add(new Line(name, count, theme.textMuted(), false, task.target() > 1 ? (float) value / task.target() : -1));
            }
        }

        // As wide as the longest line needs, within MIN_WIDTH and MAX_WIDTH; longer text is cut short.
        int width = MIN_WIDTH;
        int height = 6;
        for (Line line : lines) {
            int needed = (line.title ? 5 : 9) + font.width(line.text) + (line.count != null ? font.width(line.count) + 6 : 0) + 6;
            width = Math.max(width, Math.min(MAX_WIDTH, needed));
            height += line.height();
        }
        float scale = (float) Mth.clamp(QuestsClientConfig.get(QuestsClientConfig.HUD_SCALE), 0.5, 2.0);
        int screenWidth = Math.round(graphics.guiWidth() / scale);
        int screenHeight = Math.round(graphics.guiHeight() / scale);
        QuestsClientConfig.Corner corner = QuestsClientConfig.get(QuestsClientConfig.HUD_CORNER);
        int x = corner.right() ? screenWidth - width - MARGIN : MARGIN;
        int y = corner.bottom() ? screenHeight - height - MARGIN - 40 : MARGIN;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.fill(x, y, x + width, y + height, 0xA0101114);
        graphics.fill(x, y, x + 1, y + height, QuestDraw.accent());
        int lineY = y + 4;
        for (Line line : lines) {
            int indent = line.title ? 5 : 9;
            int right = x + width - 6;
            int countWidth = line.count != null ? font.width(line.count) : 0;
            int room = right - (x + indent) - (countWidth > 0 ? countWidth + 6 : 0);
            graphics.text(font, fit(font, line.text, room), x + indent, lineY, line.color, line.title);
            if (line.count != null) {
                graphics.text(font, line.count, right - countWidth, lineY, theme.textMuted(), false);
            }
            if (line.fraction >= 0) {
                int barX = x + indent;
                int barWidth = right - barX;
                graphics.fill(barX, lineY + 10, barX + barWidth, lineY + 11, 0x50FFFFFF);
                graphics.fill(barX, lineY + 10, barX + Math.round(barWidth * line.fraction), lineY + 11, QuestDraw.accent());
            }
            lineY += line.height();
        }
        graphics.pose().popMatrix();
    }

    // One line: a quest's title, or one of its tasks (with its count and progress bar when it counts to more than 1).
    private record Line(Component text, @Nullable Component count, int color, boolean title, float fraction) {
        int height() {
            return title ? 11 : fraction >= 0 ? 14 : 10;
        }
    }

    private static FormattedCharSequence fit(Font font, Component text, int width) {
        return Language.getInstance().getVisualOrder(font.substrByWidth(text, width));
    }
}
