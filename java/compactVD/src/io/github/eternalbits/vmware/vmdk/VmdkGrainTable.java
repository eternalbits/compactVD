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
import java.util.Arrays;
import java.util.BitSet;

import io.github.eternalbits.disk.DiskImageBlockTable;
import io.github.eternalbits.disk.InitializationException;

class VmdkGrainTable extends DiskImageBlockTable {

	private final VmdkDiskImage image;			// Parent object
	private final VmdkSparseHeader header;		// VMDK header
	
	/* VMware Virtual Disk Format 1.1
	 * 	http://www.vmware.com/app/vmdk/?src=vmdk
	 */
	private final int[] grainMap;				// Each entry points to the image offset, in sectors, of a grain in the sparse extent.
	private int dataGrainsCount;
	
	VmdkGrainTable(VmdkDiskImage vmdk) {
		image 	= vmdk;
		header 	= image.header;
		
		grainMap = new int[header.gteCount];
		dataGrainsCount = 0;
	}
	
	VmdkGrainTable(VmdkDiskImage vmdk, ByteBuffer in) throws InitializationException {
		image 	= vmdk;
		header 	= image.header;
		
		if (in.remaining() >= header.gteCount * 4) {
			in.order(VmdkDiskImage.BYTE_ORDER);
			
			BitSet bitmap = new BitSet(header.gteCount);
			grainMap = new int[header.gteCount];
			
			for (int i = 0, s = grainMap.length; i < s; i++) {
				int grain = grainMap[i] = in.getInt();
				if (grain != 0) {
					int block = image.indexOf(grain);
					if (grain < header.firstSector || grain >= header.nextSector 
							|| grain % header.grainSectors != 0 || bitmap.get(block))
						throw new InitializationException(getClass(), image.toString());
					bitmap.set(block);
				}
			}
			
			dataGrainsCount = bitmap.cardinality();
			return;
		}
		
		throw new InitializationException(getClass(), image.toString());
	}
	
	int read(int blockNumber, int blockOffset, byte[] in, int start, int length) throws IOException {
		if (grainMap[blockNumber] < header.firstSector || grainMap[blockNumber] >= header.nextSector) {
			Arrays.fill(in, start, start + length, (byte)0);
			return length;
		}
		image.getMedia().seek(grainMap[blockNumber] * VmdkSparseHeader.SECTOR_LONG + blockOffset);
		return image.getMedia().read(in, start, length);
	}

	void update(int blockNumber, int blockOffset, byte[] out, int start, int length) throws IOException {
		image.getMedia().seek(grainMap[blockNumber] * VmdkSparseHeader.SECTOR_LONG + blockOffset);
		image.getMedia().write(out, start, length);
	}

	void create(int blockNumber, int blockOffset, byte[] out, int start, int length) throws IOException {
		
		image.getMedia().seek(header.nextSector * VmdkSparseHeader.SECTOR_LONG);
		if (out == null || blockOffset != 0 || length != header.blockSize) {
			
			byte[] zero = new byte[header.blockSize];
			System.arraycopy(out, start, zero, blockOffset, length);
			image.getMedia().write(zero);
			
		} else { //write from buffer
			image.getMedia().write(out, start, length);
		}
		
		grainMap[blockNumber] = header.nextSector;
		header.nextSector += header.grainSectors;
		dataGrainsCount++;
	}

	int getDataGrainsCount() {
		return dataGrainsCount;
	}

	@Override
	protected long getOffset(int blockNumber) {
		if (exists(blockNumber))
			return grainMap[blockNumber] * VmdkSparseHeader.SECTOR_LONG;
		return -1L;
	}
	
	@Override
	protected boolean exists(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < grainMap.length)
			return grainMap[blockNumber] != 0;
		return false;
	}

	@Override
	protected void free(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < grainMap.length) {
			grainMap[blockNumber] = 0;
			dataGrainsCount--;
		}
	}

	long getUpdateOffset(boolean redundant) {
		return (redundant? header.rgtOffset: header.gtOffset) * VmdkSparseHeader.SECTOR_LONG;
	}

	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[grainMap.length * 4];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VmdkDiskImage.BYTE_ORDER);
		
		for (int i = 0, s = grainMap.length; i < s; i++) {
			bb.putInt(grainMap[i]);
		}
		
		return buffer;
	}

	void update(boolean redundant) throws IOException {
		image.getMedia().seek(getUpdateOffset(redundant));
		image.getMedia().write(getUpdateBuffer());
	}

	void reset() {
		header.nextSector = header.firstSector;
		Arrays.fill(grainMap, 0);
	}

	int get(int index) {
		return grainMap[index];
	}

	void map(int index, int grain) {
		grainMap[index] = grain;
	}

}
