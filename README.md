# FB Privacy Shield (Android 9+)

**FB Privacy Shield** is a per-app privacy VPN for stock Android that completely blocks Facebook tracking, telemetry, and background probe ports **without root**, **without modifying Facebook's APK**, and **without Xposed or LSPosed**.

---

## 🛡️ Core Features

1. **Per-App VpnService Isolation**:
   - Uses Android's native `VpnService.Builder.addAllowedApplication("com.facebook.katana")` (and Messenger `com.facebook.orca` when present).
   - Only Facebook's traffic enters the local VPN tunnel; all other system apps (browsers, banking, games) bypass the tunnel completely.

2. **Packet-Level Tracking Filtering**:
   - Parses IPv4, TCP, and UDP headers at the TUN byte-level using a zero-allocation `ByteBufferPool`.
   - **DNS Filtering (UDP 53)**: Intercepts DNS queries for tracker endpoints and responds immediately with synthesized `NXDOMAIN` packets, forcing Facebook's tracking libraries to abort cleanly.
   - **TLS SNI Inspection (TCP 443)**: Decodes TLS ClientHello packets and drops connections destined for known tracking subdomains.
   - **HTTP Inspection (TCP 80)**: Inspects URL paths and headers to drop tracker logs.

3. **Localhost Port Blocking**:
   - Pre-binds `ServerSocket` and `DatagramSocket` to `127.0.0.1` before Facebook starts:
     - **TCP Ports**: `12387`, `12388` (Incoming connections accepted and immediately closed).
     - **UDP Ports**: `12580`, `12581`, `12582`, `12583`, `12584`, `12585` (Sockets held open; probes drained and discarded).

4. **Zero Disruption to Essential Features**:
   - Allows News Feed, GraphQL (`graph.facebook.com/graphql`), Real-time MQTT, and CDN media (`*.fbcdn.net`, `*.fbsbx.com`, `*.instagram.com`) to flow smoothly.

5. **Persistent Background Service & Stats**:
   - Runs as a Foreground Service with live notification: `X packets blocked | Y MB saved`.
   - Quick Settings Tile (`VpnTileService`) for single-tap shield toggling.
   - Boot receiver (`BootReceiver`) for auto-restarting on device boot.
   - DataStore persistence for blocked counters and custom blocklist rules.

---

## 📁 Project File Structure

```
app/src/main/java/com/fbprivacy/shield/
├── App.kt                    # Application class & notification channel
├── MainActivity.kt           # Main Compose entrypoint & permission handling
├── FbVpnService.kt           # Foreground VpnService with network callbacks
├── PacketRouter.kt           # Dispatches TUN packets & enforces drops
├── EndpointBlocklist.kt      # Evaluates tracker endpoints, SNI & ports
├── LocalhostBlocker.kt       # Pre-binds loopback TCP & UDP tracking ports
├── DnsPacketParser.kt        # Parses DNS queries & synthesizes NXDOMAIN
├── IpHeaderParser.kt         # Parses IPv4 headers
├── TcpHeaderParser.kt        # Parses TCP flags, SNI & HTTP paths
├── UdpHeaderParser.kt        # Parses UDP headers
├── PacketForwarder.kt        # Protected sockets, DNS & TCP streams
├── StatsRepository.kt        # StateFlow telemetry & live audit log
├── PreferencesManager.kt     # Jetpack DataStore preferences
├── BootReceiver.kt           # Starts VPN on device boot if enabled
├── VpnTileService.kt         # Android Quick Settings tile
├── MainScreen.kt             # Screen 1: Pulsing shield, switch & stats
├── SettingsScreen.kt         # Screen 2: Boot toggle, notifications, custom rules
├── InfoScreen.kt             # Screen 3: Architecture & endpoint documentation
├── UiComponents.kt           # Glowing shield visual & glassmorphic cards
└── ui/theme/                 # Material 3 dark cyber-security theme
```

---

## 🔒 Blocked Endpoints vs Allowed Domains

### Blocked Tracking Endpoints
- `graph.facebook.com/logging_client_events`
- `graph.facebook.com/ajax/bz`
- `b-www.facebook.com/mobile/extra_data_collector`
- `b-www.facebook.com/mobile/parfait_trace_upload`
- `lookaside.facebook.com/ras/v2`
- `m.facebook.com/async/wbloks/log`
- `graph.facebook.com/mobile_crash_reports`
- `graph.facebook.com/dsp`
- `graph.facebook.com/ad_tracking`
- `graph.facebook.com/signal_collection`

### Allowed Domains
- `graph.facebook.com/graphql`
- `graph.facebook.com/graphqlbatch`
- `mqtt.facebook.com`
- `edge-mqtt.facebook.com`
- `*.fbcdn.net`
- `*.fbsbx.com`
- `*.whatsapp.net`
- `*.instagram.com`
