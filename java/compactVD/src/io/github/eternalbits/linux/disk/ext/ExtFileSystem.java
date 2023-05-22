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

package io.github.eternalbits.linux.disk.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.WrongHeaderException;

public class ExtFileSystem extends DiskFileSystem {
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	final ExtVolumeHeader header;
	
	public ExtFileSystem(DiskLayout layout, long offset, long length) throws IOException, WrongHeaderException {
		this.layout		= layout;
		this.diskOffset = offset;
		this.diskLength = length;
		
		header = new ExtVolumeHeader(this, readImage(1024, ExtVolumeHeader.HEADER_SIZE));
	}

	@Override
	public String getType() {
		return "EXT";
	}

	@Override
	public String getDescription() {
		return "Linux Extended File System";
	}

	ByteBuffer readImage(long offset, int length) throws IOException {
		byte[] buffer = new byte[length];
		int read = layout.getImage().readAll(diskOffset + offset, buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, read).order(BYTE_ORDER);
	}
	
	private final byte[] leaveMask = new byte[] {(byte)0xFF, 0x1, 0x3, 0x7, 0x0F, 0x1F, 0x3F, 0x7F};
	private final byte[] enterMask = new byte[] {(byte)0xFF, (byte)0xFE, (byte)0xFC, (byte)0xF8, 
			(byte)0xF0, (byte)0xE0, (byte)0xC0, (byte)0x80};
	
	@Override
	public boolean isAllocated(long offset, long length) {
		if (length == 0)
			return false;
		
		/* The allocation bitmap represents disk clusters. Each cluster is represented by a bit.
		 * 	If the bit is set, the cluster has data or metadata. Disk clusters are organized in 
		 * 	groups. Each group has a descriptor that contains, within other information, the 
		 * 	address of his allocation bitmap as a block number.
		 * The first data cluster is represented by the least significant bit of the first byte
		 *  in the bitmap. This is the first bit. When firstDataBlock is 1, the first data cluster
		 *  is cluster #1, not #0. The allocation status of cluster #0 is represented by the last 
		 *  bit in the bitmap of the previous group allocation block.
		 */
		long oddset = offset - header.firstDataBlock * header.clusterSize;		// Yes, this is odd
		int firstCluster = (int)(oddset / header.clusterSize);					// First cluster to check
		int lastCluster = (int)((oddset + length - 1) / header.clusterSize);	// Last cluster to check 
		if (firstCluster < 0 || lastCluster >= header.clustersCount				// Is cluster range valid?
				- header.firstDataBlock) return true;
		
		int firstByte = firstCluster / 8;										// First byte to read
		int lastByte = lastCluster / 8;											// Last byte to read
		byte firstMask = enterMask[firstCluster % 8];							// Bits to ignore in first byte
		byte lastMask = leaveMask[(lastCluster +1) % 8];						// Bits to ignore in last byte
		
		/* The super block validation checks that the size of a group bitmap data is exactly 
		 * 	the size of one data block. The file system bitmap can be handled as a file 
		 * 	with a number of block extents equal to the number of cluster groups.
		 */
		try {
			int want = lastByte - firstByte + 1, into = 0;
			byte[] buffer = new byte[want];
			int from = firstByte;
			while (want > 0) {
				int readNumber = header.bitmapBlockOrMaker[from / header.blockSize];
				int readOffset = from % header.blockSize;
				int read = Math.min(want, header.blockSize - readOffset);
				if (readNumber > 0) { // Read the bitmap from disk image
					read = layout.getImage().readAll(diskOffset + readNumber * (long)header.blockSize
							+ readOffset, buffer, into, read);
					if (read == -1) return true;
				} else {
				// Make the bitmap. Very unlikely to happen: if the bitmap is not initialized then no data
				//	was ever written and the image block should be free. Format a populated disk to test.
					for (int r = 0, s = 8*readOffset, t = -readNumber, i = into; r < read; r++, i++, s+=8) {
						buffer[i] = s >= t? 0: s >= t-8? leaveMask[t%8]: (byte)0xFF;
					}
				}
				want -= read;
				from += read;
				into += read;
			}
			
			buffer[0] &= firstMask;
			buffer[buffer.length -1] &= lastMask;
			for (int i = 0; i < buffer.length; i++)
				if (buffer[i] != 0)
					return true;
			return false;
			
		} catch (IOException e) {}
		
		return true;
	}

}
