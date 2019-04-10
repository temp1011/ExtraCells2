package extracells.util;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

import extracells.registries.ItemEnum;

public class CreativeTabEC extends CreativeTabs {

	public static final CreativeTabs INSTANCE = new CreativeTabEC();

	public CreativeTabEC() {
		super("Extra_Cells");
	}

	@Override
	public ItemStack getTabIconItem() {
		return ItemEnum.FLUIDSTORAGE.getSizedStack(1);
	}

	@Override
	public ItemStack getIconItemStack() {
		return new ItemStack(ItemEnum.FLUIDSTORAGE.getItem());
	}
}
