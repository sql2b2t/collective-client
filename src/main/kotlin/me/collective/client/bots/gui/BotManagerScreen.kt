package me.collective.client.bots.gui

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.bots.BotClient
import me.collective.client.bots.BotConnectionState
import me.collective.client.bots.BotManager
import me.collective.client.bots.control.BotInventorySnapshot
import me.collective.client.bots.follow.BotFollowManager
import me.collective.client.bots.proxy.ProxyConfig
import me.collective.client.bots.proxy.ProxyManager
import me.collective.client.bots.proxy.ProxyType
import me.collective.client.bots.session.BotSessionManager
import me.collective.client.bots.session.BotSessionTransfer
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.awt.Color

class BotManagerScreen(parent: Screen? = null) : ClientScreen() {
    enum class Tab(val title: String) {
        BOTS("Боты"),
        CREATE("+ Добавить"),
        SETTINGS("Настройки")
    }

    private var currentTab: Tab = Tab.BOTS
    private var selectedBot: BotClient? = null
    private val inventorySnapshot = BotInventorySnapshot()

    // BOTS tab state
    private var botSearchQuery: String = ""
    private var isSearchFocused: Boolean = false
    private var botListScroll: Float = 0f
    private var chatMessage: String = ""
    private var isChatFocused: Boolean = false
    private var chatFromAll: Boolean = false

    // CREATE tab state
    private var createName: String = ""
    private var createAddress: String = ""
    private var createCount: String = "1"
    private var createProxyIndex: Int = -1 // -1 = None, >=0 index in ProxyManager.proxies
    private var createActiveField: Int = 1 // 1 = name, 2 = address, 3 = count

    // SETTINGS tab state
    private var proxyName: String = ""
    private var proxyHostPort: String = ""
    private var proxyUser: String = ""
    private var proxyPass: String = ""
    private var proxyType: ProxyType = ProxyType.SOCKS5
    private var proxyActiveField: Int = 0 // 1 = name, 2 = hostPort, 3 = user, 4 = pass

    init {
        this.parentScreen = parent
        val server = ClientAccess.collectiveMinecraft.currentServer
        if (server != null && server.ip != null) {
            createAddress = server.ip
        }
        if (BotManager.bots.isNotEmpty()) {
            selectedBot = BotManager.bots.firstOrNull()
        }
    }

    override fun onClose() {
        if (parentScreen != null) {
            ClientAccess.collectiveMinecraft.setScreen(parentScreen)
        } else {
            super.onClose()
        }
    }

    private fun drawRect(poseStack: PoseStack, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Color) {
        GuiDrawUtils.fillRoundedRect(poseStack, x, y, x + w, y + h, radius, color.rgb)
    }

    private fun drawOutline(poseStack: PoseStack, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Color) {
        GuiDrawUtils.drawRoundedBorder(poseStack, x, y, x + w, y + h, radius, color.rgb, 4)
    }

    override fun renderContents(graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val window = ClientAccess.collectiveMinecraft.window
        val screenWidth = window.guiScaledWidth.toFloat()
        val screenHeight = window.guiScaledHeight.toFloat()

        val panelWidth = 550.0f
        val panelHeight = 350.0f
        val startX = (screenWidth - panelWidth) / 2.0f
        val startY = (screenHeight - panelHeight) / 2.0f

        // Main card background
        drawRect(poseStack, startX, startY, panelWidth, panelHeight, 6.0f, Color(18, 18, 22, 245))
        drawOutline(poseStack, startX, startY, panelWidth, panelHeight, 6.0f, Color(45, 45, 55, 255))

        // Title and Tabs row
        renderTopTabs(poseStack, startX, startY, panelWidth, mouseX, mouseY)

        // Content by Tab
        when (currentTab) {
            Tab.BOTS -> renderBotsTab(graphics, poseStack, startX, startY, panelWidth, panelHeight, mouseX, mouseY)
            Tab.CREATE -> renderCreateTab(poseStack, startX, startY, panelWidth, panelHeight, mouseX, mouseY)
            Tab.SETTINGS -> renderSettingsTab(poseStack, startX, startY, panelWidth, panelHeight, mouseX, mouseY)
        }
    }

    private fun renderTopTabs(poseStack: PoseStack, startX: Float, startY: Float, panelWidth: Float, mouseX: Int, mouseY: Int) {
        // App title
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§bCollective §fBots", startX + 16.0f, startY + 12.0f, Color.WHITE)

        // Tabs
        var tabX = startX + 130.0f
        for (tab in Tab.values()) {
            val isActive = currentTab == tab
            val tabW = FontRenderer.INSTANCE.getWidth(tab.title) + 16.0f
            val isHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), tabX, startY + 8.0f, tabW, 20.0f)

            if (isActive) {
                drawRect(poseStack, tabX, startY + 8.0f, tabW, 20.0f, 3.0f, Color(50, 90, 180, 220))
            } else if (isHover) {
                drawRect(poseStack, tabX, startY + 8.0f, tabW, 20.0f, 3.0f, Color(35, 35, 45, 180))
            }

            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                tab.title,
                tabX + 8.0f,
                startY + 13.0f,
                if (isActive) Color.WHITE else Color.LIGHT_GRAY
            )
            tabX += tabW + 6.0f
        }

        // Close button (X)
        val closeHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), startX + panelWidth - 26.0f, startY + 8.0f, 18.0f, 18.0f)
        FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            "§c×",
            startX + panelWidth - 20.0f,
            startY + 11.0f,
            if (closeHover) Color.RED else Color.GRAY
        )
    }

    private fun renderBotsTab(graphics: GuiGraphics, poseStack: PoseStack, startX: Float, startY: Float, panelWidth: Float, panelHeight: Float, mouseX: Int, mouseY: Int) {
        val contentY = startY + 34.0f
        val leftW = 180.0f
        val leftH = panelHeight - 44.0f

        // --- LEFT PANE: Search + Bot List ---
        drawRect(poseStack, startX + 12.0f, contentY, leftW, leftH, 4.0f, Color(14, 14, 18, 220))
        drawOutline(poseStack, startX + 12.0f, contentY, leftW, leftH, 4.0f, Color(32, 32, 40, 255))

        // Search bar
        val searchBoxY = contentY + 6.0f
        drawRect(poseStack, startX + 18.0f, searchBoxY, leftW - 12.0f, 18.0f, 3.0f, if (isSearchFocused) Color(30, 30, 40) else Color(22, 22, 28))
        val searchDisplay = if (botSearchQuery.isEmpty() && !isSearchFocused) "§8Поиск ботов..." else botSearchQuery
        FontRenderer.INSTANCE.drawWithShadow(poseStack, searchDisplay, startX + 22.0f, searchBoxY + 5.0f, Color.LIGHT_GRAY)

        // Bulk action mini-buttons
        val bulkY = searchBoxY + 22.0f
        val btnW = (leftW - 16.0f) / 2.0f
        val connAllHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), startX + 18.0f, bulkY, btnW, 16.0f)
        drawRect(poseStack, startX + 18.0f, bulkY, btnW, 16.0f, 2.0f, if (connAllHover) Color(35, 120, 60) else Color(25, 90, 45))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Подкл. все", startX + 23.0f, bulkY + 4.0f, Color.WHITE)

        val discAllHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), startX + 20.0f + btnW, bulkY, btnW, 16.0f)
        drawRect(poseStack, startX + 20.0f + btnW, bulkY, btnW, 16.0f, 2.0f, if (discAllHover) Color(140, 35, 35) else Color(100, 25, 25))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Откл. все", startX + 26.0f + btnW, bulkY + 4.0f, Color.WHITE)

        // Bot items list
        val listY = bulkY + 20.0f
        val listH = leftH - 52.0f
        val filteredBots = BotManager.bots.filter {
            botSearchQuery.isEmpty() || it.profile.username.contains(botSearchQuery, ignoreCase = true)
        }

        val totalHeight = filteredBots.size * 30.0f
        val maxScroll = maxOf(0f, totalHeight - listH + 4.0f)
        botListScroll = botListScroll.coerceIn(0f, maxScroll)

        if (filteredBots.isEmpty()) {
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                if (BotManager.bots.isEmpty()) "Ботов нет" else "Ничего не найдено",
                startX + 40.0f,
                listY + 20.0f,
                Color.GRAY
            )
        } else {
            GuiDrawUtils.flush(true)
            graphics.enableScissor(
                (startX + 12.0f).toInt(),
                listY.toInt(),
                (startX + 12.0f + leftW).toInt(),
                (listY + listH).toInt()
            )

            var itemY = listY + 2.0f - botListScroll
            for (bot in filteredBots) {
                if (itemY + 28.0f >= listY && itemY <= listY + listH) {
                    val isSelected = selectedBot == bot
                    val itemHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), startX + 16.0f, itemY, leftW - 8.0f, 28.0f)

                    val bgCol = if (isSelected) Color(40, 50, 75, 240) else if (itemHover) Color(24, 24, 32) else Color(18, 18, 24)
                    drawRect(poseStack, startX + 16.0f, itemY, leftW - 8.0f, 28.0f, 3.0f, bgCol)
                    if (isSelected) {
                        drawOutline(poseStack, startX + 16.0f, itemY, leftW - 8.0f, 28.0f, 3.0f, Color(70, 120, 220))
                    }

                    // Status dot
                    val dotColor = when (bot.connectionState) {
                        BotConnectionState.IN_GAME -> Color.GREEN
                        BotConnectionState.CONNECTING, BotConnectionState.LOGGING_IN, BotConnectionState.CONFIGURING -> Color.YELLOW
                        BotConnectionState.CONTROLLED -> Color.CYAN
                        else -> Color.RED
                    }
                    drawRect(poseStack, startX + 22.0f, itemY + 11.0f, 6.0f, 6.0f, 3.0f, dotColor)

                    // Nickname & server
                    FontRenderer.INSTANCE.drawWithShadow(poseStack, bot.profile.username, startX + 32.0f, itemY + 5.0f, Color.WHITE)
                    FontRenderer.INSTANCE.drawWithShadow(poseStack, bot.profile.serverAddress, startX + 32.0f, itemY + 16.0f, Color.GRAY)
                }
                itemY += 30.0f
            }

            GuiDrawUtils.flush(true)
            graphics.disableScissor()

            // Scrollbar indicator
            if (totalHeight > listH) {
                val scrollbarH = (listH * (listH / totalHeight)).coerceIn(12f, listH)
                val scrollbarY = listY + (botListScroll / (totalHeight - listH)) * (listH - scrollbarH)
                drawRect(poseStack, startX + 12.0f + leftW - 4.0f, scrollbarY, 2.0f, scrollbarH, 1.0f, Color(80, 80, 110, 180))
            }
        }

        // --- RIGHT PANE: Selected Bot Details ---
        val rightX = startX + 200.0f
        val rightW = panelWidth - 212.0f
        val rightH = leftH

        drawRect(poseStack, rightX, contentY, rightW, rightH, 4.0f, Color(14, 14, 18, 220))
        drawOutline(poseStack, rightX, contentY, rightW, rightH, 4.0f, Color(32, 32, 40, 255))

        val currentBot = selectedBot
        if (currentBot == null) {
            val emptyNotice = "Выбери бота в списке слева"
            val noticeW = FontRenderer.INSTANCE.getWidth(emptyNotice)
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                emptyNotice,
                rightX + (rightW - noticeW) / 2.0f,
                contentY + rightH / 2.0f - 6.0f,
                Color.GRAY
            )
            return
        }

        var curY = contentY + 8.0f

        // 1. Header (Name, Address, Badges)
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§f${currentBot.profile.username}", rightX + 12.0f, curY, Color.WHITE)
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§7${currentBot.profile.serverAddress}", rightX + 12.0f + FontRenderer.INSTANCE.getWidth(currentBot.profile.username) + 10.0f, curY, Color.GRAY)

        val pingBadge = if (currentBot.isOnline()) "§a${currentBot.ping}ms" else "§7—"
        val uptimeBadge = "§e${currentBot.uptimeFormatted}"
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Пинг: $pingBadge  Аптайм: $uptimeBadge", rightX + rightW - 150.0f, curY, Color.LIGHT_GRAY)
        curY += 16.0f

        // 2. Status Card
        drawRect(poseStack, rightX + 10.0f, curY, rightW - 20.0f, 44.0f, 3.0f, Color(20, 20, 26))
        drawOutline(poseStack, rightX + 10.0f, curY, rightW - 20.0f, 44.0f, 3.0f, Color(35, 35, 45))

        val statusText = when (currentBot.connectionState) {
            BotConnectionState.IN_GAME -> "§aВ игре"
            BotConnectionState.CONNECTING, BotConnectionState.LOGGING_IN -> "§eПодключение..."
            BotConnectionState.CONFIGURING -> "§eКонфигурация..."
            BotConnectionState.CONTROLLED -> "§bПерехвачен сессией"
            BotConnectionState.FAILED -> "§cОшибка"
            else -> "§7Отключен"
        }
        val reason = if (currentBot.disconnectReason.isNotEmpty()) " (${currentBot.disconnectReason})" else ""
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Состояние: $statusText$reason", rightX + 16.0f, curY + 6.0f, Color.WHITE)

        val proxyStr = if (currentBot.profile.proxyId.isEmpty()) "—" else currentBot.profile.proxyId
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Прокси: §7$proxyStr", rightX + 16.0f, curY + 18.0f, Color.WHITE)

        // Health bar
        val hp = currentBot.health.coerceIn(0.0f, 20.0f)
        val hpPercent = hp / 20.0f
        val hpBarW = 100.0f
        val hpBarX = rightX + rightW - 120.0f
        val hpBarY = curY + 18.0f
        drawRect(poseStack, hpBarX, hpBarY, hpBarW, 8.0f, 2.0f, Color(60, 20, 20))
        drawRect(poseStack, hpBarX, hpBarY, hpBarW * hpPercent, 8.0f, 2.0f, Color(40, 180, 60))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "HP: §a${hp.toInt()}/20", hpBarX - 44.0f, hpBarY, Color.WHITE)

        curY += 50.0f

        // 3. Inventory Card
        inventorySnapshot.update(currentBot)
        val invH = 92.0f
        drawRect(poseStack, rightX + 10.0f, curY, rightW - 20.0f, invH, 3.0f, Color(20, 20, 26))
        drawOutline(poseStack, rightX + 10.0f, curY, rightW - 20.0f, invH, 3.0f, Color(35, 35, 45))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Инвентарь", rightX + 16.0f, curY + 5.0f, Color.WHITE)

        val slotSize = 16.0f
        val slotSpacing = 2.0f
        val invStartX = rightX + (rightW - (9 * (slotSize + slotSpacing))) / 2.0f
        var slotY = curY + 16.0f

        var hoveredStack: ItemStack? = null

        // Rows 0..2: Main inventory (slots 9..35)
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slot = 9 + row * 9 + col
                val slotX = invStartX + col * (slotSize + slotSpacing)
                val isSlotHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), slotX, slotY, slotSize, slotSize)

                drawRect(poseStack, slotX, slotY, slotSize, slotSize, 1.0f, if (isSlotHover) Color(45, 45, 55) else Color(12, 12, 16))
                drawOutline(poseStack, slotX, slotY, slotSize, slotSize, 1.0f, Color(35, 35, 42))

                val item = inventorySnapshot.getItem(slot)
                if (!item.isEmpty) {
                    GuiDrawUtils.drawItem(poseStack, item, slotX, slotY, 0.0f)
                    if (isSlotHover) hoveredStack = item
                }
            }
            slotY += slotSize + slotSpacing
        }

        // Row 3: Hotbar (slots 0..8)
        slotY += 2.0f
        val selectedSlot = currentBot.playerState.selectedSlot.coerceIn(0, 8)
        for (col in 0 until 9) {
            val slotX = invStartX + col * (slotSize + slotSpacing)
            val isHeld = col == selectedSlot
            val isSlotHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), slotX, slotY, slotSize, slotSize)

            drawRect(poseStack, slotX, slotY, slotSize, slotSize, 1.0f, if (isSlotHover) Color(45, 45, 55) else Color(12, 12, 16))
            drawOutline(poseStack, slotX, slotY, slotSize, slotSize, 1.0f, if (isHeld) Color(80, 140, 240) else Color(35, 35, 42))

            val item = inventorySnapshot.getItem(col)
            if (!item.isEmpty) {
                GuiDrawUtils.drawItem(poseStack, item, slotX, slotY, 0.0f)
                if (isSlotHover) hoveredStack = item
            }
        }

        curY += invH + 8.0f

        // 4. Action Buttons
        val actBtnH = 20.0f
        val isOnline = currentBot.isOnline()
        val connText = if (isOnline) "Отключить" else "Подключить"
        val connHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), rightX + 10.0f, curY, 74.0f, actBtnH)
        drawRect(
            poseStack, rightX + 10.0f, curY, 74.0f, actBtnH, 2.0f,
            if (isOnline) (if (connHover) Color(140, 35, 35) else Color(100, 25, 25))
            else (if (connHover) Color(35, 120, 60) else Color(25, 90, 45))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, connText, rightX + 16.0f, curY + 5.0f, Color.WHITE)

        val isFollowing = BotFollowManager.isFollowing
        val followHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), rightX + 90.0f, curY, 68.0f, actBtnH)
        drawRect(
            poseStack, rightX + 90.0f, curY, 68.0f, actBtnH, 2.0f,
            if (isFollowing) (if (followHover) Color(140, 90, 20) else Color(110, 70, 15))
            else (if (followHover) Color(35, 45, 65) else Color(25, 35, 50))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, if (isFollowing) "За мной: ДА" else "За мной: НЕТ", rightX + 96.0f, curY + 5.0f, Color.WHITE)

        val isControlled = BotSessionManager.isBotControlled(currentBot)
        val sessHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), rightX + 164.0f, curY, 82.0f, actBtnH)
        drawRect(
            poseStack, rightX + 164.0f, curY, 82.0f, actBtnH, 2.0f,
            if (isControlled) (if (sessHover) Color(180, 120, 20) else Color(140, 90, 15))
            else (if (sessHover) Color(40, 80, 150) else Color(30, 60, 110))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, if (isControlled) "Вернуться" else "Сессия: Зайти", rightX + 170.0f, curY + 5.0f, Color.WHITE)

        val delHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), rightX + rightW - 68.0f, curY, 58.0f, actBtnH)
        drawRect(poseStack, rightX + rightW - 68.0f, curY, 58.0f, actBtnH, 2.0f, if (delHover) Color(160, 30, 30) else Color(110, 20, 20))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Удалить", rightX + rightW - 60.0f, curY + 5.0f, Color.WHITE)

        curY += actBtnH + 10.0f

        // 5. Chat Card
        val chatBoxW = rightW - 130.0f
        drawRect(poseStack, rightX + 10.0f, curY, chatBoxW, 20.0f, 2.0f, if (isChatFocused) Color(30, 30, 40) else Color(20, 20, 26))
        drawOutline(poseStack, rightX + 10.0f, curY, chatBoxW, 20.0f, 2.0f, Color(35, 35, 45))
        val chatDisplay = if (chatMessage.isEmpty() && !isChatFocused) "§8Сообщение в чат..." else chatMessage
        FontRenderer.INSTANCE.drawWithShadow(poseStack, chatDisplay, rightX + 16.0f, curY + 5.0f, Color.LIGHT_GRAY)

        val allToggleHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), rightX + chatBoxW + 16.0f, curY, 44.0f, 20.0f)
        drawRect(
            poseStack, rightX + chatBoxW + 16.0f, curY, 44.0f, 20.0f, 2.0f,
            if (chatFromAll) (if (allToggleHover) Color(40, 140, 80) else Color(30, 110, 60))
            else (if (allToggleHover) Color(45, 45, 55) else Color(30, 30, 38))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, if (chatFromAll) "§fВсе" else "§7Один", rightX + chatBoxW + 24.0f, curY + 5.0f, Color.WHITE)

        val sendHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), rightX + rightW - 60.0f, curY, 50.0f, 20.0f)
        drawRect(poseStack, rightX + rightW - 60.0f, curY, 50.0f, 20.0f, 2.0f, if (sendHover) Color(50, 100, 200) else Color(35, 75, 160))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Послать", rightX + rightW - 54.0f, curY + 5.0f, Color.WHITE)

        // Draw tooltip if hovered on inventory item
        if (hoveredStack != null) {
            val tipText = hoveredStack.hoverName.string
            val tipW = FontRenderer.INSTANCE.getWidth(tipText) + 8.0f
            val tipX = mouseX.toFloat() + 8.0f
            val tipY = mouseY.toFloat() - 14.0f
            drawRect(poseStack, tipX, tipY, tipW, 14.0f, 2.0f, Color(15, 15, 20, 230))
            drawOutline(poseStack, tipX, tipY, tipW, 14.0f, 2.0f, Color(60, 60, 80))
            FontRenderer.INSTANCE.drawWithShadow(poseStack, tipText, tipX + 4.0f, tipY + 3.0f, Color.WHITE)
        }
    }

    private fun renderCreateTab(poseStack: PoseStack, startX: Float, startY: Float, panelWidth: Float, panelHeight: Float, mouseX: Int, mouseY: Int) {
        val formX = startX + 60.0f
        var curY = startY + 50.0f
        val inputW = 280.0f

        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§bСоздание нового бота", formX, curY, Color.WHITE)
        curY += 26.0f

        // 1. Nickname
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Никнейм бота:", formX, curY, Color.LIGHT_GRAY)
        curY += 12.0f
        val nameFocus = createActiveField == 1
        drawRect(poseStack, formX, curY, inputW, 20.0f, 2.0f, if (nameFocus) Color(35, 35, 45) else Color(22, 22, 28))
        drawOutline(poseStack, formX, curY, inputW, 20.0f, 2.0f, if (nameFocus) Color(70, 120, 220) else Color(40, 40, 50))
        val nameDisp = if (createName.isEmpty() && !nameFocus) "§8Пример: BotWorker" else createName
        FontRenderer.INSTANCE.drawWithShadow(poseStack, nameDisp, formX + 6.0f, curY + 5.0f, Color.WHITE)
        curY += 28.0f

        // 2. Server address
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Адрес сервера (IP:Port):", formX, curY, Color.LIGHT_GRAY)
        curY += 12.0f
        val addrFocus = createActiveField == 2
        drawRect(poseStack, formX, curY, inputW, 20.0f, 2.0f, if (addrFocus) Color(35, 35, 45) else Color(22, 22, 28))
        drawOutline(poseStack, formX, curY, inputW, 20.0f, 2.0f, if (addrFocus) Color(70, 120, 220) else Color(40, 40, 50))
        val addrDisp = if (createAddress.isEmpty() && !addrFocus) "§8Пример: play.server.com:25565" else createAddress
        FontRenderer.INSTANCE.drawWithShadow(poseStack, addrDisp, formX + 6.0f, curY + 5.0f, Color.WHITE)
        curY += 28.0f

        // 3. Count
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Количество ботов:", formX, curY, Color.LIGHT_GRAY)
        curY += 12.0f
        val countFocus = createActiveField == 3
        drawRect(poseStack, formX, curY, 80.0f, 20.0f, 2.0f, if (countFocus) Color(35, 35, 45) else Color(22, 22, 28))
        drawOutline(poseStack, formX, curY, 80.0f, 20.0f, 2.0f, if (countFocus) Color(70, 120, 220) else Color(40, 40, 50))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, createCount, formX + 6.0f, curY + 5.0f, Color.WHITE)
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§8(Если >1, к нику добавится 1, 2, 3...)", formX + 90.0f, curY + 5.0f, Color.GRAY)
        curY += 28.0f

        // 4. Proxy selection
        val proxyTitle = if (createProxyIndex in ProxyManager.proxies.indices) {
            val p = ProxyManager.proxies[createProxyIndex]
            "${p.name} (${p.host}:${p.port})"
        } else {
            "Без прокси"
        }
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Прокси: §b$proxyTitle", formX, curY + 5.0f, Color.LIGHT_GRAY)
        val cycleHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 220.0f, curY, 60.0f, 18.0f)
        drawRect(poseStack, formX + 220.0f, curY, 60.0f, 18.0f, 2.0f, if (cycleHover) Color(45, 55, 75) else Color(30, 38, 52))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Сменить", formX + 228.0f, curY + 4.0f, Color.WHITE)
        curY += 36.0f

        // 5. Buttons: Create, Back
        val createHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX, curY, 130.0f, 24.0f)
        drawRect(poseStack, formX, curY, 130.0f, 24.0f, 3.0f, if (createHover) Color(40, 140, 75) else Color(30, 110, 55))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§fСоздать бота", formX + 26.0f, curY + 7.0f, Color.WHITE)

        val backHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 150.0f, curY, 80.0f, 24.0f)
        drawRect(poseStack, formX + 150.0f, curY, 80.0f, 24.0f, 3.0f, if (backHover) Color(45, 45, 55) else Color(30, 30, 38))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Назад", formX + 172.0f, curY + 7.0f, Color.LIGHT_GRAY)
    }

    private fun renderSettingsTab(poseStack: PoseStack, startX: Float, startY: Float, panelWidth: Float, panelHeight: Float, mouseX: Int, mouseY: Int) {
        val formX = startX + 40.0f
        var curY = startY + 44.0f

        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§bПараметры ботов", formX, curY, Color.WHITE)
        curY += 20.0f

        // 1. Connect Delay
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Задержка очереди входа: §e${BotManager.connectDelayMs} ms", formX, curY + 4.0f, Color.LIGHT_GRAY)
        val minusHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 230.0f, curY, 36.0f, 18.0f)
        drawRect(poseStack, formX + 230.0f, curY, 36.0f, 18.0f, 2.0f, if (minusHover) Color(45, 45, 55) else Color(30, 30, 38))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "-100", formX + 236.0f, curY + 4.0f, Color.WHITE)

        val plusHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 272.0f, curY, 36.0f, 18.0f)
        drawRect(poseStack, formX + 272.0f, curY, 36.0f, 18.0f, 2.0f, if (plusHover) Color(45, 45, 55) else Color(30, 30, 38))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "+100", formX + 278.0f, curY + 4.0f, Color.WHITE)
        curY += 26.0f

        // 2. Auto Reconnect
        val autoRec = BotManager.autoReconnect
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Автопереподключение:", formX, curY + 4.0f, Color.LIGHT_GRAY)
        val recHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 230.0f, curY, 78.0f, 18.0f)
        drawRect(
            poseStack, formX + 230.0f, curY, 78.0f, 18.0f, 2.0f,
            if (autoRec) (if (recHover) Color(40, 140, 75) else Color(30, 110, 55))
            else (if (recHover) Color(140, 35, 35) else Color(100, 25, 25))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, if (autoRec) "ВКЛЮЧЕНО" else "ВЫКЛЮЧЕНО", formX + 236.0f, curY + 4.0f, Color.WHITE)
        curY += 26.0f

        // 3. Chunk loading
        val chunkLd = BotManager.chunkLoading
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Прогрузка чанков:", formX, curY + 4.0f, Color.LIGHT_GRAY)
        val chunkHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 230.0f, curY, 78.0f, 18.0f)
        drawRect(
            poseStack, formX + 230.0f, curY, 78.0f, 18.0f, 2.0f,
            if (chunkLd) (if (chunkHover) Color(40, 140, 75) else Color(30, 110, 55))
            else (if (chunkHover) Color(140, 35, 35) else Color(100, 25, 25))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, if (chunkLd) "ВКЛЮЧЕНО" else "ВЫКЛЮЧЕНО", formX + 236.0f, curY + 4.0f, Color.WHITE)
        curY += 26.0f

        // 4. Auto respawn
        val respawn = BotManager.autoRespawn
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "Авто-респавн при смерти:", formX, curY + 4.0f, Color.LIGHT_GRAY)
        val respHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 230.0f, curY, 78.0f, 18.0f)
        drawRect(
            poseStack, formX + 230.0f, curY, 78.0f, 18.0f, 2.0f,
            if (respawn) (if (respHover) Color(40, 140, 75) else Color(30, 110, 55))
            else (if (respHover) Color(140, 35, 35) else Color(100, 25, 25))
        )
        FontRenderer.INSTANCE.drawWithShadow(poseStack, if (respawn) "ВКЛЮЧЕНО" else "ВЫКЛЮЧЕНО", formX + 236.0f, curY + 4.0f, Color.WHITE)
        curY += 32.0f

        // Divider
        drawRect(poseStack, formX, curY, panelWidth - 80.0f, 1.0f, 0.0f, Color(40, 40, 50))
        curY += 10.0f

        // 5. Proxy list & addition
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "§bПрокси менеджер §7(Всего: ${ProxyManager.proxies.size})", formX, curY, Color.WHITE)
        curY += 16.0f

        val prxNameDisp = if (proxyName.isEmpty() && proxyActiveField != 1) "Имя" else proxyName
        val prxHostDisp = if (proxyHostPort.isEmpty() && proxyActiveField != 2) "IP:Port" else proxyHostPort
        val prxUserDisp = if (proxyUser.isEmpty() && proxyActiveField != 3) "Логин" else proxyUser
        val prxPassDisp = if (proxyPass.isEmpty() && proxyActiveField != 4) "Пароль" else proxyPass

        drawRect(poseStack, formX, curY, 70.0f, 18.0f, 2.0f, if (proxyActiveField == 1) Color(35, 35, 45) else Color(22, 22, 28))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, prxNameDisp, formX + 4.0f, curY + 4.0f, Color.WHITE)

        drawRect(poseStack, formX + 76.0f, curY, 110.0f, 18.0f, 2.0f, if (proxyActiveField == 2) Color(35, 35, 45) else Color(22, 22, 28))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, prxHostDisp, formX + 80.0f, curY + 4.0f, Color.WHITE)

        drawRect(poseStack, formX + 192.0f, curY, 64.0f, 18.0f, 2.0f, if (proxyActiveField == 3) Color(35, 35, 45) else Color(22, 22, 28))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, prxUserDisp, formX + 196.0f, curY + 4.0f, Color.WHITE)

        drawRect(poseStack, formX + 262.0f, curY, 64.0f, 18.0f, 2.0f, if (proxyActiveField == 4) Color(35, 35, 45) else Color(22, 22, 28))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, prxPassDisp, formX + 266.0f, curY + 4.0f, Color.WHITE)

        val prxAddHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 334.0f, curY, 80.0f, 18.0f)
        drawRect(poseStack, formX + 334.0f, curY, 80.0f, 18.0f, 2.0f, if (prxAddHover) Color(40, 140, 75) else Color(30, 110, 55))
        FontRenderer.INSTANCE.drawWithShadow(poseStack, "+ Добавить", formX + 342.0f, curY + 4.0f, Color.WHITE)
        curY += 24.0f

        // Display up to 3 existing proxies
        for ((idx, prx) in ProxyManager.proxies.take(3).withIndex()) {
            val rowY = curY + idx * 18.0f
            FontRenderer.INSTANCE.drawWithShadow(poseStack, "• §f${prx.name} §7(${prx.host}:${prx.port}) [${prx.type.name}]", formX + 4.0f, rowY + 3.0f, Color.LIGHT_GRAY)
            val delHover = isMouseOver(mouseX.toFloat(), mouseY.toFloat(), formX + 280.0f, rowY, 18.0f, 16.0f)
            FontRenderer.INSTANCE.drawWithShadow(poseStack, "§c×", formX + 284.0f, rowY + 3.0f, if (delHover) Color.RED else Color.GRAY)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val window = ClientAccess.collectiveMinecraft.window
        val mouseX = event.x.toFloat()
        val mouseY = event.y.toFloat()
        val screenWidth = window.guiScaledWidth.toFloat()
        val screenHeight = window.guiScaledHeight.toFloat()
        val panelWidth = 550.0f
        val panelHeight = 350.0f
        val startX = (screenWidth - panelWidth) / 2.0f
        val startY = (screenHeight - panelHeight) / 2.0f

        // Close button (X)
        if (isMouseOver(mouseX, mouseY, startX + panelWidth - 26.0f, startY + 8.0f, 18.0f, 18.0f)) {
            onClose()
            return true
        }

        // Top tab switcher
        var tabX = startX + 130.0f
        for (tab in Tab.values()) {
            val tabW = FontRenderer.INSTANCE.getWidth(tab.title) + 16.0f
            if (isMouseOver(mouseX, mouseY, tabX, startY + 8.0f, tabW, 20.0f)) {
                currentTab = tab
                return true
            }
            tabX += tabW + 6.0f
        }

        // Dispatch by tab
        when (currentTab) {
            Tab.BOTS -> handleBotsTabClick(startX, startY, panelWidth, panelHeight, mouseX, mouseY)
            Tab.CREATE -> handleCreateTabClick(startX, startY, mouseX, mouseY)
            Tab.SETTINGS -> handleSettingsTabClick(startX, startY, panelWidth, mouseX, mouseY)
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun handleBotsTabClick(startX: Float, startY: Float, panelWidth: Float, panelHeight: Float, mouseX: Float, mouseY: Float) {
        val contentY = startY + 34.0f
        val leftW = 180.0f
        val searchBoxY = contentY + 6.0f

        // Search focus
        if (isMouseOver(mouseX, mouseY, startX + 18.0f, searchBoxY, leftW - 12.0f, 18.0f)) {
            isSearchFocused = true
            isChatFocused = false
            return
        } else {
            isSearchFocused = false
        }

        // Bulk actions
        val bulkY = searchBoxY + 22.0f
        val btnW = (leftW - 16.0f) / 2.0f
        if (isMouseOver(mouseX, mouseY, startX + 18.0f, bulkY, btnW, 16.0f)) {
            BotManager.connectAll()
            return
        }
        if (isMouseOver(mouseX, mouseY, startX + 20.0f + btnW, bulkY, btnW, 16.0f)) {
            BotManager.disconnectAll()
            return
        }

        // Bot items list
        val listY = bulkY + 20.0f
        val listH = panelHeight - 44.0f - 52.0f
        val filteredBots = BotManager.bots.filter {
            botSearchQuery.isEmpty() || it.profile.username.contains(botSearchQuery, ignoreCase = true)
        }
        var itemY = listY + 2.0f - botListScroll
        for (bot in filteredBots) {
            if (mouseY >= listY && mouseY <= listY + listH && isMouseOver(mouseX, mouseY, startX + 16.0f, itemY, leftW - 8.0f, 28.0f)) {
                selectedBot = bot
                return
            }
            itemY += 30.0f
        }

        // Right pane actions
        val rightX = startX + 200.0f
        val rightW = panelWidth - 212.0f
        val bot = selectedBot ?: return

        // Action Buttons row (curY ~ contentY + 8f + 16f + 50f + 92f + 8f = contentY + 174f)
        val actY = contentY + 174.0f
        val actBtnH = 20.0f

        // Connect/Disconnect
        if (isMouseOver(mouseX, mouseY, rightX + 10.0f, actY, 74.0f, actBtnH)) {
            if (bot.isOnline()) {
                bot.disconnect("Disconnected from GUI")
            } else {
                BotManager.enqueueConnect(bot)
            }
            return
        }

        // Follow ("За мной")
        if (isMouseOver(mouseX, mouseY, rightX + 90.0f, actY, 68.0f, actBtnH)) {
            if (BotFollowManager.isFollowing) {
                BotFollowManager.stopFollowing()
            } else {
                val player = ClientAccess.collectiveMinecraft.player
                if (player != null) {
                    BotFollowManager.startFollowing(player.gameProfile.name)
                }
            }
            return
        }

        // Session transfer ("Сессия: Зайти / Вернуться")
        if (isMouseOver(mouseX, mouseY, rightX + 164.0f, actY, 82.0f, actBtnH)) {
            if (BotSessionManager.isBotControlled(bot)) {
                BotSessionTransfer.transferBackToBot(bot)
            } else {
                BotSessionTransfer.transferToClient(bot)
            }
            return
        }

        // Delete
        if (isMouseOver(mouseX, mouseY, rightX + rightW - 68.0f, actY, 58.0f, actBtnH)) {
            BotManager.removeBot(bot)
            selectedBot = BotManager.bots.firstOrNull()
            return
        }

        // Chat row
        val chatY = actY + actBtnH + 10.0f
        val chatBoxW = rightW - 130.0f

        // Chat input field
        if (isMouseOver(mouseX, mouseY, rightX + 10.0f, chatY, chatBoxW, 20.0f)) {
            isChatFocused = true
            isSearchFocused = false
            return
        } else {
            isChatFocused = false
        }

        // Chat "Все" toggle
        if (isMouseOver(mouseX, mouseY, rightX + chatBoxW + 16.0f, chatY, 44.0f, 20.0f)) {
            chatFromAll = !chatFromAll
            return
        }

        // Chat Send
        if (isMouseOver(mouseX, mouseY, rightX + rightW - 60.0f, chatY, 50.0f, 20.0f)) {
            sendChatAction(bot)
            return
        }
    }

    private fun sendChatAction(bot: BotClient) {
        if (chatMessage.isBlank()) return
        if (chatFromAll) {
            BotManager.sendChatToAll(chatMessage)
        } else {
            bot.sendChat(chatMessage)
        }
        chatMessage = ""
    }

    private fun handleCreateTabClick(startX: Float, startY: Float, mouseX: Float, mouseY: Float) {
        val formX = startX + 60.0f
        val curY1 = startY + 50.0f + 26.0f + 12.0f
        val curY2 = curY1 + 28.0f + 12.0f
        val curY3 = curY2 + 28.0f + 12.0f
        val curY4 = curY3 + 28.0f
        val curY5 = curY4 + 36.0f

        // Name focus
        if (isMouseOver(mouseX, mouseY, formX, curY1, 280.0f, 20.0f)) {
            createActiveField = 1
            return
        }
        // Address focus
        if (isMouseOver(mouseX, mouseY, formX, curY2, 280.0f, 20.0f)) {
            createActiveField = 2
            return
        }
        // Count focus
        if (isMouseOver(mouseX, mouseY, formX, curY3, 80.0f, 20.0f)) {
            createActiveField = 3
            return
        }

        // Cycle Proxy button
        if (isMouseOver(mouseX, mouseY, formX + 220.0f, curY4, 60.0f, 18.0f)) {
            if (ProxyManager.proxies.isEmpty()) {
                createProxyIndex = -1
            } else {
                createProxyIndex = if (createProxyIndex >= ProxyManager.proxies.size - 1) -1 else createProxyIndex + 1
            }
            return
        }

        // Create Bot button
        if (isMouseOver(mouseX, mouseY, formX, curY5, 130.0f, 24.0f)) {
            val count = createCount.toIntOrNull()?.coerceIn(1, 100) ?: 1
            val proxyId = if (createProxyIndex in ProxyManager.proxies.indices) ProxyManager.proxies[createProxyIndex].id else ""
            if (createName.isNotEmpty() && createAddress.isNotEmpty()) {
                val created = BotManager.addBots(createName, count, createAddress, proxyId)
                if (created.isNotEmpty()) {
                    selectedBot = created.first()
                }
                createName = ""
                currentTab = Tab.BOTS
            }
            return
        }

        // Back button
        if (isMouseOver(mouseX, mouseY, formX + 150.0f, curY5, 80.0f, 24.0f)) {
            currentTab = Tab.BOTS
            return
        }
    }

    private fun handleSettingsTabClick(startX: Float, startY: Float, panelWidth: Float, mouseX: Float, mouseY: Float) {
        val formX = startX + 40.0f
        val yDelay = startY + 64.0f
        val yRec = yDelay + 26.0f
        val yChunk = yRec + 26.0f
        val yResp = yChunk + 26.0f

        // -100ms
        if (isMouseOver(mouseX, mouseY, formX + 230.0f, yDelay, 36.0f, 18.0f)) {
            BotManager.connectDelayMs = (BotManager.connectDelayMs - 100).coerceAtLeast(50L)
            return
        }
        // +100ms
        if (isMouseOver(mouseX, mouseY, formX + 272.0f, yDelay, 36.0f, 18.0f)) {
            BotManager.connectDelayMs = (BotManager.connectDelayMs + 100).coerceAtMost(5000L)
            return
        }

        // AutoReconnect
        if (isMouseOver(mouseX, mouseY, formX + 230.0f, yRec, 78.0f, 18.0f)) {
            BotManager.autoReconnect = !BotManager.autoReconnect
            return
        }

        // ChunkLoading
        if (isMouseOver(mouseX, mouseY, formX + 230.0f, yChunk, 78.0f, 18.0f)) {
            BotManager.chunkLoading = !BotManager.chunkLoading
            return
        }

        // AutoRespawn
        if (isMouseOver(mouseX, mouseY, formX + 230.0f, yResp, 78.0f, 18.0f)) {
            BotManager.autoRespawn = !BotManager.autoRespawn
            return
        }

        // Proxy Manager form
        val prxFormY = yResp + 32.0f + 10.0f + 16.0f
        if (isMouseOver(mouseX, mouseY, formX, prxFormY, 70.0f, 18.0f)) {
            proxyActiveField = 1
            return
        }
        if (isMouseOver(mouseX, mouseY, formX + 76.0f, prxFormY, 110.0f, 18.0f)) {
            proxyActiveField = 2
            return
        }
        if (isMouseOver(mouseX, mouseY, formX + 192.0f, prxFormY, 64.0f, 18.0f)) {
            proxyActiveField = 3
            return
        }
        if (isMouseOver(mouseX, mouseY, formX + 262.0f, prxFormY, 64.0f, 18.0f)) {
            proxyActiveField = 4
            return
        }

        // Add Proxy button
        if (isMouseOver(mouseX, mouseY, formX + 334.0f, prxFormY, 80.0f, 18.0f)) {
            val parts = proxyHostPort.split(":")
            val host = parts.getOrNull(0) ?: ""
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 1080
            if (host.isNotEmpty()) {
                val prx = ProxyConfig(
                    name = proxyName.ifEmpty { "Proxy-${ProxyManager.proxies.size + 1}" },
                    type = proxyType,
                    host = host,
                    port = port,
                    username = proxyUser,
                    password = proxyPass
                )
                ProxyManager.addProxy(prx)
                proxyName = ""
                proxyHostPort = ""
                proxyUser = ""
                proxyPass = ""
                proxyActiveField = 0
            }
            return
        }

        // Delete proxy button
        val listY = prxFormY + 24.0f
        for ((idx, prx) in ProxyManager.proxies.take(3).withIndex()) {
            val rowY = listY + idx * 18.0f
            if (isMouseOver(mouseX, mouseY, formX + 280.0f, rowY, 18.0f, 16.0f)) {
                ProxyManager.removeProxy(prx.id)
                return
            }
        }
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val ch = event.codepoint.toChar()

        when (currentTab) {
            Tab.BOTS -> {
                if (isSearchFocused) {
                    botSearchQuery += ch
                    return true
                }
                if (isChatFocused) {
                    chatMessage += ch
                    return true
                }
            }
            Tab.CREATE -> {
                when (createActiveField) {
                    1 -> { createName += ch; return true }
                    2 -> { createAddress += ch; return true }
                    3 -> { if (ch.isDigit()) createCount += ch; return true }
                }
            }
            Tab.SETTINGS -> {
                when (proxyActiveField) {
                    1 -> { proxyName += ch; return true }
                    2 -> { proxyHostPort += ch; return true }
                    3 -> { proxyUser += ch; return true }
                    4 -> { proxyPass += ch; return true }
                }
            }
        }

        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == GLFW.GLFW_KEY_BACKSPACE) {
            when (currentTab) {
                Tab.BOTS -> {
                    if (isSearchFocused && botSearchQuery.isNotEmpty()) {
                        botSearchQuery = botSearchQuery.dropLast(1)
                        return true
                    }
                    if (isChatFocused && chatMessage.isNotEmpty()) {
                        chatMessage = chatMessage.dropLast(1)
                        return true
                    }
                }
                Tab.CREATE -> {
                    when (createActiveField) {
                        1 -> if (createName.isNotEmpty()) { createName = createName.dropLast(1); return true }
                        2 -> if (createAddress.isNotEmpty()) { createAddress = createAddress.dropLast(1); return true }
                        3 -> if (createCount.isNotEmpty()) { createCount = createCount.dropLast(1); return true }
                    }
                }
                Tab.SETTINGS -> {
                    when (proxyActiveField) {
                        1 -> if (proxyName.isNotEmpty()) { proxyName = proxyName.dropLast(1); return true }
                        2 -> if (proxyHostPort.isNotEmpty()) { proxyHostPort = proxyHostPort.dropLast(1); return true }
                        3 -> if (proxyUser.isNotEmpty()) { proxyUser = proxyUser.dropLast(1); return true }
                        4 -> if (proxyPass.isNotEmpty()) { proxyPass = proxyPass.dropLast(1); return true }
                    }
                }
            }
        } else if (event.key == GLFW.GLFW_KEY_ENTER) {
            if (currentTab == Tab.BOTS && isChatFocused && selectedBot != null) {
                sendChatAction(selectedBot!!)
                return true
            }
        } else if (event.key == GLFW.GLFW_KEY_TAB) {
            if (currentTab == Tab.CREATE) {
                createActiveField = if (createActiveField >= 3) 1 else createActiveField + 1
                return true
            } else if (currentTab == Tab.SETTINGS) {
                proxyActiveField = if (proxyActiveField >= 4) 1 else proxyActiveField + 1
                return true
            }
        }

        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (currentTab == Tab.BOTS) {
            val window = ClientAccess.collectiveMinecraft.window
            val screenWidth = window.guiScaledWidth.toFloat()
            val screenHeight = window.guiScaledHeight.toFloat()
            val panelWidth = 550.0f
            val panelHeight = 350.0f
            val startX = (screenWidth - panelWidth) / 2.0f
            val startY = (screenHeight - panelHeight) / 2.0f
            val contentY = startY + 34.0f
            val leftW = 180.0f
            val searchBoxY = contentY + 6.0f
            val bulkY = searchBoxY + 22.0f
            val listY = bulkY + 20.0f
            val leftH = panelHeight - 44.0f
            val listH = leftH - 52.0f

            if (isMouseOver(mouseX.toFloat(), mouseY.toFloat(), startX + 12.0f, listY, leftW, listH)) {
                val filteredBots = BotManager.bots.filter {
                    botSearchQuery.isEmpty() || it.profile.username.contains(botSearchQuery, ignoreCase = true)
                }
                val totalHeight = filteredBots.size * 30.0f
                val maxScroll = maxOf(0f, totalHeight - listH + 4.0f)
                botListScroll = (botListScroll - verticalAmount.toFloat() * 18.0f).coerceIn(0f, maxScroll)
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun isMouseOver(mouseX: Float, mouseY: Float, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }
}
