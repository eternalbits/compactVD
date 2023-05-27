#### Building compactVD

The files `.classpath`, `.project` `build.xml` and `.settings\` are specific
 to edit compactVD with the Eclipse IDE and Ant build, and can be ignored
 in other environments. The Ant Build must be run in the same JRE as the
 workspace, this is an Ant Build setting.

#### The compactTU project

The compactTU project is a test unit for compactVD, to check if there are
 no obvious regressions and to do some stress tests. There is one script
 `compTest` and some test cases `*.bz2` in the `../test` folder. Examples:

`java -jar -Dcrash=header compTest.jar INLINE <image> NZ`  
writes random bytes to the image header and exits. It is expected that
 the image header is recovered at next call.

`java -jar -Dstop=33x compTest.jar INLINE <image> NZ`  
exits before the operation reaches 33%, like with an abnormal
 termination.

`java -jar -Dstop=44 compTest.jar INLINE <image> NZ`  
interrupts the operation before 44%, as if interrupted by the user.

`java -jar compTest.jar MD5VDI <image> <expected md5>`  
compares a modified md5 sum of the image with the expected result.
