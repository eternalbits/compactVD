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

import io.github.eternalbits.disk.WrongHeaderException;

class LvmMetadaAreaHeader {
	static final int HEADER_SIZE = 512;

	private static long MAGICONE = 0x5B7820324D564C20L;
	private static long MAGICTWO = 0x3E2A4E3072254135L;

	final LvmSimpleDiskLayout layout;

//	MDA header
	int		crc;							// Checksum of rest of mda_header [non-standard, not checked]
	long	magic1;							// " LVM2 x[", to aid scans for metadata
	long	magic2;							// "5A%r0N*>"
	int		version;						// Version
	long	start;							// Absolute start byte of MDA header
	long	size;							// Size of metadata area
	
//	Metadata list
	long	metadataOffset;					// Offset in bytes to start of MDA header
	long	metadataSize;					// Bytes with null terminated metadata
	int		metadataCrc;					// Checksum [non-standard, not checked]
	int		metadataFiller;					// Filler
	
	LvmMetadaAreaHeader(LvmSimpleDiskLayout lvm, ByteBuffer in) throws WrongHeaderException {
		this.layout		= lvm;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(LvmSimpleDiskLayout.BYTE_ORDER);
			
			crc		= in.getInt();
			magic1	= in.getLong();
			magic2	= in.getLong();
			version	= in.getInt();
			start	= in.getLong();
			size	= in.getLong();
			
			metadataOffset	= in.getLong();
			metadataSize	= in.getLong();
			metadataCrc		= in.getInt();
			metadataFiller	= in.getInt();
			
			if (magic1 == MAGICONE && magic2 == MAGICTWO
					&& start == layout.label.metaStart && size == layout.label.metaSize
					&& metadataOffset > 0 && metadataSize > 0) {
				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), layout.toString());
	}

}
