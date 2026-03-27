@echo off
setlocal

rem === EDIT THIS LINE: path to your extracted JavaFX 22 Windows jmods folder ===
set JAVAFX_JMODS=C:\Users\Tharu\Downloads\openjfx-22.0.2_windows-x64_bin-jmods\javafx-jmods-22.0.2
set JPACKAGE="C:\Program Files\Java\jdk-22\bin\jpackage"

if not exist "%JAVAFX_JMODS%" (
    echo ERROR: JavaFX jmods not found at %JAVAFX_JMODS%
    echo Download from: https://gluonhq.com/products/javafx/
    echo Extract and set JAVAFX_JMODS at the top of this script.
    pause
    exit /b 1
)

echo [1/4] Building project...
if exist target\app-image rmdir /s /q target\app-image
if exist target\DocuBridge.zip del /q target\DocuBridge.zip
call mvn clean package -q
if errorlevel 1 (
    echo ERROR: Maven build failed.
    pause
    exit /b 1
)

echo [2/4] Copying app JAR and config to package-input...
copy /Y target\DocuBridge-1.0-SNAPSHOT.jar target\package-input\
if exist config.properties copy /Y config.properties target\package-input\

echo [3/4] Running jpackage (portable app-image, no install needed)...
%JPACKAGE% ^
  --type app-image ^
  --name DocuBridge ^
  --app-version 1.0 ^
  --input target\package-input ^
  --main-jar DocuBridge-1.0-SNAPSHOT.jar ^
  --main-class Group12.Launcher ^
  --module-path "%JAVAFX_JMODS%" ^
  --add-modules javafx.controls,javafx.fxml,javafx.web,java.sql,java.naming ^
  --java-options "--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" ^
  --dest target\app-image

if errorlevel 1 (
    echo ERROR: jpackage failed.
    pause
    exit /b 1
)

echo [4/4] Zipping app-image...
powershell -Command "Compress-Archive -Path 'target\app-image\DocuBridge' -DestinationPath 'target\DocuBridge.zip' -Force"

echo Done!
echo Portable app folder: target\app-image\DocuBridge\
echo Zip for sharing:     target\DocuBridge.zip
echo.
echo Teacher just unzips DocuBridge.zip and runs DocuBridge\DocuBridge.exe
echo No installation or Java needed.
pause
