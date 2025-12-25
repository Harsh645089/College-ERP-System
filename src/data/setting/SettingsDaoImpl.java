package data.setting;

// Note: This class should ultimately implement the SettingsDao interface from your domain.java file.

public class SettingsDaoImpl {

    // --- TEMPORARY MOCK DATABASE STATE ---
    // Simulates the 'settings' table in the ERP DB
    private static final java.util.Map<String, String> settingsStore = new java.util.HashMap<>();

    static {
        // Initialize the critical maintenance flag
        settingsStore.put("maintenance_on", "false");
    }
    // --- END MOCK ---

    /** Simulates reading a value from the database based on a key. */
    public String getValue(String key) {
        if (!settingsStore.containsKey(key)) {
            return null;
        }
        return settingsStore.get(key);
    }

    /** Simulates updating a value in the database. */
    public void setValue(String key, String value) {
        settingsStore.put(key, value);
        // System.out.println("DB Mock: Set setting '" + key + "' to '" + value + "'"); // Optional log
    }
}