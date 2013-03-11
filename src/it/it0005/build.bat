@echo off

cd C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\de-DE
del *.cab
"C:\Program Files (x86)\Microsoft SDKs\Windows\v7.0A\Bin\MsiDb.Exe" -d msiA-0.0.1-SNAPSHOT.msi -x localised_1031.cab

cd C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005

copy C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\en-US\msiA-0.0.1-SNAPSHOT.msi C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\msiA-0.0.1-SNAPSHOT.msi
cscript "C:\Data\Microsoft SDKs\Windows\v6.0\Samples\sysmgmt\msi\scripts\WiStream.vbs" C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\msiA-0.0.1-SNAPSHOT.msi C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\de-DE\localised_1031.cab localised_1031.cab
cscript "C:\Data\Microsoft SDKs\Windows\v6.0\Samples\sysmgmt\msi\scripts\WiSubStg.vbs" C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\msiA-0.0.1-SNAPSHOT.msi C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\de-DE\msiA-0.0.1-SNAPSHOT.mst 1031

cd C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005

rem cscript WiLangId.vbs "C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msp\target\Release\x86\en-US\msp-0.0.1-SNAPSHOT.msp" 
rem cscript "C:\Data\Microsoft SDKs\Windows\v6.0\Samples\sysmgmt\msi\scripts\WiLangId.vbs" "C:\Data\dev\maven-plugins\wix-sdk\wix-maven-plugin\src\it\it0005\msiA\target\Release\x86\msiA-0.0.1-SNAPSHOT.msi" 