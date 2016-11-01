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

package io.github.eternalbits.linux.disk.lvm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

class LvmPhysicalVolumeLabel {
	static final int LABEL_SIZE = 512;

	private static long LABELONE = 0x454E4F4C4542414CL;

	final LvmSimpleDiskLayout layout;
	
//	Label header
	long 	id;								// LABELONE
	long 	sector;							// Sector number of this label
	int		crc;							// From next field to end of sector [non-standard, not checked]
	int		offset;							// Offset from start of labelHeader to pvHeader
	long	type;							// LVM2 001
	
//	Physical volume header
	String	uuid;							// Physical volume UUID, 32 alphanumeric (ASCII) bytes
	long	deviceSize;						// This size can be overridden if PV belongs to a VG
	
//	Locations and sizes
	long	dataOffset;						// Data area location in bytes; data area size is usually zero
	long	metaStart;						// Metadata area header location in bytes
	long	metaSize;						// Metadata area header size in bytes
	
	LvmPhysicalVolumeLabel(LvmSimpleDiskLayout lvm, ByteBuffer in) throws WrongHeaderException {
		this.layout		= lvm;
		
		in.order(LvmSimpleDiskLayout.BYTE_ORDER);
		for (int i = 0; i+8 < in.remaining(); i += LABEL_SIZE) {
			if (in.getLong(i) == LABELONE) {
				in.position(i);
				if (in.remaining() >= LABEL_SIZE) {
					
					id		= in.getLong();
					sector	= in.getLong();
					crc		= in.getInt();
					offset	= in.getInt();
					type	= in.getLong();
					
					if (sector == i/LABEL_SIZE && offset >= 32 && offset < 408) {
						in.position(i + offset);
						
						uuid			= formatedUuid(in);
						deviceSize		= in.getLong();

						dataOffset		= in.getLong(); // "Must be exactly one data area"
						in.getLong();
						// Save only first data area and first metadata area header location.
						// No metadata is a valid configuration but is not supported here.
						while (in.getLong() != 0) in.getLong(); in.getLong();
						metaStart	= in.getLong();
						metaSize	= in.getLong();
						
						if (dataOffset > 0 && metaStart > 0 && metaSize > 0) {
							return;
						}
					}
				}
				break;
			}
		}
		
		throw new WrongHeaderException(getClass(), layout.toString());
	}
	
	private static String formatedUuid(ByteBuffer in) {
		StringBuilder sb = new StringBuilder(Static.getString(in, 6, StandardCharsets.US_ASCII));
		for (int i = 0; i < 6; i++) sb.append("-").append(Static.getString(in, 4, StandardCharsets.US_ASCII));
		return sb.append(Static.getString(in, 2, StandardCharsets.US_ASCII)).toString();
	}

}
