/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.Icon;
import software.hibiscus.hibiscuslib.client.gui.popup.ConfirmDialog;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.IconButton;
import software.hibiscus.hibiscuslib.client.gui.widget.Panel;
import software.hibiscus.hibiscuslib.client.gui.widget.ScrollPanel;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.RewardTable;
import software.hibiscus.hibiscusquests.client.ClientBook;
import software.hibiscus.hibiscusquests.client.QuestDraw;
import software.hibiscus.hibiscusquests.edit.Ids;

// The book's reward tables: each with its rolls and entry count, and buttons for its settings (title, rolls, loot
// crate), its entries and deleting it. A mod's tables are listed but can't be changed (the pack can replace one with
// its own of the same id, by hand). New makes an empty table.
public final class TablesPopup extends Popup {
    private static final int ROW = 22;

    private double scroll;
    private @Nullable ScrollPanel body;

    public TablesPopup() {
        super(Math.min(Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16, 360),
                Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 16, 280));
    }

    @Override
    protected void init() {
        if (body != null) {
            scroll = body.scrollAmount();
        }
        body = new ScrollPanel(getX() + 4, getY() + 20, getWidth() - 8, getHeight() - 20 - 30, Panel.Style.INSET);
        add(body);
        int width = body.contentWidth() - 4;
        List<RewardTable> tables = new ArrayList<>();
        ClientBook.book().tables().forEach(tables::add);
        int y = 2;
        for (RewardTable table : tables) {
            boolean editable = table.source().isEmpty();
            Icon icon = table.icon().map(QuestDraw::icon).orElse(Icon.sprite(QuestDraw.TABLE));
            body.add(new IconButton(0, 0, 18, icon, Component.literal(table.id()), button -> {}).flat(), 2, y);
            Component title = Component.translatable("hibiscusquests.tables.row", BookText.titleOr(table.title(), table.id()), table.rolls(),
                    table.entries().size());
            Caption caption = new Caption(width - 24 - 3 * 19, title, editable ? Caption.Style.LABEL : Caption.Style.MUTED);
            caption.tooltip(editable ? Component.literal(table.id())
                    : Component.translatable("hibiscusquests.tables.read_only", table.id(), table.source().orElse("")));
            body.add(caption, 24, y + 5);
            int x = width - 3 * 19 + 2;
            IconButton settings = new IconButton(0, 0, 18, Icon.sprite(QuestDraw.EDIT), Component.translatable("hibiscusquests.tables.settings"),
                    button -> open(new FormPopup(Component.translatable("hibiscusquests.tables.settings_title", table.id()), Forms.table(),
                            EditActions.encodeTable(table), RewardTable.CODEC, "", tag -> EditActions.putTable(table.id(), tag, "edit_table"))));
            IconButton entries = new IconButton(0, 0, 18, Icon.sprite(QuestDraw.TABLE), Component.translatable("hibiscusquests.tables.entries"),
                    button -> open(new ListEditorPopup(ListEditorPopup.Mode.ENTRIES, "", table.id())));
            IconButton delete = new IconButton(0, 0, 18, Icon.sprite(QuestDraw.DELETE), Component.translatable("hibiscusquests.tables.delete"),
                    button -> open(new ConfirmDialog(Component.translatable("hibiscusquests.tables.delete_title"),
                            Component.translatable("hibiscusquests.tables.delete_message", table.id()), confirmed -> {
                                if (confirmed) {
                                    EditActions.putTable(table.id(), null, "delete_table");
                                }
                            }).dangerous()));
            settings.active = editable;
            entries.active = editable;
            delete.active = editable;
            body.add(settings, x, y);
            body.add(entries, x + 19, y);
            body.add(delete, x + 38, y);
            y += ROW;
        }
        if (tables.isEmpty()) {
            body.add(new Caption(width, Component.translatable("hibiscusquests.tables.none"), Caption.Style.MUTED), 4, 6);
        }
        body.setScrollAmount(scroll);
        int buttonY = getY() + getHeight() - 24;
        add(new TextButton(getX() + 6, buttonY, 90, 18, Component.translatable("hibiscusquests.tables.new"), button -> create())
                .icon(Icon.sprite(QuestDraw.ADD)));
        add(new TextButton(getX() + getWidth() - 66, buttonY, 60, 18, Component.translatable("gui.done"), button -> close()).accent());
    }

    // A new table: its settings first, then its id comes from its title.
    private void create() {
        CompoundTag start = new CompoundTag();
        start.putString("title", Component.translatable("hibiscusquests.tables.new_title").getString());
        open(new FormPopup(Component.translatable("hibiscusquests.tables.new"), Forms.table(), start, RewardTable.CODEC, "", tag -> {
            List<String> taken = new ArrayList<>();
            ClientBook.book().tables().forEach(table -> taken.add(table.id()));
            String id = Ids.fromTitle(tag.getStringOr("title", ""), "table", taken);
            EditActions.putTable(id, tag, "new_table");
        }));
    }

    private void open(Popup popup) {
        if (screen() != null) {
            screen().openPopup(popup);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        graphics.text(Minecraft.getInstance().font, Component.translatable("hibiscusquests.tables.title"), getX() + 6, getY() + 7,
                HibiscusTheme.current().text(), false);
    }
}
