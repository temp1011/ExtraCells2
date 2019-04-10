package extracells.integration.mekanism.gas

import java.util

import appeng.api.AEApi
import extracells.api.ECApi
import extracells.api.gas.{IAEGasStack, IGasStorageChannel}
import extracells.integration.Integration
import extracells.integration.jei.Jei
import mekanism.api.gas.{Gas, GasRegistry, IGasHandler, ITubeConnection}
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.{Fluid, FluidRegistry, FluidStack}

import scala.collection.JavaConversions._


object MekanismGas {

  private var fluidGas: Map[Gas, Fluid] = Map()

  def preInit(): Unit = {
    AEApi.instance.storage.registerStorageChannel[IAEGasStack, IGasStorageChannel](classOf[IGasStorageChannel], new GasStorageChannel())
  }

  def init() {

  }

  def getFluidGasMap: util.Map[Gas, Fluid] = mapAsJavaMap(fluidGas)

  def postInit() {
    val it = GasRegistry.getRegisteredGasses.iterator
    while (it.hasNext) {
      val g = it.next
      val fluid = new GasFluid(g)
      if ((!FluidRegistry.isFluidRegistered(fluid)) && FluidRegistry.registerFluid(fluid))
        fluidGas += (g -> fluid)
        if(Integration.Mods.JEI.isEnabled){
          Jei.addFluidToBlacklist(new FluidStack(fluid, 1000))
        }
    }
    ECApi.instance.addFluidToShowBlacklist(classOf[GasFluid])
    ECApi.instance.addFluidToStorageBlacklist(classOf[GasFluid])
  }

  def getGasResourceLocation(gasName: String): ResourceLocation =
    GasRegistry.getGas(gasName).getIcon

  class GasFluid(gas: Gas) extends Fluid("ec.internal." + gas.getName, gas.getIcon, gas.getIcon) {

    override def getLocalizedName(stack: FluidStack): String = gas.getLocalizedName

    def getGas: Gas = gas
  }

}
