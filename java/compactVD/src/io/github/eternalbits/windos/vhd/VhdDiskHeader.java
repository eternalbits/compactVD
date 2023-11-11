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
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

class VhdDiskHeader {
	static final int HEADER_SIZE = 1024;						// Dynamically growing base image file
	
	private static final long CXSPARSE = 0x6378737061727365L;	// "cxsparse"
	private static final int CURRENT_VERSION = 0x10000;			// Version 1.0
	private static final int DEFAULT_BLOCK_SIZE = 0x200000;		// 2 MB
	
	private static final int SECTOR_SIZE = VhdDiskImage.SECTOR_SIZE;
	private final VhdDiskImage image;							// Parent object

	/* Virtual Hard Disk Image Format Specification
	 *	https://www.microsoft.com/en-us/download/details.aspx?id=23850
	 */
	long	cookie;					// This field holds the value "cxsparse".
	long	dataOffset;				// Offset to the next structure. Should be set to 0xFFFFFFFF.
	long	tableOffset;			// Absolute byte offset of the Block Allocation Table (BAT).
	int		headerVersion;			// For the current specification this field must be 0x10000.
	int		maxTableEntries;		// Maximum entries present in the BAT.
	int		blockSize;				// The size, in bytes, of each block. Does not include the block bitmap.
	int		checksum;				// One's complement of the sum of all bytes, without checksum field.
	UUID	parentUniqueId;			// UUID of the parent hard disk, for differencing hard disks.
	int		parentTimeStamp;		// Modification time stamp of the parent hard disk image.
	int		reserved1;				// This field should be set to zero.
	byte[]	parentUnicodeName;		// Unicode string (UTF-16) of the parent hard disk filename.
	byte[]	parentLocators;			// For differencing disks. Should be set to zero for dynamic disks.
	byte[]	reserved2;				// This must be initialized to zeroes.
	
	/* Computed fields
	 */
	int		nextSector; 			// Sector number where the next block of data will be written.
	int		firstSector;			// First sector for data blocks.
	int		bitmapSectors;			// The size of the block bitmap, in sectors. 1 or 8 for blocks <= 2MB.
	int		blockSectors;			// The size of each block, in sectors, including the block bitmap.
	
	VhdDiskHeader(VhdDiskImage vhd, long diskSize) {
		this.image 			= vhd;

		cookie 				= CXSPARSE;
		dataOffset 			= ~0L;
		tableOffset			= VhdDiskFooter.FOOTER_SIZE + VhdDiskHeader.HEADER_SIZE;
		headerVersion		= CURRENT_VERSION;
		maxTableEntries		= (int)Static.ceilDiv(diskSize, DEFAULT_BLOCK_SIZE);
		blockSize			= DEFAULT_BLOCK_SIZE;
		checksum			= 0;
		parentUniqueId		= new UUID(0, 0);
		parentTimeStamp		= 0;
		reserved1			= 0;
		parentUnicodeName	= new byte[0];
		parentLocators		= new byte[0];
		reserved2			= new byte[0];

		bitmapSectors		= 1;
		firstSector			= (int) Static.ceilDiv(tableOffset + maxTableEntries * 4L, SECTOR_SIZE);
		blockSectors		= blockSize / SECTOR_SIZE + bitmapSectors;
		nextSector			= firstSector;
	}
	
	VhdDiskHeader(VhdDiskImage vhd, ByteBuffer in, long footerLocation) throws IOException, WrongHeaderException {
		this.image = vhd;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(VhdDiskImage.BYTE_ORDER);
			
			cookie				= in.getLong();
			dataOffset 			= in.getLong();
			tableOffset			= in.getLong();
			headerVersion		= in.getInt();
			maxTableEntries		= in.getInt();
			blockSize			= in.getInt();
			int p 				= in.position();
			checksum			= in.getInt();
			parentUniqueId		= new UUID(in.getLong(), in.getLong());
			parentTimeStamp		= in.getInt();
			reserved1			= in.getInt();
			parentUnicodeName	= Static.getReservedBytes(in, 512);
			parentLocators		= Static.getReservedBytes(in, 192);
			reserved2			= Static.getReservedBytes(in, 256);

			if (cookie == CXSPARSE && dataOffset == ~0L && headerVersion == CURRENT_VERSION
					&& tableOffset >= VhdDiskFooter.FOOTER_SIZE + VhdDiskHeader.HEADER_SIZE
					&& maxTableEntries >= 0
					&& blockSize >= 0x80000 && Static.isPower2(blockSize)
					&& checksum == VhdDiskImage.getChecksum(in, HEADER_SIZE, p)) {
				
				if (image.footer.diskType != VhdDiskFooter.DYNAMIC_HARD_DISK)
					throw new InitializationException(String.format("%s: Not a dynamic base image file.", vhd.toString()));
				
				/* The VHD specification is not explicit about the location of the "data section", and whether or not
				 *  data blocks are adjacent. Data blocks are assumed to be allocated continuously and include
				 *  the footer. This is validated when the allocation table is initialized.
				 */
				bitmapSectors		= 1;
				firstSector			= (int) Static.ceilDiv(tableOffset + maxTableEntries * 4L, SECTOR_SIZE); // Or greater
				blockSectors		= blockSize / SECTOR_SIZE + bitmapSectors;
				nextSector			= (int) (footerLocation / SECTOR_SIZE);
				int allocated		= (nextSector - firstSector) / blockSectors;
				firstSector			= nextSector - blockSectors * allocated;
				
				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), image.toString());
	}
	
	long getFooterOffset() {
		return nextSector * (long)SECTOR_SIZE;
	}
	
	long getUpdateOffset() {
		return image.footer.dataOffset;
	}
	
	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[HEADER_SIZE];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VhdDiskImage.BYTE_ORDER);
		
		bb.putLong(cookie);
		bb.putLong(dataOffset);
		bb.putLong(tableOffset);
		bb.putInt(headerVersion);
		bb.putInt(maxTableEntries);
		bb.putInt(blockSize);
		int p = bb.position();
		bb.putInt(checksum);
		bb.putLong(parentUniqueId.getMostSignificantBits());
		bb.putLong(parentUniqueId.getLeastSignificantBits());
		bb.putInt(parentTimeStamp);
		bb.putInt(reserved1);
		bb.put(Arrays.copyOf(parentUnicodeName, 512));
		bb.put(Arrays.copyOf(parentLocators, 192));
		bb.put(Arrays.copyOf(reserved2, 256));
		
		bb.putInt(p, VhdDiskImage.getChecksum(bb, HEADER_SIZE, p));
		return buffer;
	}

	void update() throws IOException {
		image.getMedia().seek(getUpdateOffset());
		image.getMedia().write(getUpdateBuffer());
	}
}
