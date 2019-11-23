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

package io.github.eternalbits.windos.disk.ntfs;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map.Entry;

import java.util.TreeMap;

import io.github.eternalbits.disk.InitializationException;

class NtfsFileRecord {
	private static final int FILE_SIGNATURE = 0x454C4946;
	
	private static final int ATTRIBUTE_FILE_NAME = 0x30;
	private static final int ATTRIBUTE_DATA 	 = 0x80;
	private static final int ATTRIBUTE_END 		 = -1;
	
	private static final int FILE_NAME_WIN32 	 = 1;
	
	private final NtfsFileSystem fileSystem;
	
	/* File records have a dynamic structure. Some information is outdated, other is
	 *  only used to read the structure, most do not matter here. Only relevant 
	 *  data (file name, file size and extents) is represented in fields.
	 */
	String	fileName = null;			// The file name
	long	dataLength;					// Actual file size
	TreeMap<Long, Long> extents = null;	// B-Tree with file cluster as key and logical cluster as value
	
	NtfsFileRecord(NtfsFileSystem ntfs, ByteBuffer in) throws IOException, InitializationException {
		this.fileSystem	= ntfs;
		
		// Do not assume that we are at position zero
		if (in.remaining() >= ntfs.header.recordSize) {
			in.order(NtfsFileSystem.BYTE_ORDER);
			
			int p = in.position();						// Use relative positions
			in.position(p + ntfs.header.recordSize);	// Consume the record
			
			getAttributes:
			if (in.getInt(p) == FILE_SIGNATURE) {
			
				// When a record is updated a short "update sequence number" is
				//	incremented and written in the last two bytes of each sector. The
				//	sequence number and the "marked" shorts are saved in the "fix array".
				int q = p + in.getShort(p + 4);				// Fix array offset
				int b = 1, d = in.getShort(p + 6);			// Fix array length
				short updSeq = in.getShort(q);				// The update sequence number
				for (int s = p + 510; b < d; b++, s += 512) { // TODO 512 or sector size?
					if (s + 2 > in.position() || in.getShort(s) != updSeq) 
						break getAttributes;
					in.putShort(s, in.getShort(q + 2 * b));
				}
				
				int nextOffset	= in.getShort(p + 20);		// Offset to next attribute
				while (nextOffset > 0) {					// Must be positive
					p += nextOffset;
					// At least 8 bytes remaining
					if (in.position() - p < 8)
						break getAttributes;
					
					nextOffset = in.getShort(p + 4);
					
					switch(in.getInt(p)) {
					
					case ATTRIBUTE_FILE_NAME:
						// Only resident data is expected, with at least 96 bytes for header + data
						if (in.position() - p < nextOffset || nextOffset < 96 || in.get(p + 8) != 0)
							break getAttributes;
						
						q = p + in.getShort(p + 20);					// contentOffset
						if ((in.get(q + 65) & FILE_NAME_WIN32) == 0)	// Is Windows name?
							break;
						
						StringBuilder sb = new StringBuilder();
						for (int i = 0, s = in.get(q + 64); i < s; i++)
							sb.append(in.getChar(q + 66 + 2 * i));
						fileName = sb.toString();
						break;
						
					case ATTRIBUTE_DATA:
						// At least 24 bytes are expected for resident data, and 72 for non resident
						if (in.position() - p < nextOffset || ((d = in.get(p + 8)) & 0xFE) != 0
								|| d == 0 && nextOffset < 24 || d == 1 && nextOffset < 72)
							break getAttributes;
						
						if (d == 0) { // resident data
							
							break; // Expected residents for $Bitmap and $LogFile
							
						} else { // non resident data
							
							dataLength = in.getLong(p + 48);
							
							extents = new TreeMap<Long, Long>();
							q = p + in.getShort(p + 32);			// the run list offset
							long offset = 0, length = 0;
							while ((d = in.get(q++)) != 0) {
								long off = 0, len = 0;
								for (int drl = d & 0x0F, s = 0; drl > 0; drl--, s+=8)
									len |= (in.get(q++) & 0xFF) << s;
								int n = (d >> 4) & 0x0F;
								for (int dro = n, s = 0; dro > 0; dro--, s+=8)
									off |= (in.get(q++) & 0xFF) << s;
								if (in.get(q-1) < 0) // negative offset
									off |= -1L << (8 * n); // extend 1s
								if (off == 0) // sparse or compressed
									break getAttributes;
								offset += off;
								length += len;
								extents.put(length, offset);
							}
							// start cluster must be zero and end cluster must be equal to length -1
							if (in.getLong(p + 16) != 0 || in.getLong(p + 24) != length - 1)
								break getAttributes;
						}
						
						break;
						
					case ATTRIBUTE_END:
						return;
					}
				}
					
			}
		}
		
		throw new InitializationException(getClass(), fileSystem.toString());
	}
	
	long getCluster(long offset) throws EOFException {
		if (extents != null) {
			long clusterNumber = offset / fileSystem.header.clusterSize;
			Entry<Long, Long> ext = extents.higherEntry(clusterNumber);
			if (ext != null) return ext.getValue() + clusterNumber;
		}
		throw new EOFException(String.format("%s@%d", fileName, offset));
	}
	
}
