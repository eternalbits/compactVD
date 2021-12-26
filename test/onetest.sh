#!/bin/sh

if [ $# -ne 4 ]; then
 echo "usage: ./$(basename $0) image_name VDI/VMDK/VHD compressed_md5 copied_md5"
 exit
fi
echo "Expanding $1..."
xz -dfk $1.xz
echo .
echo .
java -jar compTest.jar COPY $1 out.$2
java -jar compTest.jar MD5$2 out.$2 $3
if [ $? -eq 1 ]; then echo "************* MD5 CHECK MISMATCH *************"; fi
rm out.$2
echo .
echo .
java -jar -Dcrash=header compTest.jar INLINE $1 N
java -jar -Dstop=33x compTest.jar INLINE $1 N
java -jar -Dstop=66 compTest.jar INLINE $1 N
java -jar -Dcrash=table compTest.jar INLINE $1 N
java -jar compTest.jar INLINE $1 N
java -jar compTest.jar INLINE $1 +Z
java -jar compTest.jar MD5$2 $1 $4
if [ $? -eq 1 ]; then echo "************* MD5 CHECK MISMATCH *************"; fi
echo .
echo .
java -jar compTest.jar COPY $1 out.$2
java -jar compTest.jar MD5$2 out.$2 $3
if [ $? -eq 1 ]; then echo "************* MD5 CHECK MISMATCH *************"; fi
rm out.$2
rm $1
echo .
echo .
