package login;

import java.sql.*;
import java.io.*;
import java.nio.file.*;

/**
 * Centralized DB migration handler.
 * Ensures all schema migrations run on app startup, before any DAO or service queries execute.
 */
public class DBMigration {
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("ERROR: SQLite JDBC driver not found. Please ensure sqlite-jdbc-3.45.1.0.jar is in the lib/ folder.");
            e.printStackTrace();
        }
    }

    public static void ensureSchemaUpToDate() {
        // Ensure database file location is set up before migrations
        DatabaseConfig.ensureDatabaseExists();
        
        // Create core tables first
        createCoreTables();
        
        // Run SQL migration files from db_migrations folder
        runSqlMigrations();
        
        // Run programmatic migrations
        migrateSectionsTable();
        migrateEnrollmentsTable();
        migrateOfferingsTable();
        migrateSettingsTable();
    }

    /**
     * Create all core database tables if they don't exist.
     */
    private static void createCoreTables() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            // Users table
            s.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT, " +
                    "role TEXT NOT NULL, " +
                    "email TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // Students table
            s.execute("CREATE TABLE IF NOT EXISTS students (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "email TEXT, " +
                    "section TEXT, " +
                    "status TEXT, " +
                    "degree TEXT, " +
                    "branch TEXT, " +
                    "year_of_study TEXT, " +
                    "admission_year TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // Instructors table
            s.execute("CREATE TABLE IF NOT EXISTS instructors (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "email TEXT, " +
                    "department TEXT, " +
                    "status TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // Instructor courses mapping
            s.execute("CREATE TABLE IF NOT EXISTS instructor_courses (" +
                    "instructor_id TEXT, " +
                    "course_code TEXT, " +
                    "assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY(instructor_id, course_code))");
            
            System.out.println("Core database tables created/verified.");
        } catch (SQLException e) {
            System.err.println("Error creating core tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Execute SQL migration files from db_migrations folder.
     */
    private static void runSqlMigrations() {
        File migrationsDir = new File("db_migrations");
        if (!migrationsDir.exists() || !migrationsDir.isDirectory()) {
            System.out.println("No db_migrations directory found, skipping SQL migrations.");
            return;
        }
        
        File[] migrationFiles = migrationsDir.listFiles((dir, name) -> name.endsWith(".sql"));
        if (migrationFiles == null || migrationFiles.length == 0) {
            System.out.println("No SQL migration files found in db_migrations folder.");
            return;
        }
        
        // Sort files by name to ensure execution order
        java.util.Arrays.sort(migrationFiles, (a, b) -> a.getName().compareTo(b.getName()));
        
        for (File migrationFile : migrationFiles) {
            try {
                System.out.println("Running migration: " + migrationFile.getName());
                executeSqlFile(migrationFile);
                System.out.println("Migration completed: " + migrationFile.getName());
            } catch (Exception e) {
                System.err.println("Error running migration " + migrationFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Execute SQL statements from a file.
     */
    private static void executeSqlFile(File sqlFile) throws IOException, SQLException {
        String sql = new String(Files.readAllBytes(sqlFile.toPath()));
        
        // Split by semicolon, but be careful with semicolons inside strings
        // Simple approach: split and execute each statement
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            // Remove comments and split by semicolon
            String[] statements = sql.split(";");
            for (String statement : statements) {
                statement = statement.trim();
                // Skip empty statements and comments
                if (statement.isEmpty() || statement.startsWith("--")) {
                    continue;
                }
                // Skip transaction control statements (SQLite handles them differently)
                if (statement.toUpperCase().startsWith("BEGIN TRANSACTION") || 
                    statement.toUpperCase().startsWith("COMMIT")) {
                    continue;
                }
                try {
                    s.execute(statement);
                } catch (SQLException e) {
                    // Some statements might fail if tables/columns already exist, which is okay
                    if (!e.getMessage().contains("already exists") && 
                        !e.getMessage().contains("duplicate column")) {
                        System.err.println("Warning executing statement: " + e.getMessage());
                        System.err.println("Statement: " + statement.substring(0, Math.min(100, statement.length())));
                    }
                }
            }
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
    }

    /**
     * Ensure sections table has proper columns for the application.
     * The database may have an old schema with (id, course_id, section_name, semester).
     * We need (section_id, course_code, title, term, day_time, room, capacity, instructor_id).
     */
    private static void migrateSectionsTable() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            // Check if sections table exists
            try (ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='sections'")) {
                if (!rs.next()) {
                    // Table doesn't exist, create new schema
                    s.execute("CREATE TABLE IF NOT EXISTS sections (section_id INTEGER PRIMARY KEY, course_code TEXT, title TEXT, instructor_id TEXT, term TEXT, year INTEGER, day_time TEXT, room TEXT, capacity INTEGER, enrollment_open INTEGER DEFAULT 1)");
                    return;
                }
            }
        } catch (Exception ignored) {}

        // Table exists; check if it has the old schema and migrate if needed
        boolean needsMigration = false;
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("PRAGMA table_info('sections')")) {
                boolean hasCourseId = false;
                boolean hasSectionName = false;
                boolean hasId = false;
                boolean hasCourseCode = false;

                while (rs.next()) {
                    String name = rs.getString("name");
                    if ("course_id".equalsIgnoreCase(name)) hasCourseId = true;
                    if ("section_name".equalsIgnoreCase(name)) hasSectionName = true;
                    if ("id".equalsIgnoreCase(name)) hasId = true;
                    if ("course_code".equalsIgnoreCase(name)) hasCourseCode = true;
                }

                // If old schema detected (has course_id, section_name, id but NOT course_code), migrate the table
                needsMigration = hasCourseId && hasSectionName && hasId && !hasCourseCode;
            }
        } catch (Exception ignored) {}

        if (needsMigration) {
            try (Connection c = getConnection(); Statement s = c.createStatement()) {
                // Rename old table
                s.execute("ALTER TABLE sections RENAME TO sections_old");
                // Create new table with correct schema
                s.execute("CREATE TABLE sections (section_id INTEGER PRIMARY KEY, course_code TEXT, title TEXT, instructor_id TEXT, term TEXT, year INTEGER, day_time TEXT, room TEXT, capacity INTEGER, enrollment_open INTEGER DEFAULT 1)");
                // Migrate data: map old columns to new ones
                s.execute("INSERT INTO sections (section_id, course_code, title, instructor_id, term, day_time, room, capacity, enrollment_open) " +
                        "SELECT id, COALESCE((SELECT code FROM courses WHERE id = sections_old.course_id), 'UNKNOWN'), " +
                        "COALESCE(section_name, 'Section'), instructor_id, COALESCE(semester, 'Fall'), '', '', 60, 1 " +
                        "FROM sections_old");
                // Drop old table
                s.execute("DROP TABLE sections_old");
                System.out.println("Migrated sections table from old schema to new schema");
            } catch (Exception migrationEx) {
                System.err.println("Error during sections table migration: " + migrationEx.getMessage());
                migrationEx.printStackTrace();
            }
        }

        // Ensure all required columns exist on the current schema
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("PRAGMA table_info('sections')")) {
                boolean hasCourseCode = false;
                boolean hasDayTime = false;
                boolean hasTitle = false;
                boolean hasTerm = false;
                boolean hasCapacity = false;
                boolean hasRoom = false;

                while (rs.next()) {
                    String name = rs.getString("name");
                    if ("course_code".equalsIgnoreCase(name)) hasCourseCode = true;
                    if ("day_time".equalsIgnoreCase(name)) hasDayTime = true;
                    if ("title".equalsIgnoreCase(name)) hasTitle = true;
                    if ("term".equalsIgnoreCase(name)) hasTerm = true;
                    if ("capacity".equalsIgnoreCase(name)) hasCapacity = true;
                    if ("room".equalsIgnoreCase(name)) hasRoom = true;
                }

                if (!hasCourseCode) {
                    try (Statement add = c.createStatement()) { add.execute("ALTER TABLE sections ADD COLUMN course_code TEXT"); } catch (Exception ignoredInner) {}
                }
                if (!hasDayTime) {
                    try (Statement add = c.createStatement()) { add.execute("ALTER TABLE sections ADD COLUMN day_time TEXT"); } catch (Exception ignoredInner) {}
                }
                if (!hasTitle) {
                    try (Statement add = c.createStatement()) { add.execute("ALTER TABLE sections ADD COLUMN title TEXT"); } catch (Exception ignoredInner) {}
                }
                if (!hasTerm) {
                    try (Statement add = c.createStatement()) { add.execute("ALTER TABLE sections ADD COLUMN term TEXT"); } catch (Exception ignoredInner) {}
                }
                if (!hasCapacity) {
                    try (Statement add = c.createStatement()) { add.execute("ALTER TABLE sections ADD COLUMN capacity INTEGER"); } catch (Exception ignoredInner) {}
                }
                if (!hasRoom) {
                    try (Statement add = c.createStatement()) { add.execute("ALTER TABLE sections ADD COLUMN room TEXT"); } catch (Exception ignoredInner) {}
                }
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    /**
     * Ensure enrollments table exists with proper schema.
     */
    private static void migrateEnrollmentsTable() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS enrollments (enrollment_id INTEGER PRIMARY KEY AUTOINCREMENT, student_id TEXT, section_id INTEGER, status TEXT, UNIQUE(student_id, section_id))");
        } catch (Exception ignored) {}
    }

    /**
     * Create offerings table used to publish cohort-level course offerings
     * without creating student enrollments.
     */
    private static void migrateOfferingsTable() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS offerings (id INTEGER PRIMARY KEY AUTOINCREMENT, course_code TEXT NOT NULL, branch TEXT NOT NULL, year INTEGER NOT NULL, offered_at TEXT DEFAULT (datetime('now')), created_by TEXT, UNIQUE(course_code, branch, year))");
        } catch (Exception ignored) {}
    }

    /**
     * Ensure a simple key-value `settings` table exists. Used for small app settings
     * like `last_offered_at` to notify student dashboards of updates.
     */
    private static void migrateSettingsTable() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS settings (key_name TEXT PRIMARY KEY, value TEXT)");
        } catch (Exception ignored) {}
    }
}
