@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
if errorlevel 1 goto fail
if not exist target\labtrack-0.1.0.jar (
 echo Can chay BUILD-LABTRACK.cmd truoc.
 goto fail
)
echo Mo http://127.0.0.1:8081 sau khi may chu khoi dong.
echo Nhan Ctrl+C de dung. Giu cua so nay mo khi su dung.
java -jar target\labtrack-0.1.0.jar
if errorlevel 1 goto fail
exit /b 0
:fail
pause
exit /b 1
