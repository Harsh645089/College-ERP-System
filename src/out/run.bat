@echo off
cd /d "%~dp0"

echo Cleaning old classes...
for /r %%f in (*.class) do del "%%f"

echo Collecting sources...
dir /s /b *.java > sources.txt

echo Compiling...
javac --release 17 -cp "lib\*;." @sources.txt
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Running...
java -cp "lib\*;." login.UniversityERPApp

pause
