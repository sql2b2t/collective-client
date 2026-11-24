package me.collective.client.modules.misc


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.FloatAnimation
import me.collective.client.managers.SocialRegistry
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.SocialRole

import com.mojang.authlib.GameProfile
import java.awt.Color
import java.util.Comparator
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.ToIntFunction
import net.minecraft.Optionull
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.PlayerTeam

class ExtraTabModule : Module("ExtraTab", "Allows you to customize your TAB-screen.", ModuleCategory.MISC) {
   @JvmField
   val displaySetting: Setting<DisplayChoice>
   @JvmField
   val latencySetting: Setting<LatencyChoice>
   @JvmField
   val limitSetting: Setting<Int>
   @JvmField
   val scaleSetting: Setting<Float>
   @JvmField
   val coloredSetting: Setting<Boolean>
   @JvmField
   val smoothSetting: Setting<Boolean>
   @JvmField
   val betterSkinSetting: Setting<Boolean>
   @JvmField
   val highlightSetting: Setting<Boolean>
   @JvmField
   val selfSetting: Setting<Color>
   @JvmField
   val socialSetting: Setting<Boolean>
   @JvmField
   val sortSetting: Setting<Boolean>
   @JvmField
   val modeSetting: Setting<ModeChoice>
   @JvmField
   val reverseSetting: Setting<Boolean>
   private val openAnimation: FloatAnimation

   fun getTabScale(): Float {
      if (!this.isToggled()) {

         return 1.0f
      } else {
         return this.scaleSetting.value
      }
   }

   fun getOpenProgress(): Float {

      val remainderBase = 1.0f
      val animationSnapshot: FloatAnimation = this.openAnimation

      val remainderCapture = remainderBase - animationSnapshot.getValue()

      return ((1.0) - Math.pow(remainderCapture.toDouble(), 5.0)).toFloat()
   }

   fun createPlayerComparator(): Comparator<PlayerInfo> {
      var comparator: Comparator<PlayerInfo> = Comparator.comparingInt<PlayerInfo> { spectatorInfo: PlayerInfo ->
         var spectatorWeight = if (spectatorInfo.gameMode == GameType.SPECTATOR) 1 else 0
         val spectatorReverseSnapshot: Setting<Boolean> = this.reverseSetting
         val spectatorReverse = spectatorReverseSnapshot.value
         if (spectatorReverse) {
            spectatorWeight *= -1
         }

         spectatorWeight
      }.thenComparing(Function<PlayerInfo, String> { teamInfo ->
            val team = teamInfo.team
            val teamName = Function<PlayerTeam, String> { obj -> obj.name }
            Optionull.mapOrDefault(team, teamName, "")
         }).thenComparing(Comparator.comparingInt { nameInfo: PlayerInfo -> this.getSortWeight(nameInfo) }).thenComparing(
         Function<PlayerInfo, String> { nameInfo ->
            val profile = nameInfo.profile
            profile.name()
         },
         Comparator { first: String, second: String -> first.compareTo(second, ignoreCase = true) }
      )
      val reverseSnapshot: Setting<Boolean> = this.reverseSetting
      val reverse = reverseSnapshot.value
      if (reverse) {
         comparator = comparator.reversed()
      }

      return comparator
   }

   @EventHandler
   private fun updateOpenAnimation(event: HudRenderEvent) {
      val animationSnapshot: FloatAnimation = this.openAnimation
      val smoothSnapshot: Setting<Boolean> = this.smoothSetting
      val smoothEnabled = smoothSnapshot.value
      var openTarget = false
      if (smoothEnabled) {
         val tabKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyPlayerList
         if (tabKey.isDown) {
            val minecraft: Minecraft = ClientAccess.collectiveMinecraft
            openTarget = !minecraft.isLocalServer
         }
      }

      animationSnapshot.setTarget(openTarget, 200L)
   }

   private fun getSortWeight(playerInfo: PlayerInfo): Int {
      val modeSnapshot: Setting<ModeChoice> = this.modeSetting
      val mode = modeSnapshot.value
      return when (mode) {
         ModeChoice.ALPHABET -> 0
         ModeChoice.SOCIALS -> {
            val socialManagerCapture: SocialRegistry = ClientContext.socialRegistry
            val socialProfile = playerInfo.profile
            val socialName = socialProfile.name()
            val socialType = socialManagerCapture.getRole(socialName)
            var socialWeight = 0
            if (socialType == SocialRole.FRIEND) {
               socialWeight = -1
            }

            if (socialType == SocialRole.ENEMY) {

               socialWeight = -2
            }

            socialWeight
         }
         ModeChoice.LATENCY -> -playerInfo.latency
         ModeChoice.LENGTH -> {
            val fontCapture: Font = ClientAccess.collectiveMinecraft.font
            val widthProfile = playerInfo.profile
            val widthName = widthProfile.name()
            -fontCapture.width(widthName)
         }
      }
   }

   init {
      displaySetting = add(EnumSetting("Display", DisplayChoice.ALL))
      latencySetting = add(EnumSetting("Latency", LatencyChoice.VANILLA))
      limitSetting = add(NumberSetting("Limit", 500, 10, 500))
      scaleSetting = add(NumberSetting("Scale", 1.0f, 0.5f, 1.5f))
      val coloredVisible = Predicate<Boolean> { ignored -> this.latencySetting.value == LatencyChoice.TEXT }
      coloredSetting = add(BooleanSetting("Colored", true, coloredVisible))
      smoothSetting = add(BooleanSetting("Smooth", false))
      betterSkinSetting = add(BooleanSetting("BetterSkin", true))
      val highlight = BooleanSetting("Highlight", true)
      highlight.markVisibilityDependency()
      highlightSetting = add(highlight)
      val selfVisible = Predicate<Color> { ignored -> this.highlightSetting.isExpanded }
      selfSetting = add(ColorSetting("Self", Color(-65281, true), selfVisible))
      val socialVisible = Predicate<Boolean> { ignored -> this.highlightSetting.isExpanded }
      socialSetting = add(BooleanSetting("Social", true, socialVisible))
      val sort = BooleanSetting("Sort", false)
      sort.markVisibilityDependency()
      sortSetting = add(sort)
      modeSetting = add(EnumSetting("Mode", ModeChoice.ALPHABET).dependsOn(this.sortSetting))
      reverseSetting = add(BooleanSetting("Reverse", false).dependsOn(this.sortSetting))
      openAnimation = FloatAnimation()
      val limitLabelSetting: Setting<Int> = this.limitSetting
      val maximumLabelPoint = SettingLabelCondition.MAX
      limitLabelSetting.setBoundaryLabel("Unlimited", maximumLabelPoint)

      this.setDrawn(false)
   }

   enum class DisplayChoice(private val displayName: String) : NamedEntry {
      ALL("All"),
      SOCIALS("Socials");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class LatencyChoice(private val displayName: String) : NamedEntry {
      VANILLA("Vanilla"),
      TEXT("Text"),
      NONE("None");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      ALPHABET("Alphabet"),
      SOCIALS("Socials"),
      LATENCY("Latency"),
      LENGTH("Length");

      override fun getName(): String {
         return this.displayName
      }
   }
}
