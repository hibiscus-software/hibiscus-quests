/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusquests.gametest;

import static software.hibiscus.hibiscusquests.gametest.TestSupport.check;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.completed;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.join;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.leave;
import static software.hibiscus.hibiscusquests.gametest.TestSupport.ref;

import java.lang.reflect.Method;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.hibiscus.hibiscusquests.HibiscusQuests;
import software.hibiscus.hibiscusquests.book.BookManager;
import software.hibiscus.hibiscusquests.task.CriterionTasks;

// Tests that only run when another mod is installed (registered only then, see HibiscusQuestsGameTests). Arcforge's
// triggers are fired through reflection, so Hibiscus Quests never compiles against Arcforge.
final class IntegrationGameTests {
    private static final String CHAPTER = "test_arcforge";

    private IntegrationGameTests() {}

    // Criterion tasks on each of Arcforge's custom triggers parse with the trigger's codec and count when it fires.
    static void arcforgeTriggers(GameTestHelper helper) throws ReflectiveOperationException {
        check(helper, BookManager.book().chapter(CHAPTER).isPresent(), "test_arcforge loads when Arcforge is installed");
        for (String quest : new String[] {"formed", "produced", "turbine", "oxy", "linked"}) {
            check(helper, CriterionTasks.isBound(ref(CHAPTER, quest), "t"), "the " + quest + " criterion parses with Arcforge's codec");
        }
        ServerPlayer player = join(helper);
        try {
            fire(player, "multiblock_formed", Identifier.fromNamespaceAndPath("arcforge", "arcforge_furnace"), 3);
            fire(player, "machine_produced", new ItemStack(Items.IRON_INGOT), null, null);
            fire(player, "turbine_full_speed", Identifier.fromNamespaceAndPath("arcforge", "steam_turbine"), 5, 15);
            fire(player, "oxy_fuel", 1200);
            fire(player, "machine_linked", true, 4);
            for (String quest : new String[] {"formed", "produced", "turbine", "oxy", "linked"}) {
                check(helper, completed(player, CHAPTER, quest), "Arcforge's " + quest + " trigger completes its quest");
            }
            HibiscusQuests.LOGGER.info("Criterion tasks work with Arcforge's five triggers");
            helper.succeed();
        } finally {
            leave(helper, player);
        }
    }

    // Calls arcforge:<name>'s trigger(ServerPlayer, ...) with the given arguments.
    private static void fire(ServerPlayer player, String name, Object... args) throws ReflectiveOperationException {
        CriterionTrigger<?> trigger = BuiltInRegistries.TRIGGER_TYPES.getValue(Identifier.fromNamespaceAndPath("arcforge", name));
        if (trigger == null) {
            throw new IllegalStateException("arcforge:" + name + " isn't registered");
        }
        for (Method method : trigger.getClass().getMethods()) {
            if (method.getName().equals("trigger") && method.getParameterCount() == args.length + 1
                    && method.getParameterTypes()[0] == ServerPlayer.class) {
                Object[] all = new Object[args.length + 1];
                all[0] = player;
                System.arraycopy(args, 0, all, 1, args.length);
                method.invoke(trigger, all);
                return;
            }
        }
        throw new IllegalStateException("arcforge:" + name + " has no trigger(ServerPlayer, " + args.length + " more)");
    }
}
