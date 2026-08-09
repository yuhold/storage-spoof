package com.yuholt.storagespoof.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yuholt.storagespoof.R;
import com.yuholt.storagespoof.config.ProfileStore;
import com.yuholt.storagespoof.config.SpoofProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnAppClickListener {
        void onAppClick(AppEntry app);
    }

    private static final int TYPE_APP = 0;
    private static final int TYPE_HEADER = 1;

    private final Context context;
    private final boolean customUi;
    private final OnAppClickListener listener;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();
    private final List<ListRow> rows = new ArrayList<>();

    private SharedPreferences preferences;
    private Set<String> configuredPackages = Collections.emptySet();
    private String query = "";
    private boolean showSystemApps;
    private String filterMode = AppListPolicy.FILTER_ALL;
    private String sortMode = AppListPolicy.SORT_DEFAULT;

    public AppAdapter(Context context, boolean customUi, OnAppClickListener listener) {
        this.context = context;
        this.customUi = customUi;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setApps(List<AppEntry> apps) {
        allApps.clear();
        allApps.addAll(apps);
        refresh();
    }

    public void setPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
        configuredPackages = preferences == null
                ? Collections.emptySet()
                : new HashSet<>(preferences.getStringSet(
                        ProfileStore.KEY_PACKAGES,
                        Collections.emptySet()));
        refresh();
    }

    public void refreshProfiles() {
        setPreferences(preferences);
    }

    public void filter(String value) {
        query = value == null ? "" : value;
        refresh();
    }

    public void setShowSystemApps(boolean showSystemApps) {
        this.showSystemApps = showSystemApps;
        refresh();
    }

    public void setFilterMode(String filterMode) {
        this.filterMode = filterMode;
        refresh();
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
        refresh();
    }

    public boolean isEmpty() {
        return visibleApps.isEmpty();
    }

    public int getVisibleCount() {
        return visibleApps.size();
    }

    public int getConfiguredCount() {
        int count = 0;
        for (AppEntry app : visibleApps) {
            if (configuredPackages.contains(app.packageName())) {
                count++;
            }
        }
        return count;
    }

    private void refresh() {
        visibleApps.clear();
        for (AppEntry app : allApps) {
            AppListPolicy.Candidate candidate = candidate(app);
            if (AppListPolicy.isVisible(candidate, query, showSystemApps, filterMode)) {
                visibleApps.add(app);
            }
        }
        visibleApps.sort((left, right) -> AppListPolicy.comparator(sortMode)
                .compare(candidate(left), candidate(right)));
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        rows.clear();
        boolean configuredHeaderAdded = false;
        boolean realHeaderAdded = false;
        for (AppEntry app : visibleApps) {
            boolean configured = configuredPackages.contains(app.packageName());
            if (configured && !configuredHeaderAdded) {
                rows.add(new HeaderRow(
                        context.getString(R.string.section_modified),
                        R.string.section_modified));
                configuredHeaderAdded = true;
            } else if (!configured && !realHeaderAdded) {
                rows.add(new HeaderRow(
                        context.getString(R.string.section_unmodified),
                        R.string.section_unmodified));
                realHeaderAdded = true;
            }
            rows.add(new AppRow(app));
        }
    }

    private AppListPolicy.Candidate candidate(AppEntry app) {
        return new AppListPolicy.Candidate(
                app.label(),
                app.packageName(),
                app.systemApp(),
                configuredPackages.contains(app.packageName()));
    }

    @Override
    public long getItemId(int position) {
        ListRow row = rows.get(position);
        if (row instanceof AppRow appRow) {
            return appRow.app().packageName().hashCode();
        }
        return Long.MIN_VALUE + ((HeaderRow) row).stableId();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof HeaderRow ? TYPE_HEADER : TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(
                    R.layout.item_section_header,
                    parent,
                    false));
        }
        int layout = customUi ? R.layout.item_app_custom : R.layout.item_app;
        return new AppViewHolder(inflater.inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListRow row = rows.get(position);
        if (holder instanceof HeaderViewHolder headerHolder
                && row instanceof HeaderRow headerRow) {
            headerHolder.title.setText(headerRow.title());
            return;
        }
        AppEntry app = ((AppRow) row).app();
        AppViewHolder appHolder = (AppViewHolder) holder;
        boolean configured = configuredPackages.contains(app.packageName());
        appHolder.icon.setImageDrawable(app.icon());
        appHolder.label.setText(app.label());
        appHolder.packageName.setText(app.packageName());
        appHolder.summary.setText(buildSummary(app.packageName()));
        appHolder.status.setText(configured ? R.string.configured : R.string.real_data);
        appHolder.status.setSelected(configured);
        appHolder.systemBadge.setVisibility(app.systemApp() ? View.VISIBLE : View.GONE);
        appHolder.itemView.setOnClickListener(view -> listener.onAppClick(app));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private CharSequence buildSummary(String packageName) {
        if (preferences == null) {
            return context.getString(R.string.not_configured);
        }
        SpoofProfile profile = ProfileStore.get(preferences, packageName);
        if (profile == null) {
            return context.getString(R.string.not_configured);
        }
        if (!profile.isEnabled()) {
            return context.getString(R.string.spoof_disabled);
        }
        return context.getString(R.string.app_size) + " "
                + Formatter.formatFileSize(context, profile.getAppBytes())
                + " · " + context.getString(R.string.data_size) + " "
                + Formatter.formatFileSize(context, profile.getDataBytes())
                + " · " + context.getString(R.string.cache_size) + " "
                + Formatter.formatFileSize(context, profile.getCacheBytes());
    }

    private interface ListRow {
    }

    private record AppRow(AppEntry app) implements ListRow {
    }

    private record HeaderRow(String title, int stableId) implements ListRow {
    }

    static final class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final TextView summary;
        final TextView status;
        final TextView systemBadge;

        AppViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.app_label);
            packageName = itemView.findViewById(R.id.package_name);
            summary = itemView.findViewById(R.id.profile_summary);
            status = itemView.findViewById(R.id.configuration_status);
            systemBadge = itemView.findViewById(R.id.system_badge);
        }
    }

    static final class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.section_title);
        }
    }
}
