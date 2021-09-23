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

package io.github.eternalbits.apple.disk.apfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

public class ApfsVolumeHeader {
	final static int HEADER_SIZE = 4096;

	private static final int SUPERBLOCK_MAGIC = 0x4253584E;
	private static final int CONTAINER = 0x80000001;
	
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
	int		nx_test_type;							// Reserved for testing.
	int		nx_max_file_systems;					// The maximum number of volumes that can be stored in this container.
	long[]	nx_fs_oid;								// An array of virtual object identifiers for volumes, divide the size of the container by 512 MiB and round up (100).
	long[]	nx_counters;							// An array of counters that store information about the container (32).
	long	nx_blocked_out_start_paddr;				// The physical range of starts where space will not be allocated.
	long	nx_blocked_out_block_count;				// The physical range of blocks where space will not be allocated.
	long	nx_evict_mapping_tree_oid;				// The physical object identifier of a tree used to keep track of objects that must be moved out of blocked-out storage.
	long	nx_flags;								// Other container flags.
	long	nx_efi_jumpstart;						// The physical object identifier of the object that contains EFI driver data extents.
	UUID	nx_fusion_uuid;							// The universally unique identifier of the container's Fusion set, or zero for non-Fusion containers.
	long	nx_keylocker;							// The location of the container's keybag.
	long[]	nx_ephemeral_info;						// An array of fields used in the management of ephemeral data (4).
	long	nx_test_oid;							// Reserved for testing.
	long	nx_fusion_mt_oid;						// The physical object identifier of the Fusion middle tree.
	long	nx_fusion_wbc_oid;						// The ephemeral object identifier of the Fusion write-back cache state.
	long[]	nx_fusion_wbc;							// The blocks used for the Fusion write-back cache area.
	long	nx_newest_mounted_version;				// Other implementations must not modify this field.
	long[]	nx_mkb_locker;							// Wrapped media key.
	byte[]	nx_unused;								// Unused block (2696)

	ApfsVolumeHeader(ApfsFileSystem apfs, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= apfs;

		if (in.remaining() >= HEADER_SIZE) {
			in.order(ApfsFileSystem.BYTE_ORDER);
			
			if (checkChecksum(in) == 0) {
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
				nx_test_type					= in.getInt();
				nx_max_file_systems				= in.getInt();
				nx_fs_oid						= Static.getLongs(in, 100);
				nx_counters						= Static.getLongs(in, 32);
				nx_blocked_out_start_paddr		= in.getLong();
				nx_blocked_out_block_count		= in.getLong();
				nx_evict_mapping_tree_oid		= in.getLong();
				nx_flags						= in.getLong();
				nx_efi_jumpstart				= in.getLong();
				nx_fusion_uuid					= new UUID(in.getLong(), in.getLong());
				nx_keylocker					= in.getLong();
				nx_ephemeral_info				= Static.getLongs(in, 4);
				nx_test_oid						= in.getLong();
				nx_fusion_mt_oid				= in.getLong();
				nx_fusion_wbc_oid				= in.getLong();
				nx_fusion_wbc					= Static.getLongs(in, 2);
				nx_newest_mounted_version		= in.getLong();
				nx_mkb_locker					= Static.getLongs(in, 2);
				nx_unused						= Static.getBytes(in, 2696);
	
				if (nx_type == CONTAINER && nx_subtype == 0 
						&& nx_magic == SUPERBLOCK_MAGIC && Static.isPower2(nx_block_size) 
						&& nx_block_count * nx_block_size == fileSystem.getLength()
						&& nx_xp_desc_blocks == nx_xp_data_base - nx_xp_desc_base
						&& nx_xp_desc_blocks > 32 && nx_xp_desc_blocks < 512) {
					return;
				}
			}
		}

		throw new WrongHeaderException(getClass(), fileSystem.toString());
	}
	
    private long checkChecksum(ByteBuffer data) {
        long modValue = (2L<<31) - 1;
        long sum = 0;
        for (int i = 0; i < data.capacity(); i=i+4) {
            long check = data.getInt(i) & modValue;
            sum = (sum + check) % modValue;
         }
        return sum;
    }
}
