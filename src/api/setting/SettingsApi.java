package api.setting;

public interface SettingsApi {
    boolean isMaintenanceOn();
    boolean toggleMaintenanceMode() throws Exception;
}