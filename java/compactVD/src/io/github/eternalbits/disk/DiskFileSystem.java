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

public abstract class DiskFileSystem {
	
	protected DiskLayout layout = null;
	protected long diskOffset = 0;
	protected long diskLength = 0;

	public abstract String getType();
	public abstract String getDescription();
	
	/**
	 * Checks the file system allocation status of {@code length} bytes starting at 
	 * 	{@code offset}. Typical file systems have a bitmap to track allocated blocks,
	 * 	where one bit represents a file system block. If the area checked is not 
	 * 	completely covered by the file system bitmap, true is returned.
	 * 
	 * @param	offset	The address of the first byte to check.
	 * @param	length	The number of bytes to check.
	 * @return	true if at least one byte checked is allocated by the file system, false otherwise.
	 */
	public abstract boolean isAllocated(long offset, long length);

	public long getOffset() {
		return diskOffset;
	}
	
	public long getLength() {
		return diskLength;
	}

	@Override
	public String toString() {
		return getType();
	}

}
