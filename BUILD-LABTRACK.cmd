@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0frontend"
if errorlevel 1 goto fail
if not exist node_modules\@angular\cli\bin\ng.js (
 call npm.cmd ci --cache .npm-cache --no-audit --no-fund
 if errorlevel 1 goto fail
)
call npm.cmd run build
if errorlevel 1 goto fail
cd /d "%~dp0"
node scripts\copy-frontend.mjs
if errorlevel 1 goto fail
call mvn.cmd -B package
if errorlevel 1 goto fail
echo THANH CONG. Mo CHAY-LABTRACK.cmd de chay website.
pause
exit /b 0
:fail
echo Build chua thanh cong. Xem loi o tren.
pause
exit /b 1
