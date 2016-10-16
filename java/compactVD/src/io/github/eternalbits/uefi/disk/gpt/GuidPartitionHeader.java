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

import io.github.eternalbits.disk.InitializationException;

class GuidPartitionHeader {
	static final int HEADER_SIZE = 92;
	
	private static final long EFI_PART_SIGNATURE = 0x5452415020494645L; //LITTLE_ENDIAN "EFI PART"
	private static final int DOCUMENTED_REVISION = 1 << 16 + 0; //1.0

	private static final int DEFAULT_PARTITION_COUNT = 128;
	
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
	long	alternateLBA;			// Starting block of the GUID alternate table
	int		partitionCount;			// Capacity of the GUID partition table
	int		partitionSize;			// Size of each GUID partition entry
	int		partitionCrc32;			// CRC32 of the GUID partition table

	GuidPartitionHeader(GptDiskLayout gpt, long blockCount) {
		this.layout		= gpt;
		int tableCount	= DEFAULT_PARTITION_COUNT * GuidPartitionTable.ENTRY_SIZE / layout.blockSize;
		
		signature 		= EFI_PART_SIGNATURE;
		revision		= DOCUMENTED_REVISION;
		headerSize		= HEADER_SIZE;
		headerCrc32		= 0;
		reservedInt		= 0;
		currentLBA		= 1;
		backupLBA		= blockCount - 1;
		firstUsableLBA	= 2 + tableCount;
		lastUsableLBA	= blockCount - tableCount - 2;
		diskGUID		= UUID.randomUUID();
		partitionLBA	= 2;
		alternateLBA	= blockCount - tableCount - 1;
		partitionCount	= DEFAULT_PARTITION_COUNT;
		partitionSize	= GuidPartitionTable.ENTRY_SIZE;
		partitionCrc32	= 0;
	}

	GuidPartitionHeader(GptDiskLayout gpt, long blockCount, ByteBuffer in) throws IOException {
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
			
			int tableCount 		= partitionCount * partitionSize / layout.blockSize;
			alternateLBA		= blockCount - tableCount - 1;
			
			in.putInt(hcrcIndex, 0);
			CRC32 crc32 = new CRC32();
			crc32.update(in.array(), 0, HEADER_SIZE);
			in.putInt(hcrcIndex, headerCrc32);
			
			if (signature == EFI_PART_SIGNATURE 
					&& headerSize == HEADER_SIZE
					&& headerCrc32 == (int)crc32.getValue()
					&& currentLBA == 1 && partitionLBA == 2
					&& partitionCount > 0 && partitionSize >= 128
					&& firstUsableLBA == partitionLBA + tableCount
					&& lastUsableLBA == alternateLBA - 1
					&& backupLBA == blockCount - 1) {
				return;
			}
		}
		
		throw new InitializationException(getClass(), layout.toString());
	}

	void update(boolean isBackup) throws IOException {
		
		byte[] buffer = new byte[HEADER_SIZE];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(GptDiskLayout.BYTE_ORDER);
		
		bb.putLong(signature);
		bb.putInt(revision);
		bb.putInt(headerSize);
		int hcrcIndex = bb.position();
		bb.putInt(0);
		bb.putInt(reservedInt);
		bb.putLong(isBackup? backupLBA: currentLBA);
		bb.putLong(isBackup? currentLBA: backupLBA);
		bb.putLong(firstUsableLBA);
		bb.putLong(lastUsableLBA);
		bb.putLong(diskGUID.getMostSignificantBits());
		bb.putLong(diskGUID.getLeastSignificantBits());
		bb.putLong(isBackup? alternateLBA: partitionLBA);
		bb.putInt(partitionCount);
		bb.putInt(partitionSize);
		bb.putInt(partitionCrc32);
		
		CRC32 crc32 = new CRC32();
		crc32.update(bb.array());
		headerCrc32 = (int)crc32.getValue();
		bb.putInt(hcrcIndex, headerCrc32);
		
		layout.getImage().seek(layout.blockSize * (isBackup? backupLBA: currentLBA));
		layout.getImage().write(buffer, 0, buffer.length);
	}
}
