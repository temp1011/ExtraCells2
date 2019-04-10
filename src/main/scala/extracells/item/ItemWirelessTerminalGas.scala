package extracells.item

import extracells.api.{ECApi, IWirelessGasTermHandler}
import extracells.item.ItemWirelessTerminalFluid.isInCreativeTab
import extracells.models.ModelManager
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.util.{ActionResult, EnumActionResult, EnumHand}
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

object ItemWirelessTerminalGas extends ItemECBase with IWirelessGasTermHandler with WirelessTermBase {
  def THIS: ItemWirelessTerminalGas.type = this

  ECApi.instance.registerWirelessTermHandler(this)


  override def getUnlocalizedName(itemStack: ItemStack): String =
    super.getUnlocalizedName(itemStack).replace("item.extracells", "extracells.item")


  def isItemNormalWirelessTermToo(is: ItemStack): Boolean = false


  override def onItemRightClick(world: World, entityPlayer: EntityPlayer, hand: EnumHand): ActionResult[ItemStack] =
    new ActionResult(EnumActionResult.SUCCESS, ECApi.instance.openWirelessGasTerminal(entityPlayer, hand, world))


  @SideOnly(Side.CLIENT)
  override def registerModel(item: Item, manager: ModelManager): Unit =
    manager.registerItemModel(item, 0, "terminals/fluid_wireless")

  override def isInCreativeTab2(targetTab: CreativeTabs): Boolean = isInCreativeTab(targetTab)

}
