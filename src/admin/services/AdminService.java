package admin.services;

import java.util.List;

// ...existing code...
import admin.dao.StudentDAO;
import domain.Student;

/**
 * Thin service that coordinates DAO calls and performs validation.
 * Replace with more services (Course, Instructor) as needed.
 */
public class AdminService {
    private final StudentDAO studentDao;

    public AdminService() {
        this.studentDao = new StudentDAO();
    }

    public List<Student> getAllStudents() {
        return studentDao.listAll();
    }

    public List<Student> searchStudents(String q) {
        return studentDao.search(q);
    }

    public boolean addStudent(Student s) {
        if (s == null || s.getId() == null || s.getId().trim().isEmpty()) return false;
        // simple email validation
        if (s.getEmail() != null && !s.getEmail().matches("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,6}$")) {
            throw new IllegalArgumentException("Invalid email");
        }
        boolean ok = studentDao.create(s);
        return ok;
    }

    public boolean updateStudent(Student s) {
        boolean ok = studentDao.update(s);
        return ok;
    }

    public boolean deleteStudent(String id) {
        boolean ok = studentDao.delete(id);
        return ok;
    }

    public Student findStudent(String id) {
        return studentDao.findById(id).orElse(null);
    }
}
