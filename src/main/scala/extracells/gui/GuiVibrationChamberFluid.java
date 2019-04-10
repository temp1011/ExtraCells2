package extracells.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import extracells.container.ContainerVibrationChamberFluid;
import extracells.gui.widget.WidgetFluidTank;
import extracells.tileentity.TileEntityVibrationChamberFluid;

public class GuiVibrationChamberFluid extends GuiBase<ContainerVibrationChamberFluid> {

	public WidgetFluidTank widgetFluidTank;

	public GuiVibrationChamberFluid(EntityPlayer player, TileEntityVibrationChamberFluid tileEntity) {
		super(new ResourceLocation("extracells", "textures/gui/vibrationchamberfluid.png"), new ContainerVibrationChamberFluid(player.inventory, tileEntity));
		widgetManager.add(new WidgetFluidTank(widgetManager, tileEntity.getTank(), 79, 6));
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);
		renderHoveredToolTip(mouseX, mouseY);
	}
}
