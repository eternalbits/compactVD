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

package io.github.eternalbits.ibmpc.disk.mbr;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.TreeMap;

import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.NullFileSystem;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskFileSystems;

public class MbrDiskLayout extends DiskLayout { // https://en.wikipedia.org/wiki/Master_boot_record
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	private static final int EXTENDED_DOS = 0x05;
	private static final int EXTENDED_LBA = 0x0F;
	private static final int EXTENDED_LINUX = 0x85;
	private static final int OLD_LINUX_SWAP = 0x42;
	private static final int LINUX_SWAP = 0x82;
	
	private static final String[] partDesc = new String[256];
	static { // https://en.wikipedia.org/wiki/Partition_type
		partDesc[0x00] = "Empty";
		partDesc[0x01] = "DOS FAT12";
		partDesc[0x02] = "XENIX root";
		partDesc[0x03] = "XENIX usr";
		partDesc[0x04] = "DOS FAT16";
		partDesc[0x05] = "DOS Extended";
		partDesc[0x06] = "DOS FAT16B";
		partDesc[0x07] = "Windows NTFS";
		partDesc[0x08] = "IBM AIX";
		partDesc[0x09] = "IBM AIX boot";
		partDesc[0x0a] = "OS/2";
		partDesc[0x0b] = "DOS FAT32";
		partDesc[0x0c] = "Windos FAT32";
		partDesc[0x0e] = "Windos FAT16B";
		partDesc[0x0f] = "Windos Extended";
		partDesc[0x24] = "NEC DOS";
		partDesc[0x27] = "Windows RE";
		partDesc[0x41] = "Old Linux/Minix";
		partDesc[0x42] = "Old Linux swap";
		partDesc[0x43] = "Old Linux";
		partDesc[0x52] = "CP/M-80";
		partDesc[0x63] = "Unix";
		partDesc[0x64] = "Netware 286";
		partDesc[0x65] = "Netware 386";
		partDesc[0x75] = "PC/IX";
		partDesc[0x81] = "Minix";
		partDesc[0x82] = "Linux swap";
		partDesc[0x83] = "Linux";
		partDesc[0x85] = "Linux extended";
		partDesc[0x88] = "Linux plaintext";
		partDesc[0x8e] = "Linux LVM";
		partDesc[0x9f] = "BSD/OS";
		partDesc[0xa5] = "FreeBSD";
		partDesc[0xa6] = "OpenBSD";
		partDesc[0xa7] = "NeXTSTEP";
		partDesc[0xa8] = "Darwin";
		partDesc[0xa9] = "NetBSD";
		partDesc[0xab] = "Darwin boot";
		partDesc[0xbe] = "Solaris boot";
		partDesc[0xbf] = "Solaris";
		partDesc[0xdb] = "CP/M-86";
		partDesc[0xeb] = "BeOS";
		partDesc[0xed] = "EFI hybrid MBR";
		partDesc[0xee] = "EFI protective MBR";
		partDesc[0xef] = "EFI system partition";
		partDesc[0xf0] = "Linux/PA-RISC boot";
		partDesc[0xfd] = "Linux RAID";
		partDesc[0xff] = "XENIX bad block";
	}
	
	private class TypeAndLength {
		final int length;
		final int type;
		TypeAndLength (int type, int length) {
			this.length = length;
			this.type = type & 0xFF;
		}
	}
	
	private final TreeMap<Integer, TypeAndLength> partMap = new TreeMap<Integer, TypeAndLength>();
	private final long blockSize;
	
	public MbrDiskLayout(DiskImage img) throws IOException, WrongHeaderException {
		this.image 		= img;
		this.blockSize 	= img.getLogicalBlockSize();
		
		extendMbr(readBootRecord(0), 0);

		int next = 0;
		for (Map.Entry<Integer,TypeAndLength> pm: partMap.entrySet()) {
			int start = pm.getKey(), length = pm.getValue().length;
			int type = pm.getValue().type;
			if (start < next) {
				next = -1;
				break;
			}
			next = start + length;
			if (next > 0) {
				if (type == OLD_LINUX_SWAP || type == LINUX_SWAP) {
					getFileSystems().add(new NullFileSystem(this, start * blockSize, 
							length * blockSize, "SWAP", partDesc[type]));
				} else {
					getFileSystems().add(DiskFileSystems.map(this, start * blockSize, 
							length * blockSize, partDesc[type]));
				}
			}
		}
		
		if (next < 0 || next > img.getDiskSize() / blockSize)
			throw new InitializationException(getClass(), image.toString());
	}

	private DiskBootRecord readBootRecord(int start) throws IOException, WrongHeaderException {
		byte[] buffer = new byte[DiskBootRecord.BOOT_SIZE];
		int read = image.readAll(start * blockSize, buffer, 0, buffer.length);
		return new DiskBootRecord(this, ByteBuffer.wrap(buffer, 0, read));
	}
	
	private void extendMbr(DiskBootRecord dbr, int start) throws IOException, WrongHeaderException {
		for (int i =0; i < 4; i++) if (!dbr.isPartEmpty(i)) {
			int type = dbr.getType(i);
			if (type != EXTENDED_DOS && type != EXTENDED_LBA && type != EXTENDED_LINUX) {
				partMap.put(start + dbr.getFirstSector(i), new TypeAndLength(dbr.getType(i), dbr.getSectorCount(i)));
			} else {
				int ext = start + dbr.getFirstSector(i);
				extendMbr(readBootRecord(ext), ext);
			}
		}
	}
	
	@Override
	public String getType() {
		return "MBR";
	}
}
