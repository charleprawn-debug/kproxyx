# Shizuku / shizzi integration notes

Source repository: https://github.com/carlelieser/shizzi
Cloned for inspection at /tmp/shizzi on 2026-11-11.

Relevant source facts:
- shizzi uses `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`.
- Manifest requests `android.permission.MANAGE_TEST_NETWORKS` and `android.permission.TETHER_PRIVILEGED`, and declares `rikka.shizuku.ShizukuProvider`.
- `TetherService` is a Shizuku UserService, rebases context to `com.android.shell`, and uses `TetherSession`.
- `TetherSession` creates a test TUN network with `SessionResources`, attaches `datapath.Datapath`, starts tethering through privileged APIs, selects the test network as upstream, and uses watchdog/teardown to stop downstream on failure.
- `SessionResources` depends on a gomobile `datapath.aar`; shizzi builds it from `/datapath` using gomobile and Go.
- Shizuku is not just an Android library; it provides the shell-privileged UserService transport.

Current Kun cloak facts:
- App has embedded Wireless ADB via `EmbeddedAdbManager` / `WirelessDebuggingManager`.
- Main navigation enum is in `android/app/src/main/java/com/proxyplatform/app/MainActivity.kt`.
- MainShell navigation and screen switch are in `android/app/src/main/java/com/proxyplatform/app/KUNScreens.kt`.
- Current Android dependencies include libadb, libbox.aar, conscrypt, bouncycastle; no Shizuku dependency yet.
- Current manifest has an instrumentation declaration for `SimCarrierConfigInstrumentation` and services for ProxyLocalService, ProxyVpnService, ADB pairing; no Shizuku provider yet.
