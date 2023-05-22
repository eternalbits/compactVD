/*
 * Copyright 2020 Rui Baptista
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

package io.github.eternalbits.darwin.disk.apfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

public class ApfsVolumeHeader {
	static long HEADER_XID = -1;

	private static final int SUPERBLOCK_MAGIC = 0x4253584E;	// The BSXN magic number.
	private static final int CONTAINER = 0x80000001;		// The object's type and flags.
	
	final ApfsFileSystem fileSystem;

	long	nx_cksum;								// The Fletcher 64 checksum of the object.
	long	nx_oid;									// The object's identifier.
	long	nx_xid;									// The identifier of the most recent transaction that this object was modified in.
	int		nx_type;								// The object's type and flags. The low 16 bits indicate the type, and the high 16 bits are flags.
	int		nx_subtype;								// The object's subtype. It indicates the type of data stored in a data structure such as a B-tree.
	int		nx_magic;								// A number that can be used to verify that you're reading an instance of nx_superblock_t.
	int		nx_block_size;							// The logical block size used in the Apple File System container.
	long	nx_block_count;							// The total number of logical blocks available in the container.
	long	nx_features;							// A bit field of the optional features being used by this container.
	long	nx_readonly_compatible_features;		// A bit field of the read-only compatible features being used by this container.
	long	nx_incompatible_features;				// A bit field of the backward-incompatible features being used by this container.
	UUID	nx_uuid;								// The universally unique identifier of this container.
	long	nx_next_oid;							// The next object identifier to be used for a new ephemeral or virtual object.
	long	nx_next_xid;							// The next transaction to be used.
	int		nx_xp_desc_blocks;						// The number of blocks used by the checkpoint descriptor area.
	int		nx_xp_data_blocks;						// The number of blocks used by the checkpoint data area.
	long	nx_xp_desc_base;						// Either the base address of the checkpoint descriptor area  or the physical object identifier of a tree.
	long	nx_xp_data_base;						// Either the base address of the checkpoint data area or the physical object identifier of a tree.
	int		nx_xp_desc_next;						// The next index to use in the checkpoint descriptor area.
	int		nx_xp_data_next;						// The next index to use in the checkpoint data area.
	int		nx_xp_desc_index;						// The index of the first valid item in the checkpoint descriptor area.
	int		nx_xp_desc_len;							// The number of blocks in the checkpoint descriptor area used by the checkpoint that this superblock belongs to.
	int		nx_xp_data_index;						// The index of the first valid item in the checkpoint data area.
	int		nx_xp_data_len;							// The number of blocks in the checkpoint data area used by the checkpoint that this superblock belongs to.
	long	nx_spaceman_oid;						// The ephemeral object identifier for the space manager.
	long	nx_omap_oid;							// The physical object identifier for the container's object map.
	long	nx_reaper_oid;							// The ephemeral object identifier for the reaper.
	byte[]	nx_still_used;							// Still used block (1224)
	byte[]	nx_unused;								// Unused block (2696)

	ApfsVolumeHeader(ApfsFileSystem apfs, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= apfs;

		if (in.remaining() >= ApfsFileSystem.HEADER_SIZE) {
			in.order(ApfsFileSystem.BYTE_ORDER);
			
			if (ApfsFileSystem.checkChecksum(in) == 0) {
				nx_cksum						= in.getLong();
				nx_oid							= in.getLong();
				nx_xid							= in.getLong();
				nx_type							= in.getInt();
				nx_subtype						= in.getInt();
				nx_magic						= in.getInt();
				nx_block_size					= in.getInt();
				nx_block_count					= in.getLong();
				nx_features						= in.getLong();
				nx_readonly_compatible_features	= in.getLong();
				nx_incompatible_features		= in.getLong();
				nx_uuid							= new UUID(in.getLong(), in.getLong());
				nx_next_oid						= in.getLong();
				nx_next_xid						= in.getLong();
				nx_xp_desc_blocks				= in.getInt();
				nx_xp_data_blocks				= in.getInt();
				nx_xp_desc_base					= in.getLong();
				nx_xp_data_base					= in.getLong();
				nx_xp_desc_next					= in.getInt();
				nx_xp_data_next					= in.getInt();
				nx_xp_desc_index				= in.getInt();
				nx_xp_desc_len					= in.getInt();
				nx_xp_data_index				= in.getInt();
				nx_xp_data_len					= in.getInt();
				nx_spaceman_oid					= in.getLong();
				nx_omap_oid						= in.getLong();
				nx_reaper_oid					= in.getLong();
				in.position(in.position() + 1224);
				in.position(in.position() + 2696);
	
				if (nx_type == CONTAINER && nx_subtype == 0 
						&& nx_magic == SUPERBLOCK_MAGIC && Static.isPower2(nx_block_size) 
						&& nx_block_count * nx_block_size == fileSystem.getLength()
						&& nx_xp_desc_blocks > 32 && nx_xp_desc_blocks < 512) {
					
					HEADER_XID = nx_xid;
					return;
				}
			}
		}

		throw new WrongHeaderException(getClass(), fileSystem.toString());
	}
	
}
