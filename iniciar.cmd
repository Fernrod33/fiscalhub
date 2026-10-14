@echo off
rem Atalho para o iniciar.ps1 (permite rodar com dois cliques, sem mudar a ExecutionPolicy).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar.ps1" %*
if errorlevel 1 pause
