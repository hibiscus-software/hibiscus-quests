/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.widget.HibiscusWidget;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.QuestDraw;

// The rows of the book's chapter list: a group's heading (click to fold it) and a chapter (icon, title, a thin
// progress bar, a gold dot when rewards wait, a lock while its gate is shut). Collapsed, the list shows icons only.
final class ChapterRows {
    static final int CHAPTER_HEIGHT = 22;
    static final int COLLAPSED_SIZE = 20;
    static final int GROUP_HEIGHT = 14;

    private ChapterRows() {}

    // A group's heading.
    static final class GroupRow extends HibiscusWidget {
        private final boolean folded;
        private final Runnable onToggle;

        GroupRow(int width, ChapterGroup group, boolean folded, Runnable onToggle) {
            super(0, 0, width, GROUP_HEIGHT, BookText.titleOr(group.title(), group.id()));
            this.folded = folded;
            this.onToggle = onToggle;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), theme.color(HibiscusTheme.HOVER));
            }
            int color = theme.textMuted();
            graphics.text(font(), folded ? "▸" : "▾", getX() + 2, getY() + 3, color, false);
            var label = Language.getInstance().getVisualOrder(font().substrByWidth(getMessage(), getWidth() - 14));
            graphics.text(font(), label, getX() + 11, getY() + 3, color, false);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            onToggle.run();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.isConfirmation()) {
                onToggle.run();
                return true;
            }
            return false;
        }
    }

    // A chapter.
    static final class ChapterRow extends HibiscusWidget {
        private final Chapter chapter;
        private final boolean selected;
        private final boolean collapsed;
        private final Runnable onSelect;

        ChapterRow(int width, Chapter chapter, boolean selected, boolean collapsed, Runnable onSelect) {
            super(0, 0, collapsed ? COLLAPSED_SIZE : width, collapsed ? COLLAPSED_SIZE : CHAPTER_HEIGHT,
                    BookText.titleOr(chapter.title(), chapter.id()));
            this.chapter = chapter;
            this.selected = selected;
            this.collapsed = collapsed;
            this.onSelect = onSelect;
            tooltipLines(this::tooltip);
        }

        private List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            if (collapsed) {
                lines.add(getMessage());
                ChapterStatus status = ClientBook.status(chapter);
                lines.add(Component.translatable("hibiscusquests.book.progress", status.completed(), status.total())
                        .withColor(theme().textMuted()));
            }
            if (ClientBook.isLocked(chapter)) {
                lines.add(Component.translatable("hibiscusquests.book.chapter_locked_short").withColor(theme().textMuted()));
            }
            if (!chapter.info().subtitle().isBlank() && !collapsed) {
                lines.add(BookText.component(chapter.info().subtitle()));
            }
            return lines;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            if (selected) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), theme.color(HibiscusTheme.SELECTION));
                graphics.fill(getX(), getY(), getX() + 1, getBottom(), QuestDraw.accent());
            } else if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), theme.color(HibiscusTheme.HOVER));
            }
            boolean locked = ClientBook.isLocked(chapter);
            boolean waiting = ClientBook.hasUnclaimed(chapter);
            int iconX = getX() + 2;
            int iconY = getY() + (getHeight() - 16) / 2;
            QuestDraw.icon(graphics, ClientBook.icon(chapter), iconX, iconY, 16);
            if (collapsed) {
                if (waiting) {
                    dot(graphics, getRight() - 5, getY() + 1);
                }
                return;
            }
            int textX = getX() + 21;
            int right = getRight() - 3;
            int titleRoom = right - textX - (locked || waiting ? 9 : 0);
            var title = Language.getInstance().getVisualOrder(font().substrByWidth(getMessage(), titleRoom));
            graphics.text(font(), title, textX, getY() + 4, locked ? theme.textMuted() : theme.text(), false);
            if (locked) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QuestDraw.LOCK, right - 8, getY() + 3, 8, 8);
            } else if (waiting) {
                dot(graphics, right - 5, getY() + 5);
            }
            ChapterStatus status = ClientBook.status(chapter);
            int barY = getY() + 15;
            int barWidth = right - textX;
            graphics.fill(textX, barY, textX + barWidth, barY + 3, theme.color(HibiscusTheme.PANEL_SHADOW));
            int filled = (int) Math.round(barWidth * status.fraction());
            if (status.total() > 0 && filled > 0) {
                graphics.fill(textX, barY, textX + filled, barY + 3, status.isCompleted() ? theme.color(HibiscusTheme.POSITIVE) : QuestDraw.accent());
            }
        }

        private void dot(GuiGraphicsExtractor graphics, int x, int y) {
            HibiscusTheme theme = theme();
            graphics.fill(x - 1, y - 1, x + 5, y + 5, theme.color(HibiscusTheme.PANEL_SHADOW));
            graphics.fill(x, y, x + 4, y + 4, theme.color(HibiscusTheme.GOLD));
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            onSelect.run();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.isConfirmation()) {
                onSelect.run();
                return true;
            }
            return false;
        }
    }
}
