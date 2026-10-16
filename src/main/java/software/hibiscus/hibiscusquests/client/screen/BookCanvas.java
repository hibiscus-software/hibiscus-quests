/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookSettings;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasImage;
import software.hibiscus.hibiscusquests.book.CanvasElements.CanvasLabel;
import software.hibiscus.hibiscusquests.book.CanvasElements.QuestLink;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ImageLayer;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.client.BookUiState;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.ClientQuestState;
import software.hibiscus.hibiscusquests.client.NodeState;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.client.QuestsClientConfig;
import software.hibiscus.hibiscusquests.client.edit.EditActions;
import software.hibiscus.hibiscusquests.client.edit.EditActions.Element;
import software.hibiscus.hibiscusquests.client.edit.EditActions.ElementKind;

// The chapter map in the middle of the book: its quests as nodes on a grid, joined by dependency lines, with the
// chapter's images and labels. Dragged to pan, the mouse wheel zooms (0.5x to 3x, about the mouse). Not a widget: the
// screen draws it under its widgets and passes it the clicks they don't take, so search results and buttons over the
// map always win. The view (zoom and pan) is kept per chapter in BookUiState.
//
// In edit mode it shows every quest (hidden ones too) and the editor works on it: click to select (Shift adds, a drag
// on empty space selects a box), drag the selection to move it (snapping to half the grid; Alt places it freely),
// Ctrl-click a node to make it a dependency of the selected quest or stop it being one, double-click a quest to open
// it, and right-drag to pan; a right click without dragging asks the screen for a context menu.
final class BookCanvas {
    // Grid pixels per unit at zoom 1, and a size-1 node's width.
    static final float GRID = 24;
    static final float NODE = 20;
    static final double MIN_ZOOM = 0.5;
    static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 1.15;
    private static final int DRAG_THRESHOLD = 3;
    private static final double SNAP = 0.5;

    // A node on the map: a quest of this chapter, or a link showing a quest from another one.
    record Node(Quest quest, @Nullable QuestLink link, double gx, double gy, double size, String shape) {
        QuestRef ref() {
            return quest.ref();
        }

        Element element() {
            return link != null ? Element.link(link.id()) : Element.quest(quest.id());
        }
    }

    // What the screen does for the editor: a context menu for an element (or empty space, at a grid point), and
    // editing an image or label (double-clicked).
    interface Editor {
        void contextMenu(@Nullable Element element, int mouseX, int mouseY, double gx, double gy);

        void edit(Element element);
    }

    private enum Drag { NONE, PAN, MOVE, BOX }

    private final Consumer<Quest> openQuest;
    private final Consumer<String> openUrl;
    private final Editor editor;
    private int x;
    private int y;
    private int width;
    private int height;
    private @Nullable Chapter chapter;
    private boolean locked;
    private boolean editing;
    private double zoom = 1;
    private double panX;
    private double panY;
    private boolean pressed;
    private boolean dragged;
    private int pressButton;
    private double pressX;
    private double pressY;
    private @Nullable QuestRef selected;
    // Edit mode.
    private final Set<Element> selection = new LinkedHashSet<>();
    private Drag drag = Drag.NONE;
    private @Nullable Element pressedOn;
    private double moveX;
    private double moveY;
    // A move that's been sent: drawn where it's going until the book changes (the server's patch, or a refusal).
    private double pendingX;
    private double pendingY;
    private int pendingVersion = -1;
    private double boxX;
    private double boxY;
    private double mouseX;
    private double mouseY;

    BookCanvas(Consumer<Quest> openQuest, Consumer<String> openUrl, Editor editor) {
        this.openQuest = openQuest;
        this.openUrl = openUrl;
        this.editor = editor;
    }

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    void setSelected(@Nullable QuestRef quest) {
        this.selected = quest;
    }

    void setEditing(boolean editing) {
        if (this.editing != editing) {
            selection.clear();
        }
        this.editing = editing;
    }

    // Shows a chapter, at its remembered view or fitted to its quests.
    void show(@Nullable Chapter newChapter, boolean isLocked) {
        if (chapter != null && newChapter != null && chapter.id().equals(newChapter.id())) {
            chapter = newChapter;
            locked = isLocked;
            return;
        }
        saveView();
        chapter = newChapter;
        locked = isLocked;
        selection.clear();
        if (chapter == null) {
            return;
        }
        Optional<BookUiState.View> view = BookUiState.view(chapter.id());
        if (view.isPresent()) {
            zoom = Mth.clamp(view.get().zoom(), MIN_ZOOM, MAX_ZOOM);
            panX = view.get().panX();
            panY = view.get().panY();
        } else {
            fit();
        }
    }

    void saveView() {
        if (chapter != null) {
            BookUiState.setView(chapter.id(), new BookUiState.View(zoom, panX, panY));
        }
    }

    // Zooms and pans so every visible node fits, no closer than 2.5x.
    void fit() {
        List<Node> nodes = nodes();
        if (nodes.isEmpty()) {
            zoom = 1;
            panX = 0;
            panY = 0;
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Node node : nodes) {
            minX = Math.min(minX, node.gx());
            minY = Math.min(minY, node.gy());
            maxX = Math.max(maxX, node.gx());
            maxY = Math.max(maxY, node.gy());
        }
        // Images and labels count too, so nothing the chapter shows starts off screen.
        if (chapter != null) {
            for (CanvasImage image : chapter.images()) {
                minX = Math.min(minX, image.x() - image.width() / 2);
                maxX = Math.max(maxX, image.x() + image.width() / 2);
                minY = Math.min(minY, image.y() - image.height() / 2);
                maxY = Math.max(maxY, image.y() + image.height() / 2);
            }
            for (CanvasLabel label : chapter.labels()) {
                minX = Math.min(minX, label.x() - 1);
                maxX = Math.max(maxX, label.x() + 1);
                minY = Math.min(minY, label.y());
                maxY = Math.max(maxY, label.y());
            }
        }
        panX = (minX + maxX) / 2 * GRID;
        panY = (minY + maxY) / 2 * GRID;
        double spanX = (maxX - minX) * GRID + NODE * 5;
        double spanY = (maxY - minY) * GRID + NODE * 5;
        zoom = Mth.clamp(Math.min(width / spanX, height / spanY), MIN_ZOOM, 2.5);
    }

    double zoom() {
        return zoom;
    }

    // Grid units to screen pixels.
    float screenX(double gx) {
        return (float) (x + width / 2.0 + (gx * GRID - panX) * zoom);
    }

    float screenY(double gy) {
        return (float) (y + height / 2.0 + (gy * GRID - panY) * zoom);
    }

    // Screen pixels to grid units.
    double gridX(double sx) {
        return ((sx - x - width / 2.0) / zoom + panX) / GRID;
    }

    double gridY(double sy) {
        return ((sy - y - height / 2.0) / zoom + panY) / GRID;
    }

    static double snap(double value) {
        return Math.round(value / SNAP) * SNAP;
    }

    // The grid point under the mouse, snapped, or the middle of the view when the mouse isn't over the map.
    double[] gridAtMouse() {
        double mx = contains(mouseX, mouseY) ? mouseX : x + width / 2.0;
        double my = contains(mouseX, mouseY) ? mouseY : y + height / 2.0;
        return new double[] {snap(gridX(mx)), snap(gridY(my))};
    }

    float nodeSize(Node node) {
        return (float) (NODE * node.size() * zoom);
    }

    // The nodes players see: the chapter's visible quests, and its links to other chapters' visible quests (editors
    // see them all).
    List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();
        if (chapter == null || (locked && !editing)) {
            return nodes;
        }
        BookSettings settings = ClientBook.book().settings();
        double chapterSize = chapter.info().defaultSize().orElse(settings.defaultSize());
        String chapterShape = chapter.info().defaultShape().orElse(settings.defaultShape());
        for (Quest quest : chapter.quests()) {
            if (editing || ClientBook.isVisible(quest)) {
                nodes.add(new Node(quest, null, quest.display().x(), quest.display().y(), quest.display().size().orElse(chapterSize),
                        quest.display().shape().orElse(chapterShape)));
            }
        }
        for (QuestLink link : chapter.links()) {
            QuestRef.parseRelative(link.quest(), chapter.id()).flatMap(ref -> ClientBook.book().quest(ref))
                    .filter(quest -> editing || ClientBook.isVisible(quest))
                    .ifPresent(quest -> nodes.add(new Node(quest, link, link.x(), link.y(), link.size().orElse(chapterSize),
                            link.shape().orElse(quest.display().shape().orElse(chapterShape)))));
        }
        return nodes;
    }

    // ---- edit mode: selection and the offset a move draws ----

    Set<Element> selection() {
        return selection;
    }

    void select(Collection<Element> elements) {
        selection.clear();
        selection.addAll(elements);
    }

    void selectAll() {
        selection.clear();
        nodes().forEach(node -> selection.add(node.element()));
        if (chapter != null) {
            for (int n = 0; n < chapter.images().size(); n++) {
                selection.add(Element.image(n));
            }
            for (int n = 0; n < chapter.labels().size(); n++) {
                selection.add(Element.label(n));
            }
        }
    }

    // The one quest selected, for Ctrl-click dependencies and the like.
    Optional<String> singleQuest() {
        if (selection.size() != 1) {
            return Optional.empty();
        }
        Element only = selection.iterator().next();
        return only.kind() == ElementKind.QUEST ? Optional.of(only.key()) : Optional.empty();
    }

    // A move was sent: keep drawing the selection there until the book changes.
    void movedBy(double dx, double dy) {
        pendingX = dx;
        pendingY = dy;
        pendingVersion = ClientQuestState.version();
    }

    private double offsetX(Element element) {
        if (!editing || !selection.contains(element)) {
            return 0;
        }
        if (drag == Drag.MOVE && dragged) {
            return moveX;
        }
        return pendingVersion == ClientQuestState.version() ? pendingX : 0;
    }

    private double offsetY(Element element) {
        if (!editing || !selection.contains(element)) {
            return 0;
        }
        if (drag == Drag.MOVE && dragged) {
            return moveY;
        }
        return pendingVersion == ClientQuestState.version() ? pendingY : 0;
    }

    // The nodes where they're drawn (a selection being moved, or just moved, is drawn at its new place).
    private List<Node> placedNodes() {
        List<Node> placed = new ArrayList<>();
        for (Node node : nodes()) {
            Element element = node.element();
            double dx = offsetX(element);
            double dy = offsetY(element);
            placed.add(dx == 0 && dy == 0 ? node : new Node(node.quest(), node.link(), node.gx() + dx, node.gy() + dy, node.size(), node.shape()));
        }
        return placed;
    }

    // ---- drawing ----

    void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (mouseX >= 0) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }
        HibiscusTheme theme = HibiscusTheme.current();
        theme.inset(graphics, x, y, width, height);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        drawBackground(graphics);
        if (chapter == null) {
            centred(graphics, font, Component.translatable(ClientBook.book().chapters().isEmpty()
                    ? "hibiscusquests.book.empty" : "hibiscusquests.book.no_chapter"), theme.textMuted());
            graphics.disableScissor();
            return;
        }
        if (locked && !editing) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QuestDraw.LOCK, x + width / 2 - 8, y + height / 2 - 22, 16, 16);
            String message = ClientBook.book().settings().lockMessage();
            centred(graphics, font, message.isBlank() ? Component.translatable("hibiscusquests.book.chapter_locked") : BookText.component(message),
                    theme.textMuted());
            graphics.disableScissor();
            return;
        }
        drawGrid(graphics);
        drawImages(graphics, ImageLayer.BEHIND);
        drawLabels(graphics, font);
        List<Node> nodes = placedNodes();
        drawLines(graphics, nodes);
        Node hovered = contains(mouseX, mouseY) ? nodeAt(nodes, mouseX, mouseY) : null;
        for (Node node : nodes) {
            float size = nodeSize(node);
            NodeState state = ClientBook.state(node.quest());
            boolean isSelected = editing ? selection.contains(node.element()) : node.ref().equals(selected);
            QuestDraw.node(graphics, font, state, ClientBook.icon(node.quest()), node.shape(), screenX(node.gx()), screenY(node.gy()),
                    size, (float) ClientBook.fraction(node.quest()), node == hovered, isSelected);
            if (editing && isSelected) {
                int half = Math.round(size / 2) + 3;
                graphics.outline(Math.round(screenX(node.gx())) - half, Math.round(screenY(node.gy())) - half, half * 2, half * 2, theme.accent());
            }
            if (node.link() != null) {
                int badge = Math.max(6, Math.round(size / 3));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QuestDraw.LINK, Math.round(screenX(node.gx()) - size / 2),
                        Math.round(screenY(node.gy()) + size / 2 - badge), badge, badge);
            }
            if (editing && node.link() == null && !ClientBook.isVisible(node.quest())) {
                // Players can't see this one yet (its hide mode): a dashed corner says so.
                int corner = Math.round(screenX(node.gx()) + size / 2 - 3);
                int top = Math.round(screenY(node.gy()) - size / 2);
                graphics.fill(corner, top, corner + 3, top + 3, theme.textMuted());
            }
        }
        drawImages(graphics, ImageLayer.FRONT);
        if (editing && drag == Drag.BOX && dragged) {
            int left = (int) Math.min(boxX, mouseX);
            int top = (int) Math.min(boxY, mouseY);
            int right = (int) Math.max(boxX, mouseX);
            int bottom = (int) Math.max(boxY, mouseY);
            graphics.fill(left, top, right, bottom, (theme.accent() & 0xFFFFFF) | 0x30000000);
            graphics.outline(left, top, right - left, bottom - top, theme.accent());
        }
        graphics.disableScissor();
        if (hovered != null && !pressed) {
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    private void centred(GuiGraphicsExtractor graphics, Font font, Component text, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(40, width - 24));
        int top = y + height / 2 - lines.size() * 5 + 4;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + (width - font.width(line)) / 2, top, color, false);
            top += 10;
        }
    }

    // The book's background: a colour ("#rrggbb") or a texture, tiled, behind the map.
    private void drawBackground(GuiGraphicsExtractor graphics) {
        Optional<String> background = ClientBook.book().settings().background();
        if (background.isEmpty()) {
            return;
        }
        String value = background.get().strip();
        if (value.startsWith("#")) {
            try {
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, HibiscusTheme.parseColor(value));
            } catch (IllegalArgumentException ignored) {
                // not a colour: no background
            }
            return;
        }
        Identifier texture = Identifier.tryParse(value);
        if (texture != null) {
            int tile = 64;
            int offX = Math.floorMod((int) Math.round(-panX * zoom / 4), tile);
            int offY = Math.floorMod((int) Math.round(-panY * zoom / 4), tile);
            for (int ty = y - tile + offY; ty < y + height; ty += tile) {
                for (int tx = x - tile + offX; tx < x + width; tx += tile) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, tx, ty, 0, 0, tile, tile, tile, tile);
                }
            }
        }
    }

    // Faint dots on the grid's whole units, when zoomed in far enough to read them.
    private void drawGrid(GuiGraphicsExtractor graphics) {
        if (zoom < 0.75) {
            return;
        }
        int color = QuestDraw.mix(HibiscusTheme.current().color(HibiscusTheme.RECESS), 0xFFFFFFFF, editing ? 0.12f : 0.06f);
        double startGx = Math.floor((panX - width / 2.0 / zoom) / GRID);
        double startGy = Math.floor((panY - height / 2.0 / zoom) / GRID);
        for (double gy = startGy; screenY(gy) < y + height; gy++) {
            for (double gx = startGx; screenX(gx) < x + width; gx++) {
                int px = Math.round(screenX(gx));
                int py = Math.round(screenY(gy));
                graphics.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private void drawImages(GuiGraphicsExtractor graphics, ImageLayer layer) {
        if (chapter == null) {
            return;
        }
        for (int n = 0; n < chapter.images().size(); n++) {
            CanvasImage image = chapter.images().get(n);
            if (image.layer() != layer) {
                continue;
            }
            Element element = Element.image(n);
            float cx = screenX(image.x() + offsetX(element));
            float cy = screenY(image.y() + offsetY(element));
            float w = (float) (image.width() * GRID * zoom);
            float h = (float) (image.height() * GRID * zoom);
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().rotate((float) Math.toRadians(image.rotation()));
            if (image.image().texture().isPresent()) {
                int alpha = Math.round((float) image.alpha() * 255) << 24;
                int iw = Math.max(1, Math.round(w));
                int ih = Math.max(1, Math.round(h));
                graphics.blit(RenderPipelines.GUI_TEXTURED, image.image().texture().get(), -iw / 2, -ih / 2, 0, 0, iw, ih, iw, ih, alpha | 0xFFFFFF);
            } else {
                float size = Math.min(w, h);
                QuestDraw.item(graphics, image.image().item(), -size / 2, -size / 2, size);
            }
            graphics.pose().popMatrix();
            if (editing && selection.contains(element)) {
                graphics.outline(Math.round(cx - w / 2) - 1, Math.round(cy - h / 2) - 1, Math.round(w) + 2, Math.round(h) + 2,
                        HibiscusTheme.current().accent());
            }
        }
    }

    private void drawLabels(GuiGraphicsExtractor graphics, Font font) {
        if (chapter == null) {
            return;
        }
        int defaultColor = HibiscusTheme.current().text();
        for (int n = 0; n < chapter.labels().size(); n++) {
            CanvasLabel label = chapter.labels().get(n);
            Element element = Element.label(n);
            Component text = BookText.component(label.text());
            float scale = (float) (label.scale() * zoom);
            float cx = screenX(label.x() + offsetX(element));
            float cy = screenY(label.y() + offsetY(element));
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.text(font, text, -font.width(text) / 2, -4, label.color().map(rgb -> 0xFF000000 | rgb).orElse(defaultColor), false);
            graphics.pose().popMatrix();
            if (editing && selection.contains(element)) {
                float halfW = font.width(text) * scale / 2 + 2;
                float halfH = 5 * scale + 1;
                graphics.outline(Math.round(cx - halfW), Math.round(cy - halfH), Math.round(halfW * 2), Math.round(halfH * 2),
                        HibiscusTheme.current().accent());
            }
        }
    }

    // A line from each dependency to the quest needing it: the accent once the dependency is done (moving dashes when it
    // leads into a quest that's open now), grey before. Dependencies in other chapters are drawn from a quest link to
    // them when the chapter has one.
    private void drawLines(GuiGraphicsExtractor graphics, List<Node> nodes) {
        if (chapter == null) {
            return;
        }
        Map<QuestRef, Node> byRef = new HashMap<>();
        for (Node node : nodes) {
            byRef.putIfAbsent(node.ref(), node);
        }
        boolean chapterHides = chapter.info().defaultHideDependencyLines();
        int done = QuestDraw.accent();
        int notDone = HibiscusTheme.current().color(HibiscusTheme.TEXT_DISABLED);
        float thickness = (float) Math.max(1, 1.5 * zoom);
        for (Node node : nodes) {
            // Editors see every line, even hidden ones, so they can see what depends on what.
            if (node.link() != null || (!editing && node.quest().display().hideDependencyLines().orElse(chapterHides))) {
                continue;
            }
            NodeState target = ClientBook.state(node.quest());
            for (QuestRef ref : node.quest().dependencies()) {
                Node from = byRef.get(ref);
                if (from == null) {
                    continue;
                }
                boolean complete = ClientBook.state(from.quest()).isComplete();
                boolean leadsIn = complete && (target == NodeState.AVAILABLE || target == NodeState.IN_PROGRESS);
                float x0 = screenX(from.gx());
                float y0 = screenY(from.gy());
                float x1 = screenX(node.gx());
                float y1 = screenY(node.gy());
                // Start and end at the nodes' edges rather than their centres.
                float length = (float) Math.hypot(x1 - x0, y1 - y0);
                if (length < 1) {
                    continue;
                }
                float r0 = nodeSize(from) / 2 + 1;
                float r1 = nodeSize(node) / 2 + 1;
                if (r0 + r1 >= length) {
                    continue;
                }
                float ux = (x1 - x0) / length;
                float uy = (y1 - y0) / length;
                QuestDraw.line(graphics, x0 + ux * r0, y0 + uy * r0, x1 - ux * r1, y1 - uy * r1, thickness, complete ? done : notDone, leadsIn);
            }
        }
    }

    private List<Component> tooltip(Node node) {
        Quest quest = node.quest();
        NodeState state = ClientBook.state(quest);
        List<Component> lines = new ArrayList<>();
        if (state == NodeState.SECRET && !editing) {
            lines.add(Component.translatable("hibiscusquests.book.secret").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(BookText.titleOr(quest.title(), quest.id()).copy().withColor(HibiscusTheme.current().text()));
        if (!quest.display().subtitle().isBlank()) {
            lines.add(BookText.component(quest.display().subtitle()).copy().withStyle(ChatFormatting.GRAY));
        }
        if (node.link() != null) {
            Component chapterTitle = ClientBook.book().chapter(quest.chapter()).map(c -> BookText.titleOr(c.title(), c.id()))
                    .orElse(Component.literal(quest.chapter()));
            lines.add(Component.translatable("hibiscusquests.book.linked_from", chapterTitle).withStyle(ChatFormatting.DARK_GRAY));
        }
        int color = switch (state) {
            case CLAIMABLE -> HibiscusTheme.current().color(HibiscusTheme.GOLD);
            case DONE -> QuestDraw.accent();
            case LOCKED -> HibiscusTheme.current().color(HibiscusTheme.TEXT_DISABLED);
            default -> HibiscusTheme.current().textMuted();
        };
        lines.add(Component.translatable(state.translationKey()).withColor(color));
        if (!state.isComplete()) {
            int shown = 0;
            for (QuestTask task : quest.tasks()) {
                if (shown++ == 4) {
                    lines.add(Component.translatable("hibiscusquests.book.more_tasks", quest.tasks().size() - 4).withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                long value = ClientBook.taskProgress(quest, task);
                Component title = task.title().map(BookText::component).orElseGet(() -> task.task().title());
                lines.add(Component.translatable("hibiscusquests.book.task_line", title, value, task.target())
                        .withStyle(value >= task.target() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        if (editing) {
            lines.add(Component.literal(node.link() != null ? node.link().id() + " → " + quest.ref() : quest.ref().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (!ClientBook.isVisible(quest)) {
                lines.add(Component.translatable("hibiscusquests.edit.hidden_from_players").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    // ---- input ----

    private @Nullable Node nodeAt(List<Node> nodes, double mx, double my) {
        Node best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Node node : nodes) {
            double half = nodeSize(node) / 2 + 1;
            double dx = mx - screenX(node.gx());
            double dy = my - screenY(node.gy());
            if (Math.abs(dx) <= half && Math.abs(dy) <= half) {
                double distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    best = node;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    @Nullable Node nodeAt(double mx, double my) {
        return contains(mx, my) ? nodeAt(placedNodes(), mx, my) : null;
    }

    // What's under the mouse in edit mode: a node, else a label, else an image (front ones first).
    @Nullable Element elementAt(double mx, double my) {
        if (!contains(mx, my) || chapter == null) {
            return null;
        }
        Node node = nodeAt(mx, my);
        if (node != null) {
            return node.element();
        }
        Font font = Minecraft.getInstance().font;
        for (int n = chapter.labels().size() - 1; n >= 0; n--) {
            CanvasLabel label = chapter.labels().get(n);
            double scale = label.scale() * zoom;
            double halfW = font.width(BookText.component(label.text())) * scale / 2 + 2;
            double halfH = 5 * scale + 1;
            if (Math.abs(mx - screenX(label.x())) <= halfW && Math.abs(my - screenY(label.y())) <= halfH) {
                return Element.label(n);
            }
        }
        for (int n = chapter.images().size() - 1; n >= 0; n--) {
            CanvasImage image = chapter.images().get(n);
            double w = image.width() * GRID * zoom / 2;
            double h = image.height() * GRID * zoom / 2;
            if (Math.abs(mx - screenX(image.x())) <= w && Math.abs(my - screenY(image.y())) <= h) {
                return Element.image(n);
            }
        }
        return null;
    }

    // Where an element sits on the grid.
    private Optional<double[]> positionOf(Element element) {
        if (chapter == null) {
            return Optional.empty();
        }
        return switch (element.kind()) {
            case QUEST -> chapter.quest(element.key()).map(quest -> new double[] {quest.display().x(), quest.display().y()});
            case LINK -> chapter.links().stream().filter(link -> link.id().equals(element.key())).findFirst()
                    .map(link -> new double[] {link.x(), link.y()});
            case IMAGE -> element.index() < chapter.images().size()
                    ? Optional.of(new double[] {chapter.images().get(element.index()).x(), chapter.images().get(element.index()).y()}) : Optional.empty();
            case LABEL -> element.index() < chapter.labels().size()
                    ? Optional.of(new double[] {chapter.labels().get(element.index()).x(), chapter.labels().get(element.index()).y()}) : Optional.empty();
        };
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (!contains(mx, my)) {
            return false;
        }
        int button = event.button();
        if (!editing) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            pressed = true;
            dragged = false;
            pressButton = button;
            pressX = mx;
            pressY = my;
            drag = Drag.PAN;
            return true;
        }
        pressed = true;
        dragged = false;
        pressButton = button;
        pressX = mx;
        pressY = my;
        pressedOn = null;
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            drag = Drag.PAN;
            return true;
        }
        Element element = elementAt(mx, my);
        if (element == null) {
            if (!event.hasShiftDown()) {
                selection.clear();
            }
            drag = Drag.BOX;
            boxX = mx;
            boxY = my;
            return true;
        }
        Optional<String> selectedQuest = singleQuest();
        if (event.hasControlDown() && selectedQuest.isPresent() && chapter != null
                && (element.kind() == ElementKind.QUEST || element.kind() == ElementKind.LINK)) {
            Node node = nodeAt(mx, my);
            if (node != null && EditActions.isEditable(chapter.id())) {
                EditActions.toggleDependency(chapter.id(), selectedQuest.get(), node.ref());
            }
            drag = Drag.NONE;
            return true;
        }
        if (doubleClick) {
            drag = Drag.NONE;
            if (element.kind() == ElementKind.QUEST || element.kind() == ElementKind.LINK) {
                Node node = nodeAt(mx, my);
                if (node != null) {
                    openQuest.accept(node.quest());
                }
            } else {
                editor.edit(element);
            }
            return true;
        }
        if (event.hasShiftDown()) {
            if (!selection.remove(element)) {
                selection.add(element);
            }
        } else if (!selection.contains(element)) {
            selection.clear();
            selection.add(element);
        }
        pressedOn = element;
        drag = Drag.MOVE;
        moveX = 0;
        moveY = 0;
        return true;
    }

    boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressed) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        mouseX = mx;
        mouseY = my;
        if (!dragged && Math.hypot(mx - pressX, my - pressY) >= DRAG_THRESHOLD) {
            dragged = true;
        }
        if (!dragged) {
            return true;
        }
        switch (drag) {
            case PAN -> {
                panX -= dx / zoom;
                panY -= dy / zoom;
            }
            case MOVE -> {
                double rawX = (mx - pressX) / zoom / GRID;
                double rawY = (my - pressY) / zoom / GRID;
                Optional<double[]> anchor = pressedOn == null ? Optional.empty() : positionOf(pressedOn);
                if (event.hasAltDown() || anchor.isEmpty()) {
                    moveX = rawX;
                    moveY = rawY;
                } else {
                    // The node grabbed lands on the half grid; the rest of the selection moves with it.
                    moveX = snap(anchor.get()[0] + rawX) - anchor.get()[0];
                    moveY = snap(anchor.get()[1] + rawY) - anchor.get()[1];
                }
            }
            default -> {
            }
        }
        return true;
    }

    boolean mouseReleased(MouseButtonEvent event) {
        if (!pressed || event.button() != pressButton) {
            return false;
        }
        pressed = false;
        double mx = event.x();
        double my = event.y();
        Drag was = drag;
        drag = Drag.NONE;
        if (!editing) {
            if (dragged) {
                return true;
            }
            Node node = nodeAt(mx, my);
            if (node != null) {
                openQuest.accept(node.quest());
                return true;
            }
            imageAt(mx, my).flatMap(CanvasImage::click).ifPresent(this::clickImage);
            return true;
        }
        switch (was) {
            case MOVE -> {
                if (dragged && chapter != null && (moveX != 0 || moveY != 0) && EditActions.isEditable(chapter.id())) {
                    EditActions.move(chapter.id(), List.copyOf(selection), moveX, moveY);
                    movedBy(moveX, moveY);
                }
            }
            case BOX -> {
                if (dragged) {
                    double left = Math.min(boxX, mx);
                    double right = Math.max(boxX, mx);
                    double top = Math.min(boxY, my);
                    double bottom = Math.max(boxY, my);
                    for (Node node : placedNodes()) {
                        double sx = screenX(node.gx());
                        double sy = screenY(node.gy());
                        if (sx >= left && sx <= right && sy >= top && sy <= bottom) {
                            selection.add(node.element());
                        }
                    }
                }
            }
            case PAN -> {
                if (!dragged && pressButton == InputConstants.MOUSE_BUTTON_RIGHT) {
                    Element element = elementAt(mx, my);
                    if (element != null && !selection.contains(element)) {
                        selection.clear();
                        selection.add(element);
                    }
                    editor.contextMenu(element, (int) mx, (int) my, snap(gridX(mx)), snap(gridY(my)));
                }
            }
            default -> {
            }
        }
        moveX = 0;
        moveY = 0;
        return true;
    }

    private void clickImage(String target) {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            openUrl.accept(target);
        } else if (chapter != null) {
            QuestRef.parseRelative(target, chapter.id()).flatMap(ref -> ClientBook.book().quest(ref)).ifPresent(openQuest);
        }
    }

    private Optional<CanvasImage> imageAt(double mx, double my) {
        if (chapter == null) {
            return Optional.empty();
        }
        for (CanvasImage image : chapter.images().reversed()) {
            double w = image.width() * GRID * zoom / 2;
            double h = image.height() * GRID * zoom / 2;
            if (image.click().isPresent() && Math.abs(mx - screenX(image.x())) <= w && Math.abs(my - screenY(image.y())) <= h) {
                return Optional.of(image);
            }
        }
        return Optional.empty();
    }

    // Zooms by wheel steps, keeping the map point under the mouse where it is.
    boolean mouseScrolled(double mx, double my, double scroll) {
        if (!contains(mx, my) || scroll == 0) {
            return false;
        }
        double sensitivity = QuestsClientConfig.get(QuestsClientConfig.ZOOM_SENSITIVITY);
        double factor = Math.pow(ZOOM_STEP, scroll * sensitivity);
        double newZoom = Mth.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        // The map point under the mouse stays put: pan' = point - (mouse - centre) / zoom'.
        double pointX = panX + (mx - cx) / zoom;
        double pointY = panY + (my - cy) / zoom;
        zoom = newZoom;
        panX = pointX - (mx - cx) / zoom;
        panY = pointY - (my - cy) / zoom;
        return true;
    }

    // Centres the map on a quest (search results, dependency links).
    void centreOn(Quest quest) {
        panX = quest.display().x() * GRID;
        panY = quest.display().y() * GRID;
    }
}
