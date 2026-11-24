package me.collective.client.utils

import me.collective.client.modules.Module
import me.collective.client.settings.Setting
import java.lang.reflect.Field
import java.util.Properties
import net.fabricmc.loader.api.FabricLoader

class ExperimentalFeatures {
   companion object {
      private var experimentalFeaturesAllowed: Boolean = false

      init {
         experimentalFeaturesAllowed = false

         val loader = FabricLoader.getInstance()

         for (launchArgument in loader.getLaunchArguments(true)) {
            if ("--collective-allow-experimental" == launchArgument) {
               experimentalFeaturesAllowed = true
               break
            }
         }

         val propertiesForPresenceCheck: Properties = System.getProperties()
         if (propertiesForPresenceCheck["collective.allowExperimental"] != null) {
            val expectedPropertyValue = "true"
            val propertyValue = System.getProperties()["collective.allowExperimental"] as String
            if (expectedPropertyValue.equals(propertyValue, ignoreCase = true)) {
               experimentalFeaturesAllowed = true
            }
         }
      }

      @JvmStatic
      fun isExperimentalDisabled(): Boolean {
         return !experimentalFeaturesAllowed
      }

      @JvmStatic
      fun isFieldHidden(field: Field): Boolean {
         return isExperimentalDisabled() && field.isAnnotationPresent(Experimental::class.java)
      }

      @JvmStatic
      fun isEnumConstantHidden(enumConstant: Enum<*>): Boolean {
         return try {
            if (!isExperimentalDisabled()) {
               false
            } else {
               val declaringClass: Class<*> = (enumConstant as java.lang.Enum<*>).declaringClass
               val constantName: String = enumConstant.name
               declaringClass.getDeclaredField(constantName).isAnnotationPresent(Experimental::class.java)
            }
         } catch (missingField: NoSuchFieldException) {
            false
         }
      }

      @JvmStatic
      fun isClassHidden(type: Class<*>): Boolean {
         return isExperimentalDisabled() && type.isAnnotationPresent(Experimental::class.java)
      }

      @JvmStatic
      fun unregisterHiddenSettings(module: Module) {
         val moduleType: Class<out Module> = module.javaClass

         for (field in moduleType.declaredFields) {
            if (!isFieldHidden(field)) {
               continue
            }
            field.isAccessible = true
            try {
               val setting = field.get(module) as Setting<*>
               module.unregister(setting)
            } catch (accessFailure: IllegalAccessException) {
               accessFailure.printStackTrace()
            }
         }
      }
   }
}
