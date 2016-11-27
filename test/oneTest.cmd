@echo off

if "%4" == "" (
 echo usage: %~nx0 image_name VDI/VMDK compressed_md5 copied_md5
 exit /b
)
echo Expanding %1...
7z x -y %1.xz > nul
echo .
echo .
java -jar compTest.jar COPY %1 out.%2
java -jar compTest.jar MD5%2 out.%2 %3
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.%2
echo .
echo .
java -jar -Dcrash=header compTest.jar INLINE %1 N
java -jar -Dstop=33x compTest.jar INLINE %1 N
java -jar -Dstop=66 compTest.jar INLINE %1 N
java -jar -Dcrash=table compTest.jar INLINE %1 N
java -jar compTest.jar INLINE %1 N
java -jar compTest.jar INLINE %1 +Z
java -jar compTest.jar MD5%2 %1 %4
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar compTest.jar COPY %1 out.%2
java -jar compTest.jar MD5%2 out.%2 %3
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.%2
del %1
echo .
echo .
