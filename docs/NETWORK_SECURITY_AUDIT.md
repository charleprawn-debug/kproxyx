# Network transport audit

## Implemented

The Android client now embeds the official Sing-box `libbox.aar` built from Sing-box v1.14.0 source with the Android arm64 target. The old `tun2socks` dependency and `engine.Engine` path have been removed.

`ProxyVpnService` initializes `Libbox`, starts a `CommandServer`, writes a runtime `Config.json` into the app's private files directory, and starts/reloads Sing-box with the configured HTTP or SOCKS5 upstream. `SingBoxPlatformInterface` implements the Android platform bridge. Sing-box owns the upstream sockets and calls `VpnService.protect(fd)` through `autoDetectInterfaceControl`, preventing recursive capture.

The runtime configuration includes an HTTPS DNS server at `1.1.1.1/dns-query` with the DNS connection detoured through the proxy, a TUN inbound with `auto_route: true` and `strict_route: true`, `route.auto_detect_interface: true`, IPv4/IPv6 TUN addresses, and default routing through the proxy.

The notification uses a versioned `IMPORTANCE_MIN` channel, a valid transparent vector resource, a silent minimum-priority notification, and a UI action opening that exact system channel. Android still controls the system VPN indicator; no app can guarantee removal of that indicator while a VpnService is active.

## Multiplexing compatibility

Sing-box's official schema rejects `multiplex` on SOCKS and HTTP outbounds. The runtime config therefore does not insert an invalid field for the two protocols exposed by the current UI. The generator adds Sing-box Mux only for protocols that expose `OutboundMultiplexOptions` (`vmess`, `vless`, `trojan`, and `shadowsocks`) if those protocols are added to the UI later. This behavior was confirmed by `sing-box check`.

## Verification

The following checks completed successfully:

| Check | Result |
|---|---|
| Official libbox build | `libbox.aar` generated successfully from Sing-box source |
| Sing-box config schema | `go run ./cmd/sing-box check -c validation-config.json` passed |
| Android compilation | `./gradlew clean assembleDebug --no-daemon` passed |
| APK native payload | `lib/arm64-v8a/libbox.so` present in the APK |
| Runtime config asset | `assets/Config.json` packaged in the APK |

The generated debug APK targets arm64-v8a because that is the native libbox artifact built and embedded in this revision. A multi-ABI release requires building and merging additional libbox AAR artifacts for `armeabi-v7a`, `x86`, and `x86_64`.

Physical-device validation is still required for Android OEM behavior, network handover, DNS leak testing, and real upstream connectivity. The sandbox can validate compilation and schema, but cannot certify those device-level runtime properties without an Android device.

## CI validation

The app declares `ACCESS_MOCK_LOCATION` with a narrowly scoped `ProtectedPermissions`/`MockLocation` lint suppression because AOSP's Developer-app picker filters candidates by requested permission; without the declaration, the app is absent from the selector. Android 15 defines this as signature-only, so a third-party install does not receive that permission. The user-selected `OP_MOCK_LOCATION` AppOp remains the real authorization gate; the app reads its mode using the Android-version-appropriate `AppOpsManager` no-throw check before connecting and again immediately before installation and each location publish. Google Play Services requires `ACCESS_COARSE_LOCATION` for its Fused mock APIs, so the app requests approximate location at runtime only when mock mode is used; it never reads real device coordinates and does not request precise location. The provider uses Android `ProviderProperties` constants required by current SDK lint checks.

## Proxy-matched Mock GPS

The app now includes an explicit, user-controlled Mock Location option. In automatic mode it sends an HTTPS GeoIP request to `ipwho.is` through a loopback mixed inbound on the running sing-box instance, which handles upstream credentials for HTTP or SOCKS5 consistently; it refreshes GeoIP every 15 minutes and republishes the last valid coordinates every two seconds. In manual mode the user supplies latitude and longitude directly. Coordinates are sent to Android GPS/network/fused test providers and explicitly into Google Play Services `FusedLocationProviderClient`; enabling FLP mock mode clears its real-location cache so Maps clients using FLP can consume the injected coordinates. The controller disables FLP mock mode and removes its Android test providers on stop, and it keeps a durable recovery marker to retry restoring real FLP mode after a process interruption.

Android requires the user to enable Developer options and select **Proxy Platform** as the mock-location app. The application does not bypass this Android security gate, does not alter hardware GPS, and does not attempt to evade mock-location detection. Some applications may ignore mock locations or detect them using Play Integrity, sensors, or their own telemetry. The GeoIP result is approximate and represents the proxy exit location as reported by the provider, not a guarantee of physical presence.

The UI provides a direct button to open Developer options, automatic/manual mode selection, coordinate validation, and lifecycle cleanup. A physical Android-device test is still required because OEMs may restrict mock providers differently.
