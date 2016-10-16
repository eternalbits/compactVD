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

package io.github.eternalbits.disk;

/**
 * Abstract class that represents the allocation table of a dynamic disk
 *  image. In dynamic disk images the space is allocated, in fixed size data
 *  clusters, only when needed to store data. The location of these clusters
 *  in the disk image, and their location in the virtual disk device, is
 *  recorded in a table that is part of the disk image metadata.
 */
public abstract class DiskImageBlockTable {
	
	

	/**
	 * Returns the absolute offset, in bytes, of the image data cluster that is
	 *  mapped to the cluster number {@code block} of the virtual disk device.
	 *  
	 * @param block	The zero-based cluster number.
	 * @return	The absolute offset of the data cluster inside the disk image,
	 * 				or {@code -1} if the cluster is not mapped.
	 */
	protected abstract long getOffset(int block);

	/**
	 * Returns the current allocation status of the cluster number {@code block}
	 *  in the virtual disk device.
	 * 
	 * @param block	The zero-based cluster number.
	 * @return	{@code true} if the cluster is allocated, {@code false} otherwise.
	 */
	protected abstract boolean exists(int block);

	/**
	 * Marks the cluster number {@code block} of the virtual disk device as not
	 *  needed. Clusters are not reorganized in the disk image by this method. 
	 * 
	 * @param block	The zero-based cluster number.
	 */
	protected abstract void free(int block);

	/**
	 * Counts the number of clusters that are allocated from {@code blockStart} 
	 *  to {@code blockEnd - 1}.
	 * 
	 * @param blockStart	The stating block, inclusive.
	 * @param blockEnd		The ending block, exclusive.
	 * @return	The number of allocated blocks in the range.
	 */
	int countBlocksMapped(int blockStart, int blockEnd) {
		int count = 0;
		for (int i = blockStart; i < blockEnd; i++) {
			if (exists(i)) count++;
		}
		return count;
	}

}
