# About Virtual Disk Compact and Copy

#### To reduce the size of virtual disk images

### How to Install CompactVD

You can find a brief description on the [`share`](https://github.com/eternalbits/compactVD/tree/master/share/)
 page. The installation of CompactVD must be done on the host-related part. Just
 drag a vdi or vmdk disk image into the main window.

### What is CompactVD?

CompactVD is a utility to optimize the size of dynamic disk images, based on
 the bitmap allocation tables of known file systems. You can check if there is
 wasted space in a dynamic disk image comparing the size of the disk image file
 with the space used by file systems inside the image. For a optimal resource
 allocation, they should not be very different.

Currently supported file systems are:
* New Technology File System (NTFS), for Windows computers
* Hierarchical or Apple File System (HFS+ or APFS), for macOS
* Extended File System (EXT), used in most linux desktops

Also reads BTRFS or XFS (Linux), but in these cases compression does not work.

### Compact Disk Image In Place

The compact operation pulls blocks of data from the end of the disk image to
 space that was detected to be not in use by file systems.

To begin a scan is made to detect blocks of data that are not in use by file
 systems, and the disk image structure is updated marking those blocks as if
 they were never used. Then blocks with effective data are moved from the end
 of the disk image to the space that was recovered by the previous operation.

Finally the image structure is updated to reflect the new position of the moved
 blocks, and the image file size is trimmed. Updates to the image structure are
 temporarily saved on the host media and will be written to the image at next
 program execution, in case of a recoverable hardware failure. 

### Copy to New Disk Image

The copy operation begins with a scan to detect blocks of data that are not in
 use by file systems. The new image is created ignoring those blocks, and blocks
 that would otherwise be completely filled with zeros.

The format of the new disk image can be select in the Copy Disk Image dialog with
 one these file extensions:
* VDI, Virtual Disk Image for Oracle VirtualBox
* VMDK, Virtual Machine Disk for VMware products
* VHD, Virtual Hard Disk for Microsoft products
* RAW, for a complete disk image

### Copyright Notices

Licensed under the Apache License, Version 2.0.

The application icon is a derivative work of original made by Susumu Yoshida
 (Copyright © 2009 McDo DESIGN.com).

The other icons that identify image disks in the view panel are copyright-protected
 computer icons of file formats. The author believes that the exhibition of icons
 to identify the file formats in question qualifies as fair use.
 