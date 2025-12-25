package instructor.dao;

import login.DatabaseConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssessmentDAO {
    public AssessmentDAO() {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        ensureTables();
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl()); }

    private void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS assessments (section_id INTEGER, student_id TEXT, assessment_type TEXT, score REAL, recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(section_id, student_id, assessment_type, recorded_at))");
        } catch (Exception ignored) {}
    }

    public void saveAssessment(int sectionId, String studentId, String type, double score) {
        // Try UPDATE first, then INSERT if not found
        String updateSql = "UPDATE assessments SET score = ? WHERE section_id = ? AND student_id = ? AND assessment_type = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(updateSql)) {
            p.setDouble(1, score);
            p.setInt(2, sectionId);
            p.setString(3, studentId);
            p.setString(4, type);
            int updated = p.executeUpdate();
            if (updated == 0) {
                // Record doesn't exist, insert it
                String insertSql = "INSERT INTO assessments (section_id, student_id, assessment_type, score) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertP = c.prepareStatement(insertSql)) {
                    insertP.setInt(1, sectionId);
                    insertP.setString(2, studentId);
                    insertP.setString(3, type);
                    insertP.setDouble(4, score);
                    insertP.executeUpdate();
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    // Return list of assessment rows for a section
    public List<Map<String, Object>> getAssessmentsForSection(int sectionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT student_id, assessment_type, score FROM assessments WHERE section_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, sectionId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("student_id", rs.getString("student_id"));
                    r.put("assessment_type", rs.getString("assessment_type"));
                    r.put("score", rs.getDouble("score"));
                    out.add(r);
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }

    // Average score for a student in a section for a given assessment type
    public double getStudentAverageForType(int sectionId, String studentId, String type) {
        String sql = "SELECT AVG(score) FROM assessments WHERE section_id = ? AND student_id = ? AND assessment_type = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, sectionId);
            p.setString(2, studentId);
            p.setString(3, type);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return 0.0;
    }
}
