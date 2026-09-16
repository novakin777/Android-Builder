package android.service.trust;

/* Compile-time stub only. It is compileOnly and MUST NOT be packaged in the APK. */
public class TrustAgentService {
    public static final int FLAG_GRANT_TRUST_INITIATED_BY_USER = 1;
    public static final int FLAG_GRANT_TRUST_DISMISS_KEYGUARD = 2;
    public static final int FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE = 4;

    public void onCreate() {}
    public void onDestroy() {}
    public void onTrustTimeout() {}
    public void onDeviceLocked() {}
    public void onDeviceUnlocked() {}
    public void onUnlockAttempt(boolean successful) {}
    public void onUserMayRequestUnlock() {}
    public void onUserRequestedUnlock(boolean dismissKeyguard) {}

    public final void setManagingTrust(boolean managingTrust) {}
    public final void grantTrust(CharSequence message, long durationMs, int flags) {}
    public final void revokeTrust() {}
}
