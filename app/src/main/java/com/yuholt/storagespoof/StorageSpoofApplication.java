package com.yuholt.storagespoof;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.yuholt.storagespoof.ui.AppearancePreferences;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class StorageSpoofApplication extends Application
        implements XposedServiceHelper.OnServiceListener {
    private static final Set<ServiceStateListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        applyThemeMode(new AppearancePreferences(this).getThemeMode());
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    public static void applyThemeMode(String mode) {
        int nightMode = switch (mode) {
            case AppearancePreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case AppearancePreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    @Nullable
    public static XposedService getService() {
        return service;
    }

    public static void addServiceStateListener(
            @NonNull ServiceStateListener listener,
            boolean notifyImmediately) {
        LISTENERS.add(listener);
        if (notifyImmediately) {
            listener.onServiceStateChanged(service);
        }
    }

    public static void removeServiceStateListener(@NonNull ServiceStateListener listener) {
        LISTENERS.remove(listener);
    }

    @Override
    public void onServiceBind(@NonNull XposedService boundService) {
        service = boundService;
        notifyListeners(boundService);
    }

    @Override
    public void onServiceDied(@NonNull XposedService deadService) {
        if (service == deadService) {
            service = null;
        }
        notifyListeners(null);
    }

    private static void notifyListeners(@Nullable XposedService currentService) {
        for (ServiceStateListener listener : LISTENERS) {
            listener.onServiceStateChanged(currentService);
        }
    }

    public interface ServiceStateListener {
        void onServiceStateChanged(@Nullable XposedService service);
    }
}
