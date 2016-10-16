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

package io.github.eternalbits.darwin.disk.hfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.WrongHeaderException;

public class HfsFileSystem extends DiskFileSystem { // https://developer.apple.com/legacy/library/technotes/tn/tn1150.html
	static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

	final HfsVolumeHeader header;
	
	public HfsFileSystem(DiskLayout layout, long offset, long length) throws IOException, WrongHeaderException {
		this.layout		= layout;
		this.diskOffset = offset;
		this.diskLength = length;
		
		header = new HfsVolumeHeader(this, readImage(1024, HfsVolumeHeader.HEADER_SIZE));
	}

	@Override
	public String getType() {
		return "HFS";
	}

	@Override
	public String getDescription() {
		return "Apple Hierarchical File System";
	}

	ByteBuffer readImage(long offset, int length) throws IOException {
		byte[] buffer = new byte[length];
		int read = layout.getImage().readAll(diskOffset + offset, buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, read).order(BYTE_ORDER);
	}
	
	private final byte[] enterMask = new byte[] {(byte)0xFF, 0x7F, 0x3F, 0x1F, 0xF, 0x7, 0x3, 0x1};
	private final byte[] leaveMask = new byte[] {(byte)0xFF, (byte)0x80, (byte)0xC0, (byte)0xE0, 
			(byte)0xF0, (byte)0xF8, (byte)0xFC, (byte)0xFE};
	
	@Override
	public boolean isAllocated(long offset, long length) {
		if (length == 0)
			return false;
		
		/* The allocation file is used to keep track of whether each allocation block 
		 * 	in a volume is currently allocated to some file system structure or not. The 
		 * 	contents of the allocation file is a bitmap. The bitmap contains one bit for 
		 * 	each allocation block in the volume. If a bit is set, the corresponding allocation 
		 * 	block is currently in use by some file system structure. If a bit is clear, the 
		 * 	corresponding allocation block is not currently in use.
		 * The first data block is represented by the most significant bit of the first byte
		 *  in the bitmap.
		 */
		int firstBlock = (int)(offset / header.blockSize);					// First block to check
		int lastBlock = (int)((offset + length - 1) / header.blockSize);	// Last block to check
		if (firstBlock < 0 || lastBlock >= header.totalBlocks)				// Is block range valid?
			return true;
		
		int firstByte = firstBlock / 8;										// First byte to read
		int lastByte = lastBlock / 8;										// Last byte to read
		byte firstMask = enterMask[firstBlock % 8];							// Bits to ignore in first byte
		byte lastMask = leaveMask[(lastBlock + 1) % 8];						// Bits to ignore in last byte
		
		try {
			int want = lastByte - firstByte + 1, from = firstByte, into = 0;
			byte[] buffer = new byte[want];
			while (want > 0) {
				int readNumber = header.allocationFile.getBlock(from);
				int readOffset = from % header.blockSize;
				int read = Math.min(want, header.blockSize - readOffset);
				read = layout.getImage().readAll(diskOffset + readNumber * (long)header.blockSize 
						+ readOffset, buffer, into, read);
				if (read == -1) return true;
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
