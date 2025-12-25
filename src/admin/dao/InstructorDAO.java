package admin.dao;

import domain.Instructor;
import login.DatabaseConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InstructorDAO {
    public InstructorDAO() {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        try { ensureTables(); seedDefaultInstructors(); } catch (Exception ex) { System.err.println("InstructorDAO init failed: " + ex.getMessage()); }
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl()); }

    private void ensureTables() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS instructors (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT, department TEXT, status TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS instructor_courses (instructor_id TEXT, course_code TEXT, assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(instructor_id, course_code))");
        }
    }

    public List<Instructor> listAll() {
        List<Instructor> out = new ArrayList<>();
        String sql = "SELECT id,name,email,department,status FROM instructors ORDER BY id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                Instructor i = new Instructor();
                i.setId(rs.getString("id"));
                i.setName(rs.getString("name"));
                i.setEmail(rs.getString("email"));
                i.setDepartment(rs.getString("department"));
                i.setStatus(rs.getString("status"));
                out.add(i);
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }

    private void seedDefaultInstructors() {
        try {
            if (listAll().size() > 0) return; // already seeded
        } catch (Exception ignored) {}

        java.util.List<Instructor> seeds = new java.util.ArrayList<>();
        seeds.add(new Instructor("INS001","A V Subramanyam","av.subramanyam@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS002","Aasim Khan","aasim.khan@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS003","Abhijit Mitra","abhijit.mitra@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS004","Angshul Majumdar","angshul.majumdar@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS005","Anmol Srivastava","anmol.srivastava@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS006","Anubha Gupta","anubha.gupta@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS007","Anuj Grover","anuj.grover@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS008","Anuradha Sharma","anuradha.sharma@iiitd.ac.in","MATHEMATICS","Active"));
        seeds.add(new Instructor("INS009","Arani Bhattacharya","arani.bhattacharya@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS010","Arjun Ray","arjun.ray@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS011","Arun Balaji Buduru","arun.buduru@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS012","Ashish Kumar Pandey","ashish.pandey@iiitd.ac.in","MATHEMATICS","Active"));
        seeds.add(new Instructor("INS013","Bapi Chatterjee","bapi.chatterjee@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS014","Chanak Prasad Vilas","chanak.prasad@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS015","Debajyoti Bera","debajyoti.bera@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS016","Debarka Sengupta","debarka.sengupta@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS017","Debidas Kundu","debidas.kundu@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS018","Debika Banerjee","debika.banerjee@iiitd.ac.in","MATHEMATICS","Active"));
        seeds.add(new Instructor("INS019","Deepak Prince","deepak.prince@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS020","Diptapriyo Majumdar","diptapriyo.maj@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS021","G P S Raghava","gps.raghava@iiitd.ac.in","COMPUTATIONAL BIOLOGY","Active"));
        seeds.add(new Instructor("INS022","Ganesh Bagler","ganesh.bagler@iiitd.ac.in","COMPUTATIONAL BIOLOGY","Active"));
        seeds.add(new Instructor("INS023","Gaurav Ahuja","gaurav.ahuja@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS024","Gaurav Arora","gaurav.arora@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS025","Gautam Shroff","gautam.shroff@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS026","Gayatri Nair","gayatri.nair@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS027","J V Meenakshi","jv.meenakshi@iiitd.ac.in","HUMAN CENTRED DESIGN","Active"));
        seeds.add(new Instructor("INS028","Jaiendra Shukla","jaiendra.shukla@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS029","Jaspreet Kaur Dhanjal","jaspreet.dhanjal@iiitd.ac.in","HUMAN CENTRED DESIGN","Active"));
        seeds.add(new Instructor("INS030","Kalpana Shankhwar","kalpana.shankhwar@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS031","Kaushik Kalyanaraman","kaushik.k@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS032","Kirti Kanjilal","kirti.kanjilal@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS033","Manohar Kumar","manohar.kumar@iiitd.ac.in","ECE","Active"));
        seeds.add(new Instructor("INS034","Manuj Mukherjee","manuj.mukherjee@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS035","Md Shad Akhtar","md.shad@iiitd.ac.in","COMPUTER SCIENCE","Active"));
        seeds.add(new Instructor("INS036","Monika Arora","monika.arora@iiitd.ac.in","MATHEMATICS","Active"));

        for (Instructor ins : seeds) {
            try { create(ins); } catch (Exception ex) { System.err.println("seed create failed: " + ex.getMessage()); }
        }
    }

    public boolean create(Instructor i) {
        String sql = "INSERT INTO instructors (id,name,email,department,status) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, i.getId()); p.setString(2, i.getName()); p.setString(3, i.getEmail()); p.setString(4, i.getDepartment()); p.setString(5, i.getStatus());
            int r = p.executeUpdate(); return r == 1;
        } catch (SQLException ex) { System.err.println("create instructor failed: " + ex.getMessage()); return false; }
    }

    public boolean update(Instructor i) {
        String sql = "UPDATE instructors SET name = ?, email = ?, department = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, i.getName()); p.setString(2, i.getEmail()); p.setString(3, i.getDepartment()); p.setString(4, i.getStatus()); p.setString(5, i.getId());
            return p.executeUpdate() == 1;
        } catch (SQLException ex) { System.err.println("update instructor failed: " + ex.getMessage()); return false; }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM instructors WHERE id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) { p.setString(1, id); return p.executeUpdate() == 1; }
        catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    public List<Instructor> search(String q) {
        List<Instructor> out = new ArrayList<>();
        String like = "%" + q.toLowerCase() + "%";
        String sql = "SELECT id,name,email,department,status FROM instructors WHERE LOWER(id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(department) LIKE ? ORDER BY id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, like); p.setString(2, like); p.setString(3, like); p.setString(4, like);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    Instructor i = new Instructor();
                    i.setId(rs.getString("id")); i.setName(rs.getString("name")); i.setEmail(rs.getString("email")); i.setDepartment(rs.getString("department")); i.setStatus(rs.getString("status"));
                    out.add(i);
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }

    public boolean assignCourse(String instructorId, String courseCode) {
        String sql = "INSERT OR IGNORE INTO instructor_courses (instructor_id, course_code) VALUES (?, ?)";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) { p.setString(1, instructorId); p.setString(2, courseCode); return p.executeUpdate() >= 0; }
        catch (SQLException ex) { System.err.println("assignCourse failed: " + ex.getMessage()); return false; }
    }

    // Remove a specific assignment of a course from an instructor
    public boolean removeAssignment(String instructorId, String courseCode) {
        String sql = "DELETE FROM instructor_courses WHERE instructor_id = ? AND course_code = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, instructorId);
            p.setString(2, courseCode);
            return p.executeUpdate() == 1;
        } catch (SQLException ex) {
            System.err.println("removeAssignment failed: " + ex.getMessage());
            return false;
        }
    }

    public Instructor getById(String id) {
        String sql = "SELECT id,name,email,department,status FROM instructors WHERE id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, id);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    Instructor i = new Instructor();
                    i.setId(rs.getString("id"));
                    i.setName(rs.getString("name"));
                    i.setEmail(rs.getString("email"));
                    i.setDepartment(rs.getString("department"));
                    i.setStatus(rs.getString("status"));
                    return i;
                }
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return null;
    }

    public List<String> getAssignedCourses(String instructorId) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT course_code FROM instructor_courses WHERE instructor_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) { p.setString(1, instructorId); try (ResultSet rs = p.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); } }
        catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }

    // Return list of instructor names assigned to a given course code
    public List<String> getInstructorsForCourse(String courseCode) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT i.name FROM instructors i JOIN instructor_courses ic ON i.id = ic.instructor_id WHERE ic.course_code = ? ORDER BY i.id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, courseCode);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException ex) { throw new RuntimeException(ex); }
        return out;
    }

    // Remove all assignments for a given course (used when deleting a course)
    public boolean removeAssignmentsByCourse(String courseCode) {
        String sql = "DELETE FROM instructor_courses WHERE course_code = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, courseCode);
            p.executeUpdate();
            return true;
        } catch (SQLException ex) { System.err.println("removeAssignmentsByCourse failed: " + ex.getMessage()); return false; }
    }
}
