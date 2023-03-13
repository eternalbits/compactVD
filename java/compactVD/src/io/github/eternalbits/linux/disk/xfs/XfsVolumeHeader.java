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

package io.github.eternalbits.linux.disk.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.WrongHeaderException;

public class XfsVolumeHeader {
	final static int HEADER_SIZE = 2048;

	private static final int SB_MAGIC 	= 0x58465342;
	private static final int AGF_MAGIC 	= 0x58414746;
	private static final int AGI_MAGIC 	= 0x58414749;
	
	final XfsFileSystem fileSystem;

	int		sb_magicnum;				// Identifies the filesystem. Its value is XFS_SB_MAGIC "XFSB" (0x58465342).
	int		sb_blocksize;				// The size of a basic unit of space allocation in bytes. This is 4096 but can range from 512 to 65536 bytes.
	long	sb_dblocks;					// Total number of blocks available for data and metadata on the filesystem.
	long	sb_rblocks;					// Number blocks in the real-time disk device. Refer to real-time sub-volumes for more information.
	long	sb_rextents;				// Number of extents on the real-time device.
	UUID	sb_uuid;					// UUID (Universally Unique ID) for the filesystem. Filesystems can be mounted by the UUID instead of device name.
	long	sb_logstart;				// First block number for the journaling log if the log is internal (ie. not on a separate disk device).
	long	sb_rootino;					// Root inode number for the filesystem. This is 128 when using a 4KB block size.
	long	sb_rbmino;					// Bitmap inode for real-time extents.
	long	sb_rsumino;					// Summary inode for real-time bitmap.
	int		sb_rextsize;				// Realtime extent size in blocks.
	int		sb_agblocks;				// Size of each AG in blocks. For the actual size of the last AG, refer to the free space agf_length value.
	int		sb_agcount;					// Number of AGs in the filesystem.
	int		sb_rbmblocks;				// Number of real-time bitmap blocks.
	int		sb_logblocks;				// Number of blocks for the journaling log.
	short	sb_versionnum;				// Filesystem version number. This is a bitmask specifying the features enabled when creating the filesystem.
	short	sb_sectsize;				// Specifies the underlying disk sector size in bytes. Typically this is 512 or 4096 bytes.
	short	sb_inodesize;				// Size of the inode in bytes. The default is 256 to 2048 bytes.
	short	sb_inopblock;				// Number of inodes per block. This is equivalent to sb_blocksize / sb_inodesize.
	byte[]	sb_fname;					// Name for the filesystem (12 bytes). This value can be used in the mount command.
	byte[]	sb_unused;					// 392 bytes
	
	int		agf_magicnum;				// Specifies the magic number for the AGF sector: "XAGF" (0x58414746).
	byte[]	agf_unused;					// 508 bytes
	
	int		agi_magicnum;				// Specifies the magic number for the AGI sector: "XAGI" (0x58414749).
	byte[]	agi_unused;					// 508 bytes
	
	int		agfl_magicnum;				// Specifies the magic number for the AGFL sector: "XAFL" (0x5841464c).
	byte[]	agfl_unused;				// 508 bytes

	XfsVolumeHeader(XfsFileSystem ext, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= ext;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(XfsFileSystem.BYTE_ORDER);
			
			sb_magicnum				= in.getInt();
			sb_blocksize			= in.getInt();
			sb_dblocks				= in.getLong();
			sb_rblocks				= in.getLong();
			sb_rextents				= in.getLong();
			sb_uuid					= new UUID(in.getLong(), in.getLong());
			sb_logstart				= in.getLong();
			sb_rootino				= in.getLong();
			sb_rbmino				= in.getLong();
			sb_rsumino				= in.getLong();
			sb_rextsize				= in.getInt();
			sb_agblocks				= in.getInt();
			sb_agcount				= in.getInt();
			sb_rbmblocks			= in.getInt();
			sb_logblocks			= in.getInt();
			sb_versionnum			= in.getShort();
			sb_sectsize				= in.getShort();
			sb_inodesize			= in.getShort();
			sb_inopblock			= in.getShort();
			sb_fname				= Static.getBytes(in, 12);
			in.position(in.position() + 392);
			
			agf_magicnum			= in.getInt();
			in.position(in.position() + 508);
			
			agi_magicnum			= in.getInt();
			in.position(in.position() + 508);
			
			agfl_magicnum			= in.getInt();
			in.position(in.position() + 508);
			
			if (sb_magicnum == SB_MAGIC && agf_magicnum == AGF_MAGIC && agi_magicnum == AGI_MAGIC
					&& sb_blocksize >= 512 && sb_blocksize <= 65536 && Static.isPower2(sb_blocksize)
					&& sb_blocksize * sb_dblocks == Static.roundDown(fileSystem.getLength(), sb_blocksize)
					&& (sb_sectsize == 512 || sb_sectsize == 4096) && Static.isPower2(sb_sectsize)
					&& sb_inodesize >= 256 && sb_inodesize <= 2048 && Static.isPower2(sb_inodesize)) {

				return;
			}
		}

		throw new WrongHeaderException(getClass(), fileSystem.toString());
	}
	
}
