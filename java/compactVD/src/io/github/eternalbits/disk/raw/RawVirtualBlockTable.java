/*
 * Copyright 2016 Rui Baptista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eternalbits.disk.raw;

import java.util.BitSet;

import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageBlockTable;
import io.github.eternalbits.disk.InitializationException;

/**
 * Virtual block table to gather statistics about file system allocation
 *  and zeroed blocks. All device blocks are initially mapped to image blocks
 *  but this can be changed by the {@link DiskImage#optimize(int)} method.
 */
class RawVirtualBlockTable extends DiskImageBlockTable {

	private final RawDiskImage image;
	private final BitSet blockMap;
	private int dataClustersCount;
	
	RawVirtualBlockTable(RawDiskImage raw) throws InitializationException {
		image 	= raw;
		
		dataClustersCount = (int)(image.diskSize / image.clusterSize);
		if (dataClustersCount == image.diskSize / image.clusterSize) {
			
			blockMap = new BitSet(dataClustersCount);
			blockMap.set(0, dataClustersCount);
			return;
		}
		
		throw new InitializationException(getClass(), image.toString());
	}

	int getDataClustersCount() {
		return dataClustersCount;
	}
	
	@Override
	protected long getOffset(int blockNumber) {
		if (exists(blockNumber))
			return image.diskStart + blockNumber * (long)image.clusterSize;
		return -1L;
	}

	@Override
	protected boolean exists(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < blockMap.size())
			return blockMap.get(blockNumber);
		return false;
	}

	@Override
	protected void free(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < blockMap.size()) {
			blockMap.clear(blockNumber);
			dataClustersCount--;
		}
	}

}
