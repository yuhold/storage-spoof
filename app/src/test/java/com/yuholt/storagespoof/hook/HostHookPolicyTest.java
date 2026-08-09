package com.yuholt.storagespoof.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostHookPolicyTest {
    @Test
    public void settingsEnablesOnlyPackageStatsSpoofing() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.android.settings");

        assertTrue(HostHookPolicy.isSupportedPackage("com.android.settings"));
        assertTrue(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
        assertTrue(policy.hasEnabledHooks());
    }

    @Test
    public void securityCenterDisablesAllStorageHooks() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.miui.securitycenter");

        assertTrue(HostHookPolicy.isSupportedPackage("com.miui.securitycenter"));
        assertFalse(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
        assertFalse(policy.hasEnabledHooks());
    }

    @Test
    public void unknownPackageFailsClosed() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.example.unknown");

        assertFalse(HostHookPolicy.isSupportedPackage("com.example.unknown"));
        assertFalse(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
        assertFalse(policy.hasEnabledHooks());
    }

    @Test
    public void processSuffixIsNotTreatedAsPackageIdentity() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.android.settings:remote");

        assertFalse(HostHookPolicy.isSupportedPackage("com.android.settings:remote"));
        assertFalse(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
    }

    @Test
    public void noCurrentPolicyEnablesHyperOsSummarySpoofing() {
        String[] packages = {
                "com.android.settings",
                "com.miui.securitycenter",
                "com.example.unknown"
        };

        for (String packageName : packages) {
            assertFalse(HostHookPolicy.forPackage(packageName).hyperOsSummarySpoofing());
        }
    }
}
