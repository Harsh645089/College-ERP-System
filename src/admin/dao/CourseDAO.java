package admin.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

public class CourseDAO {
    public static class Course {
        public final String code;
        public final String title;
        public final String department;
        public final String prerequisites;
        public Course(String c, String t) { this(c,t,"OTHER","None"); }
        public Course(String c, String t, String dept, String prereq) { code = c; title = t; department = dept; prerequisites = prereq == null ? "" : prereq; }
        public String toString() { return code + " - " + title; }
    }

    // In-memory store for the session. Seeded on first construction.
    private static final List<Course> STORE = new ArrayList<>();
    private static boolean seeded = false;

    public CourseDAO() {
        synchronized (STORE) {
            if (!seeded) {
                seeded = true;
                // Expanded seed list (many courses from the uploaded course directory)
                STORE.add(new Course("CS101", "Introduction to Programming", "CSE", "None"));
                STORE.add(new Course("CSE102", "Data Structures & Algorithms", "CSE", "CS101"));
                STORE.add(new Course("CSE112", "Computer Organization", "CSE", "None"));
                STORE.add(new Course("CSE121", "Discrete Mathematics", "CSE", "None"));
                STORE.add(new Course("CSE140", "Introduction to Intelligent Systems", "CSE", "None"));
                STORE.add(new Course("CSE201", "Advanced Programming", "CSE", "CSE101"));
                STORE.add(new Course("CSE202", "Fundamentals of DBMS", "CSE", "CSE102"));
                STORE.add(new Course("CSE222", "Algorithm Design and Analysis", "CSE", "CSE102"));
                STORE.add(new Course("CSE231", "Operating Systems", "CSE", "CSE102"));
                STORE.add(new Course("CSE232", "Computer Networks", "CSE", "CSE101"));
                STORE.add(new Course("CSE323", "Computer Graphics", "CSE", "CSE102"));
                STORE.add(new Course("CSE340", "Digital Image Processing", "CSE", "MTH101"));
                STORE.add(new Course("CSE344", "Computer Vision", "CSE", "MTH101"));
                STORE.add(new Course("CS300", "Operating Systems (Alt)", "CSE", "CS201"));
                STORE.add(new Course("MTH101", "Calculus I", "MTH", "None"));
                STORE.add(new Course("MTH210", "Linear Algebra", "MTH", "MTH101"));
                STORE.add(new Course("BIO101", "Foundations of Biology", "BIO", "None"));
                STORE.add(new Course("BIO211", "Cell Biology and Bio-Chemistry", "BIO", "None"));
                STORE.add(new Course("BIO213", "Introduction to Quantitative Biology", "BIO", "MTH100"));
                STORE.add(new Course("BIO221", "Practical Bioinformatics", "BIO", "None"));
                STORE.add(new Course("HCD200", "Design Thinking", "DES", "None"));
                STORE.add(new Course("CB101", "Computational Biology", "BIO", "None"));
                STORE.add(new Course("ECE101", "Circuits", "ECE", "None"));
                STORE.add(new Course("PHY101", "General Physics", "PHY", "None"));
                STORE.add(new Course("DSG541", "Data Sciences for Genomics", "BIO", "None"));
                STORE.add(new Course("MLBA542", "Machine Learning for Biomedical Applications", "CSE", "MTH101"));
                STORE.add(new Course("CS350", "Network Security", "CSE", "CSE231"));
                STORE.add(new Course("CSE520", "Advanced Algorithms", "CSE", "CSE222"));
                STORE.add(new Course("COM101", "Communication Skills", "SSH", "None"));
                STORE.add(new Course("TCOM301", "Technical Communication", "SSH", "None"));
                STORE.add(new Course("DM", "Discrete Mathematics", "MTH", "None"));
                STORE.add(new Course("NB101", "Network Biology", "BIO", "None"));
                STORE.add(new Course("ADA222", "Algorithm Design and Analysis", "CSE", "CSE102"));
                STORE.add(new Course("CV101", "Computer Vision Basics", "CSE", "MTH101"));
                STORE.add(new Course("HCD310", "Human Centred Design Studio", "DES", "None"));
                STORE.add(new Course("CSAM101", "Computational Algebra", "CSE", "MTH101"));
                STORE.add(new Course("OTHER001", "Intro to Interdisciplinary Studies", "OTHER", "None"));
            }
        }
    }

    public List<Course> listAllCourses() {
        synchronized (STORE) { return new ArrayList<>(STORE); }
    }

    public boolean addCourse(Course c) {
        synchronized (STORE) {
            // avoid duplicates by code
            for (Course e : STORE) if (e.code.equalsIgnoreCase(c.code)) return false;
            return STORE.add(c);
        }
    }

    public boolean updateCourse(String code, Course updated) {
        synchronized (STORE) {
            for (int i = 0; i < STORE.size(); i++) {
                Course c = STORE.get(i);
                if (c.code.equalsIgnoreCase(code)) {
                    // replace with new Course object but keep the same code if provided
                    Course nc = new Course(code, updated.title, updated.department, updated.prerequisites);
                    STORE.set(i, nc);
                    return true;
                }
            }
            return false;
        }
    }

    public boolean deleteCourse(String code) {
        synchronized (STORE) {
            Iterator<Course> it = STORE.iterator();
            while (it.hasNext()) {
                if (it.next().code.equalsIgnoreCase(code)) { it.remove(); return true; }
            }
            return false;
        }
    }

    public List<Course> search(String q, List<String> departments) {
        if (q == null) q = "";
        final String like = q.trim().toLowerCase();
        synchronized (STORE) {
            List<Course> out = new ArrayList<>();
            for (Course c : STORE) {
                boolean deptOk = (departments == null || departments.isEmpty()) || departments.contains(c.department);
                if (!deptOk) continue;
                if (like.isEmpty() || c.code.toLowerCase().contains(like) || c.title.toLowerCase().contains(like)) out.add(c);
            }
            return out;
        }
    }

    public List<String> listDepartments() {
        synchronized (STORE) {
            List<String> out = new ArrayList<>();
            for (Course c : STORE) if (!out.contains(c.department)) out.add(c.department);
            Collections.sort(out);
            return out;
        }
    }
}
