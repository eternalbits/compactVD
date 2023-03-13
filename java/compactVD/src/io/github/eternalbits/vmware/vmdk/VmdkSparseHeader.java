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

package io.github.eternalbits.vmware.vmdk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

class VmdkSparseHeader {
	static final int HEADER_SIZE = 512;
	static final int SECTOR_SIZE = 512;
	static final long SECTOR_LONG = 512L;
	
	private static final int SPARSE_MAGICNUMBER = 0x564D444B; // VMDK
	private static final int END_LINE_CHECK = 0x0A0D200A; // \n \r\n
	
	private static final int DEFAULT_GRAIN_SIZE = 128;
	
	private static final int BITMASK_NEW_LINE_DETECTION = 1;
	private static final int BITMASK_REDUNDANT_GRAIN_TABLE = 2;
// TODO: Version 2 Hosted Sparse Extents. Zeroed grains are not expected in base images.
//	private static final int BITMASK_ZEROED_GRAIN_GTE = 4;
	private static final short COMPRESSION_NONE = 0;
	
	final VmdkDiskImage image;			// Parent object
	
	/* VMware Virtual Disk Format 1.1, Virtual Disk Format 5.0
	 *	http://www.vmware.com/app/vmdk/?src=vmdk, https://www.vmware.com/support/developer/vddk/vmdk_50_technote.pdf?src=vmdk
	 */
	int		magicNumber;				// VMDK
	int		version;					// 1, 2
	int		flags;						// Only new line detection and redundant grain table are expected.
	long	capacity;					// Capacity of this extent in sectors - should be a multiple of the grain size.
	long	grainSize;					// The size of a grain in sectors. Must be a power of 2 and greater than 8.
	long	descriptorOffset;			// Offset, in sectors, of the descriptor. Set to zero if there is no descriptor.
	long	descriptorSize;				// Is valid only if descriptorOffset is non-zero. It is expressed in sectors.
	int		numGTEsPerGT;				// Number of entries in a grain table. It is 512 for VMware virtual disks.
	long	rgdOffset;					// Points to the redundant level 0 of metadata. It is expressed in sectors.
	long	gdOffset;					// Points to the level 0 of metadata. It is expressed in sectors.
	long	overHead;					// The number of sectors occupied by the metadata.
	byte	uncleanShutdown;			// If it is 1, the disk must be checked for consistency.
	int		endLineChars;				// To detect FTP transfer in text mode.
	short	compressAlgorithm;			// Grain compression is not supported.
	byte[]	pad;						// 433 bytes
	
	/* Computed values
	 */
	long	diskSize;					// Disk size expressed in bytes.
	int		blockSize;					// Grain size expressed in bytes.
	int 	gteCount; 					// Total number of grain table entries (GTE).
	int 	gdeCount;					// Total number of grain directory entries (GDE).
	long 	rgtOffset; 					// Points to the redundant level 1 of metadata. It is expressed in sectors.
	long 	gtOffset; 					// Points to the level 1 of metadata. It is expressed in sectors.
	int 	nextSector; 				// Sector number where the next block of data will be written.
	int		firstSector;				// First sector for data blocks = overHead as integer.
	int		grainSectors;				// Grain size in sectors = grainSize as integer.
	int 	fileType;					// 1 = VirtualBox, 2 = VMware.
//	VirtualBox
	UUID	uuidImage;					// UUID of image.
	UUID	uuidModification;			// UUID of image's last modification.
	UUID	uuidParent;					// UUID of previous parent, only for secondary images.
	UUID	uuidParentModification;		// UUID of previous parent's last modification.
//	VMware	
	Integer	imageCID;					// It is a random 32-bit value.
	Integer	parentCID;					// A link trough the image CID of the parent link.
	String	fileName;					// The file name, as specified.
	String	parentFileName;				// The file name of the previous parent.
	
	VmdkSparseHeader(VmdkDiskImage vmdk, long diskSize) {
		if (diskSize < 0 || diskSize % SECTOR_SIZE != 0)
			throw new IllegalArgumentException(String.format("Disk size: %d must be multiple of %d", diskSize, SECTOR_SIZE));
		
		this.image 			= vmdk;
		this.diskSize		= diskSize;
		this.blockSize		= DEFAULT_GRAIN_SIZE * SECTOR_SIZE;

		magicNumber			= SPARSE_MAGICNUMBER;
		version				= 1;
		flags				= BITMASK_NEW_LINE_DETECTION | BITMASK_REDUNDANT_GRAIN_TABLE;
		grainSize			= DEFAULT_GRAIN_SIZE;
		capacity			= Static.ceilDiv(diskSize, blockSize) * grainSize;
		descriptorOffset	= 1;
		descriptorSize		= 20;
		numGTEsPerGT		= 512;
		rgdOffset			= descriptorOffset + descriptorSize;
		gteCount			= (int)(capacity / grainSize);
		gdeCount			= (int)Static.ceilDiv(gteCount, numGTEsPerGT);
		long tabSize		= Static.ceilDiv(gteCount, SECTOR_SIZE/4);
		long dirSize		= Static.ceilDiv(gdeCount, SECTOR_SIZE/4);
		gdOffset			= rgdOffset + dirSize + tabSize;
		overHead			= Static.roundUp(gdOffset + dirSize + tabSize, grainSize);
		rgtOffset			= rgdOffset + dirSize;
		gtOffset			= gdOffset + dirSize;
		grainSectors		= (int)grainSize;
		firstSector			= (int)overHead;
		nextSector			= firstSector;
		uncleanShutdown		= 0;
		endLineChars		= END_LINE_CHECK;
		compressAlgorithm	= COMPRESSION_NONE;
		pad					= new byte[0];
	}
	
	VmdkSparseHeader(VmdkDiskImage vmdk, ByteBuffer in) throws IOException, WrongHeaderException {
		this.image = vmdk;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(VmdkDiskImage.BYTE_ORDER);
			
			magicNumber			= in.getInt();
			version				= in.getInt();
			flags				= in.getInt();
			capacity			= in.getLong();
			grainSize			= in.getLong();
			descriptorOffset	= in.getLong();
			descriptorSize		= in.getLong();
			numGTEsPerGT		= in.getInt();
			rgdOffset			= in.getLong();
			gdOffset			= in.getLong();
			overHead			= in.getLong();
			uncleanShutdown		= in.get();
			endLineChars		= in.getInt();
			compressAlgorithm	= in.getShort();
			pad					= Static.getReservedBytes(in, 433);
			
			long length			= image.getMedia().length();
			
			if (magicNumber == SPARSE_MAGICNUMBER // expecting a "standard" VMDK with flags = 3
					&& version == 1 && flags == (BITMASK_NEW_LINE_DETECTION | BITMASK_REDUNDANT_GRAIN_TABLE) 
					&& capacity > 0 && Static.isPower2(grainSize) && grainSize > 8 
					&& descriptorOffset > 0 && descriptorSize > 0
					&& rgdOffset >= descriptorOffset + descriptorSize
					&& gdOffset > rgdOffset
					&& overHead > gdOffset
					&& numGTEsPerGT == 512
					&& uncleanShutdown == 0
					&& endLineChars == END_LINE_CHECK
					&& compressAlgorithm == COMPRESSION_NONE
					&& (int)grainSize == grainSize
					&& (int)overHead == overHead
					&& capacity % grainSize == 0
					&& overHead % grainSize == 0
					&& length % SECTOR_LONG == 0) {
				
				diskSize			= -1; // In embedded descriptor
				blockSize			= (int)(grainSize * SECTOR_LONG);
				gteCount			= (int)(capacity / grainSize);
				gdeCount			= (int)Static.ceilDiv(gteCount, numGTEsPerGT);
				long tabSize		= Static.ceilDiv(gteCount, SECTOR_SIZE/4);
				long dirSize		= Static.ceilDiv(gdeCount, SECTOR_SIZE/4);
				rgtOffset			= getTableOffset(rgdOffset);
				gtOffset			= getTableOffset(gdOffset);
				grainSectors		= (int)grainSize;
				firstSector			= (int)overHead;
				nextSector			= (int)(length / SECTOR_LONG);
				
				if (blockSize == grainSize * SECTOR_LONG
						&& gteCount == capacity / grainSize 
						&& nextSector == length / SECTOR_LONG 
						&& nextSector % grainSize == 0
						&& rgtOffset >= rgdOffset + dirSize
						&& gdOffset >= rgtOffset + tabSize
						&& gtOffset >= gdOffset + dirSize
						&& overHead >= gtOffset + tabSize
						&& nextSector >= overHead) {
					return;
				}
			}
		}
		
		throw new WrongHeaderException(getClass(), image.toString());
	}
	
	private long getTableOffset(long dirOffset) throws IOException {
		return image.readMetadata(dirOffset, 4).order(VmdkDiskImage.BYTE_ORDER).getInt();
	}
	
	int getFileType() {
		fileType = 0;
		final UUID zeroUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
		if (uuidImage != null && uuidParent != null && uuidModification != null && uuidParentModification != null)
			fileType |= uuidParent.equals(zeroUUID) && uuidParentModification.equals(zeroUUID) ? 0 : 1;
		if (imageCID != null && parentCID != null && fileName != null && parentFileName != null)
			fileType |= parentCID == -1 ? 0 : 2;
		return fileType;
	}
	
	long getUpdateOffset() {
		return 0;
	}
	
	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[HEADER_SIZE];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VmdkDiskImage.BYTE_ORDER);
		
		bb.putInt(magicNumber);
		bb.putInt(version);
		bb.putInt(flags);
		bb.putLong(capacity);
		bb.putLong(grainSize);
		bb.putLong(descriptorOffset);
		bb.putLong(descriptorSize);
		bb.putInt(numGTEsPerGT);
		bb.putLong(rgdOffset);
		bb.putLong(gdOffset);
		bb.putLong(overHead);
		bb.put(uncleanShutdown);
		bb.putInt(endLineChars);
		bb.putShort(compressAlgorithm);
		bb.put(pad);
		
		return buffer;
	}

	void update() throws IOException {
		image.getMedia().seek(getUpdateOffset());
		image.getMedia().write(getUpdateBuffer());
	}
	
}
