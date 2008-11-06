
; -------------------------------
; Start
 
  !define MUI_FILE "ECUxPlot"
  ; !define VERSION "0.9r0.4"
 
  CRCCheck On
  !include "${NSISDIR}\Contrib\Modern UI\System.nsh"
 
 
;--------------------------------
;General
 
  OutFile "${MUI_FILE}-installer-${VERSION}.exe"
  ShowInstDetails "nevershow"
  ShowUninstDetails "nevershow"
  ;SetCompressor "bzip2"
 
  !define MUI_ICON "${MUI_FILE}.ico"
  !define MUI_UNICON "${MUI_FILE}.ico"
  ; !define MUI_SPECIALBITMAP "Bitmap.bmp"
 
 
;--------------------------------
;Folder selection page
 
  InstallDir "$PROGRAMFILES\${MUI_FILE}"
 
 
;--------------------------------
;Modern UI Configuration
 
  !define MUI_WELCOMEPAGE  
  !define MUI_LICENSEPAGE
  !define MUI_DIRECTORYPAGE
  !define MUI_ABORTWARNING
  !define MUI_UNINSTALLER
  !define MUI_UNCONFIRMPAGE
  !define MUI_FINISHPAGE  
 
 
;--------------------------------
;Language
 
  !insertmacro MUI_LANGUAGE "English"
 
 
;-------------------------------- 
;Modern UI System
 
;  !insertmacro MUI_SYSTEM 
 
 
;--------------------------------
;Data
 
  LicenseData "gpl-3.0.txt"
 
 
;-------------------------------- 
;Installer Sections     
Section "install" InstallationInfo
 
;Add files
  SetOutPath "$INSTDIR"
 
  File "${MUI_FILE}.exe"
  File "${MUI_FILE}.sh"
  File "${MUI_FILE}-${VERSION}.jar"
  File "jcommon-1.0.12.jar"
  File "jfreechart-1.0.9.jar"
  File "opencsv-1.8.jar"
  File "applib.jar"
  File "AppleJavaExtensions.jar"
  File "version.txt"
  File "gpl-3.0.txt"
 
;create desktop shortcut
  CreateShortCut "$DESKTOP\${MUI_FILE}.lnk" "$INSTDIR\${MUI_FILE}.exe" ""
 
;create start-menu items
  CreateDirectory "$SMPROGRAMS\${MUI_FILE}"
  CreateShortCut "$SMPROGRAMS\${MUI_FILE}\Uninstall.lnk" "$INSTDIR\Uninstall.exe" "" "$INSTDIR\Uninstall.exe" 0
  CreateShortCut "$SMPROGRAMS\${MUI_FILE}\${MUI_FILE}.lnk" "$INSTDIR\${MUI_FILE}.exe" "" "$INSTDIR\${MUI_FILE}.exe" 0
 
;write uninstall information to the registry
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${MUI_FILE}" "DisplayName" "${MUI_FILE} version ${VERSION} (remove only)"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${MUI_FILE}" "UninstallString" "$INSTDIR\Uninstall.exe"
 
  WriteUninstaller "$INSTDIR\Uninstall.exe"
 
SectionEnd
 
 
;--------------------------------    
;Uninstaller Section  
Section "Uninstall"
 
;Delete Files 
  RMDir /r "$INSTDIR\*.*"    
 
;Remove the installation directory
  RMDir "$INSTDIR"
 
;Delete Start Menu Shortcuts
  Delete "$DESKTOP\${MUI_FILE}.lnk"
  Delete "$SMPROGRAMS\${MUI_FILE}\*.*"
  RmDir  "$SMPROGRAMS\${MUI_FILE}"
 
;Delete Uninstaller And Unistall Registry Entries
  DeleteRegKey HKEY_LOCAL_MACHINE "SOFTWARE\${MUI_FILE}"
  DeleteRegKey HKEY_LOCAL_MACHINE "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${MUI_FILE}"  
 
SectionEnd
 
 
;--------------------------------    
;MessageBox Section
 
 
;Function that calls a messagebox when installation finished correctly
Function .onInstSuccess
  MessageBox MB_OK "You have successfully installed ${MUI_FILE} version ${VERSION}. Use the desktop icon to start the program."
FunctionEnd
 
 
Function un.onUninstSuccess
  MessageBox MB_OK "You have successfully uninstalled ${MUI_FILE} version ${VERSION}."
FunctionEnd
 
 
;eof
