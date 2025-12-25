package domain;

/** Simple Student POJO */
public class Student {
    private String id;
    private String name;
    private String email;
    private String section;
    private String status; // Active / Inactive
    private String degree;
    private String branch;
    private String yearOfStudy;
    private String admissionYear;

    public Student() {}

    public Student(String id, String name, String email, String section, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.section = section;
        this.status = status;
    }

    public Student(String id, String name, String email, String section, String status, String degree, String branch, String yearOfStudy, String admissionYear) {
        this(id, name, email, section, status);
        this.degree = degree;
        this.branch = branch;
        this.yearOfStudy = yearOfStudy;
        this.admissionYear = admissionYear;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(String yearOfStudy) { this.yearOfStudy = yearOfStudy; }

    public String getAdmissionYear() { return admissionYear; }
    public void setAdmissionYear(String admissionYear) { this.admissionYear = admissionYear; }

    @Override
    public String toString() {
        return id + " - " + name;
    }

    // ----- Convenience helpers used by UI components -----
    /** Returns a placeholder CGPA. Implement real calculation as needed. */
    public double getCurrentCGPA() { return 0.0; }

    /** Returns fees due (placeholder). Replace with real fees lookup when available. */
    public int getFeesDue() { return 0; }
}
