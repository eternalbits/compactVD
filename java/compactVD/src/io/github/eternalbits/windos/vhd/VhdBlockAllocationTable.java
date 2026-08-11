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

import io.github.eternalbits.disk.DiskImageBlockTable;
import io.github.eternalbits.disk.InitializationException;

class VhdBlockAllocationTable extends DiskImageBlockTable {
	private final long SECTOR_LONG = VhdDiskImage.SECTOR_SIZE;

	private final VhdDiskHeader header;
	private final VhdDiskImage image;
	
	private final int[] blockMap;
	private int dataBlocksCount;

	boolean equalValues;				// All data blocks are separated by 512 bytes
	private final int[] reverseData;	// Keeping the data when equalValues is false
	private final int[] reverseMap;		// Keeping the map when equalValues is false
	private final int dataBlocksStart;	// Save the initial block count when equalValues is false
	private int nextSector;				// Save the next sector when equalValues is false
	
	VhdBlockAllocationTable(VhdDiskImage vhd) {
		image 	= vhd;
		header 	= image.header;
		
		blockMap = new int[header.maxTableEntries];
		Arrays.fill(blockMap, -1);
		dataBlocksCount = 0;
		dataBlocksStart = 0;
		equalValues = true;
		reverseData = null;
		reverseMap = null;
		nextSector = -1;
	}
	
	VhdBlockAllocationTable(VhdDiskImage vhd, ByteBuffer in) throws IOException {
		image 	= vhd;
		header 	= image.header;
		
		if (in.remaining() >= header.maxTableEntries * 4) {
			in.order(VhdDiskImage.BYTE_ORDER);
			
			int[][] blockCopy = new int[header.maxTableEntries][2];
			blockMap = new int[header.maxTableEntries];
			dataBlocksCount = 0;
			
			for (int i = 0, s = blockMap.length; i < s; i++) {
				blockMap[i] = blockCopy[i][0] = blockCopy[i][1] = in.getInt();
				if (blockMap[i] != -1)
					dataBlocksCount++;
			}
			Arrays.sort(blockCopy, (a, b) -> a[1] - b[1]);	// Sort blockCopy in ascending order
			dataBlocksStart = dataBlocksCount;
			equalValues = true;
			nextSector = -1;
			
			for (int i = 0, s = blockCopy.length; i < s; i++) {
				int sector = blockCopy[i][1];
				if (sector != -1) {
					if (nextSector == -1) {
						header.nextSector = blockCopy[header.maxTableEntries -1][1] + header.blockSectors;
						header.firstSector = sector;
						nextSector = sector;
					}
					if (sector < header.firstSector || sector >= header.nextSector)
						throw new InitializationException(getClass(), image.toString());
					// Do not limit the spacing between data entries to 512 bytes; accept everything that comes after that.
					if (sector < nextSector)// || sector > nextSector + 7)
						throw new InitializationException(getClass(), image.toString());
					if (sector != nextSector) equalValues = false;
					blockCopy[i][0] = nextSector;
					nextSector = sector + header.blockSectors;
				}
			}
			
			if (equalValues) {
				reverseData = null;
				reverseMap = null;
			} else {
				reverseData = new int[dataBlocksStart];
				reverseMap = new int[dataBlocksStart];
				for (int i = 0, j = 0, s = blockCopy.length; i < s; i++) {
					if (blockCopy[i][1] != -1) {
						reverseData[j] = blockCopy[i][1];
						reverseMap[j] = blockCopy[i][0];
						j++;
					}
				}
			}
			
			return;
		}
		
		throw new InitializationException(getClass(), image.toString());
	}

	int indexOf(int sector) {
		if (sector == nextSector) return dataBlocksStart;
		return Arrays.binarySearch(reverseData, sector);
	}

	int sectorOf(int index) {
		if (index == reverseData.length) return nextSector;
		return reverseData[index];
	}

	int regionOf(int index) {
		if (index == reverseMap.length) return nextSector;
		return reverseMap[index];
	}

	int read(int blockNumber, int blockOffset, byte[] in, int start, int length) throws IOException {
		if (blockMap[blockNumber] < header.firstSector || blockMap[blockNumber] >= header.nextSector) {
			Arrays.fill(in, start, start + length, (byte)0);
			return length;
		}
		image.getMedia().seek((blockMap[blockNumber] + header.bitmapSectors) * SECTOR_LONG + blockOffset);
		return image.getMedia().read(in, start, length);
	}

	void update(int blockNumber, int blockOffset, byte[] out, int start, int length) throws IOException {
		image.getMedia().seek((blockMap[blockNumber] + header.bitmapSectors) * SECTOR_LONG + blockOffset);
		image.getMedia().write(out, start, length);
	}

	void create(int blockNumber, int blockOffset, byte[] out, int start, int length) throws IOException {
		
		image.getMedia().seek(header.nextSector * SECTOR_LONG);
		byte[] bits = new byte[header.bitmapSectors * (int)SECTOR_LONG];
		Arrays.fill(bits, (byte)~0); image.getMedia().write(bits);
		
		if (out == null || blockOffset != 0 || length != header.blockSize) {
			
			byte[] zero = new byte[header.blockSize];
			System.arraycopy(out, start, zero, blockOffset, length);
			image.getMedia().write(zero);
			
		} else { //write from buffer
			image.getMedia().write(out, start, length);
		}
		
		blockMap[blockNumber] = header.nextSector;
		header.nextSector += header.blockSectors;
		dataBlocksCount++;
	}

	int getDataBlocksCount() {
		return dataBlocksCount;
	}

	@Override
	protected long getOffset(int blockNumber) {
		if (exists(blockNumber))
			return blockMap[blockNumber] * SECTOR_LONG;
		return -1L;
	}

	@Override
	protected boolean exists(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < blockMap.length)
			return blockMap[blockNumber] != -1;
		return false;
	}

	@Override
	protected void free(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < blockMap.length) {
			blockMap[blockNumber] = -1;
			dataBlocksCount--;
		}
	}

	long getUpdateOffset() {
		return header.tableOffset;
	}

	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[blockMap.length * 4];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VhdDiskImage.BYTE_ORDER);
		
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
		header.nextSector = header.firstSector;
		Arrays.fill(blockMap, -1);
	}

	int get(int index) {
		return blockMap[index];
	}

	void map(int index, int sector) {
		blockMap[index] = sector;
	}
	
}
