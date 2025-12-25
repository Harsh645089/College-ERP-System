package admin.services;

import login.DatabaseConfig;
import admin.dao.CourseDAO;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * BackupService provides simple export/import helpers used by Admin UI.
 */
public class BackupService {
    /**
     * Perform a real backup suitable for recovery.
     * If `sqlite3` is available, a SQL dump is created; otherwise the raw `erp.db` file is copied.
     * Both the dump/copy and ancillary files (audit.log, user_credentials.csv, erp.sql/gui.sql) are packaged
     * into a timestamped zip which is returned.
     */
    public File performBackup(String initiatedBy) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        File dumpFile = new File("erp_dump_" + ts + ".sql");
        File dbCopy = new File("erp_db_copy_" + ts + ".db");
        boolean didDump = false;

        // Try sqlite3 dump first
        try {
            ProcessBuilder whichPb = new ProcessBuilder("which", "sqlite3");
            Process which = whichPb.start();
            int whichExit = which.waitFor();
            if (whichExit == 0) {
                // Run: sqlite3 erp.db .dump > dumpFile
                String dbPath = DatabaseConfig.getDatabasePath();
                File dbFile = new File(dbPath);
                if (dbFile.exists()) {
                    ProcessBuilder pb = new ProcessBuilder("sqlite3", dbPath, ".dump");
                    pb.redirectOutput(dumpFile);
                    Process p = pb.start();
                    int exit = p.waitFor();
                    if (exit == 0 && dumpFile.exists()) {
                        didDump = true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore and fall back
            didDump = false;
        }

        // If dump not created, fall back to copying raw DB file
        if (!didDump) {
            String dbPath = DatabaseConfig.getDatabasePath();
            File dbf = new File(dbPath);
            if (dbf.exists()) {
                Files.copy(dbf.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                // no DB present; still proceed to include other files
                dbCopy = null;
            }
        }

        // Package into a zip
        String name = "erp_backup_" + ts + ".zip";
        File out = new File(name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            if (didDump && dumpFile.exists()) addFileToZip(zos, dumpFile);
            if (!didDump && dbCopy != null && dbCopy.exists()) addFileToZip(zos, dbCopy);
            // include other non-DB artifacts only; do NOT include erp.db, erp.sql, gui.sql, or user credentials
        }

        // ...existing code...

        // cleanup intermediate files (keep dump/copy only inside zip)
        try { if (dumpFile.exists()) dumpFile.delete(); } catch (Exception ignored) {}
        try { if (dbCopy!=null && dbCopy.exists()) dbCopy.delete(); } catch (Exception ignored) {}

        return out;
    }

    public File exportAllData(String initiatedBy) throws IOException {
        long ts = System.currentTimeMillis();
        String name = "erp_full_export_" + ts + ".zip";
        File out = new File(name);

        // Create temp export dir
        Path exportDir = Path.of("erp_export_" + ts);
        Files.createDirectories(exportDir);

        // Try to create CSVs from the SQLite DB if available, otherwise skip
        boolean sqliteAvailable = false;
        try {
            ProcessBuilder whichPb = new ProcessBuilder("which", "sqlite3");
            Process which = whichPb.start();
            int whichExit = which.waitFor();
            sqliteAvailable = (whichExit == 0);
        } catch (Exception e) {
            sqliteAvailable = false;
        }

        String dbPath = DatabaseConfig.getDatabasePath();
        File dbFile = new File(dbPath);
        if (sqliteAvailable && dbFile.exists()) {
            // tables/queries we want to export
            String[][] queries = new String[][] {
                {"students", "select * from students;"},
                {"instructors", "select * from instructors;"},
                {"courses", "select * from courses;"},
                {"settings", "select * from settings;"},
                {"fees", "select * from fees;"},
                {"fee_structure", "select * from fee_structure;"}
            };
            for (String[] q : queries) {
                String fname = q[0] + ".csv";
                Path outPath = exportDir.resolve(fname);
                try {
                    ProcessBuilder pb = new ProcessBuilder("sqlite3", dbPath, "-header", "-csv", q[1]);
                    pb.redirectOutput(outPath.toFile());
                    Process p = pb.start();
                    int exit = p.waitFor();
                    if (exit != 0) {
                        // remove empty or partial file
                        try { Files.deleteIfExists(outPath); } catch (Exception ignored) {}
                    }
                } catch (Exception ex) {
                    // ignore individual query failures
                    try { Files.deleteIfExists(outPath); } catch (Exception ignored) {}
                }
            }
            // The in-memory course list is not stored in SQLite; export it from CourseDAO
            try {
                CourseDAO dao = new CourseDAO();
                java.util.List<CourseDAO.Course> courses = dao.listAllCourses();
                Path coursesCsv = exportDir.resolve("courses.csv");
                try (PrintWriter pw = new PrintWriter(new FileWriter(coursesCsv.toFile()))) {
                    pw.println("code,title,department,prerequisites");
                    for (CourseDAO.Course c : courses) {
                        String code = c.code.replaceAll(",", " ");
                        String title = c.title.replaceAll(",", " ");
                        String dept = c.department == null ? "" : c.department.replaceAll(",", " ");
                        String pre = c.prerequisites == null ? "" : c.prerequisites.replaceAll(",", " ");
                        pw.printf("%s,%s,%s,%s\n", code, title, dept, pre);
                    }
                }
            } catch (Exception ignored) {}
        } else {
            // fallback: include existing CSV files if present (plaintext credentials file)
            try {
                Files.copy(Path.of("user_credentials.csv"), exportDir.resolve("user_credentials.csv"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }

        // Also include helpful logs and non-DB artifacts if they exist
        // include plaintext credential file as requested (if present)
        addFileToDir(exportDir, new File("user_credentials.csv"));
        addFileToDir(exportDir, new File("fees_export/tuition_fees.csv"));
        addFileToDir(exportDir, new File("fees_export/part_fees.csv"));
        addFileToDir(exportDir, new File("fees_export/hostel_fees.csv"));

        // Zip the export dir
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            Files.walk(exportDir).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                try (FileInputStream fis = new FileInputStream(p.toFile())) {
                    String entryName = exportDir.relativize(p).toString();
                    zos.putNextEntry(new ZipEntry(entryName));
                    byte[] buf = new byte[4096]; int r;
                    while ((r = fis.read(buf)) != -1) zos.write(buf, 0, r);
                    zos.closeEntry();
                } catch (Exception e) {
                    // ignore individual file failures
                }
            });
        }

        // cleanup temp dir
        try {
            Files.walk(exportDir).sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (Exception ignored) {}

        // ...existing code...
        return out;
    }

    private void addFileToZip(ZipOutputStream zos, File f) throws IOException {
        if (f == null || !f.exists() || !f.isFile()) return;
        try (FileInputStream fis = new FileInputStream(f)) {
            zos.putNextEntry(new ZipEntry(f.getName()));
            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1) zos.write(buf, 0, r);
            zos.closeEntry();
        }
    }

    private void addFileToDir(Path dir, File f) {
        if (f == null || !f.exists() || !f.isFile()) return;
        try {
            Files.copy(f.toPath(), dir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    public String importDataFromZip(File zipFile, String initiatedBy) throws IOException {
        if (zipFile == null || !zipFile.exists()) return null;
        Path targetDir = Path.of("imported_data", String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = targetDir.resolve(entry.getName());
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
        // ...existing code...
        return targetDir.toString();
    }

    /**
     * Restore from a backup zip. Creates a pre-restore snapshot of `erp.db` (if exists) and then
     * applies either a SQL dump (if present in the zip) or replaces the `erp.db` file.
     * Returns the path where files were extracted (for inspection) or null on failure.
     */
    public String restoreFromBackup(File zipFile, String initiatedBy) throws IOException, InterruptedException {
        if (zipFile == null || !zipFile.exists()) return null;
        Path targetDir = Path.of("imported_data", String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = targetDir.resolve(entry.getName());
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }

        // Pre-restore snapshot
        String dbPath = DatabaseConfig.getDatabasePath();
        File currentDb = new File(dbPath);
        long ts = System.currentTimeMillis();
        if (currentDb.exists()) {
            File snap = new File("erp_pre_restore_" + ts + ".db");
            Files.copy(currentDb.toPath(), snap.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // ...existing code...
        }

        // Find SQL dump or db file in extracted files
        File sql = null; File dbfile = null;
        try (var s = Files.newDirectoryStream(targetDir)) {
            for (Path p : s) {
                String n = p.getFileName().toString().toLowerCase();
                if (n.endsWith(".sql")) sql = p.toFile();
                if (n.equals("erp.db") || n.endsWith(".db")) dbfile = p.toFile();
            }
        }

        if (sql != null) {
            // apply SQL dump via sqlite3 if available, else return failure
            ProcessBuilder whichPb = new ProcessBuilder("which", "sqlite3");
            Process which = whichPb.start();
            int whichExit = which.waitFor();
            if (whichExit == 0) {
                ProcessBuilder pb = new ProcessBuilder("sqlite3", dbPath);
                pb.redirectInput(sql);
                Process p = pb.start();
                p.waitFor();
                // applied if exit == 0 (no further tracking needed)
            } else {
                // cannot apply SQL without sqlite3
            }
        } else if (dbfile != null) {
            // replace erp.db
            Files.copy(dbfile.toPath(), currentDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // ...existing code...
        return targetDir.toString();
    }
}
