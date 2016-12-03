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

import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.NullFileSystem;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskFileSystems;
import io.github.eternalbits.linux.disk.lvm.LvmSimpleDiskLayout;

public class MbrDiskLayout extends DiskLayout { // https://en.wikipedia.org/wiki/Master_boot_record
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	// Containers ///////////////////////////////
	private static final int EXTENDED_DOS = 0x05;
	private static final int EXTENDED_LBA = 0x0F;
	private static final int EXTENDED_LINUX = 0x85;
	private static final int LINUX_LVM = 0x8E;
	
	// Non File Systems ////////////////////////
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
		partDesc[0x08] = "IBM AIX boot";
		partDesc[0x09] = "IBM AIX data";
		partDesc[0x0a] = "OS/2 boot";
		partDesc[0x0b] = "DOS FAT32";
		partDesc[0x0c] = "Windos FAT32";
		partDesc[0x0e] = "Windos FAT16B";
		partDesc[0x0f] = "Windos Extended";
		partDesc[0x24] = "NEC DOS";
		partDesc[0x27] = "Windows RE";
		partDesc[0x42] = "Windows LDM";
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
		partDesc[0xaf] = "Mac OSX HFS";
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
	
	private final long blockSize;
	
	public MbrDiskLayout(DiskImage img) throws IOException, WrongHeaderException {
		this.image 		= img;
		this.blockSize 	= img.getLogicalBlockSize();
		
		extendMbr(readBootRecord(0), 0, img.getDiskSize() / img.getLogicalBlockSize());

	}

	private DiskBootRecord readBootRecord(long start) throws IOException, WrongHeaderException {
		byte[] buffer = new byte[DiskBootRecord.BOOT_SIZE];
		int read = image.readAll(start * blockSize, buffer, 0, buffer.length);
		return new DiskBootRecord(this, ByteBuffer.wrap(buffer, 0, read));
	}
	
	private void extendMbr(DiskBootRecord dbr, long start, long size) throws IOException, WrongHeaderException {
		for (int i =0; i < 4; i++) if (!dbr.isPartEmpty(i)) {
			
			if (dbr.getFirstSector(i) + dbr.getSectorCount(i) > size)
				throw new InitializationException(getClass(), this.toString());
			
			long offset = (start + dbr.getFirstSector(i)) * blockSize;
			long length = dbr.getSectorCount(i) * blockSize;
			int type = dbr.getType(i);
			
			switch (type) {
			case EXTENDED_LINUX:
			case EXTENDED_LBA:
			case EXTENDED_DOS:
				long ext = start + dbr.getFirstSector(i);
				extendMbr(readBootRecord(ext), ext, dbr.getSectorCount(i));
				break;
			case LINUX_SWAP:
				getFileSystems().add(new NullFileSystem(this, offset, length, "SWAP", partDesc[type]));
				break;
			case LINUX_LVM: // TODO LinuxSwapSpace extends NullFileSystem, because LVM has no "swap type"?
				try {
					LvmSimpleDiskLayout lvm = new LvmSimpleDiskLayout(image, offset, length);
					for (DiskFileSystem fs: lvm.getFileSystems()) getFileSystems().add(fs);
				}
				catch(WrongHeaderException e) { tryDefault(offset, length, partDesc[type]); }
				break;
			default:
				tryDefault(offset, length, partDesc[type]);
			}
		}
	}
	
	private void tryDefault(long offset, long length, String description) throws IOException {
		getFileSystems().add(DiskFileSystems.map(this, offset, length, description));
	}
	
	@Override
	public String getType() {
		return "MBR";
	}
}
