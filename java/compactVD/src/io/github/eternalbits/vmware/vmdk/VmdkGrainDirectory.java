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

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.InitializationException;

/**
 * The grain directory is technically not necessary but has been kept for legacy reasons.
 * <p>
 * It has no memory data. The disk data is checked on read and synthesized on write.
 */
class VmdkGrainDirectory {

	private final VmdkDiskImage image;			// Parent object
	private final VmdkSparseHeader header;		// VMDK header

	/* All the grain tables are created when the sparse extent is created, hence the grain directory
	 *  is technically not necessary but has been kept for legacy reasons. Grain tables can be 
	 *  redefined as blocks of grain table entries of arbitrary size.
	 */
	
	VmdkGrainDirectory(VmdkDiskImage vmdk) {
		image 	= vmdk;
		header 	= image.header;
	}

	VmdkGrainDirectory(VmdkDiskImage vmdk, ByteBuffer in) throws InitializationException {
		image 	= vmdk;
		header 	= image.header;
		
		if (in.remaining() >= header.gdeCount * 4) {
			in.order(VmdkDiskImage.BYTE_ORDER);
			
			int offset = (int)header.gtOffset;
			int count = (int)Static.ceilDiv(header.numGTEsPerGT * 4, VmdkSparseHeader.SECTOR_LONG);
			
			for (int i = 0, s = header.gdeCount; i < s; i++) {
				if (in.getInt() != offset + i * count)
					throw new InitializationException(getClass(), image.toString());
			}
			
		}
	}

	void update(boolean redundant) throws IOException {
		
		byte[] buffer = new byte[header.gdeCount * 4];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VmdkDiskImage.BYTE_ORDER);
		
		int offset = (int)(redundant? header.rgtOffset: header.gtOffset);
		int count = (int)Static.ceilDiv(header.numGTEsPerGT * 4, VmdkSparseHeader.SECTOR_LONG);
		
		for (int i = 0, s = header.gdeCount; i < s; i++) {
			bb.putInt(offset + i * count);
		}
		
		long sector = redundant? header.rgdOffset: header.gdOffset;
		image.getMedia().seek(sector * VmdkSparseHeader.SECTOR_LONG);
		image.getMedia().write(buffer);
	}

}
