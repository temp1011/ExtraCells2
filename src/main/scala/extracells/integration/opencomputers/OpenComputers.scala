package extracells.integration.opencomputers

import appeng.api.AEApi
import extracells.integration.Integration
import extracells.item.ItemOCUpgrade
import li.cil.oc.api.Driver
import li.cil.oc.api.driver._

object OpenComputers {

	def init(){
		add(new DriverFluidExportBus)
		add(new DriverFluidImportBus)
		add(new DriverOreDictExportBus)
		add(new DriverFluidInterface)
		if(Integration.Mods.MEKANISMGAS.isEnabled){
			add(new DriverGasExportBus)
			add(new DriverGasImportBus)
		}
		add(ItemOCUpgrade)
		AEApi.instance.registries.wireless.registerWirelessHandler(WirelessHandlerUpgradeAE)
		ExtraCellsPathProvider
	}

	def add(provider: AnyRef): Unit ={
			provider match {
        case environmentProvider: EnvironmentProvider => Driver.add(environmentProvider)
        case _ =>
      }
			provider match {
        case item: DriverItem => Driver.add(item)
        case _ =>
      }
			provider match {
        case block: DriverBlock => Driver.add(block)
        case _ =>
      }
			provider match {
        case converter: Converter => Driver.add(converter)
        case _ =>
      }
			provider match {
        case inventoryProvider: InventoryProvider => Driver.add(inventoryProvider)
        case _ =>
      }
	}

}
