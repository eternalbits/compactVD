/*
 * Copyright 2016 Rui Baptista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eternalbits.windos.vhd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

public class VhdDiskFooter {
	static final int FOOTER_SIZE = 512;
	static final int DYNAMIC_HARD_DISK = 3;

	private static final long STD_COOKIE = 0x636F6E6563746978L;	// Some applications want "conectix" here
	private static final int MY_SIGNATURE = 0x63766420;			// "cvd "
	private static final int MY_HOST = 0x4A617661;				// "Java"
	
	private static final int FEATURES_RESERVED = 2;				// Always set
	private static final int CURRENT_VERSION = 0x10000;			// Version 1.0
	private static final int JANUARY_1_2000 = 946684800;		// Seconds since January 1, 1970
	
	private static final int SECTOR_SIZE = VhdDiskImage.SECTOR_SIZE;
	private final VhdDiskImage image;							// Parent object

	/* Virtual Hard Disk Image Format Specification
	 *	https://technet.microsoft.com/en-us/virtualization/bb676673.aspx
	 */
	long	cookie;					// Original creator of the hard disk image.
	int		features;				// The second bit must always be set to 1. 
	int		fileFormatVersion;		// For the current specification this field must be 0x10000.
	long	dataOffset;				// Absolute byte offset, from the beginning of the file, to the header.
	int		timeStamp;				// Number of seconds since January 1, 2000 12:00:00 AM in UTC/GMT
	int		creatorApplication;		// Signature of the application that created the image.
	int		creatorVersion;			// Major/minor version of the application that created the image.
	int		creatorHostOS;			// Type of host operating system this disk image is created on.
	long	originalSize;			// Size of the hard disk, in bytes, at creation time.
	long	currentSize;			// Current size of the hard disk, in bytes.
	int		diskGeometry;			// Cylinder, heads, and sectors per track for the hard disk. 
	int		diskType;				// Fixed = 2, Dynamic = 3, Differencing = 4.
	int		checksum;				// One's complement of the sum of all bytes, without checksum field.
	UUID	uniqueId;				// UUID identifier of the disk image.
	byte	savedState;				// Saved state = 1. Compaction cannot be performed in saved state. 
	byte[]	reserved;				// This field contains 427 zeros.
	
	VhdDiskFooter(VhdDiskImage vhd, long diskSize) {
		if (diskSize < 0 || diskSize % SECTOR_SIZE != 0)
			throw new IllegalArgumentException(String.format("Disk size: %d must be multiple of %d", diskSize, SECTOR_SIZE));
				
		this.image 			= vhd;
		
		cookie 				= STD_COOKIE;
		features 			= FEATURES_RESERVED;
		fileFormatVersion	= CURRENT_VERSION;
		dataOffset			= FOOTER_SIZE;
		timeStamp			= (int)(System.currentTimeMillis() / 1000 - JANUARY_1_2000);
		creatorApplication	= MY_SIGNATURE;
		creatorVersion		= CURRENT_VERSION;
		creatorHostOS		= MY_HOST;
		originalSize		= diskSize;
		currentSize			= diskSize;
		diskGeometry		= getDiskGeometry(diskSize / SECTOR_SIZE);
		diskType			= DYNAMIC_HARD_DISK;
		checksum			= 0;
		uniqueId			= UUID.randomUUID();
		savedState			= 0;
		reserved			= new byte[0];
	}
	
	VhdDiskFooter(VhdDiskImage vhd, ByteBuffer in) throws IOException, WrongHeaderException {
		this.image = vhd;
		
		if (in.remaining() >= FOOTER_SIZE - 1) { // 511-byte disk footer
			in.order(VhdDiskImage.BYTE_ORDER);
			
			cookie				= in.getLong();
			features 			= in.getInt();
			fileFormatVersion	= in.getInt();
			dataOffset			= in.getLong();
			timeStamp			= in.getInt();
			creatorApplication	= in.getInt();
			creatorVersion		= in.getInt();
			creatorHostOS		= in.getInt();
			originalSize		= in.getLong();
			currentSize			= in.getLong();
			diskGeometry		= in.getInt();
			diskType			= in.getInt();
			checksum			= in.getInt();
			uniqueId			= new UUID(in.getLong(), in.getLong());
			savedState			= in.get();
			reserved			= Static.getReservedBytes(in, 427 - 1);
			
			if ((features & FEATURES_RESERVED) != 0 && fileFormatVersion == CURRENT_VERSION
					&& dataOffset > 0 && dataOffset % SECTOR_SIZE == 0
					&& dataOffset < image.getMedia().length()) {
				
				return; //Too early for InitializationException, check disk type in header
				
			}
		}
		
		throw new WrongHeaderException(getClass(), image.toString());
	}
	
	private int getDiskGeometry(long totalSectors) {
		int cylinders, heads, sectorsPerTrack;
		long cylinderTimesHeads;
		if (totalSectors > 65535 * 16 * 255) {
			totalSectors = 65535 * 16 * 255;
		}
		if (totalSectors >= 65535 * 16 * 63) {
			sectorsPerTrack = 255;
			cylinderTimesHeads = totalSectors / sectorsPerTrack;
			heads = 16;
		} else {
			sectorsPerTrack = 17;
			cylinderTimesHeads = totalSectors / sectorsPerTrack;
			heads = Math.max(4, (int)((cylinderTimesHeads + 1023) / 1024));
			if (cylinderTimesHeads >= heads * 1024 || heads > 16) {
				sectorsPerTrack = cylinderTimesHeads >= heads * 1024? 63: 31;
				cylinderTimesHeads = totalSectors / sectorsPerTrack;
				heads = 16;
			}
		}
		cylinders = (int)(cylinderTimesHeads / heads);
		return cylinders << 16 | (heads & 0xFF) << 8 | (sectorsPerTrack & 0xFF);
	}
	
	long getUpdateOffset(boolean heading) {
		return heading? 0: image.header.getFooterOffset();
	}
	
	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[FOOTER_SIZE];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VhdDiskImage.BYTE_ORDER);
		
		bb.putLong(cookie);
		bb.putInt(features);
		bb.putInt(fileFormatVersion);
		bb.putLong(dataOffset);
		bb.putInt(timeStamp);
		bb.putInt(creatorApplication);
		bb.putInt(creatorVersion);
		bb.putInt(creatorHostOS);
		bb.putLong(originalSize);
		bb.putLong(currentSize);
		bb.putInt(diskGeometry);
		bb.putInt(diskType);
		int p = bb.position();
		bb.putInt(checksum);
		bb.putLong(uniqueId.getMostSignificantBits());
		bb.putLong(uniqueId.getLeastSignificantBits());
		bb.put(savedState);
		bb.put(Arrays.copyOf(reserved, 427));
		
		bb.putInt(p, VhdDiskImage.getChecksum(bb, FOOTER_SIZE, p));
		return buffer;
	}

	void update(boolean heading) throws IOException {
		image.getMedia().seek(getUpdateOffset(heading));
		image.getMedia().write(getUpdateBuffer());
	}
}
