package com.yuholt.storagespoof.ui;

import java.util.Comparator;
import java.util.Locale;

final class AppListPolicy {
    static final String FILTER_ALL = "all";
    static final String FILTER_CONFIGURED = "configured";
    static final String FILTER_UNCONFIGURED = "unconfigured";

    static final String SORT_DEFAULT = "default";
    static final String SORT_NAME = "name";

    private AppListPolicy() {
    }

    static boolean isVisible(
            Candidate app,
            String query,
            boolean showSystemApps,
            String filterMode) {
        if (app.systemApp() && !showSystemApps) {
            return false;
        }
        if (FILTER_CONFIGURED.equals(filterMode) && !app.configured()) {
            return false;
        }
        if (FILTER_UNCONFIGURED.equals(filterMode) && app.configured()) {
            return false;
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return normalizedQuery.isEmpty()
                || app.label().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || app.packageName().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    static Comparator<Candidate> comparator(String sortMode) {
        Comparator<Candidate> configuredFirst =
                Comparator.comparing(Candidate::configured).reversed();
        if (!SORT_NAME.equals(sortMode)) {
            return configuredFirst;
        }
        Comparator<Candidate> alphabetical = Comparator
                .comparing(Candidate::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Candidate::packageName, String.CASE_INSENSITIVE_ORDER);
        return configuredFirst.thenComparing(alphabetical);
    }

    record Candidate(
            String label,
            String packageName,
            boolean systemApp,
            boolean configured) {
    }
}
