# WatchTrust Clean

Clean rewrite of the WatchTrust prototype.

## Architecture

Phone (rooted):
- `dev.watchtrust/.WatchTrustAgent` is a real `TrustAgentService`.
- Phone APK is installed as a privileged system app through KernelSU.
- Only `PROVIDE_TRUST_AGENT` is whitelisted.
- No `persistent=true`, package-cache hacks, or direct-boot hacks.

Watch (no root):
- Uses `KeyguardManager.isDeviceLocked()`.
- Uses `TYPE_LOW_LATENCY_OFFBODY_DETECT` where available.
- Sends on-body + unlocked state to phone through Wearable Data Layer.
- Phone and watch use the same package name and signing key.

## Important KernelSU setting

A systemless privileged APK must remain visible in its own app mount namespace. Disable **Umount modules by default** temporarily, or create an App Profile for `dev.watchtrust` and set **Umount modules = OFF**, then reboot.

## Phase 1: phone only

Build `:phone`, install the generated KernelSU module, reboot, then verify:

```sh
su -c 'pm path dev.watchtrust'
su -c 'cmd package query-services --brief -a android.service.trust.TrustAgentService'
su -c 'dumpsys trust'
```

Expected:

```text
dev.watchtrust/.WatchTrustAgent
bound=1
connected=1
managingTrust=1
```

Open the WatchTrust launcher activity. Use `Manual GRANT + dismiss` only after the agent is connected.

## Phase 2: watch

Install `:wear` built in the same CI run and start WatchTrust on Xiaomi Watch 5.

## GitHub Actions

Workflow `.github/workflows/watchtrust-clean.yml` builds both APKs, verifies that they have the same signing certificate, creates a KernelSU module ZIP, and uploads all outputs as a workflow artifact.
