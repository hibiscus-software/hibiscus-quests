/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscuslib.client.HibiscusClientNetwork;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.HibiscusButton;
import software.hibiscus.hibiscuslib.client.gui.widget.HibiscusWidget;
import software.hibiscus.hibiscuslib.client.gui.widget.IconButton;
import software.hibiscus.hibiscuslib.client.gui.widget.ItemSlotWidget;
import software.hibiscus.hibiscuslib.client.gui.widget.Panel;
import software.hibiscus.hibiscuslib.client.gui.widget.ScrollPanel;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscuslib.client.gui.widget.TextLayoutWidget;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;
import software.hibiscus.hibiscusquests.book.AutoClaim;
import software.hibiscus.hibiscusquests.book.BookIcon;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.book.QuestReward;
import software.hibiscus.hibiscusquests.book.QuestTask;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.book.UsesRewardTable;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.NodeState;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.client.edit.ClientEditor;
import software.hibiscus.hibiscusquests.client.edit.EditActions;
import software.hibiscus.hibiscusquests.client.edit.ListEditorPopup;
import software.hibiscus.hibiscusquests.network.QuestPayloads;
import software.hibiscus.hibiscusquests.progress.QuestClock;
import software.hibiscus.hibiscusquests.progress.QuestLogic;
import software.hibiscus.hibiscusquests.progress.QuestState;
import software.hibiscus.hibiscusquests.reward.ChoiceReward;
import software.hibiscus.hibiscusquests.task.CheckmarkTask;

// The quest view: a panel over the map with the quest's icon, title and state, its description (in pages), its tasks
// with their progress and Submit buttons or checkboxes, its rewards with a Claim button each, and the dependencies it
// still needs (click one to go to it). Item icons open the recipe viewer (left click recipes, right click uses). Closes
// with Esc, a right click, or a click outside. Rebuilt on every sync (the book re-adds its widgets), so its state (the
// quest, the page, the scroll) lives in fields.
final class QuestViewPopup extends Popup {
    // The click event a {quest:chapter/quest} link in a description carries.
    static final Identifier OPEN_QUEST = HibiscusQuests.id("open_quest");
    private static final int HEADER = 30;
    private static final int ROW = 22;

    private final QuestRef questRef;
    private final Consumer<QuestRef> openQuest;
    private int page;
    private double scroll;
    private ScrollPanel body;
    private final Map<String, HibiscusWidget> rewardIcons = new HashMap<>();

    QuestViewPopup(QuestRef quest, int screenWidth, int screenHeight, Consumer<QuestRef> openQuest) {
        super(width(screenWidth), height(screenHeight));
        this.questRef = quest;
        this.openQuest = openQuest;
    }

    // About 60% of the screen, within what fits.
    private static int width(int screenWidth) {
        return Math.min(screenWidth - 16, Math.max(240, screenWidth * 6 / 10));
    }

    private static int height(int screenHeight) {
        return Math.min(screenHeight - 16, Math.max(160, screenHeight * 7 / 10));
    }

    QuestRef quest() {
        return questRef;
    }

    // Closes on any click outside, like a menu, and on a right click.
    @Override
    public boolean clickedOutside(MouseButtonEvent event) {
        cancel();
        return true;
    }

    @Override
    public boolean dimsBackground() {
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            cancel();
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        if (body != null) {
            scroll = body.scrollAmount();
        }
        Optional<Quest> found = ClientBook.book().quest(questRef);
        if (found.isEmpty()) {
            // Gone from the book (an edit, a reload): nothing to show.
            Minecraft.getInstance().execute(this::close);
            return;
        }
        Quest quest = found.get();
        add(new TextButton(getX() + getWidth() - 18, getY() + 4, 14, 14, Component.literal("×"), button -> close())
                .withTooltip(Component.translatable("gui.close")));
        boolean pinned = ClientBook.progress().pinned(ClientBook.me()).contains(quest.ref().toString());
        IconButton pin = new IconButton(getX() + getWidth() - 36, getY() + 4, 14, Icon.sprite(QuestDraw.PIN),
                Component.translatable(pinned ? "hibiscusquests.book.unpin" : "hibiscusquests.book.pin"),
                button -> HibiscusClientNetwork.sendToServer(new QuestPayloads.Pin(quest.ref().toString(), !pinned)));
        if (!pinned) {
            pin.flat();
        }
        add(pin);
        boolean editable = ClientEditor.isEditing() && EditActions.isEditable(quest.chapter());
        if (editable) {
            add(new IconButton(getX() + getWidth() - 54, getY() + 4, 14, Icon.sprite(QuestDraw.EDIT),
                    Component.translatable("hibiscusquests.edit.menu.edit_quest"), button -> {
                        if (screen() instanceof QuestBookScreen book) {
                            book.menus().editQuest(quest.chapter(), quest.id());
                        }
                    }));
        }
        add(new Header(getX() + 6, getY() + 6, getWidth() - (editable ? 64 : 46), quest));
        body = new ScrollPanel(getX() + 4, getY() + HEADER, getWidth() - 8, getHeight() - HEADER - 4, Panel.Style.INSET);
        add(body);
        fillBody(quest, body.contentWidth());
        body.setScrollAmount(scroll);
    }

    private void fillBody(Quest quest, int width) {
        int y = 0;
        NodeState state = ClientBook.state(quest);
        boolean editing = ClientEditor.isEditing();
        boolean editable = editing && EditActions.isEditable(quest.chapter());
        boolean hidden = state == NodeState.SECRET && !editing;
        List<List<String>> pages = quest.display().pages();
        boolean hasText = pages.stream().anyMatch(lines -> lines.stream().anyMatch(line -> !line.isBlank()));
        if (hasText && !hidden) {
            page = Math.min(page, pages.size() - 1);
            TextLayoutWidget text = new TextLayoutWidget(0, 0, width, String.join("\n", pages.get(page)), this::token)
                    .onClickEvent(this::clickEvent);
            body.add(text, 0, y);
            y += text.getHeight() + 4;
            if (pages.size() > 1) {
                body.add(new TextButton(0, 0, 18, 14, Component.literal("<"), button -> turn(-1)).withTooltip(Component.translatable("hibiscusquests.book.previous_page")), 0, y);
                body.add(new Caption(width - 40, Component.translatable("hibiscusquests.book.page", page + 1, pages.size())), 22, y + 3);
                body.add(new TextButton(0, 0, 18, 14, Component.literal(">"), button -> turn(1)).withTooltip(Component.translatable("hibiscusquests.book.next_page")), width - 18, y);
                y += 18;
            }
            y += 2;
        }
        if ((!quest.tasks().isEmpty() || editable) && !hidden) {
            y = heading(Component.translatable("hibiscusquests.book.tasks"), width, y,
                    editable ? () -> openList(ListEditorPopup.Mode.TASKS, quest) : null);
            List<QuestTask> active = QuestLogic.activeTasks(ClientBook.book(), ClientBook.progress(), quest, QuestClock.now());
            for (QuestTask task : quest.tasks()) {
                if (task.hideUntilStarted() && ClientBook.progress().state(quest.ref()) == null && !editing) {
                    continue;
                }
                body.add(taskRow(quest, task, active.contains(task), width), 0, y);
                y += ROW;
            }
            y += 4;
        }
        if ((!quest.rewards().isEmpty() || editable) && !hidden) {
            y = heading(Component.translatable("hibiscusquests.book.rewards"), width, y,
                    editable ? () -> openList(ListEditorPopup.Mode.REWARDS, quest) : null);
            for (QuestReward reward : quest.rewards()) {
                if (reward.autoClaim() == AutoClaim.INVISIBLE && !editing) {
                    continue;
                }
                body.add(rewardRow(quest, reward, width), 0, y);
                y += ROW;
            }
            y += 4;
        }
        if (!state.isComplete()) {
            List<Quest> missing = new ArrayList<>();
            for (QuestRef ref : quest.dependencies()) {
                ClientBook.book().quest(ref).filter(dependency -> !ClientBook.progress().isCompleted(ref)).ifPresent(missing::add);
            }
            if (!missing.isEmpty()) {
                y = heading(Component.translatable("hibiscusquests.book.requires"), width, y);
                for (Quest dependency : missing) {
                    boolean secret = !ClientBook.isVisible(dependency) || ClientBook.state(dependency) == NodeState.SECRET;
                    Component name = secret ? Component.translatable("hibiscusquests.book.secret") : BookText.titleOr(dependency.title(), dependency.id());
                    TextButton jump = new TextButton(0, 0, width, 16, name, button -> openQuest.accept(dependency.ref()));
                    jump.icon(secret ? Icon.NONE : QuestDraw.icon(ClientBook.icon(dependency)));
                    jump.active = !secret;
                    body.add(jump, 0, y);
                    y += 18;
                }
            }
        }
        if (y == 0) {
            body.add(new Caption(width, Component.translatable(hidden ? "hibiscusquests.book.secret_hint" : "hibiscusquests.book.nothing_to_do")), 0, 2);
        }
    }

    private int heading(Component text, int width, int y) {
        return heading(text, width, y, null);
    }

    // A section's heading; in edit mode, with a button that edits the section's list.
    private int heading(Component text, int width, int y, @Nullable Runnable edit) {
        body.add(new Caption(width - (edit != null ? 18 : 0), text.copy().withStyle(style -> style.withBold(true)), true), 0, y);
        if (edit != null) {
            body.add(new IconButton(0, 0, 14, Icon.sprite(QuestDraw.EDIT), Component.translatable("hibiscusquests.edit.edit_list"),
                    button -> edit.run()), width - 14, y - 2);
            return y + 14;
        }
        return y + 12;
    }

    private void openList(ListEditorPopup.Mode mode, Quest quest) {
        if (screen() != null) {
            screen().openPopup(new ListEditorPopup(mode, quest.chapter(), quest.id()));
        }
    }

    private void turn(int by) {
        page = Math.max(0, page + by);
        scroll = 0;
        if (body != null) {
            body.setScrollAmount(0);
        }
        if (screen() != null) {
            attach(screen());
        }
    }

    // {quest:chapter/quest} in a description: the quest's title, as a link that opens it.
    private Component token(String name, String argument, net.minecraft.network.chat.Style style) {
        if (!name.equals("quest")) {
            return null;
        }
        Optional<Quest> target = QuestRef.parseRelative(argument, questRef.chapter()).flatMap(ref -> ClientBook.book().quest(ref));
        if (target.isEmpty()) {
            return null;
        }
        Quest quest = target.get();
        boolean secret = ClientBook.state(quest) == NodeState.SECRET || !ClientBook.isVisible(quest);
        Component title = secret ? Component.translatable("hibiscusquests.book.secret") : BookText.titleOr(quest.title(), quest.id());
        return title.copy().setStyle(style.withColor(HibiscusTheme.current().color(HibiscusTheme.ACCENT_HOVER)).withUnderlined(true)
                .withClickEvent(new ClickEvent.Custom(OPEN_QUEST, Optional.of(StringTag.valueOf(quest.ref().toString()))))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("hibiscusquests.book.open_quest"))));
    }

    private boolean clickEvent(ClickEvent event) {
        if (event instanceof ClickEvent.Custom(Identifier id, Optional<Tag> payload) && id.equals(OPEN_QUEST)) {
            payload.flatMap(Tag::asString).flatMap(QuestRef::parse).ifPresent(openQuest);
            return true;
        }
        return false;
    }

    // ---- rows ----

    private Panel taskRow(Quest quest, QuestTask task, boolean active, int width) {
        Panel row = new Panel(0, 0, width, ROW - 2, Panel.Style.NONE);
        BookIcon icon = ClientBook.icon(task);
        row.add(slot(icon), 0, 1);
        long value = ClientBook.taskProgress(quest, task);
        boolean done = value >= task.target();
        Task type = task.task();
        boolean submittable = active && !done && type.type() instanceof TaskType<?> taskType
                && canSubmit(taskType, type);
        int actionWidth = 0;
        if (submittable) {
            if (type instanceof CheckmarkTask) {
                row.add(new Checkbox(() -> submit(quest, task)), width - 16, 3);
                actionWidth = 18;
            } else {
                int w = Math.max(44, textWidth(Component.translatable("hibiscusquests.book.submit")) + 12);
                row.add(new TextButton(0, 0, w, 16, Component.translatable("hibiscusquests.book.submit"), button -> submit(quest, task)), width - w, 2);
                actionWidth = w + 2;
            }
        }
        Component title = task.title().map(BookText::component).orElseGet(type::title);
        row.add(new TaskInfo(width - 22 - actionWidth, title, value, task.target(), task.optional(), active || done), 22, 0);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Task> boolean canSubmit(TaskType<?> type, Task task) {
        return ((TaskType<T>) type).canSubmit((T) task);
    }

    // For the screenshot harness: where a reward's icon is, to hover over it.
    Optional<HibiscusWidget> rewardIcon(String rewardId) {
        return Optional.ofNullable(rewardIcons.get(rewardId));
    }

    private void submit(Quest quest, QuestTask task) {
        HibiscusClientNetwork.sendToServer(new QuestPayloads.Submit(quest.ref().toString(), task.id()));
    }

    private Panel rewardRow(Quest quest, QuestReward reward, int width) {
        Panel row = new Panel(0, 0, width, ROW - 2, Panel.Style.NONE);
        ItemStack stack = reward.reward().icon();
        HibiscusWidget icon = slot(stack.isEmpty() ? BookIcon.of(new ItemStack(Items.CHEST)) : BookIcon.of(stack));
        if (reward.reward() instanceof UsesRewardTable user) {
            icon = new TablePreviewSlot(stack.isEmpty() ? new ItemStack(Items.CHEST) : stack, user.table());
        } else if (reward.reward() instanceof ChoiceReward choice) {
            icon.tooltipLines(() -> choiceTooltip(choice));
        }
        row.add(icon, 0, 1);
        rewardIcons.put(reward.id(), icon);
        boolean canClaim = ClientBook.canClaim(quest, reward);
        boolean claimed = ClientBook.isClaimed(quest, reward);
        int buttonWidth = Math.max(44, textWidth(Component.translatable("hibiscusquests.book.claim")) + 12);
        if (!claimed) {
            TextButton claim = new TextButton(0, 0, buttonWidth, 16, Component.translatable(reward.reward() instanceof ChoiceReward
                    ? "hibiscusquests.book.choose" : "hibiscusquests.book.claim"), button -> {
                if (reward.reward() instanceof ChoiceReward choice) {
                    if (screen() != null) {
                        screen().openPopup(new ChoicePopup(quest.ref(), reward.id(), choice.items()));
                    }
                } else {
                    HibiscusClientNetwork.sendToServer(new QuestPayloads.Claim(quest.ref().toString(), reward.id(), -1));
                }
            });
            claim.active = canClaim;
            if (canClaim) {
                claim.accent();
            }
            row.add(claim, width - buttonWidth, 2);
        }
        Component status;
        if (claimed) {
            status = reward.teamReward() ? claimedBy(quest, reward) : Component.translatable("hibiscusquests.book.claimed");
        } else if (!ClientBook.progress().isCompleted(quest.ref())) {
            status = Component.translatable(reward.teamReward() ? "hibiscusquests.book.team_reward" : "hibiscusquests.book.your_reward");
        } else {
            status = Component.translatable(reward.teamReward() ? "hibiscusquests.book.team_reward_ready" : "hibiscusquests.book.ready");
        }
        row.add(new RewardInfo(width - 24 - (claimed ? 0 : buttonWidth + 2), reward.reward().title(), status, claimed, canClaim), 22, 0);
        return row;
    }

    // "Claimed", or "Claimed by <name>" for a team reward someone else took.
    private static Component claimedBy(Quest quest, QuestReward reward) {
        QuestState state = ClientBook.progress().state(quest.ref());
        UUID by = state == null ? null : state.teamClaimer(reward.id()).orElse(null);
        if (by == null || by.equals(ClientBook.me())) {
            return Component.translatable("hibiscusquests.book.claimed");
        }
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(by);
        Component name = info != null ? Component.literal(info.getProfile().name()) : Component.translatable("hibiscusquests.book.a_teammate");
        return Component.translatable("hibiscusquests.book.claimed_by", name);
    }

    // An item icon in a slot (recipes on click), or a texture icon.
    private static HibiscusWidget slot(BookIcon icon) {
        if (icon.texture().isEmpty()) {
            ItemStack stack = icon.item();
            return new ItemSlotWidget(0, 0, stack);
        }
        return new IconSlot(icon);
    }

    // A choice reward's options, on hover.
    private static List<Component> choiceTooltip(ChoiceReward choice) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hibiscusquests.book.choice_of"));
        for (ItemStack item : choice.items()) {
            lines.add(Component.literal("  " + item.getCount() + " \u00d7 ").append(item.getHoverName()).withColor(HibiscusTheme.current().textMuted()));
        }
        return lines;
    }

    // What a reward table may give, with each entry's chance per roll.
    static List<Component> tablePreview(String tableId) {
        List<Component> lines = new ArrayList<>();
        Optional<RewardTable> table = ClientBook.book().table(tableId);
        if (table.isEmpty()) {
            lines.add(Component.translatable("hibiscusquests.book.no_table", tableId));
            return lines;
        }
        RewardTable rewardTable = table.get();
        lines.add(Component.translatable("hibiscusquests.book.table_title", BookText.titleOr(rewardTable.title(), rewardTable.id()),
                rewardTable.rolls()).withColor(HibiscusTheme.current().text()));
        for (RewardTable.Entry entry : rewardTable.entries()) {
            String chance = String.format(Locale.ROOT, "%.1f%%", rewardTable.chance(entry) * 100);
            lines.add(Component.literal("  " + chance + "  ").withColor(HibiscusTheme.current().color(HibiscusTheme.GOLD))
                    .append(entry.reward().title().copy().withColor(HibiscusTheme.current().textMuted())));
        }
        if (rewardTable.emptyWeight() > 0) {
            String chance = String.format(Locale.ROOT, "%.1f%%", (double) rewardTable.emptyWeight() / rewardTable.totalWeight() * 100);
            lines.add(Component.literal("  " + chance + "  ").withColor(HibiscusTheme.current().color(HibiscusTheme.GOLD))
                    .append(Component.translatable("hibiscusquests.book.table_nothing").withColor(HibiscusTheme.current().textMuted())));
        }
        return lines;
    }

    // ---- small widgets ----

    // A reward that rolls a table: its icon, with the table's entries and chances on hover.
    private static final class TablePreviewSlot extends HibiscusWidget {
        private final ItemStack stack;

        TablePreviewSlot(ItemStack stack, String table) {
            super(0, 0, ItemSlotWidget.SIZE, ItemSlotWidget.SIZE, Component.empty());
            this.stack = stack;
            tooltipLines(() -> tablePreview(table));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            theme().inset(graphics, getX(), getY(), getWidth(), getHeight());
            graphics.item(stack, getX() + 1, getY() + 1);
            graphics.itemDecorations(font(), stack, getX() + 1, getY() + 1);
            if (isHovered()) {
                graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, theme().color(HibiscusTheme.HOVER));
            }
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return false;
        }
    }

    // The header: icon, title, subtitle and state.
    private static final class Header extends HibiscusWidget {
        private final Quest quest;

        Header(int x, int y, int width, Quest quest) {
            super(x, y, width, 20, BookText.titleOr(quest.title(), quest.id()));
            this.quest = quest;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            NodeState state = ClientBook.state(quest);
            boolean secret = state == NodeState.SECRET;
            if (secret) {
                graphics.text(font(), "?", getX() + 5, getY() + 6, theme.textMuted(), false);
            } else {
                QuestDraw.icon(graphics, ClientBook.icon(quest), getX(), getY() + 2, 16);
            }
            Component stateLabel = Component.translatable(state.translationKey());
            int stateWidth = font().width(stateLabel);
            int stateColor = switch (state) {
                case CLAIMABLE -> theme.color(HibiscusTheme.GOLD);
                case DONE -> QuestDraw.accent();
                case LOCKED, SECRET -> theme.color(HibiscusTheme.TEXT_DISABLED);
                default -> theme.textMuted();
            };
            graphics.text(font(), stateLabel, getRight() - stateWidth, getY() + 1, stateColor, false);
            int room = getWidth() - 22 - stateWidth - 6;
            Component title = secret ? Component.translatable("hibiscusquests.book.secret") : getMessage().copy().withStyle(style -> style.withBold(true));
            graphics.text(font(), Language.getInstance().getVisualOrder(font().substrByWidth(title, room)), getX() + 22, getY() + 1, theme.text(), false);
            if (!secret && !quest.display().subtitle().isBlank()) {
                Component subtitle = BookText.component(quest.display().subtitle());
                graphics.text(font(), Language.getInstance().getVisualOrder(font().substrByWidth(subtitle, getWidth() - 22)),
                        getX() + 22, getY() + 11, theme.textMuted(), false);
            }
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return false;
        }
    }

    // A line of text (a heading, the page number).
    private static final class Caption extends HibiscusWidget {
        private final boolean heading;

        Caption(int width, Component text) {
            this(width, text, false);
        }

        Caption(int width, Component text, boolean heading) {
            super(0, 0, width, 10, text);
            this.heading = heading;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int color = heading ? QuestDraw.accent() : theme().textMuted();
            var line = Language.getInstance().getVisualOrder(font().substrByWidth(getMessage(), getWidth()));
            int x = heading ? getX() : getX() + (getWidth() - font().width(line)) / 2;
            graphics.text(font(), line, x, getY(), color, false);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return false;
        }
    }

    // A task's title and progress: a bar for counts above 1, "done" with a check when it's finished.
    private static final class TaskInfo extends HibiscusWidget {
        private final long value;
        private final long target;
        private final boolean optional;
        private final boolean tracked;

        TaskInfo(int width, Component title, long value, long target, boolean optional, boolean tracked) {
            super(0, 0, width, 20, title);
            this.value = value;
            this.target = target;
            this.optional = optional;
            this.tracked = tracked;
            if (optional) {
                tooltip(Component.translatable("hibiscusquests.book.optional_task"));
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            boolean done = value >= target;
            Component count = Component.literal(format(value) + " / " + format(target));
            int countWidth = target > 1 ? font().width(count) + 4 : 0;
            int room = getWidth() - countWidth - (done ? 12 : 0);
            Component title = optional ? getMessage().copy().append(Component.translatable("hibiscusquests.book.optional_suffix")) : getMessage();
            int color = done ? theme.color(HibiscusTheme.POSITIVE) : tracked ? theme.text() : theme.textMuted();
            graphics.text(font(), Language.getInstance().getVisualOrder(font().substrByWidth(title, room)), getX(), getY() + 2, color, false);
            if (done) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QuestDraw.CHECK, getRight() - 10, getY() + 1, 10, 10);
            } else if (target > 1) {
                graphics.text(font(), count, getRight() - countWidth + 4, getY() + 2, theme.textMuted(), false);
            }
            int barY = getY() + 13;
            graphics.fill(getX(), barY, getRight(), barY + 3, theme.color(HibiscusTheme.PANEL_SHADOW));
            int filled = (int) Math.round(getWidth() * Math.min(1.0, (double) value / Math.max(1, target)));
            if (filled > 0) {
                graphics.fill(getX(), barY, getX() + filled, barY + 3, done ? theme.color(HibiscusTheme.POSITIVE) : QuestDraw.accent());
            }
        }

        private static String format(long n) {
            return n >= 100_000 ? String.format(Locale.ROOT, "%.1fk", n / 1000.0) : Long.toString(n);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return false;
        }
    }

    // A reward's title and whether it's claimed, waiting or not yet earned.
    private static final class RewardInfo extends HibiscusWidget {
        private final Component status;
        private final boolean claimed;
        private final boolean ready;

        RewardInfo(int width, Component title, Component status, boolean claimed, boolean ready) {
            super(0, 0, width, 20, title);
            this.status = status;
            this.claimed = claimed;
            this.ready = ready;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            graphics.text(font(), Language.getInstance().getVisualOrder(font().substrByWidth(getMessage(), getWidth())), getX(), getY() + 1,
                    claimed ? theme.textMuted() : theme.text(), false);
            int color = claimed ? theme.color(HibiscusTheme.POSITIVE) : ready ? theme.color(HibiscusTheme.GOLD) : theme.color(HibiscusTheme.TEXT_DISABLED);
            graphics.text(font(), Language.getInstance().getVisualOrder(font().substrByWidth(status, getWidth())), getX(), getY() + 11, color, false);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return false;
        }
    }

    // A texture icon in a slot.
    private static final class IconSlot extends HibiscusWidget {
        private final BookIcon icon;

        IconSlot(BookIcon icon) {
            super(0, 0, ItemSlotWidget.SIZE, ItemSlotWidget.SIZE, Component.empty());
            this.icon = icon;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            theme().inset(graphics, getX(), getY(), getWidth(), getHeight());
            QuestDraw.icon(graphics, icon, getX() + 1, getY() + 1, 16);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return false;
        }
    }

    // The tick box of a checkmark task.
    private static final class Checkbox extends HibiscusButton {
        Checkbox(Runnable onPress) {
            super(0, 0, 14, 14, Component.translatable("hibiscusquests.book.mark_done"), button -> onPress.run());
            setTooltip(Tooltip.create(Component.translatable("hibiscusquests.book.mark_done")));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            theme().draw(graphics, isHoveredOrFocused() ? HibiscusTheme.SPRITE_TEXT_FIELD_FOCUSED : HibiscusTheme.SPRITE_TEXT_FIELD,
                    getX(), getY(), getWidth(), getHeight());
            if (isHovered()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, QuestDraw.CHECK, getX() + 2, getY() + 2, 10, 10, 0x80FFFFFF);
            }
        }
    }

    // The font's width of a component, for sizing buttons before they exist.
    private static int textWidth(Component text) {
        return Minecraft.getInstance().font.width(text);
    }
}
