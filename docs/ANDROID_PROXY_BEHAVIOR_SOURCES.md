# Android proxy-restoration references

The advanced-mode disconnect change uses Android's explicit direct-proxy state only when the saved pre-session HTTP proxy was empty. These references were checked while implementing the change:

- [AOSP `ProxyTracker.java`](https://android.googlesource.com/platform/frameworks/base/+/c9f553c7ed3eb217b0f1bcd9c02d8864af027eab/services/core/java/com/android/server/connectivity/ProxyTracker.java) documents that empty `ProxyInfo` values are canonicalized to `null`, that `sendProxyBroadcast()` broadcasts the current proxy (using an empty direct proxy if none is set), and that applying a global proxy writes the host/port settings and sends the proxy-change broadcast.
- [Android `android.net.Proxy` API](https://developer.android.com/reference/android/net/Proxy) documents `PROXY_CHANGE_ACTION` as the notification for apps that cache a system proxy, and recommends querying the current default proxy after that change.

The implementation writes `http_proxy=:0` as the Android shell-level direct-proxy sentinel, clears/restores the separate host and port values, verifies that no proxy remains, and only then stops the local forwarding service. Real-device checks remain necessary because OEM builds and app/browser proxy caching behavior can differ.
