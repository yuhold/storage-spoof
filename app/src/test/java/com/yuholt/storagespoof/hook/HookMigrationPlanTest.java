package com.yuholt.storagespoof.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class HookMigrationPlanTest {
    @Test
    public void settingsReplacesMatchingHooks() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("uuid"),
                        HookMigrationPlan.desired("string")),
                List.of(HookMigrationPlan.existing("old-uuid", "uuid"),
                        HookMigrationPlan.existing("old-string", "string")));

        assertEquals(2, actions.size());
        assertTrue(actions.stream().allMatch(action ->
                action.kind() == HookMigrationPlan.ActionKind.REPLACE));
    }

    @Test
    public void missingDesiredHookIsInstalled() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("uuid"),
                        HookMigrationPlan.desired("string")),
                List.of(HookMigrationPlan.existing("old-uuid", "uuid")));

        assertEquals(2, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.REPLACE, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.INSTALL, actions.get(1).kind());
    }

    @Test
    public void duplicateAndObsoleteHooksAreUnhooked() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("uuid")),
                List.of(HookMigrationPlan.existing("first", "uuid"),
                        HookMigrationPlan.existing("duplicate", "uuid"),
                        HookMigrationPlan.existing("obsolete", "old")));

        assertEquals(3, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.REPLACE, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(1).kind());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(2).kind());
    }

    @Test
    public void disabledHostsOnlyUnhookAndDisable() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.miui.securitycenter",
                List.of(HookMigrationPlan.desired("uuid")),
                List.of(HookMigrationPlan.existing("old", "uuid")));

        assertEquals(2, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.DISABLE, actions.get(1).kind());
    }

    @Test
    public void unknownHostsOnlyUnhookAndDisable() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                null,
                List.of(HookMigrationPlan.desired("uuid")),
                List.of(HookMigrationPlan.existing("old", "uuid")));

        assertEquals(2, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.DISABLE, actions.get(1).kind());
    }

    @Test
    public void unhookFailurePreventsFailClosedClaim() {
        HookMigrationPlan.CleanupResult result = HookMigrationPlan.cleanup(
                List.of(
                        new HookMigrationPlan.CleanupAttempt("old-one", true),
                        new HookMigrationPlan.CleanupAttempt("old-two", false)));

        assertFalse(result.isComplete());
        assertEquals(List.of("old-two"), result.failedIds());
    }
    @Test
    public void storageExecutableIsCleanupCandidateWithoutReliableId() {
        assertTrue(HookMigrationPlan.isModuleOwnedStorageHandle(null, true));
        assertTrue(HookMigrationPlan.isModuleOwnedStorageHandle(null, false, false));
        assertTrue(HookMigrationPlan.isModuleOwnedStorageHandle(null, true, false));
        assertTrue(HookMigrationPlan.isModuleOwnedStorageHandle("third-party-id", true));
        assertTrue(HookMigrationPlan.isModuleOwnedStorageHandle(
                "storage-spoof-hyperos-summary", false));
        assertFalse(HookMigrationPlan.isModuleOwnedStorageHandle("third-party-id", false));
    }

    @Test
    public void installationCountIncludesSuccessAfterFailure() {
        int successful = HookMigrationPlan.countSuccessfulInstallations(
                List.of(
                        new HookMigrationPlan.InstallationAttempt("first", false),
                        new HookMigrationPlan.InstallationAttempt("second", true)));

        assertEquals(1, successful);
    }
    @Test
    public void actionOrderingIsDeterministic() {
        List<HookMigrationPlan.Action> first = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("b"), HookMigrationPlan.desired("a")),
                List.of(HookMigrationPlan.existing("old-b", "b"),
                        HookMigrationPlan.existing("old-a", "a")));
        List<HookMigrationPlan.Action> second = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("b"), HookMigrationPlan.desired("a")),
                List.of(HookMigrationPlan.existing("old-b", "b"),
                        HookMigrationPlan.existing("old-a", "a")));

        assertEquals(first, second);
    }
}
