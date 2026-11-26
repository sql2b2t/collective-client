package me.collective.client.modules.client

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PlayerTotemEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.AliasRegistry
import me.collective.client.events.ModuleToggleEvent
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.managers.ConfigurationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import org.apache.commons.io.FileUtils as ApacheFileUtils
import java.awt.Color
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Random
import java.util.function.Predicate
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.ChatVisiblity

class NotificationsModule : Module("Notifications", "Lets you know about what's going on in the chat.", ModuleCategory.CLIENT) {
    @JvmField
    val watermarkSetting: Setting<Color>
    @JvmField
    val bracketsSetting: Setting<Color>
    private val commandsSetting: Setting<Boolean>
    private val modulesSetting: Setting<Boolean>
    private val onlyDrawnSetting: Setting<Boolean>
    private val totemPopsSetting: Setting<Boolean>
    private val selfSetting: Setting<Boolean>
    private val primarySetting: Setting<Color>
    private val secondarySetting: Setting<Color>
    private val sayChatSetting: Setting<Boolean>
    private val pathSetting: Setting<String>
    private val delaySetting: Setting<Float>
    private val onlyEnemiesSetting: Setting<Boolean>
    private val randomSetting: Setting<Boolean>
    private val refreshSetting: Setting<Boolean>
    private var totemMessageIndex: Int
    private val totemMessages: MutableList<String>
    private val totemRandom: Random

    override fun onEnable() {
        this.reloadTotemMessages()
    }

    private fun printTotemNotification(playerName: String, actionText: String, popCount: Int) {
        val options = ClientAccess.collectiveMinecraft.options
        val visibilityOption = options.chatVisibility()
        if (visibilityOption.get() != ChatVisiblity.HIDDEN) {
            var messageBuilder = Component.empty()
            messageBuilder = messageBuilder.append(Component.literal(playerName).withStyle { lambdaStyle ->
                val lambdaColorOption: Setting<Color> = this.primarySetting
                val lambdaColor = lambdaColorOption.value
                val lambdaColorArgb = lambdaColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
            messageBuilder = messageBuilder.append(Component.literal(actionText).withStyle { lambdaStyle ->
                val lambdaColorOption: Setting<Color> = this.secondarySetting
                val lambdaColor = lambdaColorOption.value
                val lambdaColorArgb = lambdaColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
            val countText = popCount.toString()
            messageBuilder = messageBuilder.append(Component.literal(countText).withStyle { lambdaStyle ->
                val lambdaColorOption: Setting<Color> = this.primarySetting
                val lambdaColor = lambdaColorOption.value
                val lambdaColorArgb = lambdaColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })

            val suffixText = if (popCount == 1) " totem." else " totems."
            val message = messageBuilder.append(Component.literal(suffixText).withStyle { lambdaStyle ->
                val lambdaColorOption: Setting<Color> = this.secondarySetting
                val lambdaColor = lambdaColorOption.value
                val lambdaColorArgb = lambdaColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
            val nameHash = playerName.hashCode()
            val signatureId = Math.abs(nameHash) * -1

            ChatUtils.printChat(message, ChatUtils.createSignature((signatureId)), ChatUtils.MessagePriority.MID)
        }
    }

    @EventHandler
    private fun onModuleToggle(event: ModuleToggleEvent) {
        if (this.isWorldUnavailable()) {
            return
        }
        val modulesOption: Setting<Boolean> = this.modulesSetting
        val notifyModules = modulesOption.value
        if (!notifyModules) {
            return
        }
        if (event.module is HUDModule) {
            return
        }
        if (event.module is UIModule) {
            return
        }
        val drawnOption: Setting<Boolean> = this.onlyDrawnSetting
        val onlyDrawn = drawnOption.value
        if (onlyDrawn && !event.module.isDrawn()) {
            return
        }

        val message = Component.empty()
        val toggledModule = event.module
        if (toggledModule.isToggled()) {
            val enabledOpening = "["
            message.append(Component.literal(enabledOpening).withStyle { lambdaStyle ->
                val lambdaToggleColor = this.moduleToggleColor(true)
                val lambdaDarkColor = lambdaToggleColor.darker()
                val lambdaColorArgb = lambdaDarkColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
            val enabledSign = "+"
            message.append(Component.literal(enabledSign).withStyle { lambdaStyle ->
                val lambdaToggleColor = this.moduleToggleColor(true)
                val lambdaColorArgb = lambdaToggleColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })

            val enabledClosing = "] "
            message.append(Component.literal(enabledClosing).withStyle { lambdaStyle ->
                val lambdaToggleColor = this.moduleToggleColor(true)
                val lambdaDarkColor = lambdaToggleColor.darker()
                val lambdaColorArgb = lambdaDarkColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
        } else {
            val disabledOpening = "["
            message.append(Component.literal(disabledOpening).withStyle { lambdaStyle ->
                val lambdaToggleColor = this.moduleToggleColor(false)
                val lambdaDarkColor = lambdaToggleColor.darker()
                val lambdaColorArgb = lambdaDarkColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
            val disabledSign = "-"
            message.append(Component.literal(disabledSign).withStyle { lambdaStyle ->
                val lambdaToggleColor = this.moduleToggleColor(false)
                val lambdaColorArgb = lambdaToggleColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })

            val disabledClosing = "] "
            message.append(Component.literal(disabledClosing).withStyle { lambdaStyle ->
                val lambdaToggleColor = this.moduleToggleColor(false)
                val lambdaDarkColor = lambdaToggleColor.darker()
                val lambdaColorArgb = lambdaDarkColor.hashCode()
                lambdaStyle.withColor(lambdaColorArgb)
            })
        }

        val aliasRegistry = ClientContext.aliasRegistry
        val aliasModule = event.module
        val moduleName = AliasRegistry.getModuleAlias(aliasRegistry, aliasModule)
        message.append(Component.literal(moduleName))
        ClientContext.taskScheduler.scheduleRunTickTask {
            val lambdaSignatureModule = event.module
            ChatUtils.printChat(message, ChatUtils.moduleSignature(lambdaSignatureModule))
        }
    }

    @EventHandler
    private fun onPlayerTotem(event: PlayerTotemEvent) {
        val localPlayer = ClientAccess.collectiveMinecraft.player!!
        val self = localPlayer == event.player
        val popsOption: Setting<Boolean> = this.totemPopsSetting
        val notifyPops = popsOption.value
        if (!notifyPops) {
            return
        }

        if (self) {
            val selfOption: Setting<Boolean> = this.selfSetting
            val notifySelf = selfOption.value
            if (!notifySelf) {
                return
            }
        }

        val displayName: String = if (self) {
            "You"
        } else {
            event.name
        }

        val playerName = displayName
        val popCount = event.popCount
        if (PlayerTotemEvent.Kind.TOTEM_POP != event.kind) {
            this.printTotemNotification(playerName, " has died after popping ", popCount)
            return
        }

        val popVerb: String = if (self) {
            " have"
        } else {
            " has"
        }

        this.printTotemNotification(playerName, popVerb + " popped ", popCount)
        val friendRegistry = ClientContext.socialRegistry

        if (friendRegistry.isFriendName(playerName) || self) {
            return
        }

        val enemyRegistry = ClientContext.socialRegistry

        if (!enemyRegistry.isEnemyName(playerName)) {
            val enemiesOption: Setting<Boolean> = this.onlyEnemiesSetting
            val onlyEnemies = enemiesOption.value
            if (onlyEnemies) {
                return
            }
        }

        val chatOption: Setting<Boolean> = this.sayChatSetting
        val sayChat = chatOption.value
        if (!sayChat || this.totemMessages.isEmpty()) {
            return
        }

        ClientAccess.executorService.submit {
            try {
                val lambdaDelayOption: Setting<Float> = this.delaySetting
                val lambdaDelay = lambdaDelayOption.value
                if (lambdaDelay > 0.0f) {
                    val lambdaSleepDelayOption: Setting<Float> = this.delaySetting
                    val lambdaDelaySeconds = lambdaSleepDelayOption.value

                    val lambdaDelayMillis = (lambdaDelaySeconds * 1e+03f).toLong()
                    val lambdaJitterRandom = this.totemRandom

                    val lambdaSleepMillis = lambdaDelayMillis + lambdaJitterRandom.nextLong(500L)
                    Thread.sleep(lambdaSleepMillis)
                }

                var lambdaChatMessage: String? = null
                val lambdaRandomOption: Setting<Boolean> = this.randomSetting
                val lambdaRandomOrder = lambdaRandomOption.value
                if (lambdaRandomOrder) {
                    if (this.totemMessages.size == 1) {
                        lambdaChatMessage = this.totemMessages[0]
                    } else {
                        while (lambdaChatMessage == null) {
                            val lambdaRandomSource = this.totemRandom
                            val lambdaMessageCount = this.totemMessages.size
                            val lambdaRandomIndex = lambdaRandomSource.nextInt(lambdaMessageCount)
                            if (this.totemMessageIndex != lambdaRandomIndex) {
                                this.totemMessageIndex = lambdaRandomIndex
                                lambdaChatMessage = this.totemMessages[lambdaRandomIndex]
                            }
                        }
                    }
                } else {
                    lambdaChatMessage = this.totemMessages[this.totemMessageIndex]
                    this.totemMessageIndex = (this.totemMessageIndex + 1) % this.totemMessages.size
                }

                lambdaChatMessage = lambdaChatMessage!!.replace("{name}", playerName)

                lambdaChatMessage = lambdaChatMessage.replace("{totems}", popCount.toString())

                ChatUtils.sendChatOrCommand(lambdaChatMessage)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun moduleToggleColor(enabled: Boolean): Color {
        return if (enabled) Color(0, 190, 50) else Color(200, 0, 0)
    }

    private fun reloadTotemMessages() {
        try {
            var configuredPath: java.nio.file.Path = ConfigurationManager.ROOT_DIRECTORY
            val pathOption: Setting<String> = this.pathSetting
            configuredPath = configuredPath.resolve(pathOption.value)
            val messagePath = me.collective.client.utils.FileUtils.findExistingWithSuffixes(configuredPath, ".txt")
            val messageFile = messagePath.toFile()
            if (!messageFile.exists()) {
                throw RuntimeException("Invalid totem pop file path")
            }

            this.totemMessageIndex = 0
            this.totemMessages.clear()
            val messages = this.totemMessages
            val messageReadFile = messagePath.toFile()
            val charset = StandardCharsets.UTF_8
            messages.addAll(ApacheFileUtils.readLines(messageReadFile, charset))
        } catch (exception: Exception) {
            val chatOption: Setting<Boolean> = this.sayChatSetting
            val sayChat = chatOption.value
            if (sayChat) {
                val errorMessage = Component.literal("Failed to open totem pop file")
                    .withStyle { style -> style.withColor(ChatFormatting.RED) }
                val errorDescription = exception.toString()
                val errorHash = errorDescription.hashCode()

                ChatUtils.printChat(errorMessage, ChatUtils.createSignature((errorHash)), ChatUtils.MessagePriority.LOW)
                exception.printStackTrace()
            }
        }
    }

    // Java-методы getBracketColor()/getWatermarkColor(); ChatUtils.kt читает их как свойства.
    val bracketColor: Int
        get() {
            val bracketColor = this.bracketsSetting.value
            return bracketColor.hashCode()
        }

    val watermarkColor: Int
        get() {
            val watermarkColor = this.watermarkSetting.value
            return watermarkColor.hashCode()
        }

    init {
        this.watermarkSetting = this.add(ColorSetting(("Watermark"), Color(-4351489, true)))
        this.bracketsSetting = this.add(ColorSetting("Brackets", Color(-4351489, true)))
        this.commandsSetting = this.add(BooleanSetting(("Commands"), true))
        val modulesOption = BooleanSetting("Modules", true)
        modulesOption.markVisibilityDependency()
        this.modulesSetting = this.add(modulesOption)
        this.onlyDrawnSetting = this.add(BooleanSetting("OnlyDrawn", false)
            .dependsOn(this.modulesSetting))
        val popsOption = BooleanSetting("TotemPops", false)
        popsOption.markVisibilityDependency()
        this.totemPopsSetting = this.add(popsOption)
        this.selfSetting = this.add(BooleanSetting("Self", false)
            .dependsOn(this.totemPopsSetting))
        val primaryVisibility = Predicate<Color> { ignored -> this.totemPopsSetting.isExpanded }
        this.primarySetting = this.add(ColorSetting("Primary", Color(-2828, true), primaryVisibility))
        val secondaryVisibility = Predicate<Color> { ignored -> this.totemPopsSetting.isExpanded }
        this.secondarySetting = this.add(ColorSetting("Secondary", Color(-4351489, true), secondaryVisibility))
        val sayChatVisibility = Predicate<Boolean> { ignored -> this.totemPopsSetting.isExpanded }
        val sayChatOption = BooleanSetting("SayChat", false, sayChatVisibility)
        sayChatOption.markVisibilityDependency()
        this.sayChatSetting = this.add(sayChatOption)
        val pathVisibility = Predicate<String> { ignored ->
            this.totemPopsSetting.isExpanded && this.sayChatSetting.isExpanded
        }
        this.pathSetting = this.add(StringSetting(("Path"), "totempop.txt", pathVisibility))
        val delayVisibility = Predicate<Float> { ignored ->
            this.totemPopsSetting.isExpanded && this.sayChatSetting.isExpanded
        }
        val delayOption = NumberSetting("Delay", 0.0f, 0.0f, 15.0f, delayVisibility)
        delayOption.withUnit(("s"))
        this.delaySetting = this.add(delayOption)
        this.onlyEnemiesSetting = this.add(BooleanSetting("OnlyEnemies", false)
            .dependsOn(this.totemPopsSetting, this.sayChatSetting))
        val randomVisibility = Predicate<Boolean> { ignored ->
            this.totemPopsSetting.isExpanded && this.sayChatSetting.isExpanded
        }
        this.randomSetting = this.add(BooleanSetting("Random", true, randomVisibility))
        val refreshVisibility = Predicate<Boolean> { ignored ->
            this.totemPopsSetting.isExpanded && this.sayChatSetting.isExpanded
        }
        this.refreshSetting = this.add(BooleanSetting("Refresh", false, refreshVisibility))
        this.totemMessageIndex = 0
        this.totemMessages = ArrayList()
        this.totemRandom = Random()

        this.setDrawn(false)
        this.sayChatSetting.setChangeCallback {
            val lambdaChatOption: Setting<Boolean> = this.sayChatSetting
            val lambdaSayChat = lambdaChatOption.value
            if (lambdaSayChat) {
                this.reloadTotemMessages()
            }
        }
        this.refreshSetting.setChangeCallback {
            val lambdaRefreshOption: Setting<Boolean> = this.refreshSetting
            val lambdaRefreshEnabled = lambdaRefreshOption.value
            if (lambdaRefreshEnabled) {
                this.refreshSetting.value = false
                this.reloadTotemMessages()
            }
        }
        this.randomSetting.setChangeCallback { this.totemMessageIndex = 0 }
        this.pathSetting.setChangeCallback { this.reloadTotemMessages() }
    }

    companion object {
        @JvmStatic
        fun shouldNotifyCommands(notifications: NotificationsModule): Boolean {
            return notifications.commandsSetting.value
        }
    }
}
