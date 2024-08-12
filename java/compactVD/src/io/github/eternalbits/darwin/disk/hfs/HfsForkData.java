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

import java.io.EOFException;
import java.nio.ByteBuffer;

import io.github.eternalbits.disk.InitializationException;

class HfsForkData {
	static final String ALLOCATION_FILE = "AllocationFile";
	
	static final byte TYPE_DATA = (byte)0x00;
	static final byte TYPE_RSRC = (byte)0xFF;
	
	private static final int FORK_DATA_SIZE = 80;
	private static final int EXTENT_RECORD_SIZE = 8;
	
	private final int blockSize;
	private final String forkPath;
	private final byte forkType;
	
	long	logicalSize;		// The size, in bytes, of the valid data in the fork
	int		clumpSize;			// For volume header files; reserved for other files
	int		totalBlocks;		// The total number of allocation blocks used by all the extents in this fork
	int		size;				// Number of active extents in this fork, including the overflow extents
	int[]	extentStart;		// One object for each extent descriptor would be a bit excessive, 
	int[]	extentCount;		//	HFSPlusExtentDescriptors are stored in two integer arrays
	
	HfsForkData(int blockSize, String path, byte type, ByteBuffer in) throws InitializationException {
		this.blockSize = blockSize;
		this.forkPath = path;
		this.forkType = type;
		
		if (in.remaining() >= FORK_DATA_SIZE) {
			in.order(HfsFileSystem.BYTE_ORDER);
			
			logicalSize 	= in.getLong();
			clumpSize 		= in.getInt();
			totalBlocks 	= in.getInt();
			size			= 0;
			extentStart 	= new int[EXTENT_RECORD_SIZE];
			extentCount 	= new int[EXTENT_RECORD_SIZE];
			
			int blockCount = 0;
			for (int i = 0; i < EXTENT_RECORD_SIZE; i++ ) {
				extentStart[i]	= in.getInt();
				extentCount[i]	= in.getInt();
				if (extentStart[i] < 0 || extentCount[i] < 0)
					throw new InitializationException(getClass(), path);
				if (extentStart[i] > 0 || extentCount[i] > 0)
					size = i + 1;
				blockCount += extentCount[i];
			}
			
			// Do not add more extents from the overflow file; this is very unlikely
			//	to be needed because only the allocation file is really used
			if (forkPath.equals(ALLOCATION_FILE)) {
				if (logicalSize >= 0 && totalBlocks >= 0 && blockCount == totalBlocks) 
					return;
			} else {
				if (logicalSize >= 0 && totalBlocks >= 0) 
					return;
			}
		}
		
		throw new InitializationException(getClass(), path);
	}
	
	int getBlock(long offset) throws EOFException {
		if (offset >= 0) {
			int blockNumber = (int)(offset / blockSize);
			for (int i = 0; i < size; i++) {
				if (blockNumber < extentCount[i])
					return extentStart[i] + blockNumber;
				blockNumber -= extentCount[i];
			}
		}
		throw new EOFException(String.format("%s::%s@%d", forkPath, forkType==TYPE_DATA?"data":"rsrc", offset));
	}
}
