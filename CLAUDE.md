# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build all modules
mvn clean package

# Build a single module
mvn clean package -pl zxf-java-sslvpn-server
mvn clean package -pl zxf-java-sslvpn-client

# Build client fat JAR (jar-with-dependencies)
mvn clean package -pl zxf-java-sslvpn-client
```

No test framework is configured — there are no unit tests in this project.

## Project Overview

Java 17 multi-module Maven project implementing an SSL/TLS VPN with two proxy modes: SOCKS5 (application-level) and TUN device (OS-level packet interception).

## Module Structure

| Module | Purpose |
|--------|---------|
| `zxf-java-sslvpn-common` | Shared utilities: SSL socket factories, bidirectional data forwarding, socket helpers |
| `zxf-java-sslvpn-server` | SSL VPN gateway — listens on port 8443, accepts SSL connections, forwards to target hosts |
| `zxf-java-sslvpn-client` | Three subsystems: VPN client (`zxf.vpn`), SOCKS5 proxy (`zxf.socks`, port 1080), TUN proxy (`zxf.tunproxy`) |
| `zxf-java-sslvpn-test` | Manual test utilities (not automated tests) |
| `zxf-java-sslvpn-echo-server` | TCP echo server for manual testing |

## Architecture

```
Applications (curl, browser)
       │
  ┌────┴────────────────┐
  │ SOCKS5 Proxy :1080  │   TUN Device (OS-level)
  └────┬────────────────┘   ProxyServer
       │                        │
       └──────┬─────────────────┘
              │ SSL/TLS tunnel
       ┌──────▼──────────┐
       │ VPN Server :8443 │
       └──────┬──────────┘
              │
       Target Servers
```

**Data flow**: Client proxy (SOCKS5 or TUN) → SSL tunnel to VPN server → VPN server opens plain socket to target host → bidirectional forwarding via `DataForwarder`.

## Key Classes

- **`zxf.SSLSocketFactories`** (common) — Creates SSL contexts from JKS keystores/truststores. Central to all SSL connections.
- **`zxf.DataForwarder`** (common) — Record-based bidirectional socket data forwarding with logging.
- **`zxf.SSLVPNServer`** / `zxf.ClientHandler` (server) — VPN gateway entry point; custom binary protocol for connection requests.
- **`zxf.socks.Socks5ProxyServer`** / `Socks5Handler` (client) — Full SOCKS5 implementation (IPv4/IPv6/domain, auth negotiation).
- **`zxf.tunproxy.ProxyServer`** / `TunProxy` (client) — TUN device proxy; parses raw IP/TCP/UDP packets via `PacketParser`/`PacketBuilder`, manages TCP sessions.
- **`zxf.tunproxy.tun.TunDeviceManager`** (client) — Linux TUN device creation/cleanup via JNA.

## Key Dependencies

- **Logback + SLF4J** — Logging (use Lombok `@Slf4j`)
- **Guava** — Threading utilities (`ThreadFactoryBuilder`)
- **Lombok** — `@Slf4j`, `@Getter`
- **JNA** (client only) — Native TUN device interaction on Linux

## SSL/TLS Configuration

Keystores are in each module's `src/main/resources/keystore/` directory (JKS format). Server and client each have their own keystore and truststore pairs.

## Running

```bash
# VPN Server (must start first)
java -cp zxf-java-sslvpn-server/target/classes:zxf-java-sslvpn-common/target/classes zxf.SSLVPNServer

# SOCKS5 Proxy Client
java -cp zxf-java-sslvpn-client/target/classes:zxf-java-sslvpn-common/target/classes zxf.socks.Socks5ProxyServer

# TUN Proxy (requires root for TUN device)
sudo java -jar zxf-java-sslvpn-client/target/*-jar-with-dependencies.jar

# Test with curl through SOCKS5
curl -x socks5://localhost:1080 http://example.com
```
