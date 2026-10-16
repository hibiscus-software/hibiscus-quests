/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import software.hibiscus.hibiscusquests.client.NodeState;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.client.QuestsClientConfig;

// The chapter map in the middle of the book: its quests as nodes on a grid, joined by dependency lines, with the
// chapter's images and labels. Dragged to pan, the mouse wheel zooms (0.5x to 3x, about the mouse). Not a widget: the
// screen draws it under its widgets and passes it the clicks they don't take, so search results and buttons over the
// map always win. The view (zoom and pan) is kept per chapter in BookUiState.
final class BookCanvas {
    // Grid pixels per unit at zoom 1, and a size-1 node's width.
    static final float GRID = 24;
    static final float NODE = 20;
    static final double MIN_ZOOM = 0.5;
    static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 1.15;
    private static final int DRAG_THRESHOLD = 3;

    // A node on the map: a quest of this chapter, or a link showing a quest from another one.
    record Node(Quest quest, @Nullable QuestLink link, double gx, double gy, double size, String shape) {
        QuestRef ref() {
            return quest.ref();
        }
    }

    private final Consumer<Quest> openQuest;
    private final Consumer<String> openUrl;
    private int x;
    private int y;
    private int width;
    private int height;
    private @Nullable Chapter chapter;
    private boolean locked;
    private double zoom = 1;
    private double panX;
    private double panY;
    private boolean pressed;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private @Nullable QuestRef selected;

    BookCanvas(Consumer<Quest> openQuest, Consumer<String> openUrl) {
        this.openQuest = openQuest;
        this.openUrl = openUrl;
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

    float nodeSize(Node node) {
        return (float) (NODE * node.size() * zoom);
    }

    // The nodes players see: the chapter's visible quests, and its links to other chapters' visible quests.
    List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();
        if (chapter == null || locked) {
            return nodes;
        }
        BookSettings settings = ClientBook.book().settings();
        double chapterSize = chapter.info().defaultSize().orElse(settings.defaultSize());
        String chapterShape = chapter.info().defaultShape().orElse(settings.defaultShape());
        for (Quest quest : chapter.quests()) {
            if (ClientBook.isVisible(quest)) {
                nodes.add(new Node(quest, null, quest.display().x(), quest.display().y(), quest.display().size().orElse(chapterSize),
                        quest.display().shape().orElse(chapterShape)));
            }
        }
        for (QuestLink link : chapter.links()) {
            QuestRef.parseRelative(link.quest(), chapter.id()).flatMap(ref -> ClientBook.book().quest(ref))
                    .filter(ClientBook::isVisible)
                    .ifPresent(quest -> nodes.add(new Node(quest, link, link.x(), link.y(), link.size().orElse(chapterSize),
                            link.shape().orElse(quest.display().shape().orElse(chapterShape)))));
        }
        return nodes;
    }

    // ---- drawing ----

    void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
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
        if (locked) {
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
        List<Node> nodes = nodes();
        drawLines(graphics, nodes);
        Node hovered = contains(mouseX, mouseY) ? nodeAt(nodes, mouseX, mouseY) : null;
        for (Node node : nodes) {
            float size = nodeSize(node);
            NodeState state = ClientBook.state(node.quest());
            QuestDraw.node(graphics, font, state, ClientBook.icon(node.quest()), node.shape(), screenX(node.gx()), screenY(node.gy()),
                    size, (float) ClientBook.fraction(node.quest()), node == hovered, node.ref().equals(selected));
            if (node.link() != null) {
                int badge = Math.max(6, Math.round(size / 3));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QuestDraw.LINK, Math.round(screenX(node.gx()) - size / 2),
                        Math.round(screenY(node.gy()) + size / 2 - badge), badge, badge);
            }
        }
        drawImages(graphics, ImageLayer.FRONT);
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
        int color = QuestDraw.mix(HibiscusTheme.current().color(HibiscusTheme.RECESS), 0xFFFFFFFF, 0.06f);
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
        for (CanvasImage image : chapter.images()) {
            if (image.layer() != layer) {
                continue;
            }
            float w = (float) (image.width() * GRID * zoom);
            float h = (float) (image.height() * GRID * zoom);
            graphics.pose().pushMatrix();
            graphics.pose().translate(screenX(image.x()), screenY(image.y()));
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
        }
    }

    private void drawLabels(GuiGraphicsExtractor graphics, Font font) {
        if (chapter == null) {
            return;
        }
        int defaultColor = HibiscusTheme.current().text();
        for (CanvasLabel label : chapter.labels()) {
            Component text = BookText.component(label.text());
            float scale = (float) (label.scale() * zoom);
            graphics.pose().pushMatrix();
            graphics.pose().translate(screenX(label.x()), screenY(label.y()));
            graphics.pose().scale(scale, scale);
            graphics.text(font, text, -font.width(text) / 2, -4, label.color().map(rgb -> 0xFF000000 | rgb).orElse(defaultColor), false);
            graphics.pose().popMatrix();
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
            if (node.link() != null || node.quest().display().hideDependencyLines().orElse(chapterHides)) {
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
        if (state == NodeState.SECRET) {
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
        return contains(mx, my) ? nodeAt(nodes(), mx, my) : null;
    }

    boolean mouseClicked(double mx, double my) {
        if (!contains(mx, my)) {
            return false;
        }
        pressed = true;
        dragged = false;
        pressX = mx;
        pressY = my;
        return true;
    }

    boolean mouseDragged(double mx, double my, double dx, double dy) {
        if (!pressed) {
            return false;
        }
        if (!dragged && Math.hypot(mx - pressX, my - pressY) >= DRAG_THRESHOLD) {
            dragged = true;
        }
        if (dragged) {
            panX -= dx / zoom;
            panY -= dy / zoom;
        }
        return true;
    }

    boolean mouseReleased(double mx, double my) {
        if (!pressed) {
            return false;
        }
        pressed = false;
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
