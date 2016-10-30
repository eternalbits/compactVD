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

package io.github.eternalbits.linux.disk.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

/**
 * The class {@code ExtVolumeHeader} represents the Extended File System "Super Block"
 *  that holds information about the enclosing file system and the data block bitmaps.
 * Source: <a href="https://ext4.wiki.kernel.org/index.php/Ext4_Disk_Layout"
 *  >Ext4 Wiki: Ext4 Disk Layout</a>.
 */
class ExtVolumeHeader {
	final static int HEADER_SIZE = 1024;

	private static final short EXT_MAGIC = (short)0xEF53;
	
	private static final int COMPAT_HAS_JOURNAL 	= 0x4; 
	private static final int COMPAT_UNKNOWN			= 0b11111111111111111111110000000000;
	private static final int INCOMPAT_JOURNAL_DEV	= 0x8;
	private static final int INCOMPAT_META_BG		= 0x10;
	private static final int INCOMPAT_UNKNOWN		= 0b11111111111111111000000000000000;
	private static final int RO_COMPAT_UNKNOWN		= 0b11111111111111111100000000000000;
	
	final ExtFileSystem fileSystem;

	int 	inodesCount;			// Total inode count
	int 	blocksCount;			// Total block count
	int 	resBlocksCount;			// This number of blocks can only be allocated by the super-user
	int 	freeBlocksCount;		// Free block count
	int 	freeInodesCount;		// Free inode count
	int 	firstDataBlock;			// First data block. This is 1 when blockSize = 1K, 0 otherwise. Has impact on allocation bitmaps
	int 	blockSize;				// Block size is 2 ^ (10 + logBlockSize)
	int 	clusterSize;			// Cluster size is 2 ^ (10 + logClusterSize)
	int 	blocksPerGroup;			// Blocks per group
	int 	clustersPerGroup;		// Clusters per group
	int 	inodesPerGroup;			// Inodes per group
	int 	mountTime;				// Mount time, in seconds since the epoch
	int 	writeTime;				// Write time, in seconds since the epoch
	short 	mountCount;				// Number of mounts since the last FSCK
	short 	maxMountCount;			// Number of mounts beyond which a FSCK is needed
	short 	magic;					// Magic signature, 0xEF53
	short 	state;					// File system state. 1 = Cleanly unmounted 
	short 	errors;					// Behavior when detecting errors
	short 	minorRevLevel;			// Minor revision level
	int 	lastCheck;				// Time of last check, in seconds since the epoch
	int 	checkInterval;			// Maximum time between checks, in seconds
	int 	creatorOS;				// 0=Linux, 3=FreeBSD, others
	int 	revLevel;				// Revision level
	short 	defResUid;				// Default user for reserved blocks
	short 	defResGid;				// Default group for reserved blocks
	int 	firstInode;				// First non-reserved inode
	short 	inodeSize;				// Size of inode structure, in bytes
	short 	blockGroupNumber;		// Block group number of this super block
	int 	featureCompat;			// Compatible feature set flags. Kernel can still read/write even if it doesn't understand a flag
	int 	featureIncompat;		// Incompatible feature set. If Kernel doesn't understand one of these bits, it should stop
	int 	featureROCompat;		// Read only compatible feature set. Kernel can mount the file system in read-only mode
	UUID 	volumeUuid;				// 128-bit UUID for volume
	String 	volumeName;				// Volume label [16 bytes]
	String 	lastMounted;			// Directory where the file system was last mounted [64 bytes]
	int 	algoUsageBitmap;		// For compression (Not used in e2fsprogs/Linux)
	byte 	preallocBlocks;			// Number of blocks to try to preallocate for ... files? (Not used in e2fsprogs/Linux)
	byte 	preallocDirBlocks;		// Number of blocks to preallocate for directories. (Not used in e2fsprogs/Linux)
	short 	reservedGdtBlocks;		// Number of reserved GDT blocks for future file system expansion
	UUID 	journalUuid;			// UUID of journal super block
	int 	journalInode;			// Inode number of journal file
	int 	journalDevice;			// Device number of journal file, if the external journal feature flag is set
	int 	lastOrphan;				// Start of list of orphaned inodes to delete
	byte[] 	hashSeed;				// HTREE hash seed [16 bytes]
	byte 	defHashVersion;			// Default hash algorithm to use for directory hashes
	byte 	journalBackupType;		// If this value is 0 or EXT3_JNL_BACKUP_BLOCKS, then journalBlocks contains a backup
	short 	descSize;				// Size of group descriptors, in bytes, if the 64bit feature flag is set. Default is 32 bytes
	int 	defaultMountOpts;		// Default mount options
	int 	firstMetaBG;			// First meta block group, if the meta_bg feature is enabled
	int 	mkfsTime;				// When the file system was created, in seconds since the epoch
	byte[] 	journalBlocks;			// Backup copy of the journal inode's array in the first 15 elements, i_size_high and i_size [17 integers]
	int 	blocksCountHigh;		// High 32-bits of the total block count
	int 	resBlocksCountHigh;		// High 32-bits of the reserved block count
	int 	freeBlocksCountHigh;	// High 32-bits of the free block count
	short 	minExtraInodeSize;		// All inodes have at least this number of extra bytes
	short 	wantExtraInodeSize;		// New inodes should reserve this number of bytes
	int 	flags;					// Miscellaneous flags
	short 	raidStride;				// RAID stride. This affects the placement of file system metadata
	short 	mmpInterval;			// Number of seconds to wait in multi-mount prevention (MMP) checking
	long 	mmpBlock;				// Block number for multi-mount protection data
	int 	raidStripeWidth;		// RAID stripe width. Used by the block allocator to try to reduce read-modify-write operations
	int 	groupsPerFlex;			// Size of a flexible block group is 2 ^ logGroupsPerFlex !disk size of logGroupsPerFlex is byte!
	byte 	checksumType;			// Metadata checksum algorithm type. The only valid value is 1 (crc32c)
	short 	reservedPad;			// 
	long 	kbytesWritten;			// Number of KiB written to this file system over its lifetime
	int 	snapshotInode;			// Inode number of active snapshot. (Not used in e2fsprogs/Linux.)
	int 	snapshotId;				// Sequential ID of active snapshot. (Not used in e2fsprogs/Linux.)
	long 	snapshotReserved;		// Number of blocks reserved for active snapshot's future use. (Not used in e2fsprogs/Linux.)
	int 	snapshotList;			// Inode number of the head of the on-disk snapshot list. (Not used in e2fsprogs/Linux.)
	int 	errorCount;				// Number of errors seen
	int 	firstErrorTime;			// First time an error happened, in seconds since the epoch
	int 	firstErrorInode;		// Inode involved in first error
	long 	firstErrorBlock;		// Number of block involved of first error
	String 	firstErrorFunc;			// Name of function where the error happened [32 bytes]
	int 	firstErrorLine;			// Line number where error happened
	int 	lastErrorTime;			// Time of most recent error, in seconds since the epoch
	int 	lastErrorInode;			// Inode involved in most recent error
	int 	lastErrorLine;			// Line number where most recent error happened
	long 	lastErrorBlock;			// Number of block involved in most recent error
	String 	lastErrorFunc;			// Name of function where the most recent error happened [32 bytes]
	String 	mountOpts;				// ASCIIZ string of mount options [64 bytes]
	int 	userQuotaInode;			// Inode number of user quota file
	int 	groupQuotaInode;		// Inode number of group quota file
	int 	overheadBlocks;			// Overhead blocks/clusters in the file system. This field is always zero?
	long 	backupBG;				// Block groups containing super block backups (if sparse_super2)
	int 	encryptAlgos;			// Encryption algorithms in use. There can be up to four algorithms in use at any time
	byte[] 	encryptPwSalt;			// Salt for the string2key algorithm for encryption [16 bytes]
	int 	lostFoundInode;			// Inode number of lost+found
	int 	checksumSeed;			// Checksum seed used for metadata_csum calculations. This value is crc32c(~0, $orig_fs_uuid)
	byte[] 	reserved;				// Padding to the end of the block [396 bytes]
	int 	checksum;				// Super block checksum
	
	final int[] bitmapBlockOrMaker;
	final int clustersInLastGroup;
	final int blocksInLastGroup;
	final int clustersCount;
	
	ExtVolumeHeader(ExtFileSystem ext, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= ext;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(ExtFileSystem.BYTE_ORDER);
			
			inodesCount 		= in.getInt();
			blocksCount 		= in.getInt();
			resBlocksCount 		= in.getInt();
			freeBlocksCount 	= in.getInt();
			freeInodesCount 	= in.getInt();
			firstDataBlock 		= in.getInt();
			blockSize 			= 1024 << in.getInt();
			clusterSize 		= 1024 << in.getInt();
			blocksPerGroup 		= in.getInt();
			clustersPerGroup 	= in.getInt();
			inodesPerGroup 		= in.getInt();
			mountTime 			= in.getInt();
			writeTime 			= in.getInt();
			mountCount 			= in.getShort();
			maxMountCount 		= in.getShort();
			magic 				= in.getShort();
			state 				= in.getShort();
			errors 				= in.getShort();
			minorRevLevel 		= in.getShort();
			lastCheck 			= in.getInt();
			checkInterval 		= in.getInt();
			creatorOS 			= in.getInt();
			revLevel 			= in.getInt();
			defResUid 			= in.getShort();
			defResGid 			= in.getShort();
			firstInode 			= in.getInt();
			inodeSize 			= in.getShort();
			blockGroupNumber 	= in.getShort();
			featureCompat 		= in.getInt();
			featureIncompat 	= in.getInt();
			featureROCompat 	= in.getInt();
			volumeUuid 			= new UUID(in.getLong(), in.getLong());
			volumeName 			= Static.getString(in, 16, StandardCharsets.UTF_8);
			lastMounted 		= Static.getString(in, 64, StandardCharsets.UTF_8);
			algoUsageBitmap 	= in.getInt();
			preallocBlocks 		= in.get();
			preallocDirBlocks 	= in.get();
			reservedGdtBlocks 	= in.getShort();
			journalUuid 		= new UUID(in.getLong(), in.getLong());
			journalInode 		= in.getInt();
			journalDevice 		= in.getInt();
			lastOrphan 			= in.getInt();
			hashSeed 			= Static.getBytes(in, 16);
			defHashVersion 		= in.get();
			journalBackupType 	= in.get();
			descSize 			= in.getShort();
			defaultMountOpts 	= in.getInt();
			firstMetaBG 		= in.getInt();
			mkfsTime 			= in.getInt();
			journalBlocks 		= Static.getBytes(in, 68);
			blocksCountHigh 	= in.getInt();
			resBlocksCountHigh 	= in.getInt();
			freeBlocksCountHigh = in.getInt();
			minExtraInodeSize 	= in.getShort();
			wantExtraInodeSize 	= in.getShort();
			flags 				= in.getInt();
			raidStride 			= in.getShort();
			mmpInterval 		= in.getShort();
			mmpBlock 			= in.getLong();
			raidStripeWidth 	= in.getInt();
			groupsPerFlex 		= 1 << in.get();
			checksumType 		= in.get();
			reservedPad 		= in.getShort();
			kbytesWritten 		= in.getLong();
			snapshotInode 		= in.getInt();
			snapshotId 			= in.getInt();
			snapshotReserved 	= in.getLong();
			snapshotList 		= in.getInt();
			errorCount 			= in.getInt();
			firstErrorTime 		= in.getInt();
			firstErrorInode 	= in.getInt();
			firstErrorBlock 	= in.getLong();
			firstErrorFunc 		= Static.getString(in, 32, StandardCharsets.US_ASCII);
			firstErrorLine 		= in.getInt();
			lastErrorTime 		= in.getInt();
			lastErrorInode 		= in.getInt();
			lastErrorLine 		= in.getInt();
			lastErrorBlock 		= in.getLong();
			lastErrorFunc 		= Static.getString(in, 32, StandardCharsets.US_ASCII);
			mountOpts 			= Static.getString(in, 64, StandardCharsets.US_ASCII);
			userQuotaInode 		= in.getInt();
			groupQuotaInode 	= in.getInt();
			overheadBlocks 		= in.getInt();
			backupBG 			= in.getLong();
			encryptAlgos 		= in.getInt();
			encryptPwSalt 		= Static.getBytes(in, 16);
			lostFoundInode 		= in.getInt();
			checksumSeed 		= in.getInt();
			in.position(in.position() + 396);
			checksum 			= in.getInt();
			
			if (descSize == 0)
				descSize = 32;
			
			if (magic == EXT_MAGIC && inodesCount > 0 && blocksCount > 0 && resBlocksCount >= 0
					&& blocksCountHigh == 0 && resBlocksCountHigh == 0 && freeBlocksCountHigh == 0
					&& freeInodesCount >= 0 && freeInodesCount < inodesCount
					&& freeBlocksCount >= 0 && freeBlocksCount < blocksCount
					&& blockSize <= 65536 && blockSize <= clusterSize
					&&(firstDataBlock == 0 || firstDataBlock == 1 && blockSize == 1024)
					&& clustersPerGroup > 0 && blocksPerGroup >= clustersPerGroup && inodesPerGroup > 0
					&& blocksPerGroup * blockSize == clustersPerGroup * clusterSize
					&& blocksPerGroup % clustersPerGroup == 0
					&& blockSize == ext.getLength() / blocksCount
					&& blockSize == clustersPerGroup / 8	// Each allocation bitmap occupies exactly one block 
					&& descSize >= 32) {
				
				int blocksPerCluster = blocksPerGroup / clustersPerGroup;
				clustersCount = (int)Static.ceilDiv(blocksCount, blocksPerCluster);
				clustersInLastGroup = clustersCount % clustersPerGroup;
				blocksInLastGroup = blocksCount % blocksPerGroup;
				
				if ((featureCompat & COMPAT_UNKNOWN) != 0 || (featureIncompat & INCOMPAT_UNKNOWN) != 0 || (featureROCompat & RO_COMPAT_UNKNOWN) != 0)
					throw new InitializationException("Incompatible features found");
				if ((featureCompat & COMPAT_HAS_JOURNAL) != 0 && !isJournalEmpty())
					throw new InitializationException("The journal is not empty");
				if ((featureIncompat & INCOMPAT_META_BG) != 0)
					throw new InitializationException("Meta block groups are not supported");
				
				bitmapBlockOrMaker = getBitmapHandler();
				
				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), fileSystem.toString());
	}
	
	/**
	 * Returns information to read or make the bitmap of each group, as an array of integers.
	 * 	If the group value is greater than zero, it is a block number to read the bitmap data. 
	 * 	Otherwise it is the negative number of allocated clusters and the clusters are 
	 * 	consecutive, starting at the first cluster in the group. 
	 * 
	 * @return the handler to read or make the bitmap data.
	 * @throws WrongHeaderException if an invalid group group descriptor is found. 
	 * @throws IOException if some I/O error occurs.
	 */
	private int[] getBitmapHandler() throws IOException, WrongHeaderException {
		int[] handler = new int[(int)Static.ceilDiv(blocksCount, blocksPerGroup)];
		
		ByteBuffer in = fileSystem.readImage(blockSize == 1024? 2048: blockSize, handler.length * descSize);
		for (int i = 0, j = 0; i < handler.length; i++, j += descSize) {
			if ((in.getShort(j + 18) & 2) == 0) {
			// The bitmap is initialized
				handler[i] = in.getInt(j);
				if (handler[i] <= 0 || handler[i] >= blocksCount)
					throw new WrongHeaderException(getClass(), fileSystem.toString());
			} else {
			// The bitmap is not initialized, allocated clusters are reserved for metadata
			//	The number of clusters in last group may be smaller than clustersPerGroup
				int nc = i == handler.length -1? clustersInLastGroup: clustersPerGroup;
				handler[i] = (in.getShort(j + 12) & 0xffff) - nc;
				if (handler[i] > 0)
					throw new WrongHeaderException(getClass(), fileSystem.toString());
			}
		}
		
		return handler;
	}
	
	/**
	 * Checks if the journal has transactions. Implementations accessing a journaled volume
	 *  with transactions must either refuse to access the volume, or replay the journal.
	 * 
	 * @return	true if there are no transactions.
	 * @throws IOException if some I/O error occurs.
	 */
	private boolean isJournalEmpty() throws IOException {
		// Just a slight check, no need for objects here
		
		if ((featureIncompat & INCOMPAT_JOURNAL_DEV) != 0)
			return false;
		if (journalBackupType != 1) // No journalBlocks
			return false;
		
		ByteBuffer bb = ByteBuffer.wrap(journalBlocks);
		bb.order(ExtFileSystem.BYTE_ORDER);
		// This is the extents header
		short ehMagic 	= bb.getShort();		// 0xF30A
		short ehEntries = bb.getShort();		// One entry must be present
		short ehMax 	= bb.getShort();		// There is room  for 4 entries
		short ehDepth 	= bb.getShort();		// Only leaf nodes are expected
		bb.position(bb.position() + 4);
		if (ehMagic != (short)0xF30A || ehMax !=4 || ehDepth != 0 
				|| ehEntries < 1 || ehEntries > ehMax)
			return false;
		// Now read the first extent
		int eeBlock 	= bb.getInt();			// First file block number that this extent covers
		int eeCount 	= bb.getShort();		// Number of blocks covered by this extent
		int eeStartHi	= bb.getShort();		// This is expected to be zero, in this 32 bits world
		int eeStartLo 	= bb.getInt();			// Disk block number to which this extent points
		if (eeBlock != 0 || eeCount < 1 && eeCount != -32768 
				|| eeStartHi != 0 || eeStartLo < 0)
			return false;
		
		ByteBuffer in = fileSystem.readImage(eeStartLo * (long)blockSize, 12+12+12);
		in.order(ByteOrder.BIG_ENDIAN);
		int magic = in.getInt();
		if (magic != 0xC03B3998) // The header is invalid
			return false;
		
		// The journal superblock's s_start field is zero if, and only if,
		//	the journal was cleanly unmounted.
		return in.getInt(12+12+4) == 0;
	}
}
