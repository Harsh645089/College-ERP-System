package admin;

/** In-memory global system state (maintenance flag). Replace with DB-backed setting later. */
public class SystemState {
    private static volatile boolean maintenance = false;

    public static boolean isMaintenance() { return maintenance; }
    public static void setMaintenance(boolean v) { maintenance = v; }
}
