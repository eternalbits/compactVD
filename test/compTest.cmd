@echo off

echo Expanding 3hfs.vdi...
7z x -y 3hfs.vdi.xz > nul
echo .
echo .
java -jar compTest.jar COPY 3hfs.vdi out.vdi
java -jar compTest.jar MD5VDI out.vdi B23F9FE37B5576C1539C17BC0B002197
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vdi
echo .
echo .
java -jar -Dcrash=header compTest.jar INLINE 3hfs.vdi N
java -jar -Dstop=33x compTest.jar INLINE 3hfs.vdi N
java -jar -Dstop=44  compTest.jar INLINE 3hfs.vdi N
java -jar -Dstop=55x compTest.jar INLINE 3hfs.vdi N
java -jar compTest.jar INLINE 3hfs.vdi N
java -jar compTest.jar MD5VDI 3hfs.vdi CB529F214326E6B86CEC99B503D7DBF1
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar -Dcrash=table compTest.jar INLINE 3hfs.vdi +Z
java -jar compTest.jar INLINE 3hfs.vdi +Z
java -jar compTest.jar MD5VDI 3hfs.vdi 2A02D7728BC5FB4EA361D4EED449A403
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar compTest.jar COPY 3hfs.vdi out.vdi
java -jar compTest.jar MD5VDI out.vdi B23F9FE37B5576C1539C17BC0B002197
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vdi
del 3hfs.vdi
echo .
echo .

echo Expanding 3ext.vmdk...
7z x -y 3ext.vmdk.xz > nul
echo .
echo .
java -jar compTest.jar COPY 3ext.vmdk out.vmdk
java -jar compTest.jar MD5VMDK out.vmdk 1D1E035F342383D8167ADE22E6B6E6DA
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vmdk
echo .
echo .
java -jar -Dcrash=header compTest.jar INLINE 3ext.vmdk N
java -jar -Dstop=33x compTest.jar INLINE 3ext.vmdk N
java -jar -Dstop=33 compTest.jar INLINE 3ext.vmdk N
java -jar -Dcrash=table compTest.jar INLINE 3ext.vmdk N
java -jar compTest.jar INLINE 3ext.vmdk N
java -jar compTest.jar MD5VMDK 3ext.vmdk 57335C94DBC1FAD7B1F322645D4115DE
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar compTest.jar INLINE 3ext.vmdk +Z
java -jar compTest.jar MD5VMDK 3ext.vmdk 57335C94DBC1FAD7B1F322645D4115DE
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar compTest.jar COPY 3ext.vmdk out.vmdk
java -jar compTest.jar MD5VMDK out.vmdk 1D1E035F342383D8167ADE22E6B6E6DA
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vmdk
del 3ext.vmdk
echo .
echo .

echo Expanding 3ext.vdi...
7z x -y 3ext.vdi.xz > nul
echo .
echo .
java -jar compTest.jar COPY 3ext.vdi out.vdi
java -jar compTest.jar MD5VDI out.vdi 4768E137452A32C00AE89FBF661529D9
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vdi
echo .
echo .
java -jar -Dcrash=header compTest.jar INLINE 3ext.vdi N
java -jar -Dstop=22 compTest.jar INLINE 3ext.vdi N
java -jar -Dstop=44x compTest.jar INLINE 3ext.vdi N
java -jar -Dstop=66 compTest.jar INLINE 3ext.vdi N
java -jar compTest.jar INLINE 3ext.vdi N
java -jar compTest.jar MD5VDI 3ext.vdi 8DCE50AE3C96CFFDD14E8C8368F792C0
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar -Dcrash=table compTest.jar INLINE 3ext.vdi +Z
java -jar compTest.jar INLINE 3ext.vdi +Z
java -jar compTest.jar MD5VDI 3ext.vdi B7A88EE6384FB6064CBADC2509FED56B
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar compTest.jar COPY 3ext.vdi out.vdi
java -jar compTest.jar MD5VDI out.vdi 4768E137452A32C00AE89FBF661529D9
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vdi
del 3ext.vdi
echo .
echo .

echo Expanding 3lvm.vdi...
7z x -y 3lvm.vdi.xz > nul
echo .
echo .
java -jar compTest.jar COPY 3lvm.vdi out.vdi
java -jar compTest.jar MD5VDI out.vdi 009D8EF0F43CF3342ED1E6785DFEFFE2
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vdi
echo .
echo .
java -jar -Dcrash=header compTest.jar INLINE 3lvm.vdi N
java -jar -Dstop=33x compTest.jar INLINE 3lvm.vdi N
java -jar -Dstop=88 compTest.jar INLINE 3lvm.vdi N
java -jar compTest.jar INLINE 3lvm.vdi N
java -jar compTest.jar MD5VDI 3lvm.vdi D5A26F0970F474BE2ECB3318282F21D0
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar -Dcrash=table compTest.jar INLINE 3lvm.vdi +Z
java -jar compTest.jar INLINE 3lvm.vdi +Z
java -jar compTest.jar MD5VDI 3lvm.vdi D5A26F0970F474BE2ECB3318282F21D0
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
echo .
echo .
java -jar compTest.jar COPY 3lvm.vdi out.vdi
java -jar compTest.jar MD5VDI out.vdi 009D8EF0F43CF3342ED1E6785DFEFFE2
if errorlevel 1 echo ************* MD5 CHECK MISMATCH *************
del out.vdi
del 3lvm.vdi
echo .
echo .
