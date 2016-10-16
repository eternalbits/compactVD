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

import io.github.eternalbits.disk.DiskImage.FileSysData;

/**
 * A read-only view of a {@link DiskFileSystem}. All fields are public and final.
 * <p>
 */
public class DiskFileSystemView {

	public final boolean isFileSystem;
	public final long offset;
	public final long length;
	public final String type;
	public final String description;
	public final int blocksCount;
	public final int blocksMapped;
	public final int blocksNotInUse;
	public final int blocksZeroed;
	
	public DiskFileSystemView(DiskFileSystem fileSys, FileSysData cc) {
		isFileSystem	= !(fileSys instanceof NullFileSystem);
		offset 			= fileSys.getOffset();
		length 			= fileSys.getLength();
		type			= fileSys.getType();
		description 	= fileSys.getDescription();
		blocksCount 	= cc.blockEnd - cc.blockStart;
		blocksMapped 	= cc.blocksMapped;
		blocksNotInUse 	= cc.blocksNotInUse;
		blocksZeroed 	= cc.blocksZeroed;
	}

}
