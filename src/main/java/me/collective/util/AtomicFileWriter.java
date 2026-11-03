package me.collective.util;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Перезапись файла без риска получить обрезанный результат: данные пишутся в
 * файл-сосед с суффиксом .tmp и атомарно переименовываются поверх целевого.
 * Fallback на обычный move — для файловых систем без атомарного rename
 * (некоторые сетевые шары).
 */
public final class AtomicFileWriter {

    private static final String TEMP_SUFFIX = ".tmp";

    private AtomicFileWriter() {
    }

    public static void write(Path target, String content) throws Exception {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        Files.writeString(temp, content,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
