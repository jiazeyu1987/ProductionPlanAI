@echo off
setlocal
set ROOT_DIR=%~dp0
set STACK_SCRIPT=%ROOT_DIR%code\scripts\run_stack.ps1

if not exist "%STACK_SCRIPT%" (
  echo [ERROR] not found: %STACK_SCRIPT%
  exit /b 1
)

powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%STACK_SCRIPT%" %*
set EXIT_CODE=%ERRORLEVEL%
endlocal & exit /b %EXIT_CODE%
