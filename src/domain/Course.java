package domain;

/**
 * Lightweight Course DTO used by StudentDashboard.
 * This class intentionally provides getters expected by the UI.
 */
public class Course {
    private final int sectionId;
    private final String courseCode;
    private final String title;
    private final int credits;
    private final String instructorName;
    private final String schedule;
    private final int enrolled;
    private final int capacity;
    private final String status;

    public Course(int sectionId, String courseCode, String title, int credits, String instructorName,
                  String schedule, int enrolled, int capacity, String status) {
        this.sectionId = sectionId;
        this.courseCode = courseCode;
        this.title = title;
        this.credits = credits;
        this.instructorName = instructorName;
        this.schedule = schedule;
        this.enrolled = enrolled;
        this.capacity = capacity;
        this.status = status;
    }

    public String getSectionId() { return String.valueOf(sectionId); }
    public String getCourseCode() { return courseCode; }
    public String getTitle() { return title; }
    public int getCredits() { return credits; }
    public String getInstructorName() { return instructorName; }
    public String getSchedule() { return schedule; }
    public int getEnrolled() { return enrolled; }
    public int getCapacity() { return capacity; }
    public String getStatus() { return status; }
}
