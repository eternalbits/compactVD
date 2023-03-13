/*
 * Copyright 2023 Rui Baptista
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

package io.github.eternalbits.linux.disk.btrfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

public class BtrfsVolumeHeader {
	final static int HEADER_SIZE = 4096;

	private static final long SB_MAGIC 	= 0x4D5F53665248425FL;
	
	final BtrfsFileSystem fileSystem;
	
	byte[]	csum;						// Checksum of everything past this field (from 32 to 4096)
	UUID	fsid;						// FS UUID
	long	bytenr;						// physical address of this block (different for mirrors)
	long	flags;						// flags
	long	magic;						// magic ("_BHRfS_M" or 0x4D5F53665248425FL)
	long	generation;					// generation
	long	root;						// logical address of the root tree root
	long	chunk_root;					// logical address of the chunk tree root
	long	log_root;					// logical address of the log tree root
	long	log_root_transid;			// log_root_transid
	long	total_bytes;				// total_bytes
	long	bytes_used;					// bytes_used
	long	root_dir_objectid;			// root_dir_objectid (usually 6)
	long	num_devices;				// num_devices
	int		sectorsize;					// sectorsize
	int		nodesize;					// nodesize
	int		leafsize;					// leafsize
	int		stripesize;					// stripesize
	int		sys_chunk_array_size;		// sys_chunk_array_size
	long	chunk_root_generation;		// chunk_root_generation
	long	compat_flags;				// compat_flags
	long	compat_ro_flags;			// compat_ro_flags - only implementations that support the flags can write to the filesystem
	long	incompat_flags;				// incompat_flags - only implementations that support the flags can use the filesystem

	BtrfsVolumeHeader(BtrfsFileSystem ext, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= ext;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(BtrfsFileSystem.BYTE_ORDER);
			
			csum					= Static.getBytes(in, 32);
			fsid					= new UUID(in.getLong(), in.getLong());
			bytenr					= in.getLong();
			flags					= in.getLong();
			magic					= in.getLong();
			generation				= in.getLong();
			root					= in.getLong();
			chunk_root				= in.getLong();
			log_root				= in.getLong();
			log_root_transid		= in.getLong();
			total_bytes				= in.getLong();
			bytes_used				= in.getLong();
			root_dir_objectid		= in.getLong();
			num_devices				= in.getLong();
			sectorsize				= in.getInt();
			nodesize				= in.getInt();
			leafsize				= in.getInt();
			stripesize				= in.getInt();
			sys_chunk_array_size	= in.getInt();
			chunk_root_generation	= in.getLong();
			compat_flags			= in.getLong();
			compat_ro_flags			= in.getLong();
			incompat_flags			= in.getLong();
			in.position(in.position() + 3900);
			
			if (magic == SB_MAGIC 
					&& Static.isPower2(sectorsize) && Static.isPower2(nodesize) 
					&& Static.isPower2(leafsize) && Static.isPower2(stripesize) 
					&& total_bytes == Static.roundDown(fileSystem.getLength(), sectorsize)) {

				return;
			}
		}

		throw new WrongHeaderException(getClass(), fileSystem.toString());
	}
	
}
