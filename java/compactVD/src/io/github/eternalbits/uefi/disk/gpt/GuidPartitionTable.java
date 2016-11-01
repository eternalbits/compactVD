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
import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.NullFileSystem;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskFileSystems;
import io.github.eternalbits.linux.disk.lvm.LvmSimpleDiskLayout;

class GuidPartitionTable {
	
	private static final UUID EFI_SYSTEM_GUID = new UUID(0x11D2F81FC12A7328L, 0x3BC93EC9A0004BBAL);
	private static final UUID LINUX_SWAP_GUID = new UUID(0x43C4A4AB0657FD6DL, 0x4F4F4BC83309E584L);
	private static final UUID LINUX_LVM_GUID = new UUID(0x44C2F507E6D6D379L, 0x28F93D2A8F233CA2L);
	private static final UUID NULL_UUID = new UUID(0, 0);
	private static final int NAME_SIZE = 72;

	final GuidPartitionHeader header;
	final GuidPartitionEntry[] array;
	final GptDiskLayout layout;

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
					if (array[i].startingLBA < header.firstUsableLBA
							|| array[i].endingLBA > header.lastUsableLBA
							|| array[i].endingLBA < array[i].startingLBA - 1) {
						throw new InitializationException(getClass(), layout.toString());
					}
					long length = (array[i].endingLBA - array[i].startingLBA + 1) * layout.blockSize;
					long offset = array[i].startingLBA * layout.blockSize;
					
					if (array[i].partitionType.equals(EFI_SYSTEM_GUID)) {
						addNullFileSystem(offset, length, "EFI", "EFI System Partition");
					} else
					if (array[i].partitionType.equals(LINUX_SWAP_GUID)) {
						addNullFileSystem(offset, length, "SWAP", "Linux Swap Space");
					} else
					if (array[i].partitionType.equals(LINUX_LVM_GUID)) {
						try {
							LvmSimpleDiskLayout lvm = new LvmSimpleDiskLayout(gpt.getImage(), offset, length);
							for (DiskFileSystem fs: lvm.getFileSystems()) gpt.getFileSystems().add(fs);
						}
						catch(WrongHeaderException e) { tryDefault(offset, length); }
					} else {
						tryDefault(offset, length);
					}
				}
			}
		}
	}
	
	private void addNullFileSystem(long offset, long length, String type, String description) {
		layout.getFileSystems().add(new NullFileSystem(layout, offset, length, type, description));
	}
	
	private void tryDefault(long offset, long length) throws IOException {
		layout.getFileSystems().add(DiskFileSystems.map(layout, offset, length, null));
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
