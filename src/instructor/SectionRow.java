package instructor;

// Use the SectionRow class created in the previous step (edu.univ.erp.api.types.SectionRow)
// Since that was not confirmed, I will define a minimal row here.

public class SectionRow {
    public int sectionId;
    public String courseCode;
    public String courseTitle;
    public String dayTime;
    public String room;
    public int capacity; // Used as enrolled count in mock
    // Add other fields as needed

    public SectionRow(int sectionId, String code, String title, String time, String room, int count) {
        this.sectionId = sectionId;
        this.courseCode = code;
        this.courseTitle = title;
        this.dayTime = time;
        this.room = room;
        this.capacity = count;
    }
}
