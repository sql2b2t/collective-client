package me.collective.util;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DiscordIpc implements AutoCloseable {
    // Discord Application ID for Minecraft: Java Edition (displays "Minecraft: Java Edition")
    private static final String CLIENT_ID = "1410791091501928458";
    public static final String DEFAULT_ICON = "https://raw.githubusercontent.com/anvres56/b2xy/main/collective_icon.png";
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private SocketChannel unixChannel;
    private RandomAccessFile winPipe;
    private InputStream inStream;
    private OutputStream outStream;
    private boolean connected = false;

    public synchronized boolean connect() {
        close();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return connectWindows();
        } else {
            return connectUnix();
        }
    }

    private boolean connectWindows() {
        for (int i = 0; i < 10; i++) {
            try {
                File pipeFile = new File("\\\\.\\pipe\\discord-ipc-" + i);
                this.winPipe = new RandomAccessFile(pipeFile, "rw");
                this.inStream = Channels.newInputStream(winPipe.getChannel());
                this.outStream = Channels.newOutputStream(winPipe.getChannel());
                if (sendHandshake()) {
                    this.connected = true;
                    return true;
                }
            } catch (Exception ignored) {
                close();
            }
        }
        return false;
    }

    private boolean connectUnix() {
        String xdg = System.getenv("XDG_RUNTIME_DIR");
        String tmpdir = System.getenv("TMPDIR");
        String tmp = System.getenv("TMP");
        String temp = System.getenv("TEMP");

        List<File> baseDirs = new ArrayList<>();
        if (xdg != null && !xdg.isEmpty()) {
            baseDirs.add(new File(xdg));
            baseDirs.add(new File(xdg, "app/com.discordapp.Discord"));
        }
        if (tmpdir != null && !tmpdir.isEmpty()) baseDirs.add(new File(tmpdir));
        if (tmp != null && !tmp.isEmpty()) baseDirs.add(new File(tmp));
        if (temp != null && !temp.isEmpty()) baseDirs.add(new File(temp));
        baseDirs.add(new File("/tmp"));

        for (int i = 0; i < 10; i++) {
            for (File baseDir : baseDirs) {
                File candidate = new File(baseDir, "discord-ipc-" + i);
                if (!candidate.exists()) continue;

                try {
                    UnixDomainSocketAddress address = UnixDomainSocketAddress.of(candidate.toPath());
                    this.unixChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    this.unixChannel.connect(address);
                    this.inStream = Channels.newInputStream(this.unixChannel);
                    this.outStream = Channels.newOutputStream(this.unixChannel);
                    if (sendHandshake()) {
                        this.connected = true;
                        return true;
                    }
                } catch (Exception ignored) {
                    close();
                }
            }
        }
        return false;
    }

    private boolean sendHandshake() {
        try {
            String json = "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}";
            writeFrame(OP_HANDSHAKE, json);

            // Read handshake response
            byte[] header = readFully(8);
            if (header != null) {
                ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int op = buf.getInt();
                int len = buf.getInt();
                if (len > 0) {
                    readFully(len);
                }
                return op == OP_FRAME;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public synchronized boolean setActivity(String details, String state, long startTimestampSeconds) {
        return setActivity(details, state, DEFAULT_ICON, "Collective Core 1.21.11", startTimestampSeconds);
    }

    public synchronized boolean setActivity(String details, String state, String largeImageUrl, String largeText, long startTimestampSeconds) {
        if (!connected) return false;
        try {
            long pid = ProcessHandle.current().pid();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":").append(pid);
            sb.append(",\"activity\":{");

            boolean first = true;
            if (details != null && !details.isEmpty()) {
                sb.append("\"details\":\"").append(escape(details)).append("\"");
                first = false;
            }
            if (state != null && !state.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"state\":\"").append(escape(state)).append("\"");
                first = false;
            }
            if (startTimestampSeconds > 0) {
                if (!first) sb.append(",");
                sb.append("\"timestamps\":{\"start\":").append(startTimestampSeconds * 1000L).append("}");
                first = false;
            }
            if (largeImageUrl != null && !largeImageUrl.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"assets\":{\"large_image\":\"").append(escape(largeImageUrl)).append("\"");
                if (largeText != null && !largeText.isEmpty()) {
                    sb.append(",\"large_text\":\"").append(escape(largeText)).append("\"");
                }
                sb.append("}");
            }

            sb.append("}},\"nonce\":\"").append(java.util.UUID.randomUUID()).append("\"}");

            writeFrame(OP_FRAME, sb.toString());

            // Read SET_ACTIVITY response frame so buffer does not clog
            byte[] header = readFully(8);
            if (header != null) {
                ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                buf.getInt(); // op
                int len = buf.getInt();
                if (len > 0) {
                    readFully(len);
                }
            }
            return true;
        } catch (Exception e) {
            close();
            return false;
        }
    }

    private void writeFrame(int op, String json) throws Exception {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op);
        buf.putInt(data.length);
        buf.put(data);
        outStream.write(buf.array());
        outStream.flush();
    }

    private byte[] readFully(int length) throws Exception {
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            int r = inStream.read(data, read, length - read);
            if (r == -1) return null;
            read += r;
        }
        return data;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public synchronized void close() {
        connected = false;
        try {
            if (unixChannel != null) unixChannel.close();
        } catch (Exception ignored) {}
        try {
            if (winPipe != null) winPipe.close();
        } catch (Exception ignored) {}

        unixChannel = null;
        winPipe = null;
        inStream = null;
        outStream = null;
    }
}