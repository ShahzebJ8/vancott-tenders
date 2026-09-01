@echo off
title Tender Scanner
cd /d "%~dp0"
".venv\Scripts\python.exe" "tenderscanpp.py"
if errorlevel 1 (
  echo.
  echo Could not start. Make sure this folder still contains the .venv folder.
  pause
)
