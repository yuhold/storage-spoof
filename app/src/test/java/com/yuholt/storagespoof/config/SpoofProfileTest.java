package com.yuholt.storagespoof.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SpoofProfileTest {
    @Test
    public void storesConfiguredValues() {
        SpoofProfile profile = new SpoofProfile("com.example.app", false, 10L, 20L, 30L);

        assertEquals("com.example.app", profile.getPackageName());
        assertFalse(profile.isEnabled());
        assertEquals(10L, profile.getAppBytes());
        assertEquals(20L, profile.getDataBytes());
        assertEquals(30L, profile.getCacheBytes());
    }

    @Test
    public void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpoofProfile("com.example.app", true, -1L, 0L, 0L));
    }
}
