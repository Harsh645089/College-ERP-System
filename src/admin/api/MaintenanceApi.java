package admin.api;

import admin.SystemState;

/**
 * Simple maintenance API wrapper used by UI and services. Provides a small, testable
 * boundary for checking or setting read-only (maintenance) state.
 */
public class MaintenanceApi {
    public static boolean isReadOnlyNow() {
        return SystemState.isMaintenance();
    }

    public static void setMaintenance(boolean v) {
        SystemState.setMaintenance(v);
    }
}
