package extracells.tileentity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import extracells.util.ItemStackUtils;
import extracells.util.MachineSource;
import extracells.util.StorageChannels;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.crafting.ICraftingWatcher;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import extracells.api.IECTileEntity;
import extracells.container.fluid.ContainerFluidCrafter;
import extracells.crafting.CraftingPattern;
import extracells.gridblock.ECFluidGridBlock;
import extracells.gui.fluid.GuiFluidCrafter;
import extracells.network.IGuiProvider;

public class TileEntityFluidCrafter extends TileBase implements IActionHost, ICraftingProvider, ICraftingWatcherHost, IECTileEntity, ITickable, IGuiProvider {

	private class FluidCrafterInventory implements IInventory {

		private ItemStack[] inv = new ItemStack[9];

		private FluidCrafterInventory(){
			for(int i = 0; i < inv.length; i++){
				inv[i] = ItemStack.EMPTY;
			}
		}

		@Override
		public void closeInventory(EntityPlayer player) {
		}

		@Override
		public ItemStack decrStackSize(int slot, int amt) {
			ItemStack stack = getStackInSlot(slot);
			if (stack != null && !stack.isEmpty()) {
				if (stack.getCount() <= amt) {
					setInventorySlotContents(slot, ItemStack.EMPTY);
				} else {
					stack = stack.splitStack(amt);
					if (stack.getCount() == 0) {
						setInventorySlotContents(slot, ItemStack.EMPTY);
					}
				}
			}
			TileEntityFluidCrafter.this.update = true;
			onContentsChanged();
			return stack;
		}

		@Override
		public String getName() {
			return "inventory.fluidCrafter";
		}

		@Override
		public int getInventoryStackLimit() {
			return 1;
		}

		@Override
		public int getSizeInventory() {
			return this.inv.length;
		}

		@Override
		public boolean isEmpty() {
			for (ItemStack itemStack : inv) {
				if (itemStack != null && !itemStack.isEmpty()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return this.inv[slot];
		}

		@Nullable
		@Override
		public ItemStack removeStackFromSlot(int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean hasCustomName() {
			return false;
		}

		@Override
		public boolean isItemValidForSlot(int slot, ItemStack stack) {
			if (stack.getItem() instanceof ICraftingPatternItem) {
				ICraftingPatternDetails details = ((ICraftingPatternItem) stack
					.getItem()).getPatternForItem(stack, getWorld());
				return details != null && details.isCraftable();
			}
			return false;
		}

		@Override
		public boolean isUsableByPlayer(EntityPlayer player) {
			return true;
		}

		@Override
		public void markDirty() {
		}

		@Override
		public void openInventory(EntityPlayer player) {
		}

		public void readFromNBT(NBTTagCompound tagCompound) {

			NBTTagList tagList = tagCompound.getTagList("Inventory", 10);
			for (int i = 0; i < tagList.tagCount(); i++) {
				NBTTagCompound tag = tagList.getCompoundTagAt(i);
				byte slot = tag.getByte("Slot");
				if (slot >= 0 && slot < this.inv.length) {
					this.inv[slot] = new ItemStack(tag);
				}
			}
		}

		@Override
		public void setInventorySlotContents(int slot, ItemStack stack) {
			this.inv[slot] = stack;
			if (stack != null && stack.getCount() > getInventoryStackLimit()) {
				stack.setCount(getInventoryStackLimit());
			}
			onContentsChanged();
			TileEntityFluidCrafter.this.update = true;
		}

		public void writeToNBT(NBTTagCompound tagCompound) {

			NBTTagList itemList = new NBTTagList();
			for (int i = 0; i < this.inv.length; i++) {
				ItemStack stack = this.inv[i];
				if (stack != null) {
					NBTTagCompound tag = new NBTTagCompound();
					tag.setByte("Slot", (byte) i);
					stack.writeToNBT(tag);
					itemList.appendTag(tag);
				}
			}
			tagCompound.setTag("Inventory", itemList);
		}

		@Override
		public int getField(int id) {
			return 0;
		}

		@Override
		public void setField(int id, int value) {

		}

		@Override
		public int getFieldCount() {
			return 0;
		}

		@Override
		public void clear() {

		}

		@Override
		public ITextComponent getDisplayName() {
			return new TextComponentString(getName());
		}

		protected void onContentsChanged() {
			saveData();
		}
	}

	private ECFluidGridBlock gridBlock;
	private IGridNode node = null;
	private List<ICraftingPatternDetails> patternHandlers = new ArrayList<>();
	private List<IAEItemStack> requestedItems = new ArrayList<>();
	private List<IAEItemStack> removeList = new ArrayList<>();
	private ICraftingPatternDetails[] patternHandlerSlot = new ICraftingPatternDetails[9];
	private ItemStack[] oldStack = new ItemStack[9];
	private boolean isBusy = false;

	private ICraftingWatcher watcher = null;

	private boolean isFirstGetGridNode = true;

	public final FluidCrafterInventory inventory;
	private Long finishCraftingTime = 0L;
	private ItemStack returnStack = null;

	private ItemStack[] optionalReturnStack = new ItemStack[0];

	private boolean update = false;

	private final TileEntityFluidCrafter instance;

	public TileEntityFluidCrafter() {
		super();
		this.gridBlock = new ECFluidGridBlock(this);
		this.inventory = new FluidCrafterInventory();
		this.instance = this;
	}

	@Override
	public IGridNode getActionableNode() {
		if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
			return null;
		}
		if (this.node == null) {
			this.node = AEApi.instance().grid().createGridNode(this.gridBlock);
		}
		return this.node;
	}

	@Override
	public AECableType getCableConnectionType(AEPartLocation dir) {
		return AECableType.SMART;
	}

	public IGridNode getGridNode() {
		return getGridNode(AEPartLocation.INTERNAL);
	}

	@Override
	public IGridNode getGridNode(AEPartLocation dir) {
		if (FMLCommonHandler.instance().getSide().isClient() && (getWorld() == null || getWorld().isRemote)) {
			return null;
		}
		if (this.isFirstGetGridNode) {
			this.isFirstGetGridNode = false;
			getActionableNode().updateState();
		}
		return this.node;
	}

	public IInventory getInventory() {
		return this.inventory;
	}

	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}

	@Override
	public double getPowerUsage() {
		return 0;
	}

	@Override
	public boolean isBusy() {
		return this.isBusy;
	}

	@Override
	public void onRequestChange(ICraftingGrid craftingGrid, IAEItemStack what) {
		if (craftingGrid.isRequesting(what)) {
			if (!this.requestedItems.contains(what)) {
				this.requestedItems.add(what);
			}
		} else
			this.requestedItems.remove(what);

	}

	@Override
	public void provideCrafting(ICraftingProviderHelper craftingTracker) {
		this.patternHandlers = new ArrayList<>();
		ICraftingPatternDetails[] oldHandler = patternHandlerSlot;
		patternHandlerSlot = new ICraftingPatternDetails[9];
		for (int i = 0; this.inventory.inv.length > i; i++) {
			ItemStack currentPatternStack = this.inventory.inv[i];
			ItemStack oldItem = this.oldStack[i];
			if (currentPatternStack != null && ItemStack.areItemStacksEqual(currentPatternStack, oldItem)) {
				ICraftingPatternDetails pa = oldHandler[i];
				if (pa != null) {
					patternHandlerSlot[i] = pa;
					patternHandlers.add(pa);
					if (pa.getCondensedInputs().length == 0) {
						craftingTracker.setEmitable(pa.getCondensedOutputs()[0]);
					} else {
						craftingTracker.addCraftingOption(this, pa);
					}
					continue;
				}
			}
			if (!ItemStackUtils.isEmpty(currentPatternStack)
				&& currentPatternStack.getItem() != null
				&& currentPatternStack.getItem() instanceof ICraftingPatternItem) {
				ICraftingPatternItem currentPattern = (ICraftingPatternItem) currentPatternStack
					.getItem();

				if (currentPattern != null && currentPattern.getPatternForItem(currentPatternStack, getWorld()) != null
					&& currentPattern.getPatternForItem(currentPatternStack, getWorld()).isCraftable()) {
					ICraftingPatternDetails pattern = new CraftingPattern(currentPattern.getPatternForItem(currentPatternStack, getWorld()));
					this.patternHandlers.add(pattern);
					this.patternHandlerSlot[i] = pattern;
					if (pattern.getCondensedInputs().length == 0) {
						craftingTracker.setEmitable(pattern.getCondensedOutputs()[0]);
					} else {
						craftingTracker.addCraftingOption(this, pattern);
					}
				}
			}
			oldStack[i] = currentPatternStack;
		}
		updateWatcher();
	}

	@Override
	public boolean pushPattern(ICraftingPatternDetails patternDetails,
		InventoryCrafting table) {
		if (this.isBusy) {
			return false;
		}
		if (patternDetails instanceof CraftingPattern) {
			CraftingPattern patter = (CraftingPattern) patternDetails;
			HashMap<Fluid, Long> fluids = new HashMap<>();
			for (IAEFluidStack stack : patter.getCondensedFluidInputs()) {
				if (fluids.containsKey(stack.getFluid())) {
					Long amount = fluids.get(stack.getFluid()) + stack.getStackSize();
					fluids.remove(stack.getFluid());
					fluids.put(stack.getFluid(), amount);
				} else {
					fluids.put(stack.getFluid(), stack.getStackSize());
				}
			}
			IGrid grid = this.node.getGrid();
			if (grid == null) {
				return false;
			}
			IStorageGrid storage = grid.getCache(IStorageGrid.class);
			if (storage == null) {
				return false;
			}
			for (Fluid fluid : fluids.keySet()) {
				Long amount = fluids.get(fluid);
				IAEFluidStack extractFluid = storage.getInventory(StorageChannels.FLUID()).extractItems(StorageChannels.FLUID().createStack(new FluidStack(fluid, (int) (amount))), Actionable.SIMULATE, new MachineSource(this));
				if (extractFluid == null || extractFluid.getStackSize() != amount) {
					return false;
				}
			}
			for (Fluid fluid : fluids.keySet()) {
				Long amount = fluids.get(fluid);
				IAEFluidStack extractFluid = storage.getInventory(StorageChannels.FLUID()).extractItems(StorageChannels.FLUID().createStack(new FluidStack(fluid, (int) (amount))), Actionable.MODULATE, new MachineSource(this));
			}
			this.finishCraftingTime = System.currentTimeMillis() + 1000;

			this.returnStack = patter.getOutput(table, getWorld());

			this.optionalReturnStack = new ItemStack[9];
			for (int i = 0; i < 9; i++) {
				ItemStack s = table.getStackInSlot(i);
				if (s != null && s.getItem() != null) {
					this.optionalReturnStack[i] = s.getItem().getContainerItem(s.copy());
				}
			}

			this.isBusy = true;
		}
		return true;
	}

	@Override
	public void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		this.inventory.readFromNBT(tagCompound);
		if (hasWorld()) {
			IGridNode node = getGridNode();
			if (tagCompound.hasKey("nodes") && node != null) {
				node.loadFromNBT("node0", tagCompound.getCompoundTag("nodes"));
				node.updateState();
			}
		}
	}

	@Override
	public void securityBreak() {

	}

	@Override
	public void update() {
		if (getWorld() == null || getWorld().provider == null) {
			return;
		}
		if (this.update) {
			this.update = false;
			if (getGridNode() != null && getGridNode().getGrid() != null) {
				getGridNode().getGrid().postEvent(new MENetworkCraftingPatternChange(this.instance, getGridNode()));
			}
		}
		if (this.isBusy && this.finishCraftingTime <= System.currentTimeMillis() && getWorld() != null && !getWorld().isRemote) {
			if (this.node == null || this.returnStack == null) {
				return;
			}
			IGrid grid = this.node.getGrid();
			if (grid == null) {
				return;
			}
			IStorageGrid storage = grid.getCache(IStorageGrid.class);
			if (storage == null) {
				return;
			}
			storage.getInventory(StorageChannels.ITEM()).injectItems(StorageChannels.ITEM().createStack(this.returnStack), Actionable.MODULATE, new MachineSource(this));
			for (ItemStack s : this.optionalReturnStack) {
				if (s == null || s.isEmpty()) {
					continue;
				}
				storage.getInventory(StorageChannels.ITEM()).injectItems(StorageChannels.ITEM().createStack(s), Actionable.MODULATE, new MachineSource(this));
			}
			this.optionalReturnStack = new ItemStack[0];
			this.isBusy = false;
			this.returnStack = null;
			markDirty();
		}
		if (!this.isBusy && getWorld() != null && !getWorld().isRemote) {
			for (IAEItemStack stack : this.removeList) {
				this.requestedItems.remove(stack);
			}
			this.removeList.clear();
			if (!this.requestedItems.isEmpty()) {
				for (IAEItemStack s : this.requestedItems) {
					IGrid grid = this.node.getGrid();
					if (grid == null) {
						break;
					}
					ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
					if (crafting == null) {
						break;
					}
					if (!crafting.isRequesting(s)) {
						this.removeList.add(s);
						continue;
					}
					for (ICraftingPatternDetails details : this.patternHandlers) {
						if (details.getCondensedOutputs()[0].equals(s)) {
							CraftingPattern patter = (CraftingPattern) details;
							HashMap<Fluid, Long> fluids = new HashMap<>();
							for (IAEFluidStack stack : patter.getCondensedFluidInputs()) {
								if (fluids.containsKey(stack.getFluid())) {
									Long amount = fluids.get(stack.getFluid()) + stack.getStackSize();
									fluids.remove(stack.getFluid());
									fluids.put(stack.getFluid(), amount);
								} else {
									fluids.put(stack.getFluid(), stack.getStackSize());
								}
							}
							IStorageGrid storage = grid.getCache(IStorageGrid.class);
							if (storage == null) {
								break;
							}
							boolean doBreak = false;
							for (Fluid fluid : fluids.keySet()) {
								Long amount = fluids.get(fluid);
								IAEFluidStack extractFluid = storage.getInventory(StorageChannels.FLUID()).extractItems(StorageChannels.FLUID().createStack(new FluidStack(fluid, (int) (amount))), Actionable.SIMULATE, new MachineSource(this));
								if (extractFluid == null || extractFluid.getStackSize() != amount) {
									doBreak = true;
									break;
								}
							}
							if (doBreak) {
								break;
							}
							for (Fluid fluid : fluids.keySet()) {
								Long amount = fluids.get(fluid);
								IAEFluidStack extractFluid = storage.getInventory(StorageChannels.FLUID()).extractItems(StorageChannels.FLUID().createStack(new FluidStack(fluid, (int) (amount))), Actionable.MODULATE, new MachineSource(this));
							}
							this.finishCraftingTime = System.currentTimeMillis() + 1000;

							this.returnStack = patter.getCondensedOutputs()[0].createItemStack();
							this.isBusy = true;
							markDirty();
							return;
						}
					}
				}
			}
		}
	}

	private void updateWatcher() {
		this.requestedItems = new ArrayList<>();
		IGrid grid;
		IGridNode node = getGridNode();
		ICraftingGrid crafting = null;
		if (node != null) {
			grid = node.getGrid();
			if (grid != null) {
				crafting = grid.getCache(ICraftingGrid.class);
			}
		}
		for (ICraftingPatternDetails patter : this.patternHandlers) {
			this.watcher.reset();
			if (patter.getCondensedInputs().length == 0) {
				this.watcher.add(patter.getCondensedOutputs()[0]);

				if (crafting != null) {
					if (crafting.isRequesting(patter.getCondensedOutputs()[0])) {
						this.requestedItems
							.add(patter.getCondensedOutputs()[0]);
					}
				}
			}
		}
	}

	@Override
	public void updateWatcher(ICraftingWatcher newWatcher) {
		this.watcher = newWatcher;
		updateWatcher();
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		super.writeToNBT(tagCompound);
		this.inventory.writeToNBT(tagCompound);
		if (!hasWorld()) {
			return tagCompound;
		}
		IGridNode node = getGridNode();
		if (node != null) {
			NBTTagCompound nodeTag = new NBTTagCompound();
			node.saveToNBT("node0", nodeTag);
			tagCompound.setTag("nodes", nodeTag);
		}
		return tagCompound;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public GuiContainer getClientGuiElement(EntityPlayer player, Object... args) {
		return new GuiFluidCrafter(player.inventory, inventory);
	}

	@Override
	public Container getServerGuiElement(EntityPlayer player, Object... args) {
		return new ContainerFluidCrafter(player.inventory, inventory);
	}
}
