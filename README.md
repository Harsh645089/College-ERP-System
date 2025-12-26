# College ERP System

The **College ERP System** is a Java-based application designed to digitally manage and automate various academic and administrative operations of a college.  
It provides a centralized platform for handling students, instructors, courses, sections, enrollments, grades, and user authentication using a structured and modular approach.

The project focuses on applying **Object-Oriented Programming principles**, clean code organization, and database-backed persistence using **SQLite**.

---

## 📖 Project Overview

In a traditional college system, academic data such as student records, course details, enrollments, and grades are often scattered or handled manually.  
This ERP system aims to solve that problem by:

- Centralizing all academic data
- Providing role-based access (Admin, Instructor, Student)
- Ensuring data persistence using a relational database
- Making the system scalable and easy to maintain

The project is built keeping **real-world ERP design principles** in mind.

---

## ✨ Key Features

### 🔐 Authentication & Authorization
- Secure login system for users
- Role-based access control
- Separate flows for Admin, Instructor, and Student

### 👨‍🎓 Student Management
- Student records stored in the database
- Enrollment into courses and sections
- Viewing grades and academic reports
- CSV-based data import/export support

### 👩‍🏫 Instructor Management
- Instructor profiles
- Course and section assignment
- Grade allocation to students
- Access to enrolled student lists

### 📚 Course & Section Management
- Course creation and management
- Multiple sections per course
- Instructor-to-section mapping
- Student enrollment handling

### 🧾 Reports & Data Handling
- Generation of CSV-based reports
- Transcript and grading data export
- Persistent storage using SQLite

---

## 🛠 Technology Stack

| Component | Technology |
|--------|-----------|
| Programming Language | Java |
| Database | SQLite |
| Database Connectivity | JDBC |
| Data Storage | CSV & SQLite |
| Version Control | Git & GitHub |

---

## 🧠 Concepts & Design Principles Used

### Object-Oriented Programming (OOP)
- **Encapsulation:**  
  Data and related operations are encapsulated inside classes such as Student, Instructor, Course, Section, etc.

- **Abstraction:**  
  Separation between user interface logic, business logic, and database operations.

- **Modularity:**  
  The project is divided into logical packages/modules, making it easy to understand and extend.

### Database Design
- Relational tables for users, students, instructors, courses, sections, enrollments, and grades
- Use of SQL scripts for schema definition
- SQLite chosen for simplicity and portability

---

## 📂 Project Structure


    College-ERP-System
    │
    ├── src/ # Java source code
    │ ├── admin/ # Admin-related logic
    │ ├── auth/ # Authentication and login
    │ ├── student/ # Student-related modules
    │ ├── instructor/ # Instructor-related modules
    │ ├── domain/ # Core domain models
    │ ├── api/ # Application logic / services
    │ └── ui/ # User interface logic
    │
    ├── database/ # Database files
    │ ├── erp.db # Main SQLite database
    │ ├── erp.sql # Database schema
    │ └── backups/ # Database backups
    │
    ├── data/ # CSV files and reports
    │ ├── sample_students.csv
    │ ├── transcripts.csv
    │ └── generated_reports.csv
    │
    ├── docs/ # Documentation and references
    │
    ├── resources/ # Configuration files
    │ └── config.properties
    │
    ├── lib/ # External libraries (JAR files)
    │
    ├── run.sh # Linux/Mac execution script
    ├── run.bat # Windows execution script
    ├── .gitignore
    └── README.md

---

## ▶️ How to Run the Project

### Prerequisites
- Java JDK installed (Java 8 or above)
- SQLite support
- Git (optional)

### On Linux / macOS
    chmod +x run.sh
    ./run.sh

### On Windows
    run.bat


---

## 🗄 Database Information

* Database Type: SQLite

* Main database file: database/erp.db

* SQL schema files are provided for recreating the database

* Database backups are maintained for safety

---
  

## 📊 Data Handling

### CSV files are used for:

  * Importing student data

  * Exporting transcripts and reports

### This allows easy data exchange and offline analysis
  
---

## 🎓 Academic Relevance

### This project was developed as part of college coursework to demonstrate:

* Practical use of Java and databases

* Application of OOP concepts in a real-world system

* Understanding of ERP-like system design

* Clean code structure and version control practices
  
---
## 📹 Video Demonstration

A short video demonstrating the working and flow of the College ERP System, including login, role-based access, course management, enrollment, and grading.

▶️ **Project Demo Video:**  
[Click here to watch the demo](https://drive.google.com/drive/u/1/folders/1FX8lADooyTlD7xPpJc1_K2lTyi27RSb9?ths=true)


## 👤 Author

Harsh Sharma  
B.Tech Student  
Indraprastha Institute of Information Technology, Delhi (IIIT Delhi)





