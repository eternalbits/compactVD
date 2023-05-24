/*
 * Copyright 2020 Rui Baptista
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

package io.github.eternalbits.darwin.disk.apfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.WrongHeaderException;

public class ApfsFileSystem extends DiskFileSystem { // https://developer.apple.com/support/downloads/Apple-File-System-Reference.pdf
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	final static int HEADER_SIZE = 4096;
	
	final ApfsVolumeHeader header;
	final ApfsSpacemanPhys spaceman;

	public ApfsFileSystem(DiskLayout layout, long offset, long length) throws IOException, WrongHeaderException {
		this.layout		= layout;
		this.diskOffset	= offset;
		this.diskLength	= length;
		
		header = new ApfsVolumeHeader(this, readImage(0, HEADER_SIZE));
		long desc = (header.nx_xp_desc_base + header.nx_xp_desc_index) * header.nx_block_size;
		long data = (header.nx_xp_data_base + header.nx_xp_data_index) * header.nx_block_size;
		for (int i = 0; i < header.nx_xp_desc_len; i++)
			new ApfsVolumeDescData(this, readImage(desc + i * HEADER_SIZE, HEADER_SIZE));
		for (int i = 0; i < header.nx_xp_data_len; i++)
			new ApfsVolumeDescData(this, readImage(data + i * HEADER_SIZE, HEADER_SIZE));
		spaceman = new ApfsSpacemanPhys(this, readImage(data, HEADER_SIZE));
	}
	
	@Override
	public String getType() {
		return "APFS";
	}

	@Override
	public String getDescription() {
		return "Apple File System";
	}

	ByteBuffer readImage(long offset, int length) throws IOException {
		byte[] buffer = new byte[length];
		int read = layout.getImage().readAll(diskOffset + offset, buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, read).order(BYTE_ORDER);
	}
	
	/**
	 * When you check a block in APFS with the following algorithm you should get null as a 
	 * 	result. Note that the input in this case is the whole block, including the checksum.
	 * 
	 * @param	data	The pointer to the structure to check.
	 * @return	{@code long} with the result of the Apple algorithm.
	 */
    static long checkChecksum(ByteBuffer data) {
        long modValue = (2L<<31) - 1;
        long check = 0, sum = 0;
        for (int i = 0; i < data.capacity(); i=i+4) {
            check = data.getInt(i) & modValue;
            sum = (sum + check) % modValue;
         }
        return sum;	// The return (sum << 32) | check is wrong!
    }
    
	private final byte[] leaveMask = new byte[] {(byte)0xFF, 0x1, 0x3, 0x7, 0x0F, 0x1F, 0x3F, 0x7F};
	private final byte[] enterMask = new byte[] {(byte)0xFF, (byte)0xFE, (byte)0xFC, (byte)0xF8, 
			(byte)0xF0, (byte)0xE0, (byte)0xC0, (byte)0x80};
	
	@Override
	public boolean isAllocated(long offset, long length) {
		if (length == 0)
			return false;
		
		/* The allocation spaceman is used to keep track of whether each allocation block 
		 * 	in a volume is currently allocated to some file system structure or not. The 
		 * 	contents of the allocation file is a bitmap. The bitmap contains one bit for 
		 * 	each allocation block in the volume. If a bit is set, the corresponding allocation 
		 * 	block is currently in use by some file system structure. If a bit is clear, the 
		 * 	corresponding allocation block is not currently in use.
		 * The first data block is represented by the least significant bit of the first byte
		 *  in the bitmap.
		 */
		long firstBlock = offset / header.nx_block_size;						// First block to check
		long lastBlock = (offset + length - 1) / header.nx_block_size;			// Last block to check
		if (firstBlock < 0 || lastBlock >= header.nx_block_count)				// Is block range valid?
			return true;
		
		long firstByte = firstBlock / 8;										// First byte to read
		long lastByte = lastBlock / 8;											// Last byte to read
		byte firstMask = enterMask[(int) (firstBlock % 8)];						// Bits to ignore in first byte
		byte lastMask = leaveMask[(int) ((lastBlock + 1) % 8)];				 	// Bits to ignore in last byte
		
		try {
			int want = (int) (lastByte - firstByte + 1), into = 0;
			byte[] buffer = new byte[want];
			long from = firstByte;
			while (want > 0) {
				long readNumber = spaceman.getSpaceman(from);
				int readOffset = (int) (from % header.nx_block_size);
				int read = Math.min(want, header.nx_block_size - readOffset);
				if (readNumber > 0) { // Read the bitmap from disk image
					read = layout.getImage().readAll(diskOffset + readNumber * header.nx_block_size 
							+ readOffset, buffer, into, read);
					if (read == -1) return true;
				} else {
				// A 0x0 byte is a normal situation to occur. As for byte 0xff it is impossible 
				//	to happen, unless the spaceman has been read incorrectly. 
					byte ins = readNumber == 0? (byte)0x0: (byte)0xff;
					for (int i= into; i < into+read; i++) 
						buffer[i] = ins;
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
