@echo off
net session >nul 2>&1
if errorlevel 1 (
	echo You need to log in with an account that has administrator privileges.
	pause >nul
	goto:eof
)
set query=
for /f "skip=1 tokens=2*" %%i in ('reg query HKCR\jarfile\shellex\DropHandler /ve') do set "query=%%j"
echo The GUID {86C86720-42A0-1069-A2E8-08002B30309D} corresponds to the class identifier (CLSID) of the native Windows drag-and-drop handler (DropHandler). It is managed directly by the system's shell32.dll library. It is frequently mapped to the following Windows Registry (regedit) keys to enable Drag and Drop functionality for different file types.
echo .
if "%query%" == "{86C86720-42A0-1069-A2E8-08002B30309D}" (
	reg delete HKCR\jarfile\shellex
	pause >nul
) else (
	choice /c yn /n /m "Permanently add the registry key HKEY_CLASSES_ROOT\jarfile\shellex\DropHandler (Yes/No)?"
	if errorlevel 2 (
		echo The operation was canceled by the user.
		pause >nul
	) else (
		reg add HKCR\jarfile\shellex\DropHandler /t REG_SZ /d {86C86720-42A0-1069-A2E8-08002B30309D}
		echo We recommend restarting the system.
		pause >nul
	)
)
