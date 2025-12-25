-- Migration: Create user_person_map and populate from students and instructors
-- This table provides deterministic mapping from auth username to student/instructor person_id.
-- Username pattern: lowercase(firstName + id)
-- Example: harsh + 2024235 => harsh2024235, A V Subramanyam + INS001 => avins001

BEGIN TRANSACTION;

DROP TABLE IF EXISTS user_person_map;

CREATE TABLE user_person_map (
    username TEXT PRIMARY KEY,
    person_type TEXT NOT NULL,
    person_id TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert student mappings
INSERT INTO user_person_map (username, person_type, person_id)
SELECT 
    lower(TRIM(
        CASE 
            WHEN INSTR(TRIM(name), ' ') = 0 THEN TRIM(name) 
            ELSE SUBSTR(TRIM(name), 1, INSTR(TRIM(name), ' ') - 1) 
        END
    ) || id),
    'student',
    id
FROM students
WHERE id IS NOT NULL AND name IS NOT NULL;

-- Insert instructor mappings
INSERT INTO user_person_map (username, person_type, person_id)
SELECT 
    lower(TRIM(
        CASE 
            WHEN INSTR(TRIM(name), ' ') = 0 THEN TRIM(name) 
            ELSE SUBSTR(TRIM(name), 1, INSTR(TRIM(name), ' ') - 1) 
        END
    ) || id),
    'instructor',
    id
FROM instructors
WHERE id IS NOT NULL AND name IS NOT NULL;

COMMIT;
