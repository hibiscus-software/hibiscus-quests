/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

// The Quest Book item: another way to open the book (the inventory button and the key are the usual ones). Crafted from
// a book and any dye; the server can hand one to new players (giveBookOnFirstJoin).
public class QuestBookItem extends Item {
    // Set by the client entrypoint: opens the book. Nothing on a dedicated server.
    public static volatile Runnable opener = () -> {};

    public QuestBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            opener.run();
        }
        return InteractionResult.SUCCESS;
    }
}
