package dev.yuzhe.aeprimitives.kinetics.content;

import dev.yuzhe.aeprimitives.kinetics.AePrimitivesKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AePrimitivesKinetics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KineticMachineGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void pressRunsCreatePressingRecipe(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_PRESS.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "ME press did not accept Create's iron pressing recipe");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "ME press did not consume its input");
        helper.assertTrue(hasQueuedOutput(machine), "ME press did not queue the pressed result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crusherRunsCreateCrushingRecipe(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_CRUSHER.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.DIORITE));
        helper.assertTrue(machine.completeCycle(helper.getLevel()), "ME crusher did not accept Create's diorite crushing recipe");
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty(), "ME crusher did not consume its input");
        helper.assertTrue(hasQueuedOutput(machine), "ME crusher did not queue any crushing result");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullOutputBufferDoesNotConsumeInput(GameTestHelper helper) {
        var machine = place(helper, KineticsContent.ME_PRESS.get());
        machine.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        for (int slot = 1; slot < machine.inventory().getSlots(); slot++) {
            machine.inventory().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.assertTrue(!machine.completeCycle(helper.getLevel()), "ME press accepted work with no output space");
        helper.assertTrue(machine.inventory().getStackInSlot(0).is(Items.IRON_INGOT), "ME press consumed blocked input");
        helper.succeed();
    }

    private static KineticMachineBlockEntity place(GameTestHelper helper, KineticMachineBlock block) {
        helper.setBlock(MACHINE, block);
        return helper.getBlockEntity(MACHINE);
    }

    private static boolean hasQueuedOutput(KineticMachineBlockEntity machine) {
        for (int slot = 1; slot < machine.inventory().getSlots(); slot++) {
            if (!machine.inventory().getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private KineticMachineGameTests() {}
}
