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

package io.github.eternalbits.darwin.disk.hfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

class HfsVolumeHeader {
	final static int HEADER_SIZE = 512;
	
	private static final short HFSPLUS_SIGNATURE = 0x482B;
	private static final short HFSPLUS_VERSION = 0x0004;
	private static final short HFSX_SIGNATURE = 0x4858;
	private static final short HFSX_VERSION = 0x0005;
	
	private static final int ATTRIBUTE_UNMOUNTED = 0x100;
	private static final int ATTRIBUTE_JOURNALED = 0x2000;

	final HfsFileSystem fileSystem;

	short 		signature;				// H+ or HX
	short 		version;				// 4 or 5
	int 		attributes;				// 
	int 		lastMountedVersion;		// 10.0
	int 		journalInfoBlock;		//
	int			createDate;				// May be used as unique identifier, do NOT change
	int			modifyDate;				// Number of seconds since midnight, January 1, 1904, GMT
	int			backupDate;				//				
	int			checkedDate;			//
	int			fileCount;				// Number of file records found in the catalog file
	int			folderCount;			// Number of folder records in catalog, minus one (root)
	int			blockSize;				// The allocation block size, in bytes
	int			totalBlocks;			// Whole disk, including this
	int			freeBlocks;				//
	int			nextAllocation;			//
	int			rsrcClumpSize;			//
	int			dataClumpSize;			//
	int			nextCatalogID;			// HfsCatalogNodeID
	int			writeCount;				//
	long		encodingsBitmap;		//
	int[]		finderInfo;				// Last two have a 64-bit unique volume identifier
	HfsForkData	allocationFile;			//
	HfsForkData	extentsFile;			//
	HfsForkData	catalogFile;			//
	HfsForkData	attributesFile;			//
	HfsForkData	startupFile;			//
	
	HfsVolumeHeader(HfsFileSystem hfs, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= hfs;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(HfsFileSystem.BYTE_ORDER);
			
			signature			= in.getShort();
			version				= in.getShort();
			attributes			= in.getInt();
			lastMountedVersion 	= in.getInt();
			journalInfoBlock 	= in.getInt();
			createDate 			= in.getInt();
			modifyDate 			= in.getInt();
			backupDate 			= in.getInt();				
			checkedDate 		= in.getInt();
			fileCount 			= in.getInt();
			folderCount 		= in.getInt();
			blockSize 			= in.getInt();
			totalBlocks 		= in.getInt();
			freeBlocks 			= in.getInt();
			nextAllocation 		= in.getInt();
			rsrcClumpSize 		= in.getInt();
			dataClumpSize 		= in.getInt();
			nextCatalogID 		= in.getInt();
			writeCount 			= in.getInt();
			encodingsBitmap 	= in.getLong();
			finderInfo 			= Static.getInts(in, 8);
			
			if (((signature == HFSPLUS_SIGNATURE && version == HFSPLUS_VERSION) 
				|| (signature == HFSX_SIGNATURE && version == HFSX_VERSION))
					&& fileCount >=0 && folderCount >= 0
					&& blockSize >= 512 && Static.isPower2(blockSize)
					&& totalBlocks == fileSystem.getLength() / blockSize
					&& freeBlocks >= 0 && nextAllocation > 0
					&& rsrcClumpSize > 0 && dataClumpSize > 0
					&& nextCatalogID > 0
					&& (attributes & ATTRIBUTE_UNMOUNTED) != 0
					&&((attributes & ATTRIBUTE_JOURNALED) == 0 || journalInfoBlock > 0)) {

				allocationFile 		= new HfsForkData(blockSize, "AllocationFile", HfsForkData.TYPE_DATA, in);
				extentsFile 		= new HfsForkData(blockSize, "ExtentsFile", HfsForkData.TYPE_DATA, in);
				catalogFile 		= new HfsForkData(blockSize, "CatalogFile", HfsForkData.TYPE_DATA, in);
				attributesFile 		= new HfsForkData(blockSize, "AttributesFile", HfsForkData.TYPE_DATA, in);
				startupFile 		= new HfsForkData(blockSize, "StartupFile", HfsForkData.TYPE_DATA, in);
				
				if ((attributes & ATTRIBUTE_JOURNALED) != 0 && !isJournalEmpty())
					throw new InitializationException("The journal is not empty");

				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), fileSystem.toString());
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
		
		ByteBuffer in = fileSystem.readImage(journalInfoBlock * (long)blockSize, 4+4*8+8);
		int flags = in.getInt();
		if ((flags & 4) != 0)	// If kJIJournalNeedInitMask is set the journal is empty
			return true;
		if ((flags & 2) != 0)	// If kJIJournalOnOtherDeviceMask is set the journal is away
			return false;
		if ((flags & 1) == 0)	// At this point kJIJournalInFSMask should be set
			return false;
		
		in = fileSystem.readImage(in.getLong(4+4*8), 4+4+8+8);
		in.order(ByteOrder.LITTLE_ENDIAN);
		int magic = in.getInt();
		if (magic == 0x784c4e4a) // The journal has big endian values
			in.order(ByteOrder.BIG_ENDIAN);
		int endian = in.getInt();
		if (endian != 0x12345678) // The journal is invalid
			return false;
		
		return in.getLong() == in.getLong();
	}
}

