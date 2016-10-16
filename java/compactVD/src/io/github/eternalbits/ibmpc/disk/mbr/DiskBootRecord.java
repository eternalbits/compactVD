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

import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

public class DiskBootRecord {
	static final int BOOT_SIZE = 512;
	
	private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	private static final short BOOT_SIGNATURE = (short)0xAA55;
	
	final DiskLayout layout;
	
	byte[]		bootstrap;					// Bootstrap code area [446 bytes]
	BootPart[]	bootTable;					// 4 partition entries
	short		bootSign;					// Boot signature

	public DiskBootRecord(DiskLayout mbr, ByteBuffer in) throws IOException, WrongHeaderException {
		this.layout		= mbr;
		
		if (in.remaining() >= BOOT_SIZE) {
			in.order(BYTE_ORDER);
			
			in.position(in.position() + 446);
			bootTable		= new BootPart[4];
			for (int i=0, s=bootTable.length; i<s; i++ )
				bootTable[i] = new BootPart(in);
			bootSign		= in.getShort();
			
			if (bootSign == BOOT_SIGNATURE) {
				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), layout.toString());
	}

	public static int bufferSize() {
		return BOOT_SIZE;
	}

	private class BootPart {
		
		int	statusChsFS;	// packed CHS first sector with status
		int typeChsLS;		// packed CHS last sector with partition type
		int	firstSector;	// Logical block address of first sector
		int	sectorCount;	// Number of sectors in partition
		
		BootPart(ByteBuffer in) throws InitializationException, WrongHeaderException {
			
			statusChsFS = in.getInt();
			typeChsLS 	= in.getInt();
			firstSector	= in.getInt();
			sectorCount	= in.getInt();
			if (isPartEmpty())
				return;
			
			if ((statusChsFS & 0x7F) == 0 && (typeChsLS & 0xFF) != 0
					&& firstSector > 0 && sectorCount > 0) {
				return;
			}
			
			throw new WrongHeaderException(getClass(), layout.toString());
		}
		
		boolean isPartEmpty() {
			return (statusChsFS | typeChsLS | firstSector | sectorCount) == 0;
		}
		
		int getType() {
			return typeChsLS & 0xFF;
		}
		
	}

	public int getType(int i) {
		return bootTable[i].getType();
	}

	public boolean isPartEmpty(int i) {
		return bootTable[i].isPartEmpty();
	}

	public int getFirstSector(int i) {
		return bootTable[i].firstSector;
	}

	public int getSectorCount(int i) {
		return bootTable[i].sectorCount;
	}
}
