package me.collective.client.utils

import me.collective.client.render.text.ReverseStringFormatter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileUtils private constructor() {
   companion object {
      private const val suffixPathPattern = "\u0001\u0001"

      @JvmStatic
      @Throws(IOException::class)
      fun readBytes(path: Path): ByteArray {
         val input = FileInputStream(path.toFile())

         try {
            return input.readAllBytes()
         } finally {
            try {
               input.close()
            } catch (ignoredCloseFailure: Exception) {
            }
         }
      }

      @JvmStatic
      fun findExistingWithSuffixes(path: Path, vararg suffixes: String): Path {
         val baseFile = path.toFile()
         if (baseFile.exists()) {
            return path
         }

         for (suffix in suffixes) {
            val pathText = path.toString()
            val candidatePathText = ReverseStringFormatter()
               .prepend(suffix)
               .prepend(pathText)
               .format(suffixPathPattern)
            val candidatePath = Path.of(candidatePathText)
            val candidateFile = candidatePath.toFile()
            if (candidateFile.exists()) {
               return candidatePath
            }
         }

         return path
      }

      @JvmStatic
      @Throws(IOException::class)
      fun writeBytes(path: Path, bytes: ByteArray) {
         // Атомарная запись: сначала во временный файл, затем rename. Прямая запись
         // в целевой файл при краше/выключении оставляет битый конфиг (modules.json и т.п.).
         val absolutePath = path.toAbsolutePath()
         val directory = absolutePath.parent
            ?: throw IOException("Path has no parent directory: $path")
         Files.createDirectories(directory)
         val temporary = Files.createTempFile(directory, ".collective-write-", ".tmp")
         try {
            FileOutputStream(temporary.toFile()).use { output ->
               output.write(bytes)
            }
            try {
               Files.move(temporary, absolutePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (atomicFailure: IOException) {
               // На Windows атомарный rename поверх существующего файла может упасть
               // (FileAlreadyExistsException/AccessDeniedException при открытом файле),
               // поэтому повторяем обычным move с заменой. На POSIX сюда попадает
               // в основном AtomicMoveNotSupportedException.
               Files.move(temporary, absolutePath, StandardCopyOption.REPLACE_EXISTING)
            }
         } finally {
            Files.deleteIfExists(temporary)
         }
      }

      @JvmStatic
      @Throws(IOException::class)
      fun writeUtf8(path: Path, text: String) {
         val charset = StandardCharsets.UTF_8
         writeBytes(path, text.toByteArray(charset))
      }

      @JvmStatic
      @Throws(IOException::class)
      fun readUtf8(path: Path): String {
         return String(readBytes(path), StandardCharsets.UTF_8)
      }
   }
}
