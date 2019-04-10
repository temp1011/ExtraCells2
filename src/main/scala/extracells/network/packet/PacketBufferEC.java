package extracells.network.packet;

import javax.annotation.Nullable;
import java.io.IOException;

import extracells.util.StorageChannels;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import extracells.part.PartECBase;
import extracells.util.TileUtil;
import io.netty.buffer.ByteBuf;

public class PacketBufferEC extends PacketBuffer {

	public PacketBufferEC(ByteBuf wrapped) {
		super(wrapped);
	}

	public String readString() {
		return super.readString(1024);
	}

	public void writeFluidStack(@Nullable FluidStack fluidStack) {
		if (fluidStack == null) {
			writeInt(-1);
		} else {
			writeInt(fluidStack.amount);
			writeFluid(fluidStack.getFluid());
		}
	}

	@Nullable
	public FluidStack readFluidStack() {
		int amount = readInt();
		if (amount > 0) {
			Fluid fluid = readFluid();
			if (fluid == null) {
				return null;
			}

			return new FluidStack(fluid, amount);
		}
		return null;
	}

	public void writeFluid(Fluid fluid) {
		if (fluid == null) {
			writeString("");
			return;
		}
		writeString(fluid.getName());
	}

	@Nullable
	public Fluid readFluid() {
		String fluidName = readString();
		return FluidRegistry.getFluid(fluidName);
	}

	public void writePart(PartECBase part) {
		IPartHost host = part.getHost();
		if (host == null || host.getTile() == null) {
			writeBoolean(false);
			return;
		}
		writeBoolean(true);
		TileEntity tileEntity = host.getTile();
		writeBlockPos(tileEntity.getPos());
		writeByte(part.getSide().ordinal());
	}

	@Nullable
	public <P extends IPart> P readPart(World world) {
		if (!readBoolean()) {
			return null;
		}
		BlockPos pos = readBlockPos();
		AEPartLocation location = AEPartLocation.fromOrdinal(readByte());
		IPartHost host = TileUtil.getTile(world, pos, IPartHost.class);
		if (host == null) {
			return null;
		}
		return (P) host.getPart(location);
	}

	public void writeTile(TileEntity tileEntity) {
		if (tileEntity == null) {
			writeBoolean(false);
			return;
		}
		writeBoolean(true);
		writeBlockPos(tileEntity.getPos());
	}

	@Nullable
	public TileEntity readTile(World world) {
		return readTile(world, TileEntity.class);
	}

	@Nullable
	public <T> T readTile(World world, Class<T> tileClass) {
		if (!readBoolean()) {
			return null;
		}
		BlockPos pos = readBlockPos();
		return TileUtil.getTile(world, pos, tileClass);
	}

	public void writeAEFluidStacks(IItemList<IAEFluidStack> fluidStackList) {
		for (IAEFluidStack stack : fluidStackList) {
			FluidStack fluidStack = stack.getFluidStack();
			writeFluidStack(fluidStack);
		}
	}

	public IItemList<IAEFluidStack> readAEFluidStacks() {
		IItemList<IAEFluidStack> fluidStackList = StorageChannels.FLUID().createList();
		while (readableBytes() > 0) {
			FluidStack fluidStack = readFluidStack();
			if (fluidStack == null) {
				continue;
			}
			fluidStackList.add(StorageChannels.FLUID().createStack(fluidStack));
		}
		return fluidStackList;
	}
}
