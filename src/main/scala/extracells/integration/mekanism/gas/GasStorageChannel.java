package extracells.integration.mekanism.gas;


import appeng.api.storage.data.IItemList;
import extracells.api.gas.IAEGasStack;
import extracells.api.gas.IGasStorageChannel;
import extracells.util.GasUtil;
import io.netty.buffer.ByteBuf;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;


public class GasStorageChannel implements IGasStorageChannel {

    @Nonnull
    @Override
    public IItemList<IAEGasStack> createList() {
        return new GasList();
    }

    @Nullable
    @Override
    public IAEGasStack readFromPacket(@Nonnull ByteBuf byteBuf) {
        return new AEGasStack(byteBuf);
    }

    @Nullable
    @Override
    public IAEGasStack createFromNBT(@Nonnull NBTTagCompound nbtTagCompound) {
        return new AEGasStack(nbtTagCompound);
    }

    @Nullable
    @Override
    public IAEGasStack createStack(@Nonnull Object o) {
        if (o instanceof Gas) {
            return new AEGasStack(new GasStack((Gas) o, 1000));
        } else if (o instanceof GasStack) {
            return new AEGasStack((GasStack) o);
        } else if (o instanceof AEGasStack) {
            return new AEGasStack((AEGasStack) o);
        } else if (o instanceof Fluid) {
            Fluid fluid = (Fluid) o;
            if (GasUtil.isGas(fluid)) {
                return new AEGasStack(GasUtil.getGasStack(new FluidStack(fluid, 1000)));
            } else {
                Gas gas = GasRegistry.getGas(fluid);
                if (gas != null) {
                    return new AEGasStack(new GasStack(gas, 1000));
                }
            }
        } else if (o instanceof FluidStack) {
            FluidStack fluidStack = (FluidStack) o;
            if (fluidStack.getFluid() == null)
                return null;
            if (GasUtil.isGas(fluidStack))
                return new AEGasStack(GasUtil.getGasStack(fluidStack));
            return this.createStack(fluidStack.getFluid());
        }
        return null;
    }
}
