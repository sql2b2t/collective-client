package me.collective.client.utils

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

class CompressionUtils private constructor() {
   companion object {
      private const val BUFFER_SIZE = 8192

      @JvmStatic
      fun inflate(input: ByteArray): ByteArray {
         val inflater = Inflater()
         try {
            inflater.setInput(input)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (!inflater.finished()) {
               val written = inflater.inflate(buffer)
               if (written == 0) {
                  break
               }
               output.write(buffer, 0, written)
            }
            return output.toByteArray()
         } catch (failure: DataFormatException) {
            throw IllegalArgumentException("Invalid compressed data", failure)
         } finally {
            inflater.end()
         }
      }

      @JvmStatic
      fun deflate(input: ByteArray, level: Int): ByteArray {
         val deflater = Deflater(level)
         try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (!deflater.finished()) {
               val written = deflater.deflate(buffer)
               output.write(buffer, 0, written)
            }
            return output.toByteArray()
         } finally {
            deflater.end()
         }
      }
   }
}
