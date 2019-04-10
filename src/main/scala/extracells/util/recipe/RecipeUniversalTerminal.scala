package extracells.util.recipe

import appeng.api.features.INetworkEncodable
import appeng.api.implementations.items.IAEItemPowerStorage
import extracells.item.{ItemWirelessTerminalUniversal, WirelessTerminalType}
import extracells.registries.ItemEnum
import extracells.util.UniversalTerminal
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.ForgeHooks
import java.util

import appeng.api.config.Actionable
import net.minecraft.util.NonNullList


object RecipeUniversalTerminal extends net.minecraftforge.registries.IForgeRegistryEntry.Impl[IRecipe] with IRecipe {

  val THIS: RecipeUniversalTerminal.type = this

  val itemUniversal: ItemWirelessTerminalUniversal.type = ItemWirelessTerminalUniversal

  override def matches(inventory: InventoryCrafting, world: World): Boolean = {
    var hasWireless = false
    var isUniversal = false
    var hasTerminal = false
    var terminals = List[WirelessTerminalType]()
    var terminal: ItemStack = null
    val size = inventory.getSizeInventory
    for (i <- 0 until size) {
      val stack = inventory.getStackInSlot(i)
      if (stack != null) {
        val item = stack.getItem
        if (item == itemUniversal) {
          if (hasWireless)
            return false
          else {
            hasWireless = true
            isUniversal = true
            terminal = stack
          }
        } else if (UniversalTerminal.isWirelessTerminal(stack)) {
          if (hasWireless)
            return false
          hasWireless = true
          terminal = stack
        } else if (UniversalTerminal.isTerminal(stack)) {
          hasTerminal = true
          val typeTerminal = UniversalTerminal.getTerminalType(stack)
          if (terminals.contains(typeTerminal)) {
            return false
          } else {
            terminals ++= List(typeTerminal)
          }
        }
      }
    }
    if (!(hasTerminal && hasWireless))
      return false
    if (isUniversal) {
      for (x <- terminals) {
        if (itemUniversal.isInstalled(terminal, x))
          return false
      }
      true
    } else {
      val terminalType = UniversalTerminal.getTerminalType(terminal)
      for (x <- terminals) {
        if (x == terminalType)
          return false
      }
      true
    }
  }

  override def getRemainingItems(inv: InventoryCrafting): NonNullList[ItemStack] = ForgeHooks.defaultRecipeGetRemainingItems(inv)

  override def getRecipeOutput: ItemStack = ItemEnum.UNIVERSALTERMINAL.getDamagedStack(0)

  //override def getRecipeSize: Int = 2

  override def getCraftingResult(inventory: InventoryCrafting): ItemStack = {
    var isUniversal = false
    var terminals = List[WirelessTerminalType]()
    var terminal: ItemStack = null
    val size = inventory.getSizeInventory
    for (i <- 0 until size) {
      val stack = inventory.getStackInSlot(i)
      if (stack != null) {
        val item = stack.getItem
        if (item == itemUniversal) {
          isUniversal = true
          terminal = stack.copy
        } else if (UniversalTerminal.isWirelessTerminal(stack)) {
          terminal = stack.copy
        } else if (UniversalTerminal.isTerminal(stack)) {
          val typeTerminal = UniversalTerminal.getTerminalType(stack)
          terminals ++= List(typeTerminal)

        }
      }
    }
    if (isUniversal) {
      for (x <- terminals)
        itemUniversal.installModule(terminal, x)
    } else {
      val terminalType = UniversalTerminal.getTerminalType(terminal)
      val itemTerminal = terminal.getItem
      val t = new ItemStack(itemUniversal)
      itemTerminal match {
        case encodable: INetworkEncodable =>
          val key = encodable.getEncryptionKey(terminal)
          if (key != null)
            itemUniversal.setEncryptionKey(t, key, null)
        case _ =>
      }
      itemTerminal match {
        case storage: IAEItemPowerStorage =>
          val power = storage.getAECurrentPower(terminal)
          itemUniversal.injectAEPower(t, power, Actionable.MODULATE)
        case _ =>
      }
      if (terminal.hasTagCompound) {
        val nbt = terminal.getTagCompound
        if (!t.hasTagCompound)
          t.setTagCompound(new NBTTagCompound)
        if (nbt.hasKey("BoosterSlot")) {
          t.getTagCompound.setTag("BoosterSlot", nbt.getTag("BoosterSlot"))
        }
        if (nbt.hasKey("MagnetSlot"))
          t.getTagCompound.setTag("MagnetSlot", nbt.getTag("MagnetSlot"))
      }
      itemUniversal.installModule(t, terminalType)
      t.getTagCompound.setByte("type", terminalType.ordinal.toByte)
      terminal = t
      for (x <- terminals)
        itemUniversal.installModule(terminal, x)
    }
    terminal
  }

  override def canFit(width: Int, height: Int): Boolean = (width >= 1 && height >= 2) || (width >= 2 && height >= 1)
}
