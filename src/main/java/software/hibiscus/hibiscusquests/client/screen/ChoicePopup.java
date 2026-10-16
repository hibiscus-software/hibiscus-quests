/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.client.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import software.hibiscus.hibiscuslib.client.HibiscusClientNetwork;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;
import software.hibiscus.hibiscuslib.client.gui.RecipeViewers;
import software.hibiscus.hibiscuslib.client.gui.popup.Popup;
import software.hibiscus.hibiscuslib.client.gui.widget.ItemSlotWidget;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscusquests.api.QuestRef;
import software.hibiscus.hibiscusquests.network.QuestPayloads;
import com.mojang.blaze3d.platform.InputConstants;

// The dialog a choice reward opens: its items in a row, one picked (click a slot; right click shows its uses), and
// Choose to claim it. The server checks the pick like any claim.
final class ChoicePopup extends Popup {
    private static final int GAP = 4;

    private final QuestRef quest;
    private final String rewardId;
    private final List<ItemStack> items;
    private int picked = -1;
    ChoicePopup(QuestRef quest, String rewardId, List<ItemStack> items) {
        super(width(items.size()), 78);
        this.quest = quest;
        this.rewardId = rewardId;
        this.items = List.copyOf(items);
    }

    private static int width(int count) {
        return Math.max(200, count * (ItemSlotWidget.SIZE + GAP) + 24);
    }

    @Override
    protected void init() {
        int rowWidth = items.size() * (ItemSlotWidget.SIZE + GAP) - GAP;
        int x = getX() + (getWidth() - rowWidth) / 2;
        for (int n = 0; n < items.size(); n++) {
            int index = n;
            ItemSlotWidget slot = new ItemSlotWidget(x + n * (ItemSlotWidget.SIZE + GAP), getY() + 24, items.get(n)) {
                @Override
                protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    super.extractContents(graphics, mouseX, mouseY, a);
                    if (picked == index) {
                        graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, HibiscusTheme.current().accent());
                    }
                }
            };
            slot.onClick(event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    RecipeViewers.showUses(items.get(index));
                } else {
                    picked = index;
                    choose.active = true;
                }
            });
            add(slot);
        }
        choose = new TextButton(getX() + getWidth() - 74, getY() + getHeight() - 26, 66, 20, Component.translatable("hibiscusquests.choice.choose"),
                button -> {
                    HibiscusClientNetwork.sendToServer(new QuestPayloads.Claim(quest.toString(), rewardId, picked));
                    close();
                }).accent();
        choose.active = picked >= 0;
        add(choose);
        add(new TextButton(getX() + 8, getY() + getHeight() - 26, 66, 20, Component.translatable("gui.cancel"), button -> cancel()));
    }

    private TextButton choose;

    // For the screenshot harness: as if the player had clicked that item.
    void pick(int index) {
        picked = index;
        if (choose != null) {
            choose.active = true;
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        HibiscusTheme theme = HibiscusTheme.current();
        Component title = Component.translatable("hibiscusquests.choice.title");
        graphics.text(Minecraft.getInstance().font, title, getX() + 8, getY() + 8, theme.text(), false);
    }
}
