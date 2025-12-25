package instructor.dao;

import login.DatabaseConfig;
import java.sql.*;
import java.util.*;

public class GradeDAO {
    public GradeDAO() {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        ensureTables();
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl()); }

    private void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS grades (section_id INTEGER, student_id TEXT, quiz REAL, midterm REAL, endsem REAL, final REAL, PRIMARY KEY(section_id, student_id))");
        } catch (Exception ignored) {}
    }

    public void saveScores(int sectionId, String studentId, double quiz, double midterm, double endsem, double finalGrade) {
        String sql = "INSERT OR REPLACE INTO grades (section_id, student_id, quiz, midterm, endsem, final) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, sectionId);
            p.setString(2, studentId);
            p.setDouble(3, quiz);
            p.setDouble(4, midterm);
            p.setDouble(5, endsem);
            p.setDouble(6, finalGrade);
            p.executeUpdate();
        } catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    public List<Map<String, Object>> getScoresForSection(int sectionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT student_id, quiz, midterm, endsem, final FROM grades WHERE section_id=?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, sectionId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("student_id", rs.getString("student_id"));
                    row.put("quiz", rs.getDouble("quiz"));
                    row.put("midterm", rs.getDouble("midterm"));
                    row.put("endsem", rs.getDouble("endsem"));
                    row.put("final", rs.getDouble("final"));
                    out.add(row);
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }
}
