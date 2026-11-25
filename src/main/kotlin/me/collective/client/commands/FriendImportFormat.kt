package me.collective.client.commands

import com.google.gson.JsonArray
import me.collective.client.ClientAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets

enum class FriendImportFormat(val displayName: String) {
   FUTURE("Future") {
      @Throws(IOException::class)
      override fun readFriendNames(): List<String> {
         var path = System.getProperty("user.home")
         val separator = File.separator
         if (!path.endsWith(separator)) {
            path += separator
         }
         path += "Future%sfriends.json".format(separator)
         val file = File(path)
         if (!file.exists() || !file.isFile) {
            throw FileNotFoundException()
         }
         val input = FileInputStream(file)
         val bytes = input.readAllBytes()
         input.close()
         if (bytes.isEmpty()) {
            return emptyList()
         }
         val names = mutableListOf<String>()
         val parser = ClientAccess.gson
         val json = String(bytes, StandardCharsets.UTF_8)
         val entries = parser.fromJson(json, JsonArray::class.java)
         for (index in 0 until entries.size()) {
            val entry = entries.get(index).asJsonObject
            if (entry.has("friend-label")) {
               val name = entry.get("friend-label").asString
               if (!names.contains(name)) {
                  names.add(name)
               }
            }
         }
         return names
      }
   },
   RUSHERHACK("RusherHack") {
      @Throws(IOException::class)
      override fun readFriendNames(): List<String> {
         val file = File("rusherhack" + File.separator + "config" + File.separator + "relations.json")
         if (!file.exists() || !file.isFile) {
            throw FileNotFoundException()
         }
         val input = FileInputStream(file)
         val bytes = input.readAllBytes()
         input.close()
         if (bytes.isEmpty()) {
            return emptyList()
         }
         val names = mutableListOf<String>()
         val parser = ClientAccess.gson
         val json = String(bytes, StandardCharsets.UTF_8)
         val entries = parser.fromJson(json, JsonArray::class.java)
         for (index in 0 until entries.size()) {
            val entry = entries.get(index).asJsonObject
            if (entry.has("username") && entry.has("state")) {
               val name = entry.get("username").asString
               val state = entry.get("state").asString
               if ("FRIEND".equals(state, ignoreCase = true) && !names.contains(name)) {
                  names.add(name)
               }
            }
         }
         return names
      }
   },
   METEOR("Meteor") {
      @Throws(IOException::class)
      override fun readFriendNames(): List<String> {
         val file = File("meteor-client" + File.separator + "friends.nbt")
         if (!file.exists() || !file.isFile || file.length() == 0L) {
            throw FileNotFoundException()
         }
         val names = mutableListOf<String>()
         val path = file.toPath()
         val root = NbtIo.read(path) ?: return names
         val storedTags = root.getList("friends", net.minecraft.nbt.Tag.TAG_COMPOUND.toInt())
         for (tag in storedTags) {
            val friend = tag as CompoundTag
            if (friend.contains("name")) {
               val name = friend.getString("name")
               if (name.isNotEmpty() && !names.contains(name)) {
                  names.add(name)
               }
            }
         }
         return names
      }
   };

   @Throws(IOException::class)
   open fun readFriendNames(): List<String> {
      return emptyList()
   }
}
