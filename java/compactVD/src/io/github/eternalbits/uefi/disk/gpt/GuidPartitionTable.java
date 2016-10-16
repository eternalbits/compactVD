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
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.NullFileSystem;
import io.github.eternalbits.disks.DiskFileSystems;

class GuidPartitionTable {
	static final int ENTRY_SIZE = 128;
	
	private static final UUID EFI_SYSTEM_GUID = new UUID(0x11D2F81FC12A7328L, 0x3BC93EC9A0004BBAL);
	private static final UUID NULL_UUID = new UUID(0, 0);
	private static final int NAME_SIZE = 72;

	final GuidPartitionHeader header;
	final GuidPartitionEntry[] array;
	final GptDiskLayout layout;

	GuidPartitionTable(GptDiskLayout gpt) {
		this.layout		= gpt;
		this.header		= gpt.header;
		
		array = new GuidPartitionEntry[header.partitionCount];

	}

	GuidPartitionTable(GptDiskLayout gpt, ByteBuffer in) throws IOException {
		this.layout		= gpt;
		this.header		= gpt.header;
		
		array = new GuidPartitionEntry[header.partitionCount];
		
		if (in.remaining() >= header.partitionSize * header.partitionCount) {
			in.order(GptDiskLayout.BYTE_ORDER);
			
			for (int i = 0; i < header.partitionCount; i++) {
				in.position(i * header.partitionSize);
				array[i] = new GuidPartitionEntry(in);
				if (array[i].partitionType.equals(NULL_UUID)) {
					array[i] = null;
				} else {
					long length = (array[i].endingLBA - array[i].startingLBA + 1) * layout.blockSize;
					long offset = array[i].startingLBA * layout.blockSize;
					if (array[i].partitionType.equals(EFI_SYSTEM_GUID)) {
						gpt.getFileSystems().add(new NullFileSystem(gpt, offset, length, "EFI", "EFI System Partition"));
					} else {
						gpt.getFileSystems().add(DiskFileSystems.map(gpt, offset, length, null));
					}
				}
			}
		}
	}

	void update() throws IOException {
		
		byte[] buffer = new byte[header.partitionSize];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(GptDiskLayout.BYTE_ORDER);
		
		layout.getImage().seek(header.partitionLBA * layout.blockSize);
		layout.getImage().write(buffer);
	}
	
	class GuidPartitionEntry {
		
		UUID	partitionType;
		UUID	uniqueGUID;
		long	startingLBA;
		long	endingLBA;
		long	attributes;
		String	partitionName;
		
		GuidPartitionEntry(ByteBuffer in) {
			partitionType		= new UUID(in.getLong(), in.getLong());
			uniqueGUID			= new UUID(in.getLong(), in.getLong());
			startingLBA			= in.getLong();
			endingLBA			= in.getLong();
			attributes			= in.getLong();
			partitionName		= Static.getString(in, NAME_SIZE, StandardCharsets.UTF_16LE);
		}
	}

}
