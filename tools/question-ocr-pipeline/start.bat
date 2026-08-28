@echo off
setlocal
cd /d "%~dp0"
python -c "import pypdf" >nul 2>nul || python -m pip install -r requirements.txt
python app.py
endlocal
