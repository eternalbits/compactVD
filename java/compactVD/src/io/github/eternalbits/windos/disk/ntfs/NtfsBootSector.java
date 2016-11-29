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

package io.github.eternalbits.windos.disk.ntfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

/**
 * The class {@code NtfsBootSector} represents the "NTFS Boot Sector" that contains
 *  the BIOS parameter block with information about the layout of the volume and
 *  the file system structures.
 * Sources: <a href="https://technet.microsoft.com/en-us/library/cc781134(v=ws.10).aspx"
 *  >How NTFS Works</a>, <a href="https://en.wikipedia.org/wiki/BIOS_parameter_block"
 *  >BIOS parameter block</a>.
 */
class NtfsBootSector {
	final static int BOOT_SIZE = 512;
	
	final NtfsFileSystem fileSystem;
	
	////	jump;				// Jump to bootstrap code (3 bytes).
	String	oemIdentifier;		// OEM ID: Usually NTFS (8 bytes).
	short	bytesPerSector;		// Bytes per logical sector, a power of 2. The most common value is 512.
	byte	sectorsPerCluster;	// Logical sectors per cluster, any power of 2 (1 to 128).
	short	reservedSectors;	// Count of reserved logical sectors. Must be zero in NTFS.
	byte	tablesCount;		// Number of File Allocation Tables. Almost always 2. Must be zero in NTFS.
	short	rootDirEntries;		// Number of old FAT root directory entries. Must be zero in FAT32 and NTFS.
	short	sectorsCount2;		// Total logical sectors (if zero, use sectorsCount4 for FAT). Must be zero in NTFS.
	byte	mediaDescriptor;	// Media descriptor. 0xF8 for fixed disk.
	short	sectorsPerTable;	// Logical sectors per File Allocation Table. Must be zero in FAT32 and NTFS.
	short	sectorsPerTrack;	// Physical sectors per track for disks with INT 13h CHS geometry. Ignored by NTFS.
	short	headsCount;			// Number of heads for disks with INT 13h CHS geometry. Ignored by NTFS.
	int		hiddenSectors;		// Count of hidden sectors preceding the partition (FAT16B). Ignored by NTFS.
	int		sectorsCount4;		// Total logical sectors (FAT16B). Must be zero in NTFS.
	byte	driveNumber;		// Physical drive number. Ignored by NTFS.
	byte	checkFlags;			// Flags. Ignored by NTFS.
	byte	signature;			// Extended boot signature: 0x80. Ignored by NTFS.
	byte	reserved1;			// Ignored by NTFS.
	long	sectorsCount;		// Number of sectors. Usually partition sectors minus 1 (last sector reserved for boot backup).
	long	masterCluster;		// Logical Cluster Number for the File $MFT.
	long	masterMirror;		// Logical Cluster Number for the File $MFTMirr.
	byte	encodedRS;			// The size of each record. Clusters count if positive, 2 raised to the absolute value if negative.
	////	reserved2;			// Not used by NTFS (3 bytes).
	byte	encodedIS;			// The size of each index buffer for directories. Computed like recordSize.
	////	reserved3;			// Not used by NTFS (3 bytes).
	long	serialNumber;		// Volume serial number.
	int		checksum;			// Checksum. Not used by NTFS.
	////	bootstrap;			// Bootstrap code (426 bytes).
	short	bootSignature;		// Boot record signature: 0x55AA.
	
	long 	clustersCount;		// Computed clusters count rounded down to nearest integer
	int 	clusterSize;		// Computed cluster size in bytes
	int 	recordSize;			// Computed record size
	int 	indexSize;			// Computed index size
	
	NtfsBootSector(NtfsFileSystem ntfs, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= ntfs;
		
		if (in.remaining() >= BOOT_SIZE) {
			in.order(NtfsFileSystem.BYTE_ORDER);
			
			in.position(in.position() +3);
			oemIdentifier 		= Static.getString(in, 8, StandardCharsets.US_ASCII);
			bytesPerSector 		= in.getShort();
			sectorsPerCluster 	= in.get();
			reservedSectors 	= in.getShort();
			tablesCount 		= in.get();
			rootDirEntries 		= in.getShort();
			sectorsCount2 		= in.getShort();
			mediaDescriptor 	= in.get();
			sectorsPerTable 	= in.getShort();
			sectorsPerTrack 	= in.getShort();
			headsCount 			= in.getShort();
			hiddenSectors 		= in.getInt();
			sectorsCount4 		= in.getInt();
			driveNumber 		= in.get();
			checkFlags 			= in.get();
			signature 			= in.get();
			reserved1 			= in.get();
			sectorsCount 		= in.getLong();
			masterCluster 		= in.getLong();
			masterMirror 		= in.getLong();
			encodedRS 			= in.get();
			in.position(in.position() +3);
			encodedIS 			= in.get();
			in.position(in.position() +3);
			serialNumber 		= in.getLong();
			checksum 			= in.getInt();
			in.position(in.position() +426);
			bootSignature 		= in.getShort();
			
			if (bytesPerSector >= 512 && bytesPerSector <= 8192
					&& Static.isPower2(bytesPerSector) && Static.isPower2(sectorsPerCluster&0xFF)
					&& reservedSectors == 0 && tablesCount == 0 && rootDirEntries == 0 
					&& sectorsCount2 == 0 && sectorsPerTable == 0 && sectorsCount4 == 0
					&& sectorsCount > 0) {
				
				clustersCount = sectorsCount / (sectorsPerCluster&0xFF);
				clusterSize = bytesPerSector * (sectorsPerCluster&0xFF);
				recordSize = decodeByte(encodedRS);
				indexSize = decodeByte(encodedIS);
				if (recordSize > 0 && indexSize > 0) {
					return;
				}
			}
		}
		
		throw new WrongHeaderException(getClass(), fileSystem.toString());
	}
	
	private int decodeByte(byte s) {
		if (s > 0) return bytesPerSector * (sectorsPerCluster&0xFF) * s;
		if (s < 0 && s > -31) return 1 << (-s);
		return -1;
	}
}
