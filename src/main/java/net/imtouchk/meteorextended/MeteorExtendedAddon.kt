package net.imtouchk.meteorextended

import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules
import net.imtouchk.meteorextended.modules.NewChunks
import net.imtouchk.meteorextended.modules.BaseRaider
import net.imtouchk.meteorextended.modules.Debugger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

class MeteorExtendedAddon : MeteorAddon() {
    override fun onInitialize() {
        LOG.info("Initializing MeteorExtended addon")

        MeteorClient.EVENT_BUS.registerLambdaFactory("net.imtouchk.meteorextended") { lookupInMethod: Method, klass: Class<*>? ->
            lookupInMethod.invoke(
                null,
                klass,
                MethodHandles.lookup()
            ) as MethodHandles.Lookup
        }

        val modules = Modules.get()
        modules.add(Debugger())
        modules.add(NewChunks())
        modules.add(BaseRaider())
    }

    override fun onRegisterCategories() {
        Modules.registerCategory(CATEGORY)
    }

    override fun getPackage(): String {
        return "net.imtouchk.meteorextended"
    }

    companion object {
        @JvmStatic val LOG: Logger = LogManager.getLogger()
        @JvmStatic val CATEGORY: Category = Category("Meteor Extended")
    }
}
