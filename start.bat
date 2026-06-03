@echo off
title Iniciando Vision+
echo ==============================================
echo        INICIANDO SERVIDORES DE VISION+
echo ==============================================
echo.
echo Liberando puertos 5000 y 5173 si estan ocupados...

:: Liberar puerto 5000 (Backend)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5000 ^| findstr LISTENING') do (
    echo Cerrando proceso en puerto 5000 con PID %%a
    taskkill /f /pid %%a >nul 2>&1
)

:: Liberar puerto 5173 (Frontend Dev)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5173 ^| findstr LISTENING') do (
    echo Cerrando proceso en puerto 5173 con PID %%a
    taskkill /f /pid %%a >nul 2>&1
)

:: Liberar puerto 8080 (Pluto Scraper)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    echo Cerrando proceso en puerto 8080 con PID %%a
    taskkill /f /pid %%a >nul 2>&1
)

echo.
echo Los servidores estan cargando en segundo plano...
echo Tu navegador se abrira automaticamente en unos segundos.
echo.
echo [NOTA] Manten esta ventana abierta mientras uses la app.
echo ==============================================
echo.

:: Iniciar el temporizador para abrir el navegador despues de 4 segundos
start /b cmd /c "timeout /t 4 >nul && start http://localhost:5173"

:: Iniciar los servidores del proyecto (Backend + Frontend)
npm run dev
