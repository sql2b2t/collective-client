Collective IRC relay (chat + shared emotes)
===========================================

Single-file Java relay for Collective Client: powers the "IRC" module
(shared chat between cheat users) and shared emotes (your emote plays
on other Collective users' players, theirs play on yours).

Protocol: plain UTF-8 lines.
  client -> server: LOGIN <name> <token> / SAY <text> / EMOTE <id> / STOP / PING
  server -> client: OK / ERR <reason> / USERS <csv> / JOIN <name> / QUIT <name>
                    / MSG <name> <text> / EMOTE <name> <id> / STOP <name>

Limits: names [A-Za-z0-9_]{3,16}, text <= 240 chars, per-user token buckets
against flooding, bounded reader lines, bounded per-client outgoing queues.

Deploy on a VPS (any JDK 11+, Debian example):
----------------------------------------------

  # 1. Java
  apt-get install -y openjdk-17-jdk-headless

  # 2. Files
  mkdir -p /opt/collective-irc
  cp CollectiveIRCServer.java /opt/collective-irc/

  # 3. Service (edit ExecStart args: <port> <token>)
  cp collective-irc.service /etc/systemd/system/
  systemctl daemon-reload
  systemctl enable --now collective-irc

  # 4. Verify
  ss -tlnp | grep 29900
  printf 'LOGIN TestUser1 collective\nPING\n' | nc -w 3 <VPS_IP> 29900
  # expected output line: OK (then PONG)

Manually (without systemd):
  java CollectiveIRCServer.java 29900 collective
  # no token = open access: pass an empty string as the token arg

Client side:
  Module list -> Client -> "IRC": set Host / Port / Token, toggle on.
  ".irc <text>"  - send to the channel
  ".irc"         - status (connected, nick, user count)
  Server restarts are-safe: the module reconnects itself (5s..30s backoff).

Notes:
  - Token is not a password-grade secret; it just keeps random people out.
  - Emote ids must exist in both clients' Emote catalog (same build);
    unknown ids are ignored silently on the receiving end.
  - Emote links survive motion: movement auto-stop on the sender side
    broadcasts STOP, so the emote ends on every viewer.
