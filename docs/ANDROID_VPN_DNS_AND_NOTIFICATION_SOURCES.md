# Android VPN DNS and notification input references

Sources checked for the KUN Proxy VPN and advanced-pairing work:

- Android Developers, **Create a notification** (Direct reply / `RemoteInput`): https://developer.android.com/develop/ui/compose/notifications/create-notification
  - Attach `RemoteInput` to a notification action; send the action through a `PendingIntent` targeting a receiver; read text with `RemoteInput.getResultsFromIntent(intent)`; refresh the same notification after processing.
- sing-box, **Rule Action** (`hijack-dns`): https://sing-box.sagernet.org/configuration/route/rule_action/
  - The exact final action is `{ "action": "hijack-dns" }`, which directs DNS requests into the sing-box DNS module.
- sing-box, **Route Rule**: https://sing-box.sagernet.org/configuration/route/rule/
  - Provides the route-rule schema for applying DNS interception before the final proxy route.
- sing-box, **TUN inbound**: https://sing-box.sagernet.org/configuration/inbound/tun/
  - `auto_route` installs routes for TUN traffic. The embedded `libbox.aar` version must be considered before using newer options such as `dns_mode`.
- sing-box, **Route**: https://sing-box.sagernet.org/configuration/route/
  - The current route reference limits generic `auto_detect_interface` to Linux, Windows, and macOS. It is disabled in KUN Proxy's Android VPN configuration.
- sing-box for Android, **VPNService**: https://github.com/SagerNet/sing-box-for-android/blob/dev/app/src/main/java/io/nekohasekai/sfa/bg/VPNService.kt
  - The upstream Android client protects sing-box outbound sockets through `VpnService.protect(fd)` in `autoDetectInterfaceControl`; KUN Proxy follows that Android platform mechanism to keep upstream sockets outside its own VPN.

These sources are implementation references, not a guarantee that a particular upstream proxy accepts the configured protocol or credentials; the app should test the upstream handshake before enabling full-device routing.


## Follow-up: TCP reaches neither the visible sing-box flow log nor the browser

Checked during the 2.2.1 device diagnosis:

- sing-box, **TUN inbound / `stack`**: https://sing-box.sagernet.org/configuration/inbound/tun/
  - Documents `system` as L3-to-L4 translation through the system network stack, `gvisor` as a userspace stack, and `mixed` as system TCP plus gVisor UDP. The `stack` option is deprecated starting in sing-box 1.15; the bundled `libbox.aar` does not expose its version in archive metadata, so do not assume the deprecation applies to the embedded engine.
- sing-box, **TUN stack migration**: https://sing-box.sagernet.org/migration/
  - Since 1.15.0, sing-tun uses its own TCP/IP stack; version compatibility must be confirmed before changing config.
- A community report with the close symptom “DNS works in TUN but HTTP(S) does not” was reviewed: https://discourse.nixos.org/t/sing-box-tun-inbound-in-client-configuration-did-not-work-for-http-s-traffics/57552
  - Its eventual fix was specific to a Linux firewall-trusted interface, so it is not evidence of an Android fix; use only as a symptom comparison.
- A reported Android `VpnService` TUN interface with no traffic received was reviewed: https://github.com/SagerNet/sing-box/issues/3701
  - The issue’s discussion highlighted TUN address/routing and DNS interception as diagnostic areas; it does not establish the cause on KUN Proxy’s Samsung device.

Device evidence received for KUN Proxy 2.2.1 (2026-09-30): Android reports active `tun0`, IPv4 default route `0.0.0.0/0`, IPv6 default as unreachable, and the VPN under the validated cellular network. The operation log showed successful DNS answers and no `no available network interface` or sing-box error. It did not show a TCP inbound flow for the opened HTTP test page. The global system proxy value is `:0` (disabled). The `toybox nc` port-80 probe timed out both with and without VPN, so it is not a valid discriminator on this network. The remaining diagnostic is to measure `tun0` packet counters around a browser request before changing the TUN stack or routes.


A focused Chrome capture (KUN Proxy 2.2.1) showed successful DNS A and HTTPS/SVCB answers for `example.com`, including IPv6 address hints, but zero sing-box TCP inbound/outbound flow records in the captured 220-line window; the only transport flows were background UDP/443. Android had reported `::/0 unreachable` on `tun0`. Reading `/sys/class/net/tun0/statistics/*` via unprivileged ADB is denied by Android, so do not request root or treat the PowerShell indexing error as an app failure.

A follow-up request to a literal IPv4 address (bypassing DNS and IPv6) also produced no TCP flow record; the 44-line capture showed only background DNS/UDP and no sing-box error. This makes DNS and IPv6 insufficient explanations; the remaining working hypothesis is Android TUN TCP-stack behavior. Diagnostic build 2.2.2 therefore switches the TUN stack from `system` to `gvisor`, lowers TUN MTU from 9000 to 1400, and logs `Libbox.version()` at startup. These changes are an experiment, not a confirmed root-cause fix until tested on the affected phone.
