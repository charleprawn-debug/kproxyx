# Advanced-mode high protection

The **حماية عالية** control appears only under **الاتصال → متقدم**.
Its corner indicator is green when an executable `su` is discoverable, and red
otherwise. Detection never runs `su`; enabling the option explicitly requests
root with `su -c 'id -u'`. Root managers that hide `su` can prevent detection.
The preference means “protect my next advanced connection”, not “firewall active”.
It is locked while connecting/connected; stop the connection before changing it.
VPN mode and the independent location service do not enable this policy.

## Network policy

Before declaring the local proxy ready, its service installs an application-owned
`KPX_WEBRTC_BLOCK` filter chain at the start of IPv4 **and** IPv6 OUTPUT.
Both families are required; inability to configure either fails the connection
and triggers rollback, rather than claiming that protection is active.

- Loopback remains available for the local proxy.
- This application's UID may make upstream TCP connections on arbitrary proxy ports.
- Only Android's root/system/DNS resolver UIDs (0, 1000, 1051) may use external DNS.
- Root/system DHCP is allowed for IPv4 and IPv6 network renewal.
- Other external UDP **and TCP** are rejected, including established connections.
- The local sing-box inbound also rejects UDP and uses a TCP-only upstream.

Blocking a short list of STUN ports is insufficient: STUN/TURN may use arbitrary
ports and TURN can use TCP/TLS. Blocking direct TCP outside the proxy avoids that
bypass. Apps ignoring the system HTTP proxy will consequently lose connectivity;
calls, games, QUIC and other UDP features may stop working. This is intentional,
and the UI warns about it before selection.

This is a **network leak-prevention policy**, not a device-wide switch for browser
WebRTC APIs. It cannot hide locally generated host/mDNS ICE candidates, disable
JavaScript APIs, or distinguish TURN-over-HTTPS from other traffic intentionally
relayed through the proxy. It does not protect tethered clients (FORWARD),
privileged software able to change firewall rules, or code sharing this app's UID.
The system resolver still uses normal device DNS for upstream hostname resolution.

## Lifecycle and recovery

Root operations run off Android's main thread, with bounded waits/output.
A synchronous private-preference journal is written **before** installing rules.
The service cleans up on startup failure, normal stop, native service stop and
`onDestroy`; the screen also explicitly restores rules when stopping the advanced
session. Cleanup deletes only this app's chain and its OUTPUT jumps, preserving
unrelated firewall rules. The journal is cleared only after cleanup is verified.
Cleanup failures are shown and remain retryable; no silent unprotected fallback.

Android does not guarantee `onDestroy` after a crash, force-stop or process kill.
In that situation the firewall can remain until reopening the connection screen
and granting root for recovery, or rebooting (which clears kernel rules).
The recovery button is available even without ADB. Root revocation while active
requires granting root again, or rebooting; do not claim instant cleanup after
force-stop. No permanent boot scripts or global firewall flushes are installed.

## Verification

`RootWebRtcRulesTest` covers policy structure and input constraints.
`python3 android/tools/test_root_webrtc_firewall.py` compiles the actual pure Kotlin
rule builder and exercises the generated shell against an isolated firewall model
(requires JDK, `kotlinc`, Python 3; never modifies the host firewall).

Before release, test a rooted **physical Android device** with IPv4 and IPv6:
grant/deny/timeout root; verify indicator and mode visibility; test HTTP and SOCKS5
upstreams (including ports 443/3478); test direct STUN and TURN over arbitrary
UDP/TCP/TLS ports; verify proxy-routed public addresses, existing TCP connections,
normal browsing, DNS and network renewal; stop and verify that both managed chains
are gone and previous connectivity is restored. Also test process kill/reopen,
revoked root, failure in the second address family and unrelated firewall rules.
Desktop model/unit/build checks cannot establish device-level leak-test results.
