package api.setting;

import data.setting.SettingsDaoImpl; // Import the DAO dependency

public class SettingsApiImpl implements SettingsApi {

    private final SettingsDaoImpl settingsDao = new SettingsDaoImpl();

    private static final String MAINTENANCE_KEY = "maintenance_on";

    @Override
    public boolean isMaintenanceOn() {
        String value = settingsDao.getValue(MAINTENANCE_KEY);
        return "true".equalsIgnoreCase(value);
    }

    @Override
    public boolean toggleMaintenanceMode() throws Exception {
        try {
            boolean currentStatus = isMaintenanceOn();
            boolean newStatus = !currentStatus;

            // Persist the change via the DAO
            settingsDao.setValue(MAINTENANCE_KEY, String.valueOf(newStatus));

            return newStatus;

        } catch (Exception e) {
            throw new Exception("Database error while toggling Maintenance Mode.", e);
        }
    }
}