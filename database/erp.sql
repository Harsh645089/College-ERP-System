-- erp.sql
PRAGMA foreign_keys = ON;

-- users table (for authentication; password hashing not included here)
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT,
  role TEXT NOT NULL,
  email TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- students table
-- students table
CREATE TABLE IF NOT EXISTS students (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT,
  section TEXT,
  status TEXT,
  degree TEXT,
  branch TEXT,
  year_of_study TEXT,
  admission_year TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ...existing code...

-- seed users
INSERT OR IGNORE INTO users (username, password_hash, role, email) VALUES ('admin1', 'dummy-hash', 'SUPERADMIN', 'admin1@univ.edu');
INSERT OR IGNORE INTO users (username, password_hash, role, email) VALUES ('admin2', 'dummy-hash', 'ADMIN', 'admin2@univ.edu');

-- seed students
INSERT OR IGNORE INTO students (id, name, email, section, status) VALUES ('S001','John Smith','john.smith@email.com','CS101-A','Active');
INSERT OR IGNORE INTO students (id, name, email, section, status) VALUES ('S002','Sarah Johnson','sarah.j@email.com','CS101-A','Active');
INSERT OR IGNORE INTO students (id, name, email, section, status) VALUES ('S003','Michael Brown','m.brown@email.com','CS101-B','Active');
INSERT OR IGNORE INTO students (id, name, email, section, status) VALUES ('S004','Emily Davis','emily.d@email.com','CS101-A','Inactive');
