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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A read-only view of a {@link DiskImage}. All fields are public and final.
 * <p>
 */
public class DiskImageView {
	
	public final String filePath;
	public final String imageType;
	public final long diskLength;
	public final long imageLength;
	public final long optimizedLength;
	public final int blockSize;
	public final int blocksCount;
	public final int blocksInFile;
	public final int blocksMapped;
	public final Integer blocksUnused;
	/** Replaced by {@code blocksUnused} */
	@Deprecated
	public final Integer blocksNotInUse;
	public final Integer blocksZeroed;
	public final String diskLayout;
	
	public final List<DiskFileSystemView> fileSystems;
	
	DiskImageView(DiskImage image) {
		filePath 		= image.path;
		imageType		= image.getType();
		diskLength		= image.getDiskSize();
		imageLength		= image.getImageLength();
		optimizedLength	= image.getOptimizedLength();
		blockSize 		= image.getImageBlockSize();
		blocksCount 	= image.getImageBlocksCount();
		blocksInFile	= image.getImageBlocksInFile();
		blocksMapped	= image.getImageBlocksMapped();
		blocksUnused 	= image.blocksUnused;
		blocksNotInUse 	= image.blocksUnused;
		blocksZeroed 	= image.blocksZeroed;
		
		diskLayout		= image.getLayout() == null? null: image.getLayout().getType();
		List<DiskFileSystemView> local = new ArrayList<DiskFileSystemView>();
		if (image.getLayout() != null) {
			for (DiskFileSystem fs: image.getLayout().getFileSystems()) {
				local.add(new DiskFileSystemView(fs, image.blockView.get(fs)));
			}
		}
		fileSystems = Collections.unmodifiableList(local);
	}
}
