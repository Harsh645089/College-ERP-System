package instructor;

import types.SectionRow;
import java.util.List;

/**
 * Defines the contract for all instructor-related business logic and data retrieval.
 */
public interface InstructorApi { // <-- Now public and in its own file
    /**
     * Retrieves all sections assigned to the given instructor.
     */
    List<SectionRow> getMySections(int instructorId) throws Exception;

    // Future methods: getStudentsInSection, enterScore, computeFinalGrade
}