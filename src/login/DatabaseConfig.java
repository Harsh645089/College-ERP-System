package login;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Centralized database configuration for portability.
 * Ensures the database path works regardless of where the application is run from.
 * The database file (erp.db) will be located relative to the project root directory.
 */
public class DatabaseConfig {
    private static final String DB_FILENAME = "erp.db";
    private static String cachedDbPath = null;
    
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}
    }
    
    /**
     * Gets the database path. The path is determined relative to the project root.
     * If running from a JAR, it uses the directory containing the JAR.
     * If running from classes, it finds the project root by looking for the erp.db file
     * or defaults to the current working directory.
     * 
     * @return The database file path as a string (relative or absolute)
     */
    public static String getDatabasePath() {
        if (cachedDbPath != null) {
            return cachedDbPath;
        }
        
        // Strategy 1: Check if erp.db exists in current working directory
        File currentDirDb = new File(DB_FILENAME);
        if (currentDirDb.exists() && currentDirDb.isFile()) {
            cachedDbPath = DB_FILENAME;
            return cachedDbPath;
        }
        
        // Strategy 2: Try to find project root by looking for erp.db in parent directories
        // (for cases where app is run from a subdirectory)
        File currentDir = new File(System.getProperty("user.dir"));
        File checkDir = currentDir;
        int maxDepth = 5; // Prevent infinite loops
        int depth = 0;
        
        while (checkDir != null && depth < maxDepth) {
            File potentialDb = new File(checkDir, DB_FILENAME);
            if (potentialDb.exists() && potentialDb.isFile()) {
                cachedDbPath = potentialDb.getAbsolutePath();
                return cachedDbPath;
            }
            
            // Also check if we're in ERP subdirectory and parent has the db
            if ("ERP".equals(checkDir.getName())) {
                File parentDb = new File(checkDir.getParent(), DB_FILENAME);
                if (parentDb.exists() && parentDb.isFile()) {
                    cachedDbPath = parentDb.getAbsolutePath();
                    return cachedDbPath;
                }
            }
            
            checkDir = checkDir.getParentFile();
            depth++;
        }
        
        // Strategy 3: Try to find the database relative to the class location
        try {
            // Get the location of DatabaseConfig.class
            File classFile = new File(DatabaseConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            if (classFile.isFile()) {
                // Running from JAR - look for erp.db in the same directory as the JAR
                File jarDir = classFile.getParentFile();
                File jarDirDb = new File(jarDir, DB_FILENAME);
                if (jarDirDb.exists()) {
                    cachedDbPath = jarDirDb.getAbsolutePath();
                    return cachedDbPath;
                }
            } else {
                // Running from classes - look in project root
                // Navigate from classes directory to project root
                File classesDir = classFile;
                for (int i = 0; i < 5; i++) {
                    File potentialDb = new File(classesDir, DB_FILENAME);
                    if (potentialDb.exists()) {
                        cachedDbPath = potentialDb.getAbsolutePath();
                        return cachedDbPath;
                    }
                    // Also check parent directories
                    File parentDb = new File(classesDir.getParent(), DB_FILENAME);
                    if (parentDb.exists()) {
                        cachedDbPath = parentDb.getAbsolutePath();
                        return cachedDbPath;
                    }
                    classesDir = classesDir.getParentFile();
                    if (classesDir == null) break;
                }
            }
        } catch (URISyntaxException | SecurityException e) {
            // Fall through to default
        }
        
        // Strategy 4: Default to current working directory (SQLite will create if doesn't exist)
        cachedDbPath = DB_FILENAME;
        return cachedDbPath;
    }
    
    /**
     * Gets the JDBC connection URL for the database.
     * @return JDBC URL string
     */
    public static String getDatabaseUrl() {
        return "jdbc:sqlite:" + getDatabasePath();
    }
    
    /**
     * Ensures the database file exists (creates empty file if it doesn't exist).
     * This helps verify the path is writable.
     */
    public static void ensureDatabaseExists() {
        try {
            String dbPath = getDatabasePath();
            File dbFile = new File(dbPath);
            
            // If using relative path, ensure parent directory exists
            if (!dbFile.isAbsolute()) {
                // For relative paths, SQLite will create in current directory
                return;
            }
            
            // For absolute paths, ensure parent directory exists
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // SQLite will create the database file on first connection, so we don't need to create it here
        } catch (Exception e) {
            System.err.println("Warning: Could not ensure database directory exists: " + e.getMessage());
        }
    }
    
    /**
     * Resets the cached database path. Useful for testing or if database location changes.
     */
    public static void resetCache() {
        cachedDbPath = null;
    }
}

