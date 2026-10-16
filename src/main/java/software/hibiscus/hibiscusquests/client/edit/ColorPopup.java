/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.edit;

import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.ColorPicker;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;

// Picks a colour with Hibiscus Lib's ColorPicker, for a form's colour fields.
final class ColorPopup extends Popup {
    private final Component title;
    private final IntConsumer onPick;
    private int color;

    ColorPopup(Component title, int color, IntConsumer onPick) {
        super(ColorPicker.WIDTH + 12, ColorPicker.HEIGHT + 56);
        this.title = title;
        this.color = color;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        add(new ColorPicker(getX() + 6, getY() + 20, color, rgb -> color = rgb));
        int buttonY = getY() + getHeight() - 24;
        add(new TextButton(getX() + 6, buttonY, 50, 18, Component.translatable("gui.cancel"), button -> cancel()));
        add(new TextButton(getX() + getWidth() - 56, buttonY, 50, 18, Component.translatable("gui.done"), button -> {
            close();
            onPick.accept(color);
        }).accent());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        graphics.text(Minecraft.getInstance().font, title, getX() + 6, getY() + 7, HibiscusTheme.current().text(), false);
    }
}
