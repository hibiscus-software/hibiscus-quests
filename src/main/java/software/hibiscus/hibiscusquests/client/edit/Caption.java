/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscuslib.client.gui.widget.HibiscusWidget;

// A line of text in the editor's popups: a field's label, a section heading (in the accent colour), or a muted note.
// Cut short to its width; hovering shows its tooltip, if it has one.
final class Caption extends HibiscusWidget {
    enum Style { LABEL, HEADING, MUTED }

    private final Style style;

    Caption(int width, Component text, Style style) {
        super(0, 0, width, 10, text);
        this.style = style;
        this.active = false;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int color = switch (style) {
            case LABEL -> theme().text();
            case HEADING -> theme().accent();
            case MUTED -> theme().textMuted();
        };
        var shown = Language.getInstance().getVisualOrder(font().substrByWidth(getMessage(), getWidth()));
        graphics.text(font(), shown, getX(), getY() + 1, color, false);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getBottom();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
