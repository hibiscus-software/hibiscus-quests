/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.TextLayout;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.HibiscusWidget;
import software.hibiscus.hibiscuslib.client.gui.widget.TextArea;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.book.BookCodecs;
import software.hibiscus.hibiscusquests.book.BookText;
import software.hibiscus.hibiscusquests.book.Quest;
import software.hibiscus.hibiscusquests.client.ClientBook;

// Edits text of several lines (a quest's description) beside a live preview of how the book will show it, with the
// book's markup listed underneath. Pages are split with a line of ---; the preview shows the page the text starts on.
public final class RichTextPopup extends Popup {
    private final Component title;
    private final String chapter;
    private final Consumer<String> onSave;
    private String text;
    private double scroll;
    private @Nullable TextArea area;

    public RichTextPopup(Component title, String text, String chapter, Consumer<String> onSave) {
        super(Math.min(Minecraft.getInstance().getWindow().getGuiScaledWidth() - 16, 560),
                Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 16, 300));
        this.title = title;
        this.text = text;
        this.chapter = chapter;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        if (area != null) {
            scroll = area.scrollAmount();
        }
        int top = getY() + 20;
        int bottom = getY() + getHeight() - 28 - helpLines().size() * 9 - 4;
        int half = (getWidth() - 16) / 2;
        area = new TextArea(getX() + 6, top, half, bottom - top, title, Component.translatable("hibiscusquests.richtext.placeholder"));
        area.setValue(text);
        area.setScrollAmount(scroll);
        area.setResponder(value -> text = value);
        add(area);
        add(new Preview(getX() + 10 + half, top, getWidth() - 16 - half, bottom - top));
        int buttonY = getY() + getHeight() - 24;
        add(new TextButton(getX() + 6, buttonY, 64, 18, Component.translatable("gui.cancel"), button -> cancel()));
        add(new TextButton(getX() + getWidth() - 76, buttonY, 70, 18, Component.translatable("hibiscusquests.form.save"), button -> {
            close();
            onSave.accept(text);
        }).accent());
        setFocused(area);
        area.setFocused(true);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        Font font = Minecraft.getInstance().font;
        HibiscusTheme theme = HibiscusTheme.current();
        graphics.text(font, title, getX() + 6, getY() + 7, theme.text(), false);
        List<FormattedCharSequence> help = helpLines();
        int y = getY() + getHeight() - 28 - help.size() * 9;
        for (FormattedCharSequence line : help) {
            graphics.text(font, line, getX() + 6, y, theme.textMuted(), false);
            y += 9;
        }
    }

    // The markup cheat sheet under the text, wrapped to the popup.
    private List<FormattedCharSequence> helpLines() {
        return Minecraft.getInstance().font.split(Component.translatable("hibiscusquests.richtext.help"), getWidth() - 12);
    }

    // A quest link shows the quest's title, as in the book. (Popup has a Style of its own, hence the full name.)
    private @Nullable Component token(String name, String argument, net.minecraft.network.chat.Style style) {
        if (!name.equals("quest")) {
            return null;
        }
        Optional<Quest> quest = QuestRef.parseRelative(argument, chapter).flatMap(ref -> ClientBook.book().quest(ref));
        return quest.map(found -> (Component) BookText.titleOr(found.title(), found.id()).copy()
                .setStyle(style.withColor(HibiscusTheme.current().accent()).withUnderlined(true))).orElse(null);
    }

    // The text as the book will draw it (the first page), redrawn as it's typed.
    private final class Preview extends HibiscusWidget {
        private String parsedFrom = "";
        private TextLayout layout = TextLayout.parse("");

        Preview(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("hibiscusquests.richtext.preview"));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            HibiscusTheme theme = theme();
            theme.inset(graphics, getX(), getY(), getWidth(), getHeight());
            if (!text.equals(parsedFrom)) {
                parsedFrom = text;
                String firstPage = String.join("\n", BookCodecs.pages(List.of(text.split("\n", -1))).getFirst());
                layout = TextLayout.parse(firstPage, RichTextPopup.this::token);
            }
            graphics.enableScissor(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1);
            layout.draw(graphics, font(), getX() + 5, getY() + 5, getWidth() - 10, theme.text());
            graphics.disableScissor();
        }
    }
}
