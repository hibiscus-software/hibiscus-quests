/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

// One thing from a registry, or a tag of them, written "minecraft:zombie" or "#minecraft:undead". The registry may be a
// dynamic one (biomes, structures): matching goes through holders, so it works whatever the world's registries hold.
public record IdOrTag<T>(ResourceKey<? extends Registry<T>> registry, Identifier id, boolean tag) {
    public static <T> Codec<IdOrTag<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return Codec.STRING.comapFlatMap(text -> {
            boolean isTag = text.startsWith("#");
            Identifier id = Identifier.tryParse(isTag ? text.substring(1) : text);
            return id == null ? DataResult.error(() -> "Not an id or #tag: " + text) : DataResult.success(new IdOrTag<>(registry, id, isTag));
        }, IdOrTag::toString);
    }

    public boolean matches(Holder<T> holder) {
        return tag ? holder.is(TagKey.create(registry, id)) : holder.is(id);
    }

    public TagKey<T> tagKey() {
        return TagKey.create(registry, id);
    }

    public ResourceKey<T> key() {
        return ResourceKey.create(registry, id);
    }

    // A readable name: the thing's translation (category.namespace.path) when it has one, else its id.
    public Component name(String category) {
        String key = (tag ? "tag." : "") + category + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return Component.translatableWithFallback(key, (tag ? "#" : "") + id);
    }

    @Override
    public String toString() {
        return (tag ? "#" : "") + id;
    }
}
