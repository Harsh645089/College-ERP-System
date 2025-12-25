package student;

import types.SectionRow;
import java.util.List;

public interface StudentApi {
    /** Retrieves all available sections for a given term. */
    List<SectionRow> getCourseCatalog(String semester, int year) throws Exception;

    /**
     * Attempts to register a student for a section.
     * @throws Exception if registration fails (e.g., class full, maintenance ON, already enrolled).
     */
    void registerSection(int studentId, int sectionId) throws Exception;

    // Future methods: dropSection, viewTimetable, viewGrades
}