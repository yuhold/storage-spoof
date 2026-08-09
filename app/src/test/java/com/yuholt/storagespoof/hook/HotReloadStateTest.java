package com.yuholt.storagespoof.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HotReloadStateTest {
    @Test
    public void acceptsSettingsPackage() {
        HotReloadState state = HotReloadState.restore("com.android.settings");
        assertTrue(state.isEnabled());
        assertEquals("com.android.settings", state.packageName());
    }

    @Test
    public void acceptsSecurityCenterIdentityButDisablesHooks() {
        HotReloadState state = HotReloadState.restore("com.miui.securitycenter");
        assertFalse(state.isEnabled());
        assertEquals("com.miui.securitycenter", state.packageName());
    }

    @Test
    public void rejectsNullState() {
        HotReloadState state = HotReloadState.restore(null);
        assertFalse(state.isEnabled());
        assertNull(state.packageName());
    }

    @Test
    public void rejectsWrongStateType() {
        HotReloadState state = HotReloadState.restore(Integer.valueOf(1));
        assertFalse(state.isEnabled());
        assertNull(state.packageName());
    }

    @Test
    public void rejectsUnknownAndProcessSuffixedNames() {
        assertFalse(HotReloadState.restore("com.example.app").isEnabled());
        assertFalse(HotReloadState.restore("com.android.settings:remote").isEnabled());
    }
}
