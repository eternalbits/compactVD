#!/bin/bash

fsck.ext4 -f /dev/sdb1
fsck.ext3 -f /dev/sdb2
fsck.ext2 -f /dev/sdb3
mnt=$(mktemp -d)
mkdir $mnt/media
mkdir $mnt/media/part1
mkdir $mnt/media/part2
mkdir $mnt/media/part3
mount -t ext4 /dev/sdb1 $mnt/media/part1
mount -t ext4 /dev/sdb2 $mnt/media/part2
mount -t ext2 /dev/sdb3 $mnt/media/part3
wd=$(pwd)
cd $mnt/media
find . -type f -exec md5sum "{}" \; > $wd/3ext.md5
cd - > /dev/null
umount /dev/sdb1
umount /dev/sdb2
umount /dev/sdb3
rm -fr $mnt

if [ $SUDO_USER ]; then chown $SUDO_USER:$(id -gn $SUDO_USER) 3ext.md5; fi
