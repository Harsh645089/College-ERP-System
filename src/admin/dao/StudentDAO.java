package admin.dao;

import domain.Student;
import login.DatabaseConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed StudentDAO using JDBC.
 * Uses centralized DatabaseConfig for portable database path resolution.
 */
public class StudentDAO {
    public StudentDAO() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
        }
        // Ensure students table exists
        try {
            ensureStudentsTableExists();
            ensureExtendedColumns();
        } catch (Exception ex) {
            System.err.println("Warning: could not ensure student table: " + ex.getMessage());
        }
    }

    private void ensureStudentsTableExists() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
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
        }
    }

    private void ensureExtendedColumns() throws SQLException, java.io.IOException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            // check if students table exists
            String checkSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='students'";
            try (ResultSet rs = s.executeQuery(checkSql)) {
                if (!rs.next()) return; // no students table, nothing to do
            }

            // collect existing columns
            java.util.Set<String> cols = new java.util.HashSet<>();
            try (ResultSet rs = s.executeQuery("PRAGMA table_info(students)")) {
                while (rs.next()) cols.add(rs.getString("name"));
            }

            java.util.List<String> toAdd = new java.util.ArrayList<>();
            if (!cols.contains("degree")) toAdd.add("degree TEXT");
            if (!cols.contains("branch")) toAdd.add("branch TEXT");
            if (!cols.contains("year_of_study")) toAdd.add("year_of_study TEXT");
            if (!cols.contains("admission_year")) toAdd.add("admission_year TEXT");

            if (toAdd.isEmpty()) return; // already migrated

            // backup DB file before altering
            String dbPathStr = DatabaseConfig.getDatabasePath();
            java.nio.file.Path dbPath = java.nio.file.Paths.get(dbPathStr);
            if (java.nio.file.Files.exists(dbPath)) {
                java.nio.file.Path bak = java.nio.file.Paths.get(dbPathStr + ".bak");
                try {
                    java.nio.file.Files.copy(dbPath, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.err.println("Backup created: " + bak.toString());
                } catch (Exception ex) {
                    System.err.println("Warning: failed to create DB backup: " + ex.getMessage());
                }
            }

            // perform ALTER TABLE for each missing column
            for (String colDef : toAdd) {
                String alter = "ALTER TABLE students ADD COLUMN " + colDef;
                try {
                    s.execute(alter);
                    System.err.println("Applied migration: " + alter);
                } catch (SQLException ex) {
                    System.err.println("Failed to apply migration (" + alter + "): " + ex.getMessage());
                }
            }
        }
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
    }

    public List<Student> listAll() {
        List<Student> out = new ArrayList<>();
        String extended = "SELECT id, name, email, section, status, degree, branch, year_of_study, admission_year FROM students ORDER BY id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(extended); ResultSet rs = p.executeQuery()) {
            while (rs.next()) out.add(mapRow(rs));
            return out;
        } catch (SQLException ex) {
            // Fallback to legacy schema
            String legacy = "SELECT id, name, email, section, status FROM students ORDER BY id";
            try (Connection c = conn(); PreparedStatement p = c.prepareStatement(legacy); ResultSet rs = p.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            } catch (SQLException ex2) {
                throw new RuntimeException(ex2);
            }
        }
        return out;
    }

    public Optional<Student> findById(String id) {
        String extended = "SELECT id, name, email, section, status, degree, branch, year_of_study, admission_year FROM students WHERE id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(extended)) {
            p.setString(1, id);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException ex) {
            // fallback to legacy
            String legacy = "SELECT id, name, email, section, status FROM students WHERE id = ?";
            try (Connection c = conn(); PreparedStatement p = c.prepareStatement(legacy)) {
                p.setString(1, id);
                try (ResultSet rs = p.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            } catch (SQLException ex2) {
                throw new RuntimeException(ex2);
            }
        }
    }

    public boolean create(Student s) {
        String sql = "INSERT INTO students (id, name, email, section, status, degree, branch, year_of_study, admission_year) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, s.getId());
            p.setString(2, s.getName());
            p.setString(3, s.getEmail());
            p.setString(4, s.getSection());
            p.setString(5, s.getStatus());
            p.setString(6, s.getDegree());
            p.setString(7, s.getBranch());
            p.setString(8, s.getYearOfStudy());
            p.setString(9, s.getAdmissionYear());
            int rows = p.executeUpdate();
            return rows == 1;
        } catch (SQLException ex) {
            // If the DB schema doesn't have new columns, fall back to legacy insert
            try {
                String legacy = "INSERT INTO students (id, name, email, section, status) VALUES (?, ?, ?, ?, ?)";
                try (Connection c2 = conn(); PreparedStatement p2 = c2.prepareStatement(legacy)) {
                    p2.setString(1, s.getId());
                    p2.setString(2, s.getName());
                    p2.setString(3, s.getEmail());
                    p2.setString(4, s.getSection());
                    p2.setString(5, s.getStatus());
                    int rows = p2.executeUpdate();
                    return rows == 1;
                }
            } catch (SQLException ex2) {
                System.err.println("create() failed: " + ex.getMessage() + " | fallback: " + ex2.getMessage());
                return false;
            }
        }
    }

    public boolean update(Student s) {
        String sql = "UPDATE students SET name = ?, email = ?, section = ?, status = ?, degree = ?, branch = ?, year_of_study = ?, admission_year = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, s.getName());
            p.setString(2, s.getEmail());
            p.setString(3, s.getSection());
            p.setString(4, s.getStatus());
            p.setString(5, s.getDegree());
            p.setString(6, s.getBranch());
            p.setString(7, s.getYearOfStudy());
            p.setString(8, s.getAdmissionYear());
            p.setString(9, s.getId());
            int rows = p.executeUpdate();
            return rows == 1;
        } catch (SQLException ex) {
            // Fallback for older schema without new columns
            try {
                String legacy = "UPDATE students SET name = ?, email = ?, section = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                try (Connection c2 = conn(); PreparedStatement p2 = c2.prepareStatement(legacy)) {
                    p2.setString(1, s.getName());
                    p2.setString(2, s.getEmail());
                    p2.setString(3, s.getSection());
                    p2.setString(4, s.getStatus());
                    p2.setString(5, s.getId());
                    int rows = p2.executeUpdate();
                    return rows == 1;
                }
            } catch (SQLException ex2) {
                throw new RuntimeException(ex);
            }
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM students WHERE id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, id);
            int rows = p.executeUpdate();
            return rows == 1;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<Student> search(String q) {
        List<Student> out = new ArrayList<>();
        String like = "%" + q.toLowerCase() + "%";
        String extended = "SELECT id, name, email, section, status, degree, branch, year_of_study, admission_year FROM students WHERE LOWER(id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(section) LIKE ? ORDER BY id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(extended)) {
            p.setString(1, like);
            p.setString(2, like);
            p.setString(3, like);
            p.setString(4, like);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException ex) {
            // fallback to legacy
            String legacy = "SELECT id, name, email, section, status FROM students WHERE LOWER(id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(section) LIKE ? ORDER BY id";
            try (Connection c = conn(); PreparedStatement p = c.prepareStatement(legacy)) {
                p.setString(1, like);
                p.setString(2, like);
                p.setString(3, like);
                p.setString(4, like);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) out.add(mapRow(rs));
                }
            } catch (SQLException ex2) {
                throw new RuntimeException(ex2);
            }
        }
        return out;
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getString("id"));
        s.setName(rs.getString("name"));
        s.setEmail(rs.getString("email"));
        s.setSection(rs.getString("section"));
        s.setStatus(rs.getString("status"));
        try {
            s.setDegree(rs.getString("degree"));
        } catch (SQLException ignore) {}
        try {
            s.setBranch(rs.getString("branch"));
        } catch (SQLException ignore) {}
        try {
            s.setYearOfStudy(rs.getString("year_of_study"));
        } catch (SQLException ignore) {}
        try {
            s.setAdmissionYear(rs.getString("admission_year"));
        } catch (SQLException ignore) {}
        return s;
    }
}
