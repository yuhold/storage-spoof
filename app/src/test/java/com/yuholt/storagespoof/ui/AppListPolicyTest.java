package com.yuholt.storagespoof.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class AppListPolicyTest {
    @Test
    public void defaultSortOnlyGroupsConfiguredAppsFirst() {
        List<AppListPolicy.Candidate> apps = new ArrayList<>(List.of(
                app("Zulu", "zulu", true),
                app("Beta", "beta", true),
                app("Alpha", "alpha", false)));

        apps.sort(AppListPolicy.comparator(AppListPolicy.SORT_DEFAULT));

        assertTrue(apps.get(0).packageName().equals("zulu"));
        assertTrue(apps.get(1).packageName().equals("beta"));
        assertTrue(apps.get(2).packageName().equals("alpha"));
    }

    @Test
    public void nameSortKeepsConfiguredGroupFirstAndSortsWithinGroups() {
        List<AppListPolicy.Candidate> apps = new ArrayList<>(List.of(
                app("Zulu", "zulu", false),
                app("Charlie", "charlie", true),
                app("Alpha", "alpha", false),
                app("Beta", "beta", true)));

        apps.sort(AppListPolicy.comparator(AppListPolicy.SORT_NAME));

        assertTrue(apps.get(0).packageName().equals("beta"));
        assertTrue(apps.get(1).packageName().equals("charlie"));
        assertTrue(apps.get(2).packageName().equals("alpha"));
        assertTrue(apps.get(3).packageName().equals("zulu"));
    }

    @Test
    public void filtersCombineWithSystemVisibilityAndSearch() {
        AppListPolicy.Candidate configuredSystem = new AppListPolicy.Candidate(
                "System Tool", "android.system", true, true);
        assertFalse(AppListPolicy.isVisible(
                configuredSystem, "", false, AppListPolicy.FILTER_ALL));
        assertTrue(AppListPolicy.isVisible(
                configuredSystem, "system", true, AppListPolicy.FILTER_CONFIGURED));
        assertFalse(AppListPolicy.isVisible(
                configuredSystem, "missing", true, AppListPolicy.FILTER_CONFIGURED));
        assertFalse(AppListPolicy.isVisible(
                configuredSystem, "", true, AppListPolicy.FILTER_UNCONFIGURED));
    }

    private static AppListPolicy.Candidate app(
            String label,
            String packageName,
            boolean configured) {
        return new AppListPolicy.Candidate(label, packageName, false, configured);
    }
}
