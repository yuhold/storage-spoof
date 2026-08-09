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
import java.util.List;
import java.util.Locale;

public final class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
    public interface OnAppClickListener {
        void onAppClick(AppEntry app);
    }

    private final Context context;
    private final boolean customUi;
    private final OnAppClickListener listener;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();

    private SharedPreferences preferences;
    private String query = "";

    public AppAdapter(Context context, boolean customUi, OnAppClickListener listener) {
        this.context = context;
        this.customUi = customUi;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setApps(List<AppEntry> apps) {
        allApps.clear();
        allApps.addAll(apps);
        applyFilter();
    }

    public void setPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
        notifyDataSetChanged();
    }

    public void filter(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    public boolean isEmpty() {
        return visibleApps.isEmpty();
    }

    private void applyFilter() {
        visibleApps.clear();
        for (AppEntry app : allApps) {
            if (query.isEmpty()
                    || app.label().toLowerCase(Locale.ROOT).contains(query)
                    || app.packageName().toLowerCase(Locale.ROOT).contains(query)) {
                visibleApps.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return visibleApps.get(position).packageName().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = customUi ? R.layout.item_app_custom : R.layout.item_app;
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppEntry app = visibleApps.get(position);
        holder.icon.setImageDrawable(app.icon());
        holder.label.setText(app.label());
        holder.packageName.setText(app.packageName());
        holder.summary.setText(buildSummary(app.packageName()));
        holder.itemView.setOnClickListener(view -> listener.onAppClick(app));
    }

    @Override
    public int getItemCount() {
        return visibleApps.size();
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
            return context.getString(R.string.configured) + " · " + context.getString(R.string.enable_spoof) + "：关";
        }
        return context.getString(R.string.app_size) + " " + Formatter.formatFileSize(context, profile.getAppBytes())
                + " · " + context.getString(R.string.data_size) + " " + Formatter.formatFileSize(context, profile.getDataBytes())
                + " · " + context.getString(R.string.cache_size) + " " + Formatter.formatFileSize(context, profile.getCacheBytes());
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final TextView summary;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.app_label);
            packageName = itemView.findViewById(R.id.package_name);
            summary = itemView.findViewById(R.id.profile_summary);
        }
    }
}
