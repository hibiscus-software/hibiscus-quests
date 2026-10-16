/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.task;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import software.hibiscus.hibiscusquests.api.task.ActiveTask;
import software.hibiscus.hibiscusquests.api.task.Task;
import software.hibiscus.hibiscusquests.api.task.TaskType;

// The tasks about where a team member is, each checked once a second while it's active (and when it starts, so being
// there already counts): location, dimension, biome and structure.
public final class PlaceTasks {
    private static final int CHECK_INTERVAL = 20;

    private PlaceTasks() {}

    // A type that completes its task while the check holds for the player.
    private abstract static class PlaceType<T extends Task> implements TaskType<T> {
        abstract boolean isThere(ServerPlayer player, T task);

        @Override
        public int pollInterval() {
            return CHECK_INTERVAL;
        }

        @Override
        public void poll(ActiveTask<T> task) {
            if (isThere(task.player(), task.task())) {
                task.complete();
            }
        }

        @Override
        public void onActivated(ActiveTask<T> task) {
            poll(task);
        }
    }

    private static final Codec<ResourceKey<Level>> DIMENSION = ResourceKey.codec(Registries.DIMENSION);

    private static Component dimensionName(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        return Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath(), id.toString());
    }

    // ---- location ----

    // location: a member stands within radius blocks of x, y, z (or inside box, given as two corners), in a dimension
    // if one is given.
    //   {type: "location", dimension: "minecraft:overworld", x: 0, y: 64, z: 0, radius: 10}
    //   {type: "location", box: [100, 0, 100, 140, 320, 140], title: "Visit the old fort"}
    public record LocationTask(Optional<ResourceKey<Level>> dimension, int x, int y, int z, double radius, Optional<List<Integer>> box)
            implements Task {
        public static final MapCodec<LocationTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DIMENSION.optionalFieldOf("dimension").forGetter(LocationTask::dimension),
                Codec.INT.optionalFieldOf("x", 0).forGetter(LocationTask::x),
                Codec.INT.optionalFieldOf("y", 0).forGetter(LocationTask::y),
                Codec.INT.optionalFieldOf("z", 0).forGetter(LocationTask::z),
                Codec.doubleRange(0.5, 100_000).optionalFieldOf("radius", 8.0).forGetter(LocationTask::radius),
                Codec.INT.listOf(6, 6).optionalFieldOf("box").forGetter(LocationTask::box))
                .apply(i, LocationTask::new));

        public static final TaskType<LocationTask> TYPE = new PlaceType<>() {
            @Override
            public MapCodec<LocationTask> codec() {
                return CODEC;
            }

            @Override
            boolean isThere(ServerPlayer player, LocationTask task) {
                if (task.dimension.isPresent() && !player.level().dimension().equals(task.dimension.get())) {
                    return false;
                }
                if (task.box.isPresent()) {
                    List<Integer> b = task.box.get();
                    return within(player.getX(), b.get(0), b.get(3)) && within(player.getY(), b.get(1), b.get(4))
                            && within(player.getZ(), b.get(2), b.get(5));
                }
                return player.position().distanceToSqr(task.x + 0.5, task.y + 0.5, task.z + 0.5) <= task.radius * task.radius;
            }
        };

        private static boolean within(double value, int a, int b) {
            return value >= Math.min(a, b) && value < Math.max(a, b) + 1;
        }

        @Override
        public TaskType<?> type() {
            return TYPE;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(Items.COMPASS);
        }

        @Override
        public Component title() {
            if (box.isPresent()) {
                List<Integer> b = box.get();
                return Component.translatable("hibiscusquests.task.location.box", b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5));
            }
            return Component.translatable("hibiscusquests.task.location", x, y, z);
        }
    }

    // ---- dimension ----

    // dimension: a member enters a dimension. {type: "dimension", dimension: "minecraft:the_nether"}
    public record DimensionTask(ResourceKey<Level> dimension) implements Task {
        public static final MapCodec<DimensionTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DIMENSION.fieldOf("dimension").forGetter(DimensionTask::dimension))
                .apply(i, DimensionTask::new));

        public static final TaskType<DimensionTask> TYPE = new PlaceType<>() {
            @Override
            public MapCodec<DimensionTask> codec() {
                return CODEC;
            }

            @Override
            boolean isThere(ServerPlayer player, DimensionTask task) {
                return player.level().dimension().equals(task.dimension);
            }
        };

        @Override
        public TaskType<?> type() {
            return TYPE;
        }

        @Override
        public ItemStack icon() {
            if (dimension.equals(Level.NETHER)) {
                return new ItemStack(Items.NETHERRACK);
            }
            if (dimension.equals(Level.END)) {
                return new ItemStack(Items.END_STONE);
            }
            return new ItemStack(Items.GRASS_BLOCK);
        }

        @Override
        public Component title() {
            return Component.translatable("hibiscusquests.task.dimension", dimensionName(dimension));
        }
    }

    // ---- biome ----

    // biome: a member stands in a biome, or any biome of a tag. {type: "biome", biome: "#minecraft:is_ocean"}
    public record BiomeTask(IdOrTag<Biome> biome) implements Task {
        public static final MapCodec<BiomeTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                IdOrTag.codec(Registries.BIOME).fieldOf("biome").forGetter(BiomeTask::biome))
                .apply(i, BiomeTask::new));

        public static final TaskType<BiomeTask> TYPE = new PlaceType<>() {
            @Override
            public MapCodec<BiomeTask> codec() {
                return CODEC;
            }

            @Override
            boolean isThere(ServerPlayer player, BiomeTask task) {
                Holder<Biome> here = player.level().getBiome(player.blockPosition());
                return task.biome.matches(here);
            }
        };

        @Override
        public TaskType<?> type() {
            return TYPE;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(Items.FILLED_MAP);
        }

        @Override
        public Component title() {
            return Component.translatable("hibiscusquests.task.biome", biome.name("biome"));
        }
    }

    // ---- structure ----

    // structure: a member is inside a structure (one of its pieces), or any structure of a tag.
    //   {type: "structure", structure: "#minecraft:village"}
    public record StructureTask(IdOrTag<Structure> structure) implements Task {
        public static final MapCodec<StructureTask> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                IdOrTag.codec(Registries.STRUCTURE).fieldOf("structure").forGetter(StructureTask::structure))
                .apply(i, StructureTask::new));

        public static final TaskType<StructureTask> TYPE = new PlaceType<>() {
            @Override
            public MapCodec<StructureTask> codec() {
                return CODEC;
            }

            @Override
            boolean isThere(ServerPlayer player, StructureTask task) {
                ServerLevel level = player.level();
                BlockPos pos = player.blockPosition();
                StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> task.structure.matches(holder));
                return start.isValid();
            }
        };

        @Override
        public TaskType<?> type() {
            return TYPE;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(Items.MOSSY_STONE_BRICKS);
        }

        @Override
        public Component title() {
            return Component.translatable("hibiscusquests.task.structure", structure.name("structure"));
        }
    }
}
