-- Auth DB: users_auth Table
-- Note: Replace 'VARCHAR(255)' with an appropriate length for your chosen hash (e.g., 60-100 for bcrypt)

CREATE TABLE users_auth (
    user_id INT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL, -- 'Student', 'Instructor', 'Admin'
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(10) DEFAULT 'Active',
    last_login DATETIME
);