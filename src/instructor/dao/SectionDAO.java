package instructor.dao;

import types.SectionRow;
import login.DatabaseConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SectionDAO {
    public SectionDAO() {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        ensureTables();
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl()); }

    private void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS sections (section_id INTEGER PRIMARY KEY, course_code TEXT, title TEXT, instructor_id TEXT, term TEXT, year INTEGER, day_time TEXT, room TEXT, capacity INTEGER, enrollment_open INTEGER DEFAULT 1)");
        } catch (Exception ignored) {}
        // ensure column exists on older DBs
        try (Connection c = conn(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("PRAGMA table_info('sections')")) {
                boolean hasEnroll = false;
                boolean hasSectionId = false;
                while (rs.next()) {
                    String name = rs.getString("name");
                    if ("enrollment_open".equalsIgnoreCase(name)) { hasEnroll = true; }
                    if ("section_id".equalsIgnoreCase(name)) { hasSectionId = true; }
                }
                if (!hasEnroll) {
                    try (Statement a = c.createStatement()) { a.execute("ALTER TABLE sections ADD COLUMN enrollment_open INTEGER DEFAULT 1"); }
                }
                // If the legacy DB uses `id` as PK instead of `section_id`, add `section_id` and backfill from `id`.
                if (!hasSectionId) {
                    try (Statement a = c.createStatement()) {
                        a.execute("ALTER TABLE sections ADD COLUMN section_id INTEGER");
                        a.execute("UPDATE sections SET section_id = id");
                    } catch (Exception ignoredInner) {}
                }
            }
        } catch (Exception ignored) {}
    }

    public List<SectionRow> getSectionsForInstructor(String instructorId, String term, int year) {
        List<SectionRow> out = new ArrayList<>();
        String sql = "SELECT section_id, course_code, title, day_time, room, capacity FROM sections WHERE instructor_id=? AND term=? AND year=?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, instructorId);
            p.setString(2, term);
            p.setInt(3, year);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.add(new SectionRow(
                        rs.getInt("section_id"),
                        rs.getString("course_code"),
                        rs.getString("title"),
                        rs.getInt("capacity"),
                        rs.getString("day_time"),
                        rs.getString("room"),
                        rs.getInt("capacity"),
                        ""
                    ));
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }

    public void updateCourseCapacity(int sectionId, int newCapacity) {
        String sql = "UPDATE sections SET capacity = ? WHERE section_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, newCapacity);
            p.setInt(2, sectionId);
            p.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean setEnrollmentOpen(int sectionId, boolean open) {
        String sql = "UPDATE sections SET enrollment_open = ? WHERE section_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, open ? 1 : 0);
            p.setInt(2, sectionId);
            int u = p.executeUpdate();
            return u > 0;
        } catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    public boolean isEnrollmentOpen(int sectionId) {
        String sql = "SELECT enrollment_open FROM sections WHERE section_id = ? LIMIT 1";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, sectionId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 1;
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return true;
    }

    // Add method to fetch all sections
    public List<SectionRow> getAllSections() {
        List<SectionRow> sections = new ArrayList<>();
        String sql = "SELECT section_id, course_code, title, day_time, room, capacity, instructor_id FROM sections";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    sections.add(new SectionRow(
                        rs.getInt("section_id"),
                        rs.getString("course_code"),
                        rs.getString("title"),
                        0, // Placeholder for credits
                        rs.getString("day_time"),
                        rs.getString("room"),
                        rs.getInt("capacity"),
                        rs.getString("instructor_id")
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to fetch sections", ex);
        }
        return sections;
    }

    // Update course schedule in the database
    public void updateCourseSchedule(int courseId, String newSchedule) throws SQLException {
        String query = "UPDATE courses SET schedule = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, newSchedule);
            pstmt.setInt(2, courseId);
            pstmt.executeUpdate();
        }
    }
}
