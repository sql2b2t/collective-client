package me.collective.proxy;

/** Прокси для подключения: socks5://[user:pass@]host:port или http://... */
public record ProxyEntry(ProxyType type, String host, int port, String user, String pass) {

    public enum ProxyType {
        SOCKS5, HTTP
    }

    public boolean hasAuth() {
        return user != null && !user.isEmpty();
    }

    /** Короткое имя для списков (пароль не светим). */
    public String displayName() {
        return type.name().toLowerCase() + "://" + host + ":" + port;
    }

    /** Разбор строки вида [socks5://|http://][user:pass@]host:port */
    public static ProxyEntry parse(String line) {
        if (line == null) return null;
        String s = line.strip();
        if (s.isEmpty() || s.startsWith("#")) return null;

        ProxyType type = ProxyType.SOCKS5;
        if (s.regionMatches(true, 0, "http://", 0, 7)) {
            type = ProxyType.HTTP;
            s = s.substring(7);
        } else if (s.regionMatches(true, 0, "socks5://", 0, 9) || s.regionMatches(true, 0, "socks://", 0, 8)) {
            type = ProxyType.SOCKS5;
            s = s.substring(s.indexOf("://") + 3);
        }

        String user = "";
        String pass = "";
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            String auth = s.substring(0, at);
            s = s.substring(at + 1);
            int colon = auth.indexOf(':');
            if (colon >= 0) {
                user = auth.substring(0, colon);
                pass = auth.substring(colon + 1);
            } else {
                user = auth;
            }
        }

        int colon = s.lastIndexOf(':');
        if (colon < 0) return null;
        String host = s.substring(0, colon).strip();
        int port;
        try {
            port = Integer.parseInt(s.substring(colon + 1).strip());
        } catch (NumberFormatException e) {
            return null;
        }
        if (host.isEmpty() || port <= 0 || port > 65535) return null;
        return new ProxyEntry(type, host, port, user, pass);
    }
}