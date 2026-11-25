package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.StringSetting
import me.collective.client.utils.chat.ChatUtils

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component

/**
 * Общий чат пользователей Collective Client.
 *
 * <p>Сеть — простой TCP-релей: после LOGIN все сообщения пересылаются каждому
 * подключённому пользователю. Канал виден только тем, у кого есть ключ (Token).
 *
 * <p>Соединение живёт и на экране входа — удобно переписываться в очереди.
 */
class IrcModule : Module(
   "IRC",
   "Общий чат пользователей Collective Client. " +
         "Ник задаётся в Nickname (пусто = имя аккаунта).",
   ModuleCategory.CLIENT
) {
   @JvmField
   val nickname: StringSetting = add(StringSetting("Nickname", ""))

   @JvmField
   val autoConnect: BooleanSetting = add(BooleanSetting("AutoConnect", true))

   @JvmField
   val showChat: BooleanSetting = add(BooleanSetting("Chat", true))

   @JvmField
   val showActivity: BooleanSetting = add(BooleanSetting("Joins Quits", true))

   @JvmField
   var server: String = DEFAULT_RELAY_HOST

   @JvmField
   var port: Int = DEFAULT_RELAY_PORT

   @JvmField
   var token: String = DEFAULT_RELAY_TOKEN

   // ── Состояние сети ──
   @Volatile
   private var thread: Thread? = null

   @Volatile
   private var socket: Socket? = null

   @Volatile
   private var writer: OutputStreamWriter? = null

   @Volatile
   private var connected: Boolean = false

   @Volatile
   private var usedName: String = ""

   @Volatile
   private var lastError: String = ""

   /** Запрошено переподключение со свежими настройками (смена server/token/ника). */
   @Volatile
   private var pendingReconnect: Boolean = false

   private val sendLock = Object()
   private val users: MutableSet<String> = ConcurrentHashMap.newKeySet()

   init {
      INSTANCE = this
      nickname.setChangeCallback {
         if (isToggled()) {
            requestReconnect()
         }
      }
   }

   fun setServer(host: String, port: Int) {
      this.server = host
      this.port = port
      requestReconnect()
   }

   fun setToken(token: String) {
      // Токен — одно слово в протоколе (LOGIN <nick> <token>), пробелы ломают разбор.
      this.token = token.trim().replace("\\s".toRegex(), "")
      requestReconnect()
   }

   /**
    * Переподключение с текущими настройками: рвёт сеанс и будит цикл обратно,
    * минуя backoff; если поток уже завершился (AutoConnect выключен) — поднимает новый.
    */
   private fun requestReconnect() {
      pendingReconnect = true
      closeQuietly()
      val current = thread
      if (isToggled() && (current == null || !current.isAlive)) {
         startThread()
      }
   }

   override fun onEnable() {
      connected = false
      users.clear()
      lastError = ""
      usedName = currentName()
      startThread()
   }

   override fun onDisable() {
      connected = false
      users.clear()
      closeQuietly()

      val current = thread
      thread = null
      current?.interrupt()
   }

   private fun startThread() {
      val current = Thread({ run() }, "Collective-IRC")
      current.isDaemon = true
      thread = current
      current.start()
   }

   // ── Цикл соединения ─────────────────────────────────────────────────────

   private fun run() {
      val self = Thread.currentThread()
      var nextAttempt = 0L
      var backoff = 0

      while (isToggled() && thread === self) {
         // Смена настроек — пробуем сразу, без паузы backoff.
         if (pendingReconnect) {
            pendingReconnect = false
            nextAttempt = 0L
            backoff = 0
         }
         if (System.currentTimeMillis() < nextAttempt) {
            sleepSmall()
            continue
         }
         var wasConnected = false
         try {
            wasConnected = session()
         } catch (t: Throwable) {
            if (!isToggled() || thread !== self) break
            lastError = describe(t)
         } finally {
            // Состояние чистит только владелец сессии: иначе старый поток
            // при быстром disable→enable задел бы новое подключение.
            if (thread === self) {
               connected = false
               users.clear()
               closeQuietly()
            }
         }

         if (!isToggled() || thread !== self) break

         // Настройки сменились во время сеанса — новая попытка сразу.
         if (pendingReconnect) continue

         // AutoConnect выключен — за сеанс подключения пробуем один раз.
         if (!autoConnect.value) {
            break
         }
         // Успешная сессия сбрасывает паузу: релей явно жив, обрывы-random.
         backoff = if (wasConnected) 0 else minOf(backoff + 1, 6)
         val delay = minOf(BACKOFF_MAX_MS, BACKOFF_STEP_MS * (1 shl backoff))
         nextAttempt = System.currentTimeMillis() + delay
      }
   }

   /**
    * Одна сессия: коннект + LOGIN + цикл чтения.
    *
    * @return дошло ли до OK-ответа релея (для сброса backoff).
    */
   private fun session(): Boolean {
      if (thread !== Thread.currentThread()) return false
      var sock: Socket? = null
      try {
         val host = if (server != null && !server.trim().isEmpty()) server.trim() else DEFAULT_RELAY_HOST
         val port = if (this.port > 0) this.port else DEFAULT_RELAY_PORT
         val authToken = if (token != null && !token.trim().isEmpty()) token.trim() else DEFAULT_RELAY_TOKEN

         sock = Socket()
         socket = sock
         sock.tcpNoDelay = true
         sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
         sock.soTimeout = LOGIN_TIMEOUT_MS

         val out = OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8)
         writer = out

         val name = currentName()
         val greeting = "LOGIN $name $authToken"
         synchronized(sendLock) {
            out.write(greeting)
            out.write('\n'.code)
            out.flush()
         }

         val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))

         // первый ответ: OK или ERR (пустые строки-пинги пропускаем)
         var reply = readLine(reader)
         while (reply != null && reply.isEmpty()) {
            reply = readLine(reader)
         }
         if (reply == null) {
            lastError = "пустой ответ от релея"
            return false
         }
         if (reply.startsWith("ERR")) {
            lastError = reply.substring(3).trim()
            return false
         }
         if (!reply.startsWith("OK")) {
            lastError = "непонятный ответ: " + trimDisplay(reply, 60)
            return false
         }
         usedName = name

         sock.soTimeout = READ_TIMEOUT_MS
         lastError = ""
         connected = true
         announceConnect()

         var missed = 0
         while (isToggled() && connected && thread === Thread.currentThread()) {
            val line: String?
            try {
               line = readLine(reader)
            } catch (ste: SocketTimeoutException) {
               // Тишина 15 с: отправляем PING; PONG придёт в тот же интервал.
               // Если тишина продолжается N раз — считаем связь мёртвой.
               if (++missed >= MAX_MISSED_PINGS) {
                  lastError = "релей не отвечает на пинги"
                  connected = false
               } else {
                  send("PING")
               }
               continue
            }
            if (line == null) {
               lastError = "релей закрыл связь"
               break
            }
            missed = 0
            if (line.isNotEmpty()) {
               handle(line)
            }
         }
         return true
      } catch (e: SocketTimeoutException) {
         // connect/login-таймаут: понятный статус, цикл выше даст реконнект.
         lastError = "релей не ответил вовремя (таймаут)"
         return false
      } catch (e: IOException) {
         lastError = describe(e)
         return false
      } finally {
         if (sock != null) {
            try {
               sock.close()
            } catch (ignored: IOException) {
            }
         }
      }
   }

   /** Строка с ограничением длины (не доверяем чужим байтам). */
   private fun readLine(reader: BufferedReader): String? {
      val sb = StringBuilder(160)
      var c = reader.read()
      if (c < 0) return null
      while (c >= 0 && c != '\n'.code) {
         if (c != '\r'.code && sb.length < MAX_LINE) {
            sb.append(c.toChar())
         }
         c = reader.read()
      }
      return sb.toString()
   }

   // ── Входящие (поток читателя; UI и мир — только через execute) ──────────

   private fun handle(line: String) {
      if (line.startsWith("MSG ")) {
         val space = line.indexOf(' ', 4)
         if (space < 0 || !showChat.value) return
         val name = line.substring(4, space)
         val text = line.substring(space + 1)
         if (name.isEmpty() || text.isEmpty()) return

         val nameF = trimDisplay(name, 24)
         val textF = trimDisplay(text, MAX_TEXT)
         ClientAccess.collectiveMinecraft.execute {
            ChatUtils.printChat(
               Component.literal(
                  "${ChatFormatting.GRAY}[${ChatFormatting.AQUA}IRC${ChatFormatting.GRAY}] " +
         "${ChatFormatting.AQUA}$nameF${ChatFormatting.WHITE}: $textF"
               ),
               ChatUtils.createSignature(IRC_SIGNATURE_ID)
            )
         }
         return
      }

      if (line.startsWith("JOIN ")) {
         val name = line.substring(5).trim()
         if (name.isEmpty()) return
         users.add(name)
         val nameF = trimDisplay(name, 24)
         if (showActivity.value) {
            ClientAccess.collectiveMinecraft.execute {
               ChatUtils.printChat(
                  Component.literal(
                     "${ChatFormatting.GRAY}[${ChatFormatting.AQUA}IRC${ChatFormatting.GRAY}] " +
         "${ChatFormatting.GRAY}$nameF вошёл в канал"
                  ),
                  ChatUtils.createSignature(IRC_SIGNATURE_ID)
               )
            }
         }
         return
      }

      if (line.startsWith("QUIT ")) {
         val name = line.substring(5).trim()
         users.remove(name)
         val nameF = trimDisplay(name, 24)
         if (showActivity.value) {
            ClientAccess.collectiveMinecraft.execute {
               ChatUtils.printChat(
                  Component.literal(
                     "${ChatFormatting.GRAY}[${ChatFormatting.AQUA}IRC${ChatFormatting.GRAY}] " +
         "${ChatFormatting.GRAY}$nameF покинул канал"
                  ),
                  ChatUtils.createSignature(IRC_SIGNATURE_ID)
               )
            }
         }
         return
      }

      if (line.startsWith("USERS ")) {
         users.clear()
         for (name in line.substring(6).split(",")) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) users.add(trimmed)
         }
      }
   }

   private fun playerUuidByName(name: String): UUID? {
      try {
         val connection: ClientPacketListener? = ClientAccess.collectiveMinecraft.player?.connection
         if (connection == null) return null
         for (info in connection.onlinePlayers) {
            if (info.profile != null && name == info.profile.name) {
               return info.profile.id
            }
         }
      } catch (ignored: Throwable) {
      }
      return null
   }

   // ── Исходящие ───────────────────────────────────────────────────────────

   private fun send(line: String) {
      val out = writer
      if (out == null || !connected) return

      synchronized(sendLock) {
         try {
            out.write(line)
            out.write('\n'.code)
            out.flush()
         } catch (e: IOException) {
            connected = false
            closeQuietly()
         }
      }
   }

   /** Сообщение в общий чат (команда .irc). */
   fun sendChat(raw: String): Boolean {
      val text = raw.trim()
      if (text.isEmpty() || text.length > MAX_TEXT) return false
      if (!connected) return false
      send("SAY $text")
      return true
   }

   /** Статус для команды .irc без аргументов (адрес релея в чат не светим). */
   fun statusText(): String {
      if (connected) {
         return "IRC на связи: ник ${ChatFormatting.AQUA}${trimDisplay(usedName, 16)}" +
            "${ChatFormatting.WHITE}, в канале ${ChatFormatting.AQUA}${users.size}${ChatFormatting.WHITE}."
      }
      val current = thread
      if (current != null && current.isAlive && isToggled()) {
         return "IRC подбирает связь, крайний сбой: ${ChatFormatting.GRAY}" +
            "${hideAddress(trimDisplay(lastError, 90))}${ChatFormatting.WHITE}."
      }
      if (isToggled()) {
         return "IRC разъединён, переподключение не идёт: ${ChatFormatting.GRAY}" +
            "${hideAddress(trimDisplay(lastError, 90))}${ChatFormatting.WHITE}."
      }
      return "IRC выключен."
   }

   // ── Мелочи ──────────────────────────────────────────────────────────────

   private fun announceConnect() {
      ClientAccess.collectiveMinecraft.execute {
         ChatUtils.printChat(
            Component.literal(
               "${ChatFormatting.GRAY}[${ChatFormatting.AQUA}IRC${ChatFormatting.GRAY}] " +
         "${ChatFormatting.GREEN}Связь с релеем установлена, ник: ${ChatFormatting.AQUA}$usedName"
            ),
            ChatUtils.createSignature(IRC_SIGNATURE_ID)
         )
      }
   }

   /** Ник для релея: настройка Nickname, иначе имя аккаунта Minecraft. */
   private fun currentName(): String {
      val custom = nickname.value.trim()
      return sanitizeName(if (custom.isEmpty()) baseName() else custom)
   }

   /** Публичный нормализатор (команда .irc nick): к правилам релея; < 3 *
         валидных символов = "" → смысл "авто, ник аккаунта". */
   fun normalizeNick(raw: String?): String {
      val trimmed = raw?.trim() ?: ""
      val sb = StringBuilder()
      var i = 0
      while (i < trimmed.length && sb.length < 16) {
         val c = trimmed[i]
         val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_'
         if (ok) {
            sb.append(c)
         }
         i++
      }
      return if (sb.length >= 3) sb.toString() else ""
   }

   /** Ник под правила релея [A-Za-z0-9_]{3,16}; кириллица вырезается. */
   private fun hideAddress(text: String): String {
      var result = IPV4_PATTERN.matcher(text).replaceAll("*.*.*.*")
      val hostValue = if (server != null) server.trim() else ""
      if (hostValue.isNotEmpty()) {
         result = result.replace(hostValue, "***")
      }
      if (DEFAULT_RELAY_HOST.isNotEmpty()) {
         result = result.replace(DEFAULT_RELAY_HOST, "***")
      }
      return result
   }

   private fun closeQuietly() {
      val sock = socket
      socket = null
      writer = null
      if (sock != null) {
         try {
            sock.close()
         } catch (ignored: IOException) {
         }
      }
   }

   private fun sleepSmall() {
      try {
         Thread.sleep(200)
      } catch (e: InterruptedException) {
         Thread.currentThread().interrupt()
      }
   }

   override fun getInfo(): String? {
      if (!isToggled()) return null
      return if (connected) ("IRC " + users.size) else "IRC …"
   }

   companion object {
      @JvmField
      var INSTANCE: IrcModule? = null

      const val DEFAULT_RELAY_HOST = "bore.pub"
      const val DEFAULT_RELAY_PORT = 29900
      const val DEFAULT_RELAY_TOKEN = "collective"

      private const val CONNECT_TIMEOUT_MS = 6_000
      private const val LOGIN_TIMEOUT_MS = 8_000
      private const val READ_TIMEOUT_MS = 15_000
      private const val MAX_MISSED_PINGS = 3
      private const val MAX_TEXT = 240
      private const val MAX_LINE = 2040
      private const val BACKOFF_STEP_MS = 5_000L
      private const val BACKOFF_MAX_MS = 30_000L

      const val IRC_SIGNATURE_ID = -772001

      private val IPV4_PATTERN: Pattern = Pattern.compile(
         "\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b"
      )

      private fun trimDisplay(source: String, limit: Int): String {
         return if (source.length <= limit) source else source.substring(0, limit) + "…"
      }

      private fun baseName(): String {
         var name: String? = null
         try {
            if (ClientAccess.collectiveMinecraft.user != null) {
               name = ClientAccess.collectiveMinecraft.user.name
            }
         } catch (ignored: Throwable) {
         }
         if (name == null || name.isBlank()) name = "Player"
         return name
      }

      /** Ник под правила релея [A-Za-z0-9_]{3,16}; кириллица вырезается. */
      private fun sanitizeName(raw: String): String {
         val sb = StringBuilder()
         var i = 0
         while (i < raw.length && sb.length < 16) {
            val c = raw[i]
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_'
            if (ok) {
               sb.append(c)
            }
            i++
         }
         val name = sb.toString()
         return if (name.length >= 3) name else "user$name"
      }

      private fun describe(t: Throwable): String {
         val message = t.message
         return if (message != null && !message.isBlank()) message else t.javaClass.simpleName
      }
   }
}
