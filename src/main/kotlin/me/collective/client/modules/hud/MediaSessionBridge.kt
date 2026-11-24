package me.collective.client.modules.hud

import me.collective.client.media.MediaSnapshot
import me.collective.client.media.PortableMediaSessionProvider

class MediaSessionBridge private constructor() {
   companion object {
      @JvmField
      @Volatile
      var playbackStatus: Int = 0

      @JvmField
      @Volatile
      var artist: String = ""

      @JvmField
      @Volatile
      var title: String = ""

      @JvmField
      @Volatile
      var positionMillis: Long = 0

      @JvmField
      @Volatile
      var durationMillis: Long = 0

      private val MEDIA: PortableMediaSessionProvider =
         PortableMediaSessionProvider { state -> update(state) }

      init {
         MEDIA.start()
         Runtime.getRuntime().addShutdownHook(Thread({ MEDIA.close() }, "collective-media-stop"))
      }

      private fun update(state: MediaSnapshot) {
         artist = state.artist
         title = state.title
         positionMillis = state.positionMillis
         durationMillis = state.durationMillis
         playbackStatus = state.playbackStatus
      }

      @JvmStatic
      fun getDisplayText(): String {
         return "$artist - $title"
      }

      @JvmStatic
      fun stopMediaUpdates() {
         MEDIA.close()
      }
   }
}
