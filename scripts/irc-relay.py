#!/usr/bin/env python3
"""collective-irc-relay — TCP-релей общего чата для Collective Client (IrcModule).

Протокол (UTF-8, строки, завершённые '\\n'; '\\r' отбрасывается):

  клиент -> сервер:
      LOGIN <nick> <token>     сразу после подключения; ник [A-Za-z0-9_]{3,16}
      SAY <text>               сообщение в общий чат (text <= 240 символов)
      PING                     клиент шлёт после 15 с входящей тишины

  сервер -> клиент:
      OK                       подтверждение входа (первая строка)
      ERR <reason>             отказ (клиент показывает reason)
      MSG <nick> <text>        сообщение чата — всем, ВКЛЮЧАЯ отправителя
                               (клиент не эхолокирует свои сообщения сам)
      JOIN <nick>              новый пользователь вошёл (кроме него самого:
                               ростер ему приходит строкой USERS)
      QUIT <nick>              пользователь покинул канал
      USERS <nick1,nick2,...>  полный список участников (заменяет ростер)

Только стандартная библиотека Python 3. Слушает 127.0.0.1 — публичность
даёт туннель (bore). Токен по умолчанию 'collective' — как у клиентов
IrcModule.DEFAULT_RELAY_TOKEN, чтобы они подключились без изменений.
"""

from __future__ import annotations

import argparse
import logging
import re
import signal
import socket
import sys
import threading
import time

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 29900
DEFAULT_TOKEN = "collective"

MAX_LINE = 2040          # клиент режет входящие строки на 2040 символах
MAX_TEXT = 240           # IrcModule.MAX_TEXT
MAX_NICK = 16
MIN_NICK = 3
LOGIN_TIMEOUT = 30.0     # сколько ждать LOGIN до разрыва
IDLE_TIMEOUT = 120.0     # живой клиент шлёт PING каждые 15 с тишины
RECV_TIMEOUT = 10.0      # тик чтения: окно для проверки простоя/остановки
SEND_TIMEOUT = 15.0      # мёртвый клиент не должен вешать broadcast

NICK_STRIP_RE = re.compile(r"[^A-Za-z0-9_]")

log = logging.getLogger("irc-relay")


class Client:
    """Одно TCP-подключение."""

    def __init__(self, sock: socket.socket, addr) -> None:
        self.sock = sock
        self.addr = addr
        self.nick: str | None = None
        self.alive = True
        self.send_lock = threading.Lock()
        self.buf = bytearray()
        self.dropping_line = False  # строка длиннее лимита — сбрасываем до '\n'
        self.last_recv = time.monotonic()

    def send_line(self, line: str) -> None:
        data = (line.replace("\n", " ").replace("\r", " ") + "\n").encode("utf-8")
        with self.send_lock:
            self.sock.sendall(data)

    def close(self) -> None:
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            self.sock.close()
        except OSError:
            pass


class Relay:
    def __init__(self, host: str, port: int, token: str) -> None:
        self.host = host
        self.port = port
        self.token = token
        self.clients: dict[str, Client] = {}
        self.lock = threading.Lock()
        self.stopping = threading.Event()
        self.server_sock: socket.socket | None = None

    # ── рассылка ──────────────────────────────────────────────────────────

    def broadcast(self, line: str, *, skip: Client | None = None) -> None:
        with self.lock:
            targets = [c for c in self.clients.values() if c is not skip]
        dead = []
        for c in targets:
            try:
                c.send_line(line)
            except OSError as exc:
                log.warning("рассылка %s не дошла до %s: %s", line.split(" ", 1)[0], c.nick or c.addr, exc)
                dead.append(c)
        for c in dead:
            self.drop(c, "ошибка записи при рассылке")

    def drop(self, client: Client, reason: str) -> None:
        nick = None
        with self.lock:
            if not client.alive:
                return
            client.alive = False
            if client.nick is not None and self.clients.get(client.nick) is client:
                del self.clients[client.nick]
                nick = client.nick
        client.close()
        log.info("отключение %s (%s): %s", nick or "без логина", client.addr[0], reason)
        if nick is not None:
            self.broadcast("QUIT " + nick)

    # ── чтение строк с ограничением длины ────────────────────────────────

    def read_line(self, client: Client, deadline: float) -> str | None:
        """Возвращает строку без '\\r', либо None по таймауту/EOF/остановке.

        Слишком длинние строки сбрасываются целиком (до '\\n'), не ломая сеанс.
        """
        while not self.stopping.is_set():
            nl = client.buf.find(b"\n")
            if nl >= 0:
                raw = bytes(client.buf[:nl])
                del client.buf[: nl + 1]
                client.dropping_line = False
                return raw.decode("utf-8", errors="replace").rstrip("\r")
            if client.dropping_line:
                # длинная строка: копим только позицию '\n', содержимое режем
                client.buf.clear()
            elif len(client.buf) > MAX_LINE * 4:
                log.warning("строка длиннее %d символов от %s — сброшена", MAX_LINE, client.addr[0])
                client.buf.clear()
                client.dropping_line = True
            if time.monotonic() >= deadline:
                return None
            try:
                chunk = client.sock.recv(4096)
            except socket.timeout:
                continue
            except OSError:
                return None
            if not chunk:
                return None
            client.last_recv = time.monotonic()
            client.buf.extend(chunk)
        return None

    # ── обработка подключения ────────────────────────────────────────────

    def handle(self, client: Client) -> None:
        addr = client.addr[0]
        try:
            client.sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
            client.sock.settimeout(RECV_TIMEOUT)
            log.info("подключение с %s", addr)

            # фаза логина: первая непустая строка обязана быть LOGIN
            deadline = time.monotonic() + LOGIN_TIMEOUT
            line = ""
            while True:
                line = self.read_line(client, deadline)
                if line is None:
                    log.info("%s: нет LOGIN за %.0f с — разрыв", addr, LOGIN_TIMEOUT)
                    return
                if line:
                    break

            if not line.startswith("LOGIN "):
                self.safe_err(client, "ожидался LOGIN")
                return
            parts = line.split()
            if len(parts) != 3:
                self.safe_err(client, "непонятный формат LOGIN")
                return
            _, raw_nick, token = parts
            if token != self.token:
                self.safe_err(client, "неверный токен")
                log.warning("%s: попытка входа с неверным токеном (ник %s)", addr, raw_nick[:32])
                return

            nick = self.register(client, raw_nick)
            try:
                client.send_line("OK")
                with self.lock:
                    roster = ",".join(sorted(self.clients.keys()))
                client.send_line("USERS " + roster)
            except OSError:
                self.drop(client, "не удалось отправить приветствие")
                return
            self.broadcast("JOIN " + nick, skip=client)
            log.info("вход: %s (с %s), всего в канале: %d", nick, addr, self.user_count())

            # основной цикл: SAY / PING / мусор
            while not self.stopping.is_set():
                if time.monotonic() - client.last_recv > IDLE_TIMEOUT:
                    self.drop(client, "простой без PING")
                    return
                line = self.read_line(client, time.monotonic() + RECV_TIMEOUT)
                if line is None:
                    if not client.alive:
                        return
                    if time.monotonic() - client.last_recv > IDLE_TIMEOUT:
                        self.drop(client, "простой без PING")
                        return
                    continue
                self.dispatch(client, nick, line)
        except OSError as exc:
            log.info("%s: сокет закрыт: %s", addr, exc)
        finally:
            self.drop(client, "сеанс завершён")

    def dispatch(self, client: Client, nick: str, line: str) -> None:
        if not line:
            return
        if line == "PING" or line.startswith("PING "):
            try:
                client.send_line("PONG")
            except OSError:
                self.drop(client, "ошибка записи PONG")
            return
        if line.startswith("SAY"):
            text = line[3:].lstrip(" ")[:MAX_TEXT].strip()
            if not text:
                return
            self.broadcast("MSG " + nick + " " + text)  # включая отправителя
            log.info("сообщение %s: %.120s", nick, text)
            return
        if line.startswith("LOGIN"):
            # повторный LOGIN за сеанс игнорируем, связь рвать не надо
            return
        log.debug("неизвестная строка от %s: %.80r", nick, line)

    def register(self, client: Client, raw_nick: str) -> str:
        """Ник по правилам [A-Za-z0-9_]{3,16}; при коллизии добавляет цифры."""
        nick = NICK_STRIP_RE.sub("", raw_nick)[:MAX_NICK]
        if len(nick) < MIN_NICK:
            nick = "user" + str(int(client.addr[1]) % 10000).zfill(4)
        base = nick
        with self.lock:
            n = 2
            while nick in self.clients:
                suffix = str(n)
                nick = base[: MAX_NICK - len(suffix)] + suffix
                n += 1
            client.nick = nick
            self.clients[nick] = client
        if nick != NICK_STRIP_RE.sub("", raw_nick)[:MAX_NICK]:
            log.info("ник %s занят/некорректен — зарегистрирован как %s", raw_nick[:32], nick)
        return nick

    def user_count(self) -> int:
        with self.lock:
            return len(self.clients)

    @staticmethod
    def safe_err(client: Client, reason: str) -> None:
        try:
            client.send_line("ERR " + reason)
        except OSError:
            pass

    # ── жизненный цикл ───────────────────────────────────────────────────

    def serve_forever(self) -> None:
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((self.host, self.port))
        srv.listen(64)
        srv.settimeout(1.0)
        self.server_sock = srv
        log.info("релей слушает %s:%d (токен %r, лимит строки %d)", self.host, self.port, self.token, MAX_LINE)
        try:
            while not self.stopping.is_set():
                try:
                    sock, addr = srv.accept()
                except socket.timeout:
                    continue
                except OSError:
                    if self.stopping.is_set():
                        break
                    raise
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                t = threading.Thread(target=self.handle, args=(Client(sock, addr),), daemon=True)
                t.start()
        finally:
            srv.close()
            with self.lock:
                clients = list(self.clients.values())
            for c in clients:
                self.drop(c, "сервер останавливается")
            log.info("сервер остановлен")

    def stop(self) -> None:
        self.stopping.set()
        if self.server_sock is not None:
            try:
                self.server_sock.close()
            except OSError:
                pass


def main() -> int:
    ap = argparse.ArgumentParser(description="Relay-сервер общего чата Collective Client (IRC-модуль)")
    ap.add_argument("--host", default=DEFAULT_HOST, help="адрес прослушивания (по умолчанию %s)" % DEFAULT_HOST)
    ap.add_argument("--port", type=int, default=DEFAULT_PORT, help="порт (по умолчанию %d)" % DEFAULT_PORT)
    ap.add_argument("--token", default=DEFAULT_TOKEN, help="токен доступа (по умолчанию %r)" % DEFAULT_TOKEN)
    ap.add_argument("-q", "--quiet", action="store_true", help="только предупреждения и ошибки")
    args = ap.parse_args()

    logging.basicConfig(
        level=logging.WARNING if args.quiet else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        stream=sys.stdout,
    )

    relay = Relay(args.host, args.port, args.token)

    def on_signal(signum, _frame):
        log.info("получен сигнал %s — останавливаюсь", signum)
        relay.stop()

    signal.signal(signal.SIGTERM, on_signal)
    signal.signal(signal.SIGINT, on_signal)

    relay.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())