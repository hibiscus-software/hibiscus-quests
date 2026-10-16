/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscuslib.api.team.ClientTeams;
import software.hibiscus.hibiscuslib.api.team.Team;
import software.hibiscus.hibiscuslib.button.ButtonSpec;
import software.hibiscus.hibiscuslib.button.HibiscusButtons;
import software.hibiscus.hibiscuslib.client.HibiscusClientNetwork;
import software.hibiscus.hibiscuslib.client.gui.BaseScreen;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.IconButton;
import software.hibiscus.hibiscuslib.client.gui.widget.Panel;
import software.hibiscus.hibiscuslib.client.gui.widget.ScrollPanel;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscuslib.client.gui.widget.TextInput;
import software.hibiscus.hibiscusquests.api.ChapterStatus;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.event.QuestBookOpenEvent;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Chapter;
import software.hibiscus.hibiscusquests.book.ChapterGroup;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestBook;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.client.BookUiState;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.ClientQuestState;
import software.hibiscus.hibiscusquests.client.NodeState;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.client.edit.ClientEditor;
import software.hibiscus.hibiscusquests.client.edit.EditActions;
import software.hibiscus.hibiscusquests.client.edit.ListEditorPopup;
import software.hibiscus.hibiscusquests.client.edit.PickerPopup;
import software.hibiscus.hibiscusquests.client.edit.RichTextPopup;
import software.hibiscus.hibiscusquests.network.QuestPayloads;

// The quest book: nearly the whole window. The chapter list down the left (collapsible to icons), a top bar with the
// chapter's title and progress, search, Claim all and the team, and the chapter's map filling the rest; clicking a
// quest opens its view over the map. Rebuilt on every sync, so everything it remembers (chapter, search, the list's
// scroll, the map's view, the open quest) lives in fields or BookUiState, not widgets. Editors get an edit mode
// button in the top bar; in edit mode the map and the chapter list take the editor's clicks, menus and keys
// (EditorMenus), and a toolbar under the list adds chapters and groups and opens the reward tables and settings.
public final class QuestBookScreen extends BaseScreen {
    private static final int TOP = 28;
    private static final int LIST_WIDTH = 128;
    private static final int LIST_COLLAPSED = 26;
    private static final int LIST_HEADER = 16;
    private static final int TOOLBAR = 22;
    private static final int MAX_RESULTS = 8;
    private static final Identifier TEAMS_BUTTON = Identifier.fromNamespaceAndPath("hibiscusteams", "teams");

    private @Nullable String chapterId;
    private boolean collapsed;
    private String query = "";
    private boolean searchFocused;
    private double listScroll;
    private final BookCanvas canvas;
    private final EditorMenus menus;
    // What's being dragged in the list (edit mode: a chapter or group), and where the mouse is.
    private @Nullable Component draggingLabel;
    private double dragX;
    private double dragY;
    private @Nullable ScrollPanel list;
    private @Nullable TextInput search;
    private @Nullable Panel results;
    private final Runnable listener = this::onSync;

    private QuestBookScreen(@Nullable Screen parent) {
        super(Component.translatable("hibiscusquests.book.title"), parent);
        this.collapsed = BookUiState.panelCollapsed();
        this.canvas = new BookCanvas(quest -> openQuest(quest.ref()), this::openUrl, new BookCanvas.Editor() {
            @Override
            public void contextMenu(EditActions.@Nullable Element element, int mouseX, int mouseY, double gx, double gy) {
                Chapter shown = chapter();
                if (shown != null) {
                    menus.canvasMenu(shown, element, mouseX, mouseY, gx, gy);
                }
            }

            @Override
            public void edit(EditActions.Element element) {
                Chapter shown = chapter();
                if (shown != null) {
                    menus.edit(shown, element);
                }
            }
        });
        this.menus = new EditorMenus(this, canvas);
        this.chapterId = BookUiState.lastChapter().orElse(null);
    }

    // Opens the book (on the chapter last viewed, or the quest given), unless a mod cancels QuestBookOpenEvent.
    public static void open(@Nullable QuestRef quest) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        Screen parent = current instanceof QuestBookScreen ? null : current;
        if (NeoForge.EVENT_BUS.post(new QuestBookOpenEvent(Optional.ofNullable(quest))).isCanceled()) {
            return;
        }
        QuestBookScreen screen = new QuestBookScreen(parent);
        minecraft.gui.setScreen(screen);
        if (quest != null) {
            screen.openQuest(quest);
        }
    }

    public static void open() {
        open(null);
    }

    // Nearly the whole window.
    @Override
    protected int preferredWidth() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected int preferredHeight() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isPauseScreen() {
        return ClientBook.book().settings().pauseGame();
    }

    @Override
    public void added() {
        super.added();
        ClientQuestState.removeListener(listener);
        ClientQuestState.addListener(listener);
    }

    @Override
    public void removed() {
        ClientQuestState.removeListener(listener);
        canvas.saveView();
        BookUiState.setPanelCollapsed(collapsed);
        if (chapterId != null) {
            BookUiState.setLastChapter(chapterId);
        }
        BookUiState.save();
        super.removed();
    }

    // The book or progress changed: rebuild (popups too) with the new data.
    private void onSync() {
        if (minecraft != null && minecraft.gui.screen() == this) {
            rebuildWidgets();
        }
    }

    // ---- layout ----

    private int listWidth() {
        return collapsed ? LIST_COLLAPSED : Math.min(LIST_WIDTH, Math.max(80, panelWidth / 4));
    }

    private @Nullable Chapter chapter() {
        QuestBook book = ClientBook.book();
        List<Chapter> visible = ClientBook.visibleChapters();
        if (chapterId != null) {
            Optional<Chapter> current = book.chapter(chapterId).filter(visible::contains);
            if (current.isPresent()) {
                return current.get();
            }
        }
        // The first chapter stands in, but chapterId is kept: a chapter just made shows once the server's patch brings it.
        return visible.isEmpty() ? null : visible.getFirst();
    }

    EditorMenus menus() {
        return menus;
    }

    @Override
    protected void initContent() {
        boolean editing = ClientEditor.isEditing();
        canvas.setEditing(editing);
        Chapter chapter = chapter();
        int top = panelY + 4;

        // The chapter list.
        if (list != null) {
            listScroll = list.scrollAmount();
        }
        int listX = panelX + 4;
        int listTop = panelY + TOP;
        int listW = listWidth();
        addRenderableWidget(new IconButton(listX + (listW - 20) / 2 - (collapsed ? 0 : (listW - 20) / 2), top, 20,
                Icon.sprite(collapsed ? QuestDraw.PANEL_EXPAND : QuestDraw.PANEL_COLLAPSE),
                Component.translatable(collapsed ? "hibiscusquests.book.expand_list" : "hibiscusquests.book.collapse_list"), button -> {
                    collapsed = !collapsed;
                    BookUiState.setPanelCollapsed(collapsed);
                    listScroll = 0;
                    rebuildWidgets();
                }).flat());
        int headerHeight = collapsed ? 0 : LIST_HEADER;
        int toolbar = editing && !collapsed ? TOOLBAR : 0;
        list = addRenderableWidget(new ScrollPanel(listX, listTop + headerHeight, listW, panelY + panelHeight - 4 - listTop - headerHeight - toolbar,
                Panel.Style.INSET));
        fillList(list, chapter);
        list.setScrollAmount(listScroll);
        if (toolbar > 0) {
            int buttonY = panelY + panelHeight - 4 - 20;
            int step = Math.min(22, (listW + 2) / 4);
            addRenderableWidget(new IconButton(listX, buttonY, 20, Icon.sprite(QuestDraw.ADD), Component.translatable("hibiscusquests.edit.new_chapter"),
                    button -> menus.newChapter("")));
            addRenderableWidget(new IconButton(listX + step, buttonY, 20, Icon.sprite(QuestDraw.PANEL_EXPAND),
                    Component.translatable("hibiscusquests.edit.new_group"), button -> menus.newGroup()));
            addRenderableWidget(new IconButton(listX + step * 2, buttonY, 20, Icon.sprite(QuestDraw.TABLE),
                    Component.translatable("hibiscusquests.edit.tables"), button -> menus.tables()));
            addRenderableWidget(new IconButton(listX + step * 3, buttonY, 20, Icon.sprite(QuestDraw.SETTINGS),
                    Component.translatable("hibiscusquests.edit.settings"), button -> menus.settings()));
        }

        // The map.
        int canvasX = listX + listW + 4;
        canvas.setBounds(canvasX, listTop, panelX + panelWidth - 4 - canvasX, panelY + panelHeight - 4 - listTop);
        canvas.show(chapter, chapter != null && ClientBook.isLocked(chapter));

        // The top bar's right side, from the right edge in: team, Claim all, search.
        int right = panelX + panelWidth - 4;
        Optional<Team> team = teamsInstalled() ? ClientTeams.myTeam() : Optional.empty();
        if (team.isPresent()) {
            Component name = team.get().displayName();
            int width = Math.min(96, font.width(name) + 12);
            right -= width;
            TextButton teamButton = new TextButton(right, top, width, 20, name, button -> openTeams());
            teamButton.withTooltip(Component.translatable("hibiscusquests.book.team_tooltip"));
            addRenderableWidget(teamButton);
            right -= 4;
        }
        boolean hud = BookUiState.hudVisible();
        right -= 20;
        IconButton hudToggle = new IconButton(right, top, 20, Icon.sprite(QuestDraw.PIN),
                Component.translatable(hud ? "hibiscusquests.book.hud_on" : "hibiscusquests.book.hud_off"), button -> {
                    BookUiState.setHudVisible(!BookUiState.hudVisible());
                    rebuildWidgets();
                });
        if (!hud) {
            hudToggle.flat();
        }
        addRenderableWidget(hudToggle);
        right -= 4;
        int claimable = ClientBook.unclaimedCount();
        right -= 20;
        IconButton claimAll = new IconButton(right, top, 20, Icon.sprite(QuestDraw.CLAIM_ALL),
                claimable > 0 ? Component.translatable("hibiscusquests.book.claim_all_count", claimable)
                        : Component.translatable("hibiscusquests.book.claim_all_none"),
                button -> HibiscusClientNetwork.sendToServer(QuestPayloads.ClaimAll.INSTANCE));
        claimAll.active = claimable > 0;
        addRenderableWidget(claimAll);
        right -= 4;
        if (ClientEditor.canEdit()) {
            right -= 20;
            IconButton edit = new IconButton(right, top, 20, Icon.sprite(QuestDraw.EDIT),
                    Component.translatable(editing ? "hibiscusquests.edit.mode_on" : "hibiscusquests.edit.mode_off"),
                    button -> ClientEditor.setEditing(!ClientEditor.isEditing()));
            if (!editing) {
                edit.flat();
            }
            addRenderableWidget(edit);
            right -= 4;
            if (editing) {
                right -= 20;
                IconButton redo = new IconButton(right, top, 20, Icon.sprite(QuestDraw.REDO), Component.translatable("hibiscusquests.edit.redo"),
                        button -> ClientEditor.redo()).flat();
                redo.active = ClientEditor.canRedo();
                addRenderableWidget(redo);
                right -= 20;
                IconButton undo = new IconButton(right, top, 20, Icon.sprite(QuestDraw.UNDO), Component.translatable("hibiscusquests.edit.undo"),
                        button -> ClientEditor.undo()).flat();
                undo.active = ClientEditor.canUndo();
                addRenderableWidget(undo);
                right -= 4;
            }
        }
        int searchWidth = Math.max(60, Math.min(130, panelWidth / 5));
        right -= searchWidth;
        search = new TextInput(right, top + 1, searchWidth, 18, Component.translatable("hibiscusquests.book.search"));
        search.placeholder(Component.translatable("hibiscusquests.book.search_hint"));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            refreshResults();
        });
        addRenderableWidget(search);
        if (searchFocused) {
            setFocused(search);
            search.setFocused(true);
        }
        refreshResults();
    }

    private void fillList(ScrollPanel panel, @Nullable Chapter selected) {
        int width = panel.contentWidth();
        int y = 0;
        QuestBook book = ClientBook.book();
        List<Chapter> chapters = ClientBook.visibleChapters();
        List<String> groupIds = book.groups().stream().map(ChapterGroup::id).toList();
        // Ungrouped chapters first, then each group under its heading.
        for (Chapter chapter : chapters) {
            if (!groupIds.contains(chapter.info().group())) {
                y = addRow(panel, chapter, selected, width, y);
            }
        }
        for (ChapterGroup group : book.groups()) {
            List<Chapter> inGroup = chapters.stream().filter(chapter -> chapter.info().group().equals(group.id())).toList();
            if (inGroup.isEmpty() && !ClientEditor.isEditing()) {
                continue;
            }
            boolean folded = BookUiState.isFolded(group.id(), group.collapsed());
            if (!collapsed) {
                ChapterRows.Editing editing = !ClientEditor.isEditing() ? null : new ChapterRows.Editing() {
                    @Override
                    public void menu(int mouseX, int mouseY) {
                        menus.groupMenu(group, mouseX, mouseY);
                    }

                    @Override
                    public void dragging(double mouseX, double mouseY) {
                        draggingLabel = BookText.titleOr(group.title(), group.id());
                        dragX = mouseX;
                        dragY = mouseY;
                    }

                    @Override
                    public void dropped(double mouseX, double mouseY) {
                        draggingLabel = null;
                        dropGroup(group, mouseX, mouseY);
                    }
                };
                panel.add(new ChapterRows.GroupRow(width, group, folded, () -> {
                    BookUiState.setFolded(group.id(), !folded, group.collapsed());
                    rebuildWidgets();
                }, editing), 0, y);
                y += ChapterRows.GROUP_HEIGHT;
            }
            if (!folded || collapsed) {
                for (Chapter chapter : inGroup) {
                    y = addRow(panel, chapter, selected, width, y);
                }
            }
        }
    }

    private int addRow(ScrollPanel panel, Chapter chapter, @Nullable Chapter selected, int width, int y) {
        boolean isSelected = selected != null && selected.id().equals(chapter.id());
        ChapterRows.Editing editing = !ClientEditor.isEditing() ? null : new ChapterRows.Editing() {
            @Override
            public void menu(int mouseX, int mouseY) {
                menus.chapterMenu(chapter, mouseX, mouseY);
            }

            @Override
            public void dragging(double mouseX, double mouseY) {
                draggingLabel = BookText.titleOr(chapter.title(), chapter.id());
                dragX = mouseX;
                dragY = mouseY;
            }

            @Override
            public void dropped(double mouseX, double mouseY) {
                draggingLabel = null;
                dropChapter(chapter, mouseX, mouseY);
            }
        };
        ChapterRows.ChapterRow row = new ChapterRows.ChapterRow(width, chapter, isSelected, collapsed, () -> selectChapter(chapter.id()), editing);
        panel.add(row, collapsed ? Math.max(0, (width - row.getWidth()) / 2) : 0, y);
        return y + row.getHeight() + (collapsed ? 2 : 1);
    }

    // A chapter dragged onto another row of the list: it goes just before that chapter (in its group), or to the
    // end of the group whose heading it was dropped on.
    private void dropChapter(Chapter moving, double mouseX, double mouseY) {
        if (list == null || !EditActions.isEditable(moving.id())) {
            return;
        }
        for (var child : list.children()) {
            if (child instanceof ChapterRows.ChapterRow row && row.isMouseOver(mouseX, mouseY)) {
                if (!row.chapter().id().equals(moving.id())) {
                    EditActions.placeChapter(moving.id(), row.chapter().id(), EditActions.groupOf(row.chapter()));
                }
                return;
            }
            if (child instanceof ChapterRows.GroupRow row && row.isMouseOver(mouseX, mouseY)) {
                EditActions.placeChapter(moving.id(), null, row.group().id());
                return;
            }
        }
    }

    // A group's heading dragged onto another: it goes just before that group.
    private void dropGroup(ChapterGroup moving, double mouseX, double mouseY) {
        if (list == null) {
            return;
        }
        for (var child : list.children()) {
            if (child instanceof ChapterRows.GroupRow row && row.isMouseOver(mouseX, mouseY) && !row.group().id().equals(moving.id())) {
                EditActions.placeGroup(moving.id(), row.group().id());
                return;
            }
        }
    }

    // ---- search ----

    // The results under the search box: quests whose title, subtitle, description or tasks mention the text.
    private void refreshResults() {
        if (results != null) {
            removeWidget(results);
            results = null;
        }
        String text = query.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || search == null) {
            return;
        }
        List<Quest> found = new ArrayList<>();
        int total = 0;
        for (Chapter chapter : ClientBook.visibleChapters()) {
            if (ClientBook.isLocked(chapter)) {
                continue;
            }
            for (Quest quest : chapter.quests()) {
                if (ClientBook.isVisible(quest) && ClientBook.state(quest) != NodeState.SECRET && matches(quest, text)) {
                    total++;
                    if (found.size() < MAX_RESULTS) {
                        found.add(quest);
                    }
                }
            }
        }
        int width = Math.max(search.getWidth(), Math.min(200, panelWidth / 2));
        int x = Math.max(panelX + 4, search.getX() + search.getWidth() - width);
        int rowHeight = 18;
        int extra = total > found.size() || found.isEmpty() ? 12 : 0;
        Panel panel = new Panel(x, search.getY() + search.getHeight() + 2, width, found.size() * rowHeight + 6 + extra, Panel.Style.PANEL);
        int y = 3;
        for (Quest quest : found) {
            Component title = BookText.titleOr(quest.title(), quest.id());
            TextButton row = new TextButton(0, 0, width - 6, rowHeight - 1, title, button -> {
                query = "";
                searchFocused = false;
                if (search != null) {
                    search.setValue("");
                }
                openQuest(quest.ref());
            });
            row.icon(QuestDraw.icon(ClientBook.icon(quest)));
            panel.add(row, 3, y);
            y += rowHeight;
        }
        if (extra > 0) {
            Component note = found.isEmpty() ? Component.translatable("hibiscusquests.book.search_none")
                    : Component.translatable("hibiscusquests.book.search_more", total - found.size());
            panel.add(new TextButton(0, 0, width - 6, 10, note, button -> {}), 3, y).active = false;
        }
        results = addRenderableWidget(panel);
    }

    private static boolean matches(Quest quest, String text) {
        if (contains(BookText.component(quest.title()).getString(), text) || contains(BookText.component(quest.display().subtitle()).getString(), text)) {
            return true;
        }
        for (String line : BookText.lines(quest.display().description())) {
            if (contains(line, text)) {
                return true;
            }
        }
        for (QuestTask task : quest.tasks()) {
            String title = task.title().map(BookText::component).orElseGet(() -> task.task().title()).getString();
            if (contains(title, text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    // ---- actions ----

    private void selectChapter(String id) {
        if (id.equals(chapterId)) {
            return;
        }
        chapterId = id;
        BookUiState.setLastChapter(id);
        rebuildWidgets();
    }

    // Shows a quest: switches to its chapter, centres the map on it, and opens its view.
    void openQuest(QuestRef ref) {
        Optional<Quest> quest = ClientBook.book().quest(ref);
        if (quest.isEmpty()) {
            return;
        }
        Optional<Chapter> chapter = ClientBook.book().chapter(ref.chapter());
        if (chapter.isEmpty() || (chapter.get().info().alwaysInvisible() && !ClientEditor.isEditing()) || ClientBook.isLocked(chapter.get())) {
            return;
        }
        topPopup().filter(popup -> popup instanceof QuestViewPopup).ifPresent(popup -> popup.close());
        if (!ref.chapter().equals(chapterId)) {
            selectChapter(ref.chapter());
            canvas.centreOn(quest.get());
        }
        canvas.setSelected(ref);
        openPopup(new QuestViewPopup(ref, width, height, this::openQuest));
    }

    // Asks before opening a link, like chat does.
    private void openUrl(String url) {
        try {
            handleClickEvent(new ClickEvent.OpenUrl(new URI(url)));
        } catch (URISyntaxException ignored) {
            // not a link
        }
    }

    private static boolean teamsInstalled() {
        return ModList.get().isLoaded("hibiscusteams");
    }

    // Hibiscus Teams' own screen, through its inventory button (never its classes).
    private void openTeams() {
        ButtonSpec teams = HibiscusButtons.get(TEAMS_BUTTON);
        if (teams != null) {
            teams.onClick().run();
        }
    }

    @Override
    public void closePopup(Popup popup) {
        super.closePopup(popup);
        if (popup instanceof QuestViewPopup) {
            canvas.setSelected(null);
        }
    }

    // ---- drawing ----

    @Override
    protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        HibiscusTheme theme = HibiscusTheme.current();
        theme.panel(graphics, panelX, panelY, panelWidth, panelHeight);
        int listX = panelX + 4;
        int listW = listWidth();
        if (!collapsed) {
            String bookTitle = ClientBook.book().settings().title();
            Component title = bookTitle.isBlank() ? getTitle() : BookText.component(bookTitle);
            graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(title, listW - 4)), listX + 2, panelY + TOP + 3,
                    theme.textMuted(), false);
        }
        Chapter chapter = chapter();
        int titleX = listX + listW + 6;
        int titleRight = search != null ? search.getX() - 8 : panelX + panelWidth - 8;
        if (chapter != null) {
            ChapterStatus status = ClientBook.status(chapter);
            Component progress = Component.translatable("hibiscusquests.book.progress", status.completed(), status.total());
            int progressWidth = font.width(progress);
            int room = Math.max(0, titleRight - titleX - progressWidth - 8);
            Component title = BookText.titleOr(chapter.title(), chapter.id()).copy().withStyle(style -> style.withBold(true));
            boolean hasSubtitle = !chapter.info().subtitle().isBlank();
            int titleY = panelY + (hasSubtitle ? 5 : 10);
            var shown = Language.getInstance().getVisualOrder(font.substrByWidth(title, room));
            graphics.text(font, shown, titleX, titleY, theme.text(), false);
            int progressX = titleX + font.width(shown) + 8;
            graphics.text(font, progress, progressX, titleY, status.isCompleted() ? theme.color(HibiscusTheme.POSITIVE) : theme.textMuted(), false);
            if (hasSubtitle) {
                Component subtitle = BookText.component(chapter.info().subtitle());
                graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(subtitle, titleRight - titleX)), titleX,
                        panelY + 15, theme.textMuted(), false);
            }
        } else if (!ClientQuestState.hasBook()) {
            graphics.text(font, Component.translatable("hibiscusquests.book.loading"), titleX, panelY + 10, theme.textMuted(), false);
        }
        boolean covered = topPopup().isPresent();
        canvas.draw(graphics, font, covered ? -1 : mouseX, covered ? -1 : mouseY);
    }

    // The chapter or group being dragged in the list, following the mouse.
    @Override
    protected void extractForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (draggingLabel == null) {
            return;
        }
        HibiscusTheme theme = HibiscusTheme.current();
        Component title = draggingLabel;
        int width = font.width(title) + 8;
        int x = (int) dragX + 6;
        int y = (int) dragY - 6;
        theme.panel(graphics, x, y, width, 14);
        graphics.text(font, title, x + 4, y + 3, theme.text(), false);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            if (search != null && getFocused() != search) {
                searchFocused = false;
            }
            return true;
        }
        if (topPopup().isPresent()) {
            return false;
        }
        if (search != null && getFocused() == search && !search.isMouseOver(event.x(), event.y())) {
            setFocused(null);
            searchFocused = false;
        }
        return canvas.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (topPopup().isEmpty() && canvas.mouseDragged(event, dx, dy)) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (topPopup().isEmpty() && canvas.mouseReleased(event)) {
            return true;
        }
        draggingLabel = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        return topPopup().isEmpty() && canvas.mouseScrolled(x, y, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = search != null && getFocused() == search;
        if (topPopup().isEmpty()) {
            if (event.hasControlDown() && event.key() == InputConstants.KEY_F && search != null) {
                setFocused(search);
                search.setFocused(true);
                searchFocused = true;
                return true;
            }
            if (typing && event.isEscape()) {
                setFocused(null);
                searchFocused = false;
                return true;
            }
            Chapter shown = chapter();
            if (!typing && ClientEditor.isEditing() && shown != null && menus.keyPressed(shown, event)) {
                return true;
            }
            if (!typing) {
                int key = event.key();
                if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
                    boolean back = key == InputConstants.KEY_UP || (key == InputConstants.KEY_TAB && event.hasShiftDown());
                    stepChapter(back ? -1 : 1);
                    return true;
                }
            }
        }
        boolean handled = super.keyPressed(event);
        if (search != null) {
            searchFocused = getFocused() == search;
        }
        return handled;
    }

    // The next (or previous) chapter in the list that isn't locked.
    private void stepChapter(int by) {
        List<Chapter> chapters = ClientBook.visibleChapters();
        if (chapters.isEmpty()) {
            return;
        }
        int index = 0;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(chapterId)) {
                index = i;
                break;
            }
        }
        for (int step = 1; step <= chapters.size(); step++) {
            Chapter next = chapters.get(Math.floorMod(index + by * step, chapters.size()));
            if (!ClientBook.isLocked(next)) {
                selectChapter(next.id());
                return;
            }
        }
    }

    // ---- for the screenshot harness ----

    public void showChapter(String id) {
        selectChapter(id);
    }

    public void showQuest(QuestRef quest) {
        openQuest(quest);
    }

    public void setListCollapsed(boolean value) {
        if (collapsed != value) {
            collapsed = value;
            rebuildWidgets();
        }
    }

    public void fitMap() {
        canvas.fit();
    }

    public void typeSearch(String text) {
        if (search != null) {
            search.setValue(text);
        }
    }

    // The centre of a reward's icon in the open quest view, in GUI coordinates.
    public Optional<int[]> rewardIconCentre(String rewardId) {
        return topPopup().filter(QuestViewPopup.class::isInstance).map(QuestViewPopup.class::cast)
                .flatMap(view -> view.rewardIcon(rewardId))
                .map(icon -> new int[] {icon.getX() + icon.getWidth() / 2, icon.getY() + icon.getHeight() / 2});
    }

    // Edit mode: a selection on the map, and the editor's menus and dialogs.
    public void selectQuests(List<String> ids) {
        canvas.select(ids.stream().map(EditActions.Element::quest).toList());
    }

    public void showEditMenu(QuestRef quest) {
        Chapter shown = chapter();
        Optional<Quest> found = ClientBook.book().quest(quest);
        if (shown != null && found.isPresent()) {
            int x = Math.round(canvas.screenX(found.get().display().x()));
            int y = Math.round(canvas.screenY(found.get().display().y()));
            menus.canvasMenu(shown, EditActions.Element.quest(quest.quest()), x, y, found.get().display().x(), found.get().display().y());
        }
    }

    public void showQuestForm(QuestRef quest) {
        menus.editQuest(quest.chapter(), quest.quest());
    }

    public void showTaskList(QuestRef quest) {
        openPopup(new ListEditorPopup(ListEditorPopup.Mode.TASKS, quest.chapter(), quest.quest()));
    }

    public void showItemPicker() {
        openPopup(PickerPopup.items(Component.translatable("hibiscusquests.list.pick_item"), stack -> {}));
    }

    public void showRichText(QuestRef quest) {
        ClientBook.book().quest(quest).ifPresent(found -> openPopup(new RichTextPopup(Component.translatable("hibiscusquests.field.description"),
                String.join("\n", found.display().description()), quest.chapter(), text -> {})));
    }

    public void showTables() {
        menus.tables();
    }

    public void closePopups() {
        while (topPopup().isPresent()) {
            topPopup().get().close();
        }
    }

    public void showChoice(QuestRef quest, String rewardId, List<ItemStack> items, int picked) {
        ChoicePopup popup = new ChoicePopup(quest, rewardId, items);
        openPopup(popup);
        popup.pick(picked);
    }
}
