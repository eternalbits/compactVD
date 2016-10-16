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

package io.github.eternalbits.vbox.vdi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

import io.github.eternalbits.disk.DiskImageBlockTable;
import io.github.eternalbits.disk.InitializationException;

class VdiImageBlockTable extends DiskImageBlockTable {
	
	private final VdiHeaderDescriptor header;
	private final VdiDiskImage image;
	
	/* VDI Header Descriptor version 1.1
	 * https://www.virtualbox.org/browser/vbox/trunk/src/VBox/Storage/VDICore.h
	 */
	private final int[] blockMap;
	private int dataBlocksCount;

	VdiImageBlockTable(VdiDiskImage vdi) {
		image 	= vdi;
		header 	= image.header;
		
		blockMap = new int[header.blocksCount];
		Arrays.fill(blockMap, -1);
		dataBlocksCount = 0;
	}
	
	VdiImageBlockTable(VdiDiskImage vdi, ByteBuffer in) throws IOException {
		image 	= vdi;
		header 	= image.header;
		
		if (in.remaining() >= header.blocksCount * 4) {
			in.order(VdiDiskImage.BYTE_ORDER);
			
			BitSet bitmap = new BitSet(header.blocksCount);
			blockMap = new int[header.blocksCount];
			
			for (int i = 0, s = blockMap.length; i < s; i++) {
				int block = blockMap[i] = in.getInt();
				if (block == -2) block = blockMap[i] = -1; // CloneVDI compatibility
				if (block != -1) {
					if (block < 0 || block >= header.blocksAllocated || bitmap.get(block))
						throw new InitializationException(getClass(), image.toString());
					bitmap.set(block);
				}
			}
			
			dataBlocksCount = bitmap.cardinality();
			return;
		}
		
		throw new InitializationException(getClass(), image.toString());
	}

	public int read(int blockNumber, int blockOffset, byte[] in, int start, int length) throws IOException {
		if (blockMap[blockNumber] < 0 || blockMap[blockNumber] >= header.blocksAllocated) {
			Arrays.fill(in, start, start + length, (byte)0);
			return length;
		}
		image.getMedia().seek(header.offsetData + blockMap[blockNumber] * (long)header.blockSize + blockOffset);
		return image.getMedia().read(in, start, length);
	}

	void update(int blockNumber, int blockOffset, byte[] out, int start, int length) throws IOException {
		image.getMedia().seek(header.offsetData + blockMap[blockNumber] * (long)header.blockSize + blockOffset);
		image.getMedia().write(out, start, length);
	}

	void create(int blockNumber, int blockOffset, byte[] out, int start, int length) throws IOException {
		
		image.getMedia().seek(header.offsetData + header.blocksAllocated * (long)header.blockSize);
		if (out == null || blockOffset != 0 || length != header.blockSize) {
			
			byte[] zero = new byte[header.blockSize];
			System.arraycopy(out, start, zero, blockOffset, length);
			image.getMedia().write(zero);
			
		} else { //write from buffer
			image.getMedia().write(out, start, length);
		}
		
		blockMap[blockNumber] = header.blocksAllocated;
		header.blocksAllocated++;
		dataBlocksCount++;
	}

	int getDataBlocksCount() {
		return dataBlocksCount;
	}

	@Override
	protected boolean exists(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < blockMap.length)
			return blockMap[blockNumber] != -1;
		return false;
	}

	@Override
	protected long getOffset(int blockNumber) {
		if (exists(blockNumber))
			return header.offsetData + blockMap[blockNumber] * (long)header.blockSize;
		return -1L;
	}
	
	@Override
	protected void free(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < blockMap.length) {
			blockMap[blockNumber] = -1;
			dataBlocksCount--;
		}
	}
	
	long getUpdateOffset() {
		return header.offsetBlocks;
	}
	
	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[blockMap.length * 4];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VdiDiskImage.BYTE_ORDER);
		
		for (int i = 0, s = blockMap.length; i < s; i++) {
			bb.putInt(blockMap[i]);
		}
		
		return buffer;
	}

	void update() throws IOException {
		image.getMedia().seek(getUpdateOffset());
		image.getMedia().write(getUpdateBuffer());
	}

	void reset() {
		header.blocksAllocated = 0;
		Arrays.fill(blockMap, -1);
	}

	int get(int index) {
		return blockMap[index];
	}

	void map(int index, int block) {
		blockMap[index] = block;
	}

}
