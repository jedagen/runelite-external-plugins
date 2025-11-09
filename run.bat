@echo off
setlocal enabledelayedexpansion

REM Change to the script's directory
cd /d "%~dp0"

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo Java is installed.
    goto :run_jar
)

echo Java is not installed. Attempting to install...

REM Try to install Java using winget (Windows Package Manager)
where winget >nul 2>&1
if %errorlevel% equ 0 (
    echo Installing Java 11 using winget...
    winget install --id=Microsoft.OpenJDK.11 --silent --accept-package-agreements --accept-source-agreements
    if %errorlevel% equ 0 (
        echo Java installed successfully!
        REM Refresh PATH for current session
        call refreshenv >nul 2>&1
        if %errorlevel% neq 0 (
            REM If refreshenv doesn't work, try to find Java in common locations
            if exist "%ProgramFiles%\Microsoft\jdk-11*" (
                set "PATH=%ProgramFiles%\Microsoft\jdk-11*\bin;%PATH%"
            )
            if exist "%ProgramFiles(x86)%\Microsoft\jdk-11*" (
                set "PATH=%ProgramFiles(x86)%\Microsoft\jdk-11*\bin;%PATH%"
            )
        )
        goto :check_java_again
    )
)

REM Try Chocolatey if winget failed
where choco >nul 2>&1
if %errorlevel% equ 0 (
    echo Installing Java 11 using Chocolatey...
    choco install openjdk11 -y
    if %errorlevel% equ 0 (
        echo Java installed successfully!
        call refreshenv >nul 2>&1
        goto :check_java_again
    )
)

REM If both failed, provide manual instructions
echo.
echo ========================================
echo Java installation failed automatically.
echo ========================================
echo.
echo Please install Java 11 manually:
echo 1. Download from: https://adoptium.net/temurin/releases/?version=11
echo 2. Or use winget manually: winget install Microsoft.OpenJDK.11
echo 3. After installing, restart this script.
echo.
pause
exit /b 1

:check_java_again
REM Verify Java is now available
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo Java installation completed but not found in PATH.
    echo Please restart your command prompt or computer and try again.
    echo.
    pause
    exit /b 1
)

:run_jar
echo.
REM Look for JAR file in the same directory as this batch file
set "JAR_FILE=%~dp0example-1.0-SNAPSHOT-all.jar"

REM If exact name not found, try to find any JAR file in the same directory
if not exist "!JAR_FILE!" (
    for %%f in ("%~dp0*.jar") do (
        set "JAR_FILE=%%f"
        goto :jar_found
    )
)

:jar_found
if not exist "!JAR_FILE!" (
    echo.
    echo ERROR: JAR file not found!
    echo Please make sure the JAR file is in the same folder as this batch file.
    echo Expected name: example-1.0-SNAPSHOT-all.jar
    echo.
    pause
    exit /b 1
)

echo Starting RuneLite with Music Playlists plugin...
echo.
java -ea -jar "!JAR_FILE!"

if %errorlevel% neq 0 (
    echo.
    echo An error occurred while running RuneLite.
    pause
)

