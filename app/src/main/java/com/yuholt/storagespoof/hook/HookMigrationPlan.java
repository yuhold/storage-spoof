package com.yuholt.storagespoof.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class HookMigrationPlan {
    private static final Comparator<DesiredHook> DESIRED_ORDER =
            Comparator.comparing(DesiredHook::executableKey);
    private static final Comparator<ExistingHook> EXISTING_ORDER =
            Comparator.comparing(ExistingHook::executableKey)
                    .thenComparing(ExistingHook::id);

    private HookMigrationPlan() {
    }

    public record DesiredHook(String executableKey) {
        public DesiredHook {
            Objects.requireNonNull(executableKey);
        }
    }

    public record ExistingHook(String id, String executableKey) {
        public ExistingHook {
            Objects.requireNonNull(id);
            Objects.requireNonNull(executableKey);
        }
    }

    public enum ActionKind {
        REPLACE,
        INSTALL,
        UNHOOK,
        DISABLE
    }

    public record Action(ActionKind kind, ExistingHook existing, DesiredHook desired) {
        public Action {
            Objects.requireNonNull(kind);
        }
    }

    public static DesiredHook desired(String executableKey) {
        return new DesiredHook(executableKey);
    }

    public static ExistingHook existing(String id, String executableKey) {
        return new ExistingHook(id, executableKey);
    }

    public static List<Action> plan(
            String packageName,
            List<DesiredHook> desired,
            List<ExistingHook> existing) {
        List<DesiredHook> sortedDesired = new ArrayList<>(desired);
        sortedDesired.sort(DESIRED_ORDER);
        List<ExistingHook> sortedExisting = new ArrayList<>(existing);
        sortedExisting.sort(EXISTING_ORDER);

        if (!HostHookPolicy.forPackage(packageName).hasEnabledHooks()) {
            List<Action> actions = new ArrayList<>();
            for (ExistingHook oldHook : sortedExisting) {
                actions.add(new Action(ActionKind.UNHOOK, oldHook, null));
            }
            actions.add(new Action(ActionKind.DISABLE, null, null));
            return List.copyOf(actions);
        }

        List<Action> actions = new ArrayList<>();
        List<Action> installs = new ArrayList<>();
        List<ExistingHook> unusedExisting = new ArrayList<>(sortedExisting);
        for (DesiredHook desiredHook : sortedDesired) {
            ExistingHook matchingHook = takeFirstMatchingHook(
                    unusedExisting, desiredHook.executableKey());
            if (matchingHook == null) {
                installs.add(new Action(ActionKind.INSTALL, null, desiredHook));
            } else {
                actions.add(new Action(ActionKind.REPLACE, matchingHook, desiredHook));
            }
        }
        actions.addAll(installs);
        for (ExistingHook oldHook : unusedExisting) {
            actions.add(new Action(ActionKind.UNHOOK, oldHook, null));
        }
        return List.copyOf(actions);
    }

    private static ExistingHook takeFirstMatchingHook(
            List<ExistingHook> hooks, String executableKey) {
        for (int index = 0; index < hooks.size(); index++) {
            ExistingHook hook = hooks.get(index);
            if (hook.executableKey().equals(executableKey)) {
                hooks.remove(index);
                return hook;
            }
        }
        return null;
    }
}
