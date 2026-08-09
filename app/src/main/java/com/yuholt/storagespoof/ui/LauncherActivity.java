package com.yuholt.storagespoof.ui;

import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.yuholt.storagespoof.R;
import com.yuholt.storagespoof.StorageSpoofApplication;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.service.XposedService;

public final class LauncherActivity extends AppCompatActivity
        implements StorageSpoofApplication.ServiceStateListener {
    public static final String EXTRA_FORCE_SHOW = "force_show";

    private static final String PREFERENCES_NAME = "onboarding";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";

    private AppearancePreferences appearancePreferences;
    private MaterialCheckBox disclaimerAccepted;
    private TextView readinessSummary;
    private TextView readinessDetails;
    private MaterialButton usageAccessButton;
    private MaterialButton continueButton;
    private List<String> currentIssues = List.of();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appearancePreferences = new AppearancePreferences(this);
        applyColors();
        super.onCreate(savedInstanceState);
        SharedPreferences onboarding = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        migrateOnboardingState(onboarding);
        if (!getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false)
                && onboarding.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            openConfigurationAndFinish();
            return;
        }
        boolean customUi = AppearancePreferences.UI_CUSTOM.equals(
                appearancePreferences.getUiMode());
        setContentView(customUi
                ? R.layout.activity_launcher_custom
                : R.layout.activity_launcher);
        applyBackgroundImage();

        disclaimerAccepted = findViewById(R.id.disclaimer_accepted);
        readinessSummary = findViewById(R.id.readiness_summary);
        readinessDetails = findViewById(R.id.readiness_details);
        usageAccessButton = findViewById(R.id.usage_access_button);
        continueButton = findViewById(R.id.continue_button);
        disclaimerAccepted.setChecked(onboarding.getBoolean(KEY_DISCLAIMER_ACCEPTED, false));
        disclaimerAccepted.setOnCheckedChangeListener((button, checked) ->
                onboarding.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, checked).apply());

        findViewById(R.id.about_button).setOnClickListener(view -> openAbout());
        usageAccessButton.setOnClickListener(view -> openUsageAccessSettings());
        findViewById(R.id.recheck_button).setOnClickListener(view -> runReadinessCheck(
                StorageSpoofApplication.getService()));
        continueButton.setOnClickListener(view -> continueToConfiguration());
        runReadinessCheck(StorageSpoofApplication.getService());
    }

    @Override
    protected void onStart() {
        super.onStart();
        StorageSpoofApplication.addServiceStateListener(this, true);
    }

    @Override
    protected void onStop() {
        StorageSpoofApplication.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    public void onServiceStateChanged(@Nullable XposedService service) {
        runOnUiThread(() -> runReadinessCheck(service));
    }

    private void applyColors() {
        if (AppearancePreferences.COLOR_MONET.equals(appearancePreferences.getColorMode())) {
            DynamicColors.applyToActivityIfAvailable(this);
            return;
        }
        DynamicColors.applyToActivityIfAvailable(
                this,
                new DynamicColorsOptions.Builder()
                        .setContentBasedSource(appearancePreferences.getSeedColor())
                        .build());
    }

    private void applyBackgroundImage() {
        String value = appearancePreferences.getBackgroundUri();
        if (value.isBlank()) {
            return;
        }
        ImageView background = findViewById(R.id.background_image);
        View scrim = findViewById(R.id.background_scrim);
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(
                    getContentResolver(),
                    Uri.parse(value));
            Drawable drawable = ImageDecoder.decodeDrawable(source);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setAdjustViewBounds(false);
            background.setImageDrawable(drawable);
            background.setVisibility(View.VISIBLE);
            scrim.setVisibility(View.VISIBLE);
            int brightness = appearancePreferences.getBackgroundBrightness();
            float requestedAlpha = 1.0f
                    - Math.max(0, Math.min(100, brightness)) / 100.0f;
            scrim.setAlpha(Math.max(0.18f, requestedAlpha));
        } catch (Exception exception) {
            appearancePreferences.setBackgroundUri("");
            background.setImageDrawable(null);
            background.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            Toast.makeText(
                    this,
                    R.string.background_load_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void runReadinessCheck(@Nullable XposedService service) {
        List<String> issues = new ArrayList<>();
        List<String> status = new ArrayList<>();
        List<String> installedTargets = getInstalledTargets();

        if (hasUsageAccess()) {
            status.add(getString(R.string.readiness_usage_access_ok));
        } else {
            issues.add(getString(R.string.readiness_usage_access_missing));
        }

        if (installedTargets.isEmpty()) {
            issues.add(getString(R.string.readiness_no_target));
        }
        if (service == null) {
            issues.add(getString(R.string.readiness_service_missing));
        } else {
            status.add(getString(
                    R.string.readiness_service_ok,
                    service.getFrameworkName(),
                    service.getApiVersion()));
            if (service.getApiVersion() < XposedService.API_102) {
                issues.add(getString(R.string.readiness_api_old, service.getApiVersion()));
            }
            List<String> scope = service.getScope();
            for (String target : installedTargets) {
                if (scope.contains(target)) {
                    status.add(getString(R.string.readiness_scope_ok, getTargetLabel(target)));
                } else {
                    issues.add(getString(
                            R.string.readiness_scope_missing,
                            getTargetLabel(target)));
                }
            }
        }

        currentIssues = List.copyOf(issues);
        readinessSummary.setText(issues.isEmpty()
                ? getString(R.string.readiness_ready)
                : getString(R.string.readiness_issues, issues.size()));
        List<String> details = new ArrayList<>(status);
        details.addAll(issues);
        readinessDetails.setText(String.join("\n", details));
        readinessDetails.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
        usageAccessButton.setVisibility(hasUsageAccess() ? View.GONE : View.VISIBLE);
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = getSystemService(AppOpsManager.class);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void openUsageAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException exception) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private List<String> getInstalledTargets() {
        List<String> targets = new ArrayList<>();
        PackageManager packageManager = getPackageManager();
        if (isPackageInstalled(packageManager, SETTINGS_PACKAGE)) {
            targets.add(SETTINGS_PACKAGE);
        }
        if (isPackageInstalled(packageManager, SECURITY_CENTER_PACKAGE)) {
            targets.add(SECURITY_CENTER_PACKAGE);
        }
        return targets;
    }

    private static boolean isPackageInstalled(PackageManager packageManager, String packageName) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private String getTargetLabel(String packageName) {
        return switch (packageName) {
            case SETTINGS_PACKAGE -> "系统设置（" + packageName + "）";
            case SECURITY_CENTER_PACKAGE -> "小米安全中心（" + packageName + "）";
            default -> packageName;
        };
    }

    private void continueToConfiguration() {
        if (!disclaimerAccepted.isChecked()) {
            Toast.makeText(this, R.string.accept_disclaimer_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentIssues.isEmpty()) {
            completeOnboardingAndOpen();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.continue_warning_title)
                .setMessage(getString(
                        R.string.continue_warning_message,
                        String.join("\n", currentIssues)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.continue_anyway,
                        (dialog, which) -> completeOnboardingAndOpen())
                .show();
    }

    private static void migrateOnboardingState(SharedPreferences onboarding) {
        if (!onboarding.contains(KEY_ONBOARDING_COMPLETED)
                && onboarding.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) {
            onboarding.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
        }
    }

    private void completeOnboardingAndOpen() {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DISCLAIMER_ACCEPTED, true)
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .apply();
        openConfigurationAndFinish();
    }

    private void openConfigurationAndFinish() {
        openConfiguration();
        finish();
    }

    private void openConfiguration() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void openAbout() {
        startActivity(new Intent(this, AboutActivity.class));
    }
}
