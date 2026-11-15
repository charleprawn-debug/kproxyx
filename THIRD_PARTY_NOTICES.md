# Third-party notices

## Embedded Shizuku-compatible server

The `android/shizuku-server` module contains modified source from the Shizuku and Shizuku-API projects, including the server, server-shared, starter, common, and UserService implementation used to provide the Binder transport for KProxyX.

Shizuku is distributed under the Apache License, Version 2.0. The copied and modified source is used under the same license. The upstream license text is available at <https://www.apache.org/licenses/LICENSE-2.0> and the upstream project is available at <https://github.com/RikkaApps/Shizuku>.

Copyright RikkaApps contributors. KProxyX-specific modifications are identified in the integration layer and package configuration.

The embedded server is launched as a separate `app_process` under the Android `shell` UID through Wireless ADB. The normal KProxyX application process does not claim shell privileges.
