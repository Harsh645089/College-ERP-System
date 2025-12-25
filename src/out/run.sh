#!/bin/bash
cd "$(dirname "$0")"

# clean old classes
find . -name "*.class" -type f -delete

# collect sources
find . -name "*.java" > sources.txt

# compile
javac --release 17 -cp "lib/*:." @sources.txt

# run
# Prevent noisy GTK module load message and filter ScreencastHelper native-load warnings from stderr.
# Unset GTK_MODULES so GTK won't attempt to load the canberra module, and filter matching stderr lines.
GTK_MODULES='' java -cp "lib/*:." login.UniversityERPApp 2> >(grep -v -E "canberra-gtk-module|ScreencastHelper" >&2)
