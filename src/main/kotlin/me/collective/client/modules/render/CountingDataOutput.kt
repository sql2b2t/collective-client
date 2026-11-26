package me.collective.client.modules.render

import java.io.DataOutput

class CountingDataOutput : DataOutput {
   var byteCount: Int = 0
      private set

   override fun write(value: Int) {
      this.byteCount++
   }

   override fun write(bytes: ByteArray) {
      this.byteCount += bytes.size
   }

   override fun write(bytes: ByteArray, offset: Int, length: Int) {
      this.byteCount += length
   }

   override fun writeBoolean(value: Boolean) {
      this.byteCount++
   }

   override fun writeByte(value: Int) {
      this.byteCount++
   }

   override fun writeShort(value: Int) {
      val previousCount = this.byteCount
      this.byteCount = previousCount + 2
   }

   override fun writeChar(value: Int) {
      val previousCount = this.byteCount
      this.byteCount = previousCount + 2
   }

   override fun writeInt(value: Int) {
      val previousCount = this.byteCount
      this.byteCount = previousCount + 4
   }

   override fun writeLong(value: Long) {
      val previousCount = this.byteCount
      this.byteCount = previousCount + 8
   }

   override fun writeFloat(value: Float) {
      val previousCount = this.byteCount
      this.byteCount = previousCount + 4
   }

   override fun writeDouble(value: Double) {
      val previousCount = this.byteCount
      this.byteCount = previousCount + 8
   }

   override fun writeBytes(value: String) {
      val previousCount = this.byteCount
      val text = value
      this.byteCount = previousCount + text.length
   }

   override fun writeChars(value: String) {
      val previousCount = this.byteCount
      val text = value
      this.byteCount = previousCount + text.toByteArray().size
   }

   override fun writeUTF(value: String) {
      val previousCount = this.byteCount
      val text = value
      this.byteCount = previousCount + text.toByteArray().size
   }

}
