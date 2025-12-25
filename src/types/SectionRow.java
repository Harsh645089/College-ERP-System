package types;

/**
 * Data Transfer Object (DTO) for displaying section details in the UI.
 * This class MUST be public.
 */
public class SectionRow {
    public int sectionId;
    public String courseCode;
    public String courseTitle;
    public int credits;          // <-- Added this field
    public String dayTime;
    public String room;
    public int capacity;
    public String instructorName; // <-- Added this field

    public SectionRow(int sectionId, String code, String title, int credits,
                      String dayTime, String room, int capacity, String instructorName) {
        this.sectionId = sectionId;
        this.courseCode = code;
        this.courseTitle = title;
        this.credits = credits;
        this.dayTime = dayTime;
        this.room = room;
        this.capacity = capacity;
        this.instructorName = instructorName;
    }

    // Add getter methods for SectionRow fields
    public int getSectionId() {
        return sectionId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public int getCredits() {
        return credits;
    }

    public String getDayTime() {
        return dayTime;
    }

    public String getRoom() {
        return room;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getInstructorName() {
        return instructorName;
    }

    // Add missing methods to SectionRow
    public boolean isEnrollmentOpen() {
        // Placeholder logic; replace with actual implementation if needed
        return true;
    }

    public int getEnrolledCount() {
        // Placeholder logic; replace with actual implementation if needed
        return 0;
    }

    public String getCourseId() {
        return courseCode; // Assuming courseCode represents the course ID
    }

    public String getCourseName() {
        return courseTitle; // Assuming courseTitle represents the course name
    }
}