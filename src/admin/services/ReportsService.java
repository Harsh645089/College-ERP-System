package admin.services;

import domain.Student;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
// ...existing code...

/** Small CSV exporter for students */
public class ReportsService {
    public File exportStudentsToCSV(List<Student> students, String initiatedBy) throws IOException {
        String filename = "students_report_" + System.currentTimeMillis() + ".csv";
        File out = new File(filename);
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("StudentID,Name,Email,Section,Status\n");
            for (Student s : students) {
                fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        safe(s.getId()), safe(s.getName()), safe(s.getEmail()), safe(s.getSection()), safe(s.getStatus())));
            }
        }
        // ...existing code...
        return out;
    }

    public File exportInstructorsToCSV(List<domain.Instructor> instructors, String initiatedBy) throws IOException {
        String filename = "instructors_report_" + System.currentTimeMillis() + ".csv";
        File out = new File(filename);
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("InstructorID,Name,Email,Department,Status\n");
            if (instructors != null) {
                for (domain.Instructor ins : instructors) {
                    fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            safe(ins.getId()), safe(ins.getName()), safe(ins.getEmail()), safe(ins.getDepartment()), safe(ins.getStatus())));
                }
            }
        }
        // ...existing code...
        return out;
    }

    public File exportCoursesToCSV(java.util.List<admin.dao.CourseDAO.Course> courses, String initiatedBy) throws IOException {
        String filename = "courses_report_" + System.currentTimeMillis() + ".csv";
        File out = new File(filename);
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("CourseCode,Title,Department,Prerequisites\n");
            if (courses != null) {
                for (admin.dao.CourseDAO.Course c : courses) {
                    fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            safe(c.code), safe(c.title), safe(c.department), safe(c.prerequisites)));
                }
            }
        }
        // ...existing code...
        return out;
    }

    public File exportHostelToCSV(java.util.List<String[]> rows, String initiatedBy) throws IOException {
        String filename = "hostel_report_" + System.currentTimeMillis() + ".csv";
        File out = new File(filename);
        try (FileWriter fw = new FileWriter(out)) {
            fw.write("Metric,Value,Notes\n");
            if (rows != null) {
                for (String[] r : rows) {
                    fw.write(String.format("\"%s\",\"%s\",\"%s\"\n", safe(r[0]), safe(r[1]), safe(r.length>2?r[2]:"")));
                }
            }
        }
        // ...existing code...
        return out;
    }

    public File exportFeesToCSV(java.util.List<String[]> tuitionRows, java.util.List<String[]> partRows, java.util.List<String[]> hostelRows, String initiatedBy) throws IOException {
        String filename = "fees_report_" + System.currentTimeMillis() + ".csv";
        File out = new File(filename);
        try (FileWriter fw = new FileWriter(out)) {
            // Tuition section
            fw.write("Tuition - Program,Year,Tuition Fee,Total\n");
            if (tuitionRows != null) {
                for (String[] r : tuitionRows) {
                    fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n", safe(r[0]), safe(r[1]), safe(r[2]), safe(r.length>3?r[3]:"")));
                }
            }
            fw.write("\n");

            // Part-wise section
            fw.write("Part-wise - Program,Part,Fee,Notes\n");
            if (partRows != null) {
                for (String[] r : partRows) {
                    fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n", safe(r[0]), safe(r[1]), safe(r[2]), safe(r.length>3?r[3]:"")));
                }
            }
            fw.write("\n");

            // Hostel section
            fw.write("Hostel - Program,Period,Fee,Notes\n");
            if (hostelRows != null) {
                for (String[] r : hostelRows) {
                    fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n", safe(r[0]), safe(r[1]), safe(r[2]), safe(r.length>3?r[3]:"")));
                }
            }
        }
        // ...existing code...
        return out;
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
    }
}
