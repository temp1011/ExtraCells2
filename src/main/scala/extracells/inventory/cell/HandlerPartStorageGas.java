package extracells.inventory.cell;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.*;
import extracells.api.gas.IAEGasStack;
import extracells.util.GasUtil;
import extracells.util.MachineSource;
import extracells.util.StorageChannels;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fluids.Fluid;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.data.IItemList;
import extracells.api.ECApi;
import extracells.api.IExternalGasStorageHandler;
import extracells.part.fluid.PartFluidStorage;

import java.util.ArrayList;
import java.util.List;

//TODO: rewrite
public class HandlerPartStorageGas implements IHandlerPartBase<IAEGasStack> {


	protected PartFluidStorage node;
	protected IGasHandler tank;
	protected AccessRestriction access = AccessRestriction.READ_WRITE;
	protected List<Fluid> prioritizedFluids = new ArrayList<>();
	protected boolean inverted;
	protected TileEntity tile = null;
	private IExternalGasStorageHandler externalHandler = null;
	public IStorageMonitorableAccessor externalSystem;

	public HandlerPartStorageGas(PartFluidStorage _node) {
		this.node = _node;
	}

	@Override
	public boolean canAccept(IAEGasStack input) {
		if (!this.node.isActive()) {
			return false;
		}
		if (this.tank == null && this.externalSystem == null && this.externalHandler == null || !(this.access == AccessRestriction.WRITE || this.access == AccessRestriction.READ_WRITE) || input == null) {
			return false;
		}
		if (externalHandler != null) {
			IMEInventory<IAEGasStack> inventory = externalHandler.getInventory(this.tile, this.node.getFacing().getOpposite(), new MachineSource(this.node));
			if (inventory == null) {
				return false;
			}
		} else {
			return false;
		}
		if (this.inverted) {
			return !this.prioritizedFluids.isEmpty() || !isPrioritized(input);
		} else {
			return this.prioritizedFluids.isEmpty() || isPrioritized(input);
		}
	}

	@Override
	public IAEGasStack extractItems(IAEGasStack request, Actionable mode, IActionSource src) {
		if (!this.node.isActive() || !(this.access == AccessRestriction.READ || this.access == AccessRestriction.READ_WRITE)) {
			return null;
		}
		if (externalHandler != null && request != null) {
			IMEInventory<IAEGasStack> inventory = externalHandler.getInventory(this.tile, this.node.getFacing().getOpposite(), new MachineSource(this.node));
			if (inventory == null) {
				return null;
			}
			return inventory.extractItems(request, mode, new MachineSource(this.node));
		}
		return null;
	}

	@Override
	public AccessRestriction getAccess() {
		return this.access;
	}

	@Override
	public IItemList<IAEGasStack> getAvailableItems(IItemList<IAEGasStack> out) {
		if (!this.node.isActive() || !(this.access == AccessRestriction.READ || this.access == AccessRestriction.READ_WRITE)) {
			return out;
		}
		if (externalHandler != null) {
			IMEInventory<IAEGasStack> inventory = externalHandler.getInventory(this.tile, this.node.getFacing().getOpposite(), new MachineSource(this.node));
			if (inventory == null) {
				return out;
			}
			IItemList<IAEGasStack> list = inventory.getAvailableItems(StorageChannels.GAS().createList());
			for (IAEGasStack stack : list) {
				out.add(stack);
			}
		}
		return out;
	}

	@Override
	public IStorageChannel getChannel() {
		return StorageChannels.GAS();
	}

	@Override
	public int getPriority() {
		return this.node.getPriority();
	}

	@Override
	public int getSlot() {
		return 0;
	}

	@Override
	public IAEGasStack injectItems(IAEGasStack input, Actionable mode, IActionSource src) {
		if (!(this.access == AccessRestriction.WRITE || this.access == AccessRestriction.READ_WRITE)) {
			return input;
		}
		if (externalHandler != null && input != null) {
			IMEInventory<IAEGasStack> inventory = externalHandler.getInventory(this.tile, this.node.getFacing().getOpposite(), new MachineSource(this.node));
			if (inventory == null) {
				return null;
			}
			return inventory.injectItems(input, mode, new MachineSource(this.node));
		}
		return input;
	}

	@Override
	public boolean isPrioritized(IAEGasStack input) {
		if (input == null) {
			return false;
		}
		Fluid gasFluid = GasUtil.getFluidStack((GasStack)input.getGasStack()).getFluid();
		for (Fluid fluid : this.prioritizedFluids) {
			if (fluid == gasFluid) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isValid(Object verificationToken) {
		return true;
	}

	@Override
	public void onListUpdate() {

	}

	public void onNeighborChange() {
		this.tank = null;
		EnumFacing orientation = this.node.getFacing();
		TileEntity hostTile = this.node.getHostTile();
		if (hostTile == null) {
			return;
		}
		if (hostTile.getWorld() == null) {
			return;
		}
		TileEntity tileEntity = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(orientation));
		this.tile = tileEntity;
		this.tank = null;
		this.externalSystem = null;
		if (tileEntity == null) {
			this.externalHandler = null;
			return;
		}
		this.externalHandler = ECApi.instance().getHandler(tileEntity, this.node.getFacing().getOpposite(), new MachineSource(this.node));
	}

	@Override
	public void postChange(IBaseMonitor<IAEGasStack> monitor,
		Iterable<IAEGasStack> change, IActionSource actionSource) {
		IGridNode gridNode = this.node.getGridNode();
		if (gridNode != null) {
			IGrid grid = gridNode.getGrid();
			if (grid != null) {
				grid.postEvent(new MENetworkCellArrayUpdate());
				gridNode.getGrid().postEvent(
					new MENetworkStorageEvent(this.node.getGridBlock()
						.getFluidMonitor(), StorageChannels.FLUID()));
			}
			this.node.getHost().markForUpdate();
		}
	}

	public void setAccessRestriction(AccessRestriction access) {
		this.access = access;
	}

	public void setInverted(boolean _inverted) {
		this.inverted = _inverted;
	}

	public void setPrioritizedFluids(Fluid[] _fluids) {
		this.prioritizedFluids.clear();
		for (Fluid fluid : _fluids) {
			if (fluid != null) {
				this.prioritizedFluids.add(fluid);
			}
		}
	}

	@Override
	public boolean validForPass(int i) {
		return true; // TODO
	}
}
