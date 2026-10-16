/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.book;

import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// An icon in the book: an item ("minecraft:diamond", or a full stack {id: ..., components: {...}}), or a texture
// ({texture: "mymod:textures/gui/quest.png"}). Exactly one of the two is set.
public record BookIcon(ItemStack item, Optional<Identifier> texture) {
    private static final Codec<ItemStack> ITEM_ID = Identifier.CODEC.comapFlatMap(
            id -> BuiltInRegistries.ITEM.getOptional(id).map(item -> DataResult.success(new ItemStack(item)))
                    .orElseGet(() -> DataResult.error(() -> "Unknown item " + id)),
            stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()));
    private static final Codec<Identifier> TEXTURE = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("texture").forGetter(id -> id)).apply(i, id -> id));

    public static final Codec<BookIcon> CODEC = Codec.either(ITEM_ID, Codec.either(TEXTURE, ItemStack.CODEC)).xmap(
            either -> either.map(BookIcon::of, inner -> inner.map(BookIcon::texture, BookIcon::of)),
            icon -> icon.texture.isPresent() ? Either.right(Either.left(icon.texture.get()))
                    : isPlain(icon.item) ? Either.left(icon.item) : Either.right(Either.right(icon.item)));

    public static BookIcon of(ItemStack item) {
        return new BookIcon(item.copy(), Optional.empty());
    }

    public static BookIcon texture(Identifier texture) {
        return new BookIcon(ItemStack.EMPTY, Optional.of(texture));
    }

    // A single item with no components, written as just its id.
    private static boolean isPlain(ItemStack stack) {
        return stack.getCount() == 1 && stack.getComponentsPatch().isEmpty();
    }

    public boolean isEmpty() {
        return texture.isEmpty() && item.isEmpty();
    }
}
