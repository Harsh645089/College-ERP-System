package student;

import types.SectionRow;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class MockStudentApi implements StudentApi {

    @Override
    public List<SectionRow> getCourseCatalog(String semester, int year) throws Exception {
        // Hardcoded mock data
        List<SectionRow> sections = new ArrayList<>();
        Random rand = new Random();

        sections.add(new SectionRow(101, "CS101", "Intro to Programming", 3, "M/W 10am", "C-201",
                25 - rand.nextInt(5), "Dr. Smith")); // Available

        sections.add(new SectionRow(102, "MA201", "Calculus II", 4, "T/Th 9am", "L-105",
                0, "Prof. Jones")); // Full (for testing logic)

        sections.add(new SectionRow(103, "EE305", "Digital Circuits", 3, "F 1pm", "E-310",
                15, "Dr. Kim")); // Available

        sections.add(new SectionRow(104, "PH101", "Physics I", 3, "M/W 1pm", "P-100",
                30, "Dr. Chen")); // Available

        return sections;
    }

    @Override
    public void registerSection(int studentId, int sectionId) throws Exception {
        // Simulate business logic checks

        if (sectionId == 102) {
            throw new Exception("Class is full. No seats available.");
        }

        // Simulate a student already being enrolled
        if (studentId == 1 && sectionId == 101) {
            throw new Exception("You are already enrolled in this section.");
        }

        // Simulate a database error
        if (studentId == 999) {
            throw new Exception("System is in Maintenance Mode. Registration is disabled.");
        }

        // Success
    }
}