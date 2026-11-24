package me.collective.client.modules.hud


import me.collective.client.CollectiveClient
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.ElapsedTimer
import me.collective.client.render.ColorUtils
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import me.collective.client.utils.math.MathUtils

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.awt.Color
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class CryptoModule : HudWidgetModule("Crypto") {
   private val coinSetting: Setting<String>
   private val fiatSetting: Setting<FiatCurrency>
   private val httpClient: HttpClient
   private val updateTimer: ElapsedTimer
   private val priceChangeAnimation: FloatAnimation
   private var priceIncreased: Boolean = false
   private var formattedPrice: String
   private var coinSlug: String?

   private fun refreshPrice() {
      if (!this.isCoinSymbolValid()) {
         this.coinSlug = null
         this.coinSetting.setValue("UNKNOWN")
         this.formattedPrice = "0.00"
         this.priceChangeAnimation.setValue(0.0f)
         return
      }

      val cachedCoinSlug = this.coinSlug
      val coinSymbol = this.coinSetting.getValue().uppercase(Locale.ROOT)
      val fiatCurrency = this.fiatSetting.getValue()
      val fiatSymbol = fiatCurrency.getName().uppercase(Locale.ROOT)

      if (cachedCoinSlug == null) {
         try {
            val uri = URI("https://api.alternative.me/v2/ticker/?convert=%s".format(fiatSymbol))
            val prices = this.requestPrices(uri)
            if (prices == null || prices.isEmpty) {
               return
            }

            for (key in prices.keySet()) {
               if (!prices.get(key)!!.isJsonObject) {
                  continue
               }
               val coin = prices.get(key)!!.getAsJsonObject()
               if (!coin.has("symbol") || !coin.has("website_slug")) {
                  continue
               }
               val responseCoinSymbol = coin.get("symbol")!!.getAsString()
               if (coinSymbol == responseCoinSymbol) {
                  val quotes = coin.getAsJsonObject("quotes")
                  if (!quotes.has(fiatSymbol)) {
                     break
                  }
                  val quote = quotes.getAsJsonObject(fiatSymbol)
                  this.updateQuotedPrice(quote.get("price")!!.getAsFloat())
                  this.coinSlug = coin.get("website_slug")!!.getAsString()
                  return
               }
            }

            if (this.coinSlug == null) {
               this.formattedPrice = "UNKNOWN"
               this.priceChangeAnimation.setValue(0.0f)
               return
            }
         } catch (ignoredException: Exception) {
            return
         }
      }

      try {

         val conversionUrl = "https://api.alternative.me/v2/ticker/%s/?convert=%s"
         val uri = URI(conversionUrl.format(cachedCoinSlug, fiatSymbol))
         val prices = this.requestPrices(uri)
         if (prices == null || prices.isEmpty) {
            return
         }
         for (key in prices.keySet()) {
            if (!prices.get(key)!!.isJsonObject) {
               continue
            }
            val coin = prices.get(key)!!.getAsJsonObject()
            if (!coin.has("quotes")) {
               continue
            }
            val quotes = coin.getAsJsonObject("quotes")
            val quote = quotes.getAsJsonObject(fiatSymbol)
            this.updateQuotedPrice(quote.get("price")!!.getAsFloat())
            break
         }
      } catch (ignoredException: Exception) {
      }
   }

   private fun requestPrices(uri: URI): JsonObject? {
      val request = HttpRequest.newBuilder().GET().uri(uri)
         .header("Accept", "application/json")
         .header("User-Agent", CollectiveClient.USER_AGENT)
         .build()
      val response = this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
         .orTimeout(15L, TimeUnit.SECONDS).join()
      val statusCode = response.statusCode()

      if (statusCode != 200) {

         this.formattedPrice = "UNKNOWN"
         this.priceChangeAnimation.setValue(0.0f)
         return null
      }

      val gson: Gson = ClientAccess.gson
      val responseBody = response.body()
      val responseType = JsonObject::class.java
      val responseJson = gson.fromJson(responseBody, responseType)

      if (!responseJson.has("data") || !responseJson.has("metadata")) {
         this.formattedPrice = "UNKNOWN"
         this.priceChangeAnimation.setValue(0.0f)
         return null
      }
      val metadata = responseJson.getAsJsonObject("metadata")
      if (metadata.has("error") && !metadata.get("error")!!.isJsonNull) {
         this.formattedPrice = "UNKNOWN"
         this.priceChangeAnimation.setValue(0.0f)
         return null
      }
      return responseJson.getAsJsonObject("data")
   }

   private fun updateQuotedPrice(quotedPrice: Float) {
      val priceText = "%.2f".format(quotedPrice)
      val roundedPrice = priceText.toFloat()
      if (!this.formattedPrice.equals("0.00", ignoreCase = true) &&
         !this.formattedPrice.equals("UNKNOWN", ignoreCase = true)) {

         this.updatePriceChangeAnimation(roundedPrice)
      }

      this.formattedPrice = priceText
   }

   private fun isCoinSymbolValid(): Boolean {
      val coinSymbol = this.coinSetting.getValue()
      return !coinSymbol.isEmpty() && coinSymbol.matches("[a-zA-Z]+".toRegex())
   }

   private fun getDisplayText(): Component {
      val text: MutableComponent = Component.empty()
      val coinSymbol = this.coinSetting.getValue()
      val displayCoinSymbol = coinSymbol.uppercase()
      text.append(displayCoinSymbol)
      text.append(" ")
      val priceColor: Int
      if (this.priceIncreased) {
         val gainColor = Color.GREEN
         val neutralColor = Color.WHITE

         val gainBlendedColor = ColorUtils.blend(
            gainColor, neutralColor, this.priceChangeAnimation.getValue()
         )
         priceColor = gainBlendedColor.hashCode()
      } else {
         val lossColor = Color.RED
         val lossNeutralColor = Color.WHITE

         val lossBlendedColor = ColorUtils.blend(
            lossColor, lossNeutralColor, this.priceChangeAnimation.getValue()
         )
         priceColor = lossBlendedColor.hashCode()
      }

      val priceText = this.formattedPrice
      val priceComponent = Component.literal(priceText)
      val coloredPrice = priceComponent.withColor(priceColor)
      text.append(coloredPrice)
      return text
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(ignoredEvent: LocalPlayerTickEvent) {
      val timer = this.updateTimer

      val intervalUnit = TimeUnit.SECONDS
      if (timer.hasElapsed(5.0, intervalUnit)) {
         val executor = ClientAccess.executorService
         val refreshTask = Runnable { this.refreshPrice() }
         executor.submit(refreshTask)
         val resetTimer = this.updateTimer

         resetTimer.reset()
      }
   }

   private fun updatePriceChangeAnimation(price: Float) {
      val previousPriceText = this.formattedPrice
      val parsedPreviousPrice = previousPriceText.toFloat()

      val comparisonPrice = MathUtils.floorToDecimals(parsedPreviousPrice, 2)
      val priceDifference = price - comparisonPrice
      val absoluteDifference = Math.abs(priceDifference).toDouble()

      if (absoluteDifference < 0.01) {
         this.priceChangeAnimation.setValue(0.0f)
         return
      }

      this.priceIncreased = price > comparisonPrice
      if (comparisonPrice != price) {
         val changeProgress = 1.0f

         this.priceChangeAnimation.setValue(changeProgress)
         this.priceChangeAnimation.setTarget(0.0f, 1000L)
      }
   }

   init {
      this.coinSetting = this.add(StringSetting("Coin", "BTC"))

      this.fiatSetting = this.add(EnumSetting("Fiat", CryptoModule.FiatCurrency.USD))
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build()
      this.updateTimer = ElapsedTimer()
      this.priceChangeAnimation = FloatAnimation()

      this.formattedPrice = "0.00"
      this.coinSlug = null
      this.setRenderer(
         HudTextListRenderer(
            this, HudTextRow({ this.getDisplayText() }, { true })
         )
      )
      this.coinSetting.setChangeCallback {
         this.coinSlug = null
         this.priceChangeAnimation.setValue(0.0f)

         this.formattedPrice = "0.00"

         if (!this.isCoinSymbolValid()) {
            this.coinSetting.setValue("UNKNOWN")
         } else {
            // Смена монеты: немедленный рефреш цены (таймер «истёк»).
            // Раньше стояло setTime(5000L), что выглядело как «через 5 сек»,
            // но фактически клало epoch-метку и тоже означало «сразу».
            this.updateTimer.clear()
         }
      }
   }

   private enum class FiatCurrency(private val displayName: String) : NamedEntry {
      USD("USD"),
      EUR("EUR"),
      RUB("RUB"),
      CNY("CNY"),
      TRY("TRY"),
      JPY("JPY"),
      PLN("PLN"),
      CHF("CHF"),
      GBP("GBP"),
      CAD("CAD"),
      KRW("KRW"),
      BRL("BRL");

      override fun getName(): String {
         return this.displayName
      }
   }
}
