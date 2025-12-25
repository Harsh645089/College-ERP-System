package student.services;

import domain.Student;
import domain.Course;
import domain.UserSession;
import admin.dao.StudentDAO;
import admin.dao.InstructorDAO;
import login.DatabaseConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer used by StudentDashboard. It is bound to an authenticated UserSession
 * and enforces strict per-user access control: students and instructors may only access
 * their own records. Admin users retain broader access.
 */
public class StudentService {
    private final StudentDAO studentDAO = new StudentDAO();
    private final InstructorDAO instructorDAO = new InstructorDAO();
    private final UserSession session;

    public StudentService(UserSession session) {
        this.session = session;
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        ensureEnrollmentsTable();
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DatabaseConfig.getDatabaseUrl()); }

    private void ensureEnrollmentsTable() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS enrollments (enrollment_id INTEGER PRIMARY KEY AUTOINCREMENT, student_id TEXT, section_id INTEGER, status TEXT, UNIQUE(student_id, section_id))");
        } catch (SQLException ignored) {}
    }

    // Map the current session to a Student id if the role is Student
    private String mappedStudentId() {
        String uname = session == null ? null : session.getUsername();
        if (uname == null) return null;
        // First try a deterministic lookup via user_person_map
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement("SELECT person_id FROM user_person_map WHERE username = ? AND person_type = 'student' LIMIT 1")) {
            p.setString(1, uname);
            try (ResultSet rs = p.executeQuery()) { if (rs.next()) return rs.getString("person_id"); }
        } catch (SQLException ignored) {}

        // exact id match
        try { java.util.Optional<Student> sOpt = studentDAO.findById(uname); if (sOpt.isPresent()) return sOpt.get().getId(); } catch (Exception ignored) {}

        // fallback: construct username: firstName + id, try to find matching student
        try { for (Student s : studentDAO.listAll()) { String first = (s.getName() == null || s.getName().isEmpty()) ? "" : s.getName().split(" ")[0]; String candidate = (first + s.getId()).toLowerCase(); if (candidate.equalsIgnoreCase(uname)) return s.getId(); } } catch (Exception ignored) {}
        return null;
    }

    // Map the current session to an Instructor id if the role is Instructor
    private String mappedInstructorId() {
        String uname = session == null ? null : session.getUsername();
        if (uname == null) return null;
        // Try user_person_map first
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement("SELECT person_id FROM user_person_map WHERE username = ? AND person_type = 'instructor' LIMIT 1")) {
            p.setString(1, uname);
            try (ResultSet rs = p.executeQuery()) { if (rs.next()) return rs.getString("person_id"); }
        } catch (SQLException ignored) {}

        try { for (domain.Instructor ins : instructorDAO.listAll()) { String first = (ins.getName() == null || ins.getName().isEmpty()) ? "" : ins.getName().split(" ")[0]; String candidate = (first + ins.getId()).toLowerCase(); if (candidate.equalsIgnoreCase(uname)) return ins.getId(); } } catch (Exception ignored) {}
        return null;
    }

    /**
     * Compute current CGPA for a student using credits-weighted GPA from the `grades` + `sections` tables.
     * GPA mapping uses mapToGPA() which returns a 10-point scale.
     */
    public double getCurrentCGPA(String studentId) {
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return 0.0;
        }
        // Calculate CGPA as the average of GPA points from all enrolled courses
        double totalGPA = 0.0;
        int courseCount = 0;
        String sql = "SELECT DISTINCT a.section_id, SUM(a.score) as final_score " +
                     "FROM assessments a " +
                     "WHERE a.student_id = ? " +
                     "GROUP BY a.section_id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, studentId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    double finalScore = rs.getDouble("final_score");
                    double gp = mapToGPA(finalScore);
                    totalGPA += gp;
                    courseCount++;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            return 0.0;
        }
        if (courseCount <= 0) return 0.0;
        double cgpa = totalGPA / courseCount;
        return Math.round(cgpa * 100.0) / 100.0;
    }

    /**
     * Compute fees due = total fees assessed - total payments made. Searches common tables (`fees`, `transactions`, `payments`).
     */
    public int getFeesDue(String studentId) {
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return 0;
        }
        int totalFees = 0;
        int totalPaid = 0;
        try (Connection c = conn()) {
            try (PreparedStatement p = c.prepareStatement("SELECT SUM(amount) FROM fees WHERE student_id = ?")) { p.setString(1, studentId); try (ResultSet rs = p.executeQuery()) { if (rs.next()) totalFees = rs.getInt(1); } } catch (SQLException ignored) {}
            try (PreparedStatement p = c.prepareStatement("SELECT SUM(amount) FROM transactions WHERE student_id = ?")) { p.setString(1, studentId); try (ResultSet rs = p.executeQuery()) { if (rs.next()) totalFees += rs.getInt(1); } } catch (SQLException ignored) {}
            try (PreparedStatement p = c.prepareStatement("SELECT SUM(amount) FROM payments WHERE student_id = ?")) { p.setString(1, studentId); try (ResultSet rs = p.executeQuery()) { if (rs.next()) totalPaid = rs.getInt(1); } } catch (SQLException ignored) {}
        } catch (SQLException ex) { return 0; }
        int due = totalFees - totalPaid;
        return Math.max(0, due);
    }

    public Student getStudentProfile(String ignoredParam) {
        // Enforce per-user access: Students and Instructors can only view their own profiles
        if (session != null && session.getRole() != null && !session.getRole().equalsIgnoreCase("Admin")) {
            String sid = mappedStudentId();
            if (sid == null) return null;
            try { return studentDAO.findById(sid).orElse(null); } catch (Exception ex) { return null; }
        }

        // Admin: if param provided, try lookups; else return null
        if (ignoredParam == null) return null;
        try { java.util.Optional<Student> sOpt = studentDAO.findById(ignoredParam); if (sOpt.isPresent()) return sOpt.get(); } catch (Exception ignored) {}
        try { java.util.List<Student> hits = studentDAO.search(ignoredParam); if (hits != null && hits.size() == 1) return hits.get(0); } catch (Exception ignored) {}
        return null;
    }

    public int getRegisteredCoursesCount(String studentId) {
        // Only allow count for the mapped student (unless Admin)
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return 0;
        }
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE student_id = ?")) {
            p.setString(1, studentId);
            try (ResultSet rs = p.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) {}
        return 0;
    }

    public List<Course> getCourseCatalogForStudent(String studentId) {
        // Ensure caller can only request their own catalog data (unless Admin)
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return new ArrayList<>();
        }

        List<Course> out = new ArrayList<>();
        
        // Fetch student's year and branch up-front. We'll use these to query the
        // `offerings` table for cohort-level published offerings. Year values in the
        // database may be stored as strings like "2nd"; parse numerics when possible.
        Integer studentYearInt = null;
        String studentBranchStr = null;
        try (Connection c = conn(); PreparedStatement sp = c.prepareStatement("SELECT year_of_study, branch FROM students WHERE id = ?")) {
            sp.setString(1, studentId);
            try (ResultSet rs = sp.executeQuery()) {
                if (rs.next()) {
                    String yearRaw = rs.getString("year_of_study");
                    if (yearRaw != null) {
                        // extract leading digits if present
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(yearRaw);
                        if (m.find()) {
                            try { studentYearInt = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
                        }
                    }
                    studentBranchStr = rs.getString("branch");
                }
            }
        } catch (SQLException ignored) {}

        // Determine cohort-level offered course codes (if any) from `offerings`.
        java.util.Set<String> cohortAllowedCourseCodes = new java.util.HashSet<>();
        if (studentBranchStr != null) {
            try (Connection c = conn(); PreparedStatement p = c.prepareStatement(
                    "SELECT DISTINCT course_code FROM offerings WHERE (UPPER(branch) = ? OR UPPER(branch) = 'ALL') AND (year = ? OR year = 0)")) {
                p.setString(1, studentBranchStr.toUpperCase());
                p.setInt(2, studentYearInt == null ? 0 : studentYearInt);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        String cc = rs.getString(1);
                        if (cc != null && !cc.isEmpty()) cohortAllowedCourseCodes.add(cc);
                    }
                }
            } catch (SQLException ignored) {}
        }

        // Determine if any offerings exist globally. If any offerings exist,
        // we should restrict visible sections to only those offered to the student's cohort.
        boolean globalOffersExist = false;
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM offerings")) {
            try (ResultSet rs = p.executeQuery()) { if (rs.next() && rs.getInt(1) > 0) globalOffersExist = true; }
        } catch (SQLException ignored) {}

        // First, get courses from sections table (traditional enrollment method)
        String sql = "SELECT section_id, course_code, title, term, day_time, room, capacity, instructor_id FROM sections ORDER BY section_id";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                int sectionId = rs.getInt("section_id");
                String code = rs.getString("course_code");
                String title = rs.getString("title");
                int credits = 3; // default credits
                String schedule = rs.getString("day_time") + " @ " + rs.getString("room");
                int capacity = rs.getInt("capacity");
                String instructor = rs.getString("instructor_id");

                int enrolled = 0;
                try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE section_id = ?")) {
                    q.setInt(1, sectionId);
                    try (ResultSet er = q.executeQuery()) { if (er.next()) enrolled = er.getInt(1); }
                }

                boolean isRegistered = false;
                try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE section_id = ? AND student_id = ?")) {
                    q.setInt(1, sectionId);
                    q.setString(2, studentId);
                    try (ResultSet er = q.executeQuery()) { if (er.next()) isRegistered = er.getInt(1) > 0; }
                }

                String status = isRegistered ? "Registered" : (enrolled < capacity ? "Open" : "Full");

                // If any offerings exist (globalOffersExist == true), only include sections
                // whose course_code is in the cohortAllowedCourseCodes set (i.e. admin-published
                // offerings for this student's branch/year). If there are no offerings at all
                // in the database, fall back to showing all sections (backwards compatibility).
                if (!globalOffersExist || cohortAllowedCourseCodes.contains(code)) {
                    out.add(new Course(sectionId, code, title, credits, instructor, schedule, enrolled, capacity, status));
                }
            }
        } catch (SQLException ex) {
            // Log the SQL error and degrade gracefully to an empty catalog so the UI doesn't crash
            System.err.println("Warning: failed to load courses from sections table: " + ex.getMessage());
        }
        
        // Next, load offered courses published in the `offerings` table for the student's cohort
        // Try to load offered courses - if student year/branch is available, filter by them; otherwise show all
        String sqlOffered;
        
        if (studentYearInt != null && studentBranchStr != null) {
            // Filter by student's branch and year
            sqlOffered = "SELECT o.course_code, COALESCE(c.name, o.course_code) AS name, COALESCE(c.credits,4) AS credits " +
                    "FROM offerings o LEFT JOIN courses c ON o.course_code = c.code " +
                    "WHERE (UPPER(o.branch) = ? OR UPPER(o.branch) = 'ALL') AND (o.year = ? OR o.year = 0)";
        } else {
            // If student info not available, show all offered courses (fallback)
            sqlOffered = "SELECT o.course_code, COALESCE(c.name, o.course_code) AS name, COALESCE(c.credits,4) AS credits " +
                    "FROM offerings o LEFT JOIN courses c ON o.course_code = c.code";
        }
        
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sqlOffered)) {
            if (studentYearInt != null && studentBranchStr != null) {
                p.setString(1, studentBranchStr.toUpperCase());
                p.setInt(2, studentYearInt == null ? 0 : studentYearInt);
            }
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("course_code");
                    String title = rs.getString("name");
                    int credits = rs.getInt("credits");
                    if (credits == 0) credits = 4;
                    String schedule = "TBA";
                    int capacity = 100;
                    String instructor = "Staff";

                    // Count enrollment for offered course - need to join with sections table since enrollments uses section_id
                    int enrolled = 0;
                    try (PreparedStatement r = c.prepareStatement(
                            "SELECT COUNT(DISTINCT e.student_id) FROM enrollments e " +
                            "JOIN sections s ON e.section_id = s.section_id " +
                            "WHERE s.course_code = ?")) {
                        r.setString(1, code);
                        try (ResultSet er = r.executeQuery()) { if (er.next()) enrolled = er.getInt(1); }
                    } catch (SQLException ignored) {}

                    // Check if student is already enrolled in this offered course (any section of this course)
                    boolean isEnrolledInOffered = false;
                    try (PreparedStatement r = c.prepareStatement(
                            "SELECT COUNT(*) FROM enrollments e " +
                            "JOIN sections s ON e.section_id = s.section_id " +
                            "WHERE s.course_code = ? AND e.student_id = ?")) {
                        r.setString(1, code);
                        r.setString(2, studentId);
                        try (ResultSet er = r.executeQuery()) { if (er.next()) isEnrolledInOffered = er.getInt(1) > 0; }
                    } catch (SQLException ignored) {}

                    String status = isEnrolledInOffered ? "Registered" : "Open";
                    out.add(new Course(-1, code, title, credits, instructor, schedule, enrolled, capacity, status));
                }
            }
        } catch (SQLException ex) {
            System.err.println("Warning: failed to load offered courses from offerings table: " + ex.getMessage());
        }
        
        return out;
    }

    public String registerCourse(String studentId, String sectionIdStr) {
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return "Unauthorized";
        }

        int sectionId; try { sectionId = Integer.parseInt(sectionIdStr); } catch (NumberFormatException ex) { return "Invalid section id"; }
        try (Connection c = conn()) {
            // Check if already registered in THIS section
            try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND section_id = ?")) {
                p.setString(1, studentId); p.setInt(2, sectionId);
                try (ResultSet rs = p.executeQuery()) { if (rs.next() && rs.getInt(1) > 0) return "Already registered in this section."; }
            }
            
            // Get the course_code for this section
            String courseCode = null;
            try (PreparedStatement p = c.prepareStatement("SELECT course_code FROM sections WHERE section_id = ?")) {
                p.setInt(1, sectionId);
                try (ResultSet rs = p.executeQuery()) { if (rs.next()) courseCode = rs.getString(1); else return "Section not found."; }
            }
            
            // Check if already registered in ANY section of this course
            if (courseCode != null) {
                try (PreparedStatement p = c.prepareStatement(
                    "SELECT COUNT(*) FROM enrollments e JOIN sections s ON e.section_id = s.section_id WHERE e.student_id = ? AND s.course_code = ?")) {
                    p.setString(1, studentId); p.setString(2, courseCode);
                    try (ResultSet rs = p.executeQuery()) { 
                        if (rs.next() && rs.getInt(1) > 0) return "Already registered in another section of this course. You can only register for one section per course."; 
                    }
                }
            }
            
            // Check capacity
            int capacity = 0;
            try (PreparedStatement p = c.prepareStatement("SELECT capacity FROM sections WHERE section_id = ?")) { p.setInt(1, sectionId); try (ResultSet rs = p.executeQuery()) { if (rs.next()) capacity = rs.getInt(1); } }
            int enrolled = 0; try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE section_id = ?")) { p.setInt(1, sectionId); try (ResultSet rs = p.executeQuery()) { if (rs.next()) enrolled = rs.getInt(1); } }
            if (enrolled >= capacity) return "Section full.";
            
            // Register the student
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO enrollments (student_id, section_id, status) VALUES (?, ?, 'ENROLLED')")) { ins.setString(1, studentId); ins.setInt(2, sectionId); ins.executeUpdate(); return "SUCCESS"; }
        } catch (SQLException ex) { String msg = ex.getMessage(); if (msg != null && msg.toLowerCase().contains("unique")) return "Already registered in this section."; return ex.getMessage(); }
    }

    public boolean dropCourse(String studentId, String sectionIdStr) {
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return false;
        }
        int sectionId; try { sectionId = Integer.parseInt(sectionIdStr); } catch (NumberFormatException ex) { return false; }
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement("DELETE FROM enrollments WHERE student_id = ? AND section_id = ?")) { p.setString(1, studentId); p.setInt(2, sectionId); int rows = p.executeUpdate(); return rows > 0; } catch (SQLException ex) { return false; }
    }

    public java.util.List<String[]> getStudentGradeHistory(String studentId) {
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return new ArrayList<>();
        }
        List<String[]> out = new ArrayList<>();
        // Query assessments table to get the student's grades per section/course
        String sql = "SELECT DISTINCT a.section_id, sec.course_code, sec.title, COALESCE(c.credits, 4) AS credits, " +
                     "SUM(a.score) as final_score " +
                     "FROM assessments a " +
                     "LEFT JOIN sections sec ON a.section_id = sec.section_id " +
                     "LEFT JOIN courses c ON sec.course_code = c.code " +
                     "WHERE a.student_id = ? " +
                     "GROUP BY a.section_id, sec.course_code, sec.title, c.credits " +
                     "ORDER BY a.section_id DESC";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, studentId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("course_code");
                    String title = rs.getString("title");
                    String credits = String.valueOf(rs.getInt("credits"));
                    double finalGrade = rs.getDouble("final_score");
                    String gradeLetter = mapToLetter(finalGrade);
                    String gpaPoints = String.format("%.2f", mapToGPA(finalGrade));
                    out.add(new String[]{
                            code == null ? "" : code,
                            title == null ? "" : title,
                            credits,
                            gradeLetter,
                            gpaPoints
                    });
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return out;
    }

    private String mapToLetter(double v) {
        if (v >= 90) return "A+";
        if (v >= 80) return "A";
        if (v >= 70) return "B+";
        if (v >= 60) return "B";
        if (v >= 50) return "C+";
        if (v >= 40) return "C";
        return "F";
    }
    
    private double mapToGPA(double v) {
        if (v >= 90) return 10.0;
        if (v >= 80) return 9.0;
        if (v >= 70) return 8.0;
        if (v >= 60) return 7.0;
        if (v >= 50) return 6.0;
        if (v >= 40) return 5.0;
        return 0.0;
    }

    public int getFeesPaid(String studentId) {
        if (session != null && !"Admin".equalsIgnoreCase(session.getRole())) {
            String mapped = mappedStudentId();
            if (mapped == null || !mapped.equals(studentId)) return 0;
        }
        try (Connection c = conn()) {
            String[] candidates = new String[]{"payments","fees","transactions"};
            for (String t : candidates) {
                try (PreparedStatement p = c.prepareStatement("SELECT SUM(amount) FROM " + t + " WHERE student_id = ?")) { p.setString(1, studentId); try (ResultSet rs = p.executeQuery()) { if (rs.next()) return rs.getInt(1); } } catch (SQLException ignored) {}
            }
        } catch (SQLException ignored) {}
        return 0;
    }
}
