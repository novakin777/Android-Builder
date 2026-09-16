# State machine

`WATCH_ELIGIBLE = onBody && watchUnlocked && heartbeatAge <= 12s`

Phone trust is revoked when the watch is off-body, locked, or the heartbeat becomes stale.

When eligible, the agent grants short `TEMPORARY_AND_RENEWABLE` trust. `DISMISS_KEYGUARD` is only added in response to Android's `onUserRequestedUnlock()` callback or the explicit local test button.

Bluetooth proximity by itself is never treated as authentication.
