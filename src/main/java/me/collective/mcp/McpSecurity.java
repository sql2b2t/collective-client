package me.collective.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/** Local-only MCP trust boundary; credentials never belong to a game config or log. */
public final class McpSecurity {
    private static final Set<String> CODE_TOOLS = Set.of("write_script", "load_script", "unload_script",
            "delete_script", "reload_scripts", "write_addon_source", "delete_addon_source",
            "build_addon", "reload_addons", "execute_command");
    private final byte[] bearer;

    private McpSecurity(String token) {
        bearer = ("Bearer " + token).getBytes(StandardCharsets.US_ASCII);
    }

    public static Path tokenFile() {
        return Path.of(System.getProperty("user.home"), ".config", "collective-private", "mcp.token");
    }

    public static McpSecurity load() throws IOException {
        return load(tokenFile());
    }

    static McpSecurity load(Path file) throws IOException {
        // Fail closed on platforms without private POSIX file permissions, rather than create a public token.
        Path directory = file.getParent();
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
            }
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("MCP credential directory must be a private real directory");
            }
            if (!Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) {
                throw new IOException("MCP credential directory must have permissions 0700");
            }
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                try (var output = Files.newByteChannel(file, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    java.nio.ByteBuffer bytes = StandardCharsets.US_ASCII.encode(token);
                    while (bytes.hasRemaining()) output.write(bytes);
                }
            }
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !Files.getPosixFilePermissions(file).equals(PosixFilePermissions.fromString("rw-------"))) {
                throw new IOException("MCP credential file must be a private regular file (0600)");
            }
            if (Files.size(file) > 128) throw new IOException("Invalid MCP credential file");
            String token = Files.readString(file, StandardCharsets.US_ASCII).trim();
            if (!token.matches("[A-Za-z0-9_-]{43}")) throw new IOException("Invalid MCP credential file");
            return new McpSecurity(token);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Private MCP credential storage is not supported on this filesystem", e);
        }
    }

    public boolean authenticates(String authorization) {
        return authorization != null && MessageDigest.isEqual(bearer,
                authorization.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean acceptsHost(String host, int port) {
        return ("127.0.0.1:" + port).equals(host) || ("localhost:" + port).equals(host);
    }

    public static boolean acceptsOrigin(String origin, int port) {
        return origin == null || ("http://127.0.0.1:" + port).equals(origin)
                || ("http://localhost:" + port).equals(origin);
    }

    public static boolean requiresCodePermission(String tool) {
        return CODE_TOOLS.contains(tool);
    }
}