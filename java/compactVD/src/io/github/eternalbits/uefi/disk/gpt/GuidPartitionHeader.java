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

package io.github.eternalbits.uefi.disk.gpt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.zip.CRC32;

import io.github.eternalbits.disk.WrongHeaderException;

class GuidPartitionHeader {
	static final int HEADER_SIZE = 92;
	
	private static final long EFI_PART_SIGNATURE = 0x5452415020494645L; //LITTLE_ENDIAN "EFI PART"
	
	final GptDiskLayout layout;
	
	long	signature;				// Constant "EFI PART"
	int		revision;				// GPT revision
	int		headerSize;				// From signature to partition CRC32
	int		headerCrc32;			// Computed with this field set to zero
	int		reservedInt;			// Must be zero
	long	currentLBA;				// The block that contains this header
	long	backupLBA;				// The block that contains the backup header
	long	firstUsableLBA;			// First block that may be used by a partition
	long	lastUsableLBA;			// Last block that may be used by a partition
	UUID	diskGUID;				// GUID that can be used to uniquely identify the disk
	long	partitionLBA;			// Starting block of the GUID partition table
	int		partitionCount;			// Capacity of the GUID partition table
	int		partitionSize;			// Size of each GUID partition entry
	int		partitionCrc32;			// CRC32 of the GUID partition table

	GuidPartitionHeader(GptDiskLayout gpt, ByteBuffer in) throws IOException, WrongHeaderException {
		this.layout		= gpt;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(GptDiskLayout.BYTE_ORDER);
			
			signature			= in.getLong();
			revision			= in.getInt();
			headerSize			= in.getInt();
			int hcrcIndex		= in.position();
			headerCrc32			= in.getInt();
			reservedInt			= in.getInt();
			currentLBA			= in.getLong();
			backupLBA			= in.getLong();
			firstUsableLBA		= in.getLong();
			lastUsableLBA		= in.getLong();
			diskGUID			= new UUID(in.getLong(), in.getLong());
			partitionLBA		= in.getLong();
			partitionCount		= in.getInt();
			partitionSize		= in.getInt();
			partitionCrc32		= in.getInt();
			
			int tableBlockCount = partitionCount * partitionSize / layout.blockSize;
			
			in.putInt(hcrcIndex, 0);
			CRC32 crc32 = new CRC32();
			crc32.update(in.array(), 0, Math.min(headerSize, in.capacity()));
			in.putInt(hcrcIndex, headerCrc32);
			
			if (signature == EFI_PART_SIGNATURE 
					&& headerCrc32 == (int)crc32.getValue()
					&& currentLBA == 1
					&& partitionCount > 0 && partitionSize >= 128
					&& firstUsableLBA >= partitionLBA + tableBlockCount
					&& lastUsableLBA >= firstUsableLBA - 1
					// The backup structure is ignored
					) {
				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), layout.toString());
	}

}
