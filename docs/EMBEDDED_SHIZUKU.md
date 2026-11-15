# Embedded Shizuku broadcast bridge

All changes described here belong to `feat/embedded-shizuku-server`. The existing
`main` implementation is a comparison baseline, not a merge destination.

## What failed

The supplied device trace shows Wireless ADB reconnecting successfully, followed
by the server exiting with status 137 before a Binder was delivered. Status 137
indicates SIGKILL; that trace alone does **not** identify who sent it or prove an
out-of-memory condition. Android's default uncaught-exception handler can also
kill its own process.

Comparison with upstream Shizuku identified independent integration defects:

- The standard client `ShizukuProvider` does not implement the manager's
  `sendUserService` protocol. Receiving the server Binder alone is insufficient
  to receive the TetherService Binder.
- The copied manager attach logic tried to grant WRITE_SECURE_SETTINGS to an
  app that does not request that permission. That grant can throw before
  bindApplication completes. The embedded manager is now authorized directly
  by its verified package/UID; the unrelated permission grant was removed.
- The spawned ServiceStarter did not inherit `kproxyx.manager.package`.
  Debug builds therefore tried to contact the release package's provider.
- UserService referenced `UserHandleHidden` and `ContextHidden`, which are
  compile-only stubs. This module does not run the Refine transform that replaces
  those names. Reflection now invokes the corresponding real framework APIs.
- The launcher recorded a wrapper shell PID rather than the actual server PID.
  Repeated Start also restarted the server unnecessarily.

The launcher now explicitly exports CLASSPATH as the upstream native starter
does, keeps the ADB stream alive, and execs app_process with the real server PID.
A small bootstrap captures linkage/startup failures to stderr before the
ShizukuService class is resolved. It reports Java failures directly instead of
leaving only a generic Binder timeout.

## Runtime path

1. The user pairs/connects through the app's existing Wireless ADB page.
2. The broadcast controller starts EmbeddedServerMain as Android UID 2000.
3. The server delivers its Binder to the app's protected
   `${applicationId}.embedded-shizuku` provider.
4. TetherClient requests the same TetherService used by the working main branch.
5. ServiceStarter creates that service and hands its Binder to the manager
   provider, which calls `Shizuku.attachUserService` as the app's identity.
6. TetherClient starts/stops the existing tethering implementation over Binder.

The VPN routing decision is unchanged: NEVER with this app's VpnService active,
AUTO otherwise, and USB tethering remains NEVER. No broadcast datapath, backend,
database, or proxy protocol was replaced.

The embedded provider authority, process name, PID, config and log files are
package-specific. They do not use the external Shizuku manager's authority or
configuration. PID-based cleanup verifies the process name before signalling.
No root privileges or global hidden-API policy changes are required.

## Verification

- `EmbeddedServerCommandTest`: JVM regression tests for startup, CLASSPATH,
  debug/release isolation, safe PID handling, and shell input validation.
- `EmbeddedServerIntegrationTest`: starts the actual server using a shell-UID
  command, verifies delivery of a shell-UID Binder, and starts a real UserService
  through the manager provider. No external Shizuku APK is installed.
- `Embedded Shizuku device tests`: runs that instrumentation test on Android 11
  (API 30) and Android 15 (API 35) emulators.
- The existing Verify and Build workflow runs app lint, unit tests and APK
  assembly separately.

The isolated bridge test does not activate a hotspot, exercise the Go/native
datapath, or prove OEM-specific Wi-Fi/VPN behavior. Those require a physical
supported arm64 device.

## Physical-device acceptance

Use a build of this branch, without installing external Shizuku:

1. Pair/connect from the ADB page, then start broadcast.
2. Confirm the hotspot becomes active and a second device can actually pass
   traffic through it.
3. Repeat with the app's VPN active and inactive.
4. Leave/reopen the broadcast page; its live status should remain correct.
5. Stop, then Start again; verify traffic works again and no stale service is
   reused.
6. Disconnect Wireless ADB while broadcasting and exercise Stop.

If startup fails, copy the broadcast terminal log. It now includes the
bootstrap/server stderr and a separate UserService log, with package-specific
process diagnostics. Do not diagnose every exit 137 as an OEM kill or increase
the Binder timeout without examining those logs.
