# ROM signing keys (rea 6.8.0 `inspect-apk-signature`, 2026-11-08)

| APK (package) | Signer SHA-256 | Subject |
|---|---|---|
| A20CarkitBackcarService (com.nwd.backcar) | `715eb4d74f69a426a5cc8e57c6d8b528cc4967d0b3502005aaaf23aaeb507590` | C=US, ST=California, L=Mountain View, O=Android, OU=Android, CN=Android, android@android.com |
| 8925_Carkit_KernelService (com.nwd.kernel) | same `715eb4d7…` | same |
| OneKeyClear (com.nwd.android.onekeyclear) | same `715eb4d7…` | same |
| CanAllInOne (com.nwd.can.setting) | `726921646a974b90006ece4d64d2a6b857179de02620f9d203963a273a0c5296` | C=US, O=Android, CN=Android Debug |

Compared with AOSP marshmallow-release public keys (`build/target/product/security/*.x509.pem`):
platform `c8a2e9bc…`, shared `28bbfe4a…`, media `465983f7…`, testkey `a40da80a…` → **no match**.

**Conclusion:** the vendor key only mimics the AOSP subject; it is private. Third-party APKs cannot be signed as
`android.uid.system` / platform. Privileged actions must go through root ADB (uid 0) instead.
To confirm next session: `dumpsys package android | grep -A3 signatures` (is `715eb4d7…` the platform key?).
`CanAllInOne` is signed with an "Android Debug" key — check whether it is a known public debug key before relying on it.
All ROM APKs carry v1+v2+v3+v3.1 blocks per rea.
