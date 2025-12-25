package instructor;

import types.SectionRow;
import java.util.List;
import java.util.ArrayList;

public class MockInstructorApi implements InstructorApi {

    /**
     * This method signature MUST EXACTLY match the interface,
     * including the 'throws Exception' clause.
     */
    @Override
    public List<SectionRow> getMySections(int instructorId) throws Exception {
        // Note: The @Override annotation helps the compiler check the signature.
        List<SectionRow> sections = new ArrayList<>();

        // Mock Professor Smith's sections (assuming instructorId 10 for consistency)
        if (instructorId == 10) {
            sections.add(new SectionRow(201, "CS400", "Advanced Algorithms", 3, "M/W 10am", "A-301", 35, "Prof. J. Smith"));
            sections.add(new SectionRow(202, "CS400", "Advanced Algorithms", 3, "T/Th 2pm", "A-301", 30, "Prof. J. Smith"));
        } else {
            // Fallback for other users
            sections.add(new SectionRow(900, "GEN100", "Introduction to Teaching", 1, "Online", "WEB", 100, "Admin"));
        }

        return sections;
    }
}