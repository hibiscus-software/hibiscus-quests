/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

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
// A group nested in another is drawn a little further in, with its chapters (indent). In edit mode a right click asks
// for the row's menu, a chapter can be dragged onto another row to move it there (and a group's heading onto another
// heading: its left half to go before it, its right half to go into it), and a mod's chapter shows a lock (it's
// read-only until overridden).
final class ChapterRows {
    static final int CHAPTER_HEIGHT = 22;
    static final int COLLAPSED_SIZE = 20;
    static final int GROUP_HEIGHT = 14;
    // How far in each level of groups goes, and the most it's drawn in (deeper levels line up with the last).
    static final int INDENT = 6;
    static final int MAX_INDENT = 24;
    private static final int DRAG_THRESHOLD = 4;

    // What edit mode adds to a row: its menu, and (chapters) dragging it.
    interface Editing {
        void menu(int mouseX, int mouseY);

        default void dragging(double mouseX, double mouseY) {}

        default void dropped(double mouseX, double mouseY) {}
    }

    private ChapterRows() {}

    // The x offset of a row at a depth in the tree of groups.
    static int indent(int depth) {
        return Math.min(depth * INDENT, MAX_INDENT);
    }

    // A group's heading.
    static final class GroupRow extends HibiscusWidget {
        private final ChapterGroup group;
        private final boolean folded;
        private final Runnable onToggle;
        private final @Nullable Editing editing;
        private double pressX;
        private double pressY;
        private boolean pressed;
        private boolean dragging;

        GroupRow(int width, ChapterGroup group, boolean folded, Runnable onToggle, @Nullable Editing editing) {
            super(0, 0, width, GROUP_HEIGHT, BookText.titleOr(group.title(), group.id()));
            this.group = group;
            this.folded = folded;
            this.onToggle = onToggle;
            this.editing = editing;
        }

        ChapterGroup group() {
            return group;
        }

        // Whether a group dropped here at this x goes into this one (the heading's right half) or before it.
        boolean nestsAt(double mouseX) {
            return mouseX >= getX() + getWidth() / 2.0;
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
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT
                    || editing != null && buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        // In edit mode a heading folds on release, so pressing it can start a drag instead.
        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && editing != null) {
                editing.menu((int) event.x(), (int) event.y());
            } else if (editing == null) {
                onToggle.run();
            } else {
                pressed = true;
                dragging = false;
                pressX = event.x();
                pressY = event.y();
            }
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) {
            if (!pressed || editing == null) {
                return;
            }
            if (!dragging && Math.hypot(event.x() - pressX, event.y() - pressY) >= DRAG_THRESHOLD) {
                dragging = true;
            }
            if (dragging) {
                editing.dragging(event.x(), event.y());
            }
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            if (!pressed) {
                return;
            }
            pressed = false;
            if (dragging && editing != null) {
                dragging = false;
                editing.dropped(event.x(), event.y());
            } else {
                onToggle.run();
            }
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
        private final @Nullable Editing editing;
        private double pressX;
        private double pressY;
        private boolean pressed;
        private boolean dragging;

        ChapterRow(int width, Chapter chapter, boolean selected, boolean collapsed, Runnable onSelect, @Nullable Editing editing) {
            super(0, 0, collapsed ? COLLAPSED_SIZE : width, collapsed ? COLLAPSED_SIZE : CHAPTER_HEIGHT,
                    BookText.titleOr(chapter.title(), chapter.id()));
            this.chapter = chapter;
            this.selected = selected;
            this.collapsed = collapsed;
            this.onSelect = onSelect;
            this.editing = editing;
            tooltipLines(this::tooltip);
        }

        Chapter chapter() {
            return chapter;
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
            if (editing != null) {
                lines.add(Component.literal(chapter.id()).withColor(theme().color(HibiscusTheme.TEXT_DISABLED)));
                chapter.source().ifPresent(source -> lines.add(Component.translatable("hibiscusquests.edit.read_only_chapter", source)
                        .withColor(theme().textMuted())));
                if (chapter.info().alwaysInvisible()) {
                    lines.add(Component.translatable("hibiscusquests.edit.invisible_chapter").withColor(theme().textMuted()));
                }
            }
            return lines;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            if (dragging) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), theme.color(HibiscusTheme.SELECTION));
                graphics.outline(getX(), getY(), getWidth(), getHeight(), QuestDraw.accent());
            } else if (selected) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), theme.color(HibiscusTheme.SELECTION));
                graphics.fill(getX(), getY(), getX() + 1, getBottom(), QuestDraw.accent());
            } else if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), theme.color(HibiscusTheme.HOVER));
            }
            boolean locked = ClientBook.isLocked(chapter) || editing != null && chapter.source().isPresent();
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
            boolean muted = locked || editing != null && chapter.info().alwaysInvisible();
            graphics.text(font(), title, textX, getY() + 4, muted ? theme.textMuted() : theme.text(), false);
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
            return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT
                    || editing != null && buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        // In edit mode a chapter is picked on release, so pressing it can start a drag instead.
        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && editing != null) {
                editing.menu((int) event.x(), (int) event.y());
                return;
            }
            if (editing == null) {
                onSelect.run();
                return;
            }
            pressed = true;
            dragging = false;
            pressX = event.x();
            pressY = event.y();
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) {
            if (!pressed || editing == null) {
                return;
            }
            if (!dragging && Math.hypot(event.x() - pressX, event.y() - pressY) >= DRAG_THRESHOLD) {
                dragging = true;
            }
            if (dragging) {
                editing.dragging(event.x(), event.y());
            }
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            if (!pressed) {
                return;
            }
            pressed = false;
            if (dragging && editing != null) {
                dragging = false;
                editing.dropped(event.x(), event.y());
            } else {
                onSelect.run();
            }
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
