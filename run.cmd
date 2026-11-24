@echo off
rem Starts the simulator on Windows without changing the PowerShell script policy.
rem Double-click it, or run: run.cmd --sport
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
if errorlevel 1 pause
