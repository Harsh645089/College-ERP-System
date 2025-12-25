package instructor.dao;

import login.DatabaseConfig;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class GradingSchemeDAO {
    public GradingSchemeDAO() {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        ensureTables();
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl()); }

    private void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS grading_scheme (section_id INTEGER, component TEXT, percentage INTEGER, PRIMARY KEY(section_id, component))");
        } catch (Exception ignored) {}
    }

    public void saveGradingScheme(int sectionId, Map<String, Integer> scheme) {
        String del = "DELETE FROM grading_scheme WHERE section_id = ?";
        String ins = "INSERT INTO grading_scheme(section_id, component, percentage) VALUES (?, ?, ?)";
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try (PreparedStatement pd = c.prepareStatement(del)) {
                pd.setInt(1, sectionId); pd.executeUpdate();
            }
            try (PreparedStatement pi = c.prepareStatement(ins)) {
                for (Map.Entry<String, Integer> e : scheme.entrySet()) {
                    pi.setInt(1, sectionId);
                    pi.setString(2, e.getKey());
                    pi.setInt(3, e.getValue());
                    pi.addBatch();
                }
                pi.executeBatch();
            }
            c.commit();
        } catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    public Map<String, Integer> loadGradingScheme(int sectionId) {
        Map<String, Integer> out = new HashMap<>();
        String sql = "SELECT component, percentage FROM grading_scheme WHERE section_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, sectionId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("component"), rs.getInt("percentage"));
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }
}
