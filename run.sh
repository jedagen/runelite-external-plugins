#!/bin/bash

# Build and run RuneLite with Music Playlists plugin
cd "$(dirname "$0")"

# Set up Java 11 path
export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@11"

echo "Building plugin..."
./gradlew shadowJar

if [ $? -eq 0 ]; then
    echo "Starting RuneLite with Music Playlists plugin..."
    java -ea -jar build/libs/example-1.0-SNAPSHOT-all.jar
else
    echo "Build failed!"
    exit 1
fi

