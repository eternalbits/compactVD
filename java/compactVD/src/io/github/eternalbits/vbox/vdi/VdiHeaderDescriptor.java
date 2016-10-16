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

package io.github.eternalbits.vbox.vdi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

class VdiHeaderDescriptor {
	static final int VDI_IMAGE_BLOCK_SIZE = 0x100000;
	static final int HEADER_SIZE = 512;
	
	private static final int HEADER_SIGNATURE = 0xBEDA107F;		// VDI signature
	private static final int CURRENT_VERSION = 0x10001;			// Version 1.1
	private static final int VDI_IMAGE_TYPE_STANDARD = 1;		// Normal dynamically growing base image file
	private static final int VDI_GEOMETRY_SECTOR_SIZE = 512;	// Currently only 512 bytes sectors are supported
	
	private final VdiDiskImage image;							// Parent object

	/* VDI Header Descriptor version 1.1
	 * https://www.virtualbox.org/browser/vbox/trunk/src/VBox/Storage/VDICore.h
	 */
	byte[]	fileInfo;				// Image info, not handled anyhow [64 bytes]
	int		signature;				// Image signature
	int		version;				// The image version
	int		headerSize;				// Starting here = 400
	int		imageType;				// The image type
	int		imageFlags;				// Image flags, unknown values
	byte[]	imageComment;			// Image comment (UTF-8) [256 bytes]
	int		offsetBlocks;			// Offset of Blocks array from the beginning of image file
	int		offsetData;				// Offset of image data from the beginning of image file
	int		pchsCylinders;			// Physical CHS geometry, set to zeros (auto detect)
	int		pchsHeads;				//  ""
	int		pchsSectors;			//  ""
	int		pSectorSize;			// Currently only 512 bytes sectors are supported
	int		unused1;
	long	diskSize;				// Size of disk (in bytes)
	int		blockSize;				// Size of each block
	int		blockExtraSize;			// Size of additional service information of every data block, 0 expected
	int		blocksCount;			// Number of blocks
	int		blocksAllocated;		// Number of allocated blocks
	UUID	uuidCreate;				// UUID of image
	UUID	uuidModify;				// UUID of image's last modification
	UUID	uuidLinkage;			// UUID of previous image, only for secondary images
	UUID	uuidParentModify;		// UUID of previous image's last modification
	int		lchsCylinders;			// Logical CHS geometry
	int		lchsHeads;				// Do not modify the number of heads and sectors
	int		lchsSectors;			// Windows guests hate it
	int		lSectorSize;			// Currently 512
	byte[]	unused2;
	
	VdiHeaderDescriptor(VdiDiskImage vdi, long diskSize) {
		if (diskSize < 0 || diskSize % VDI_GEOMETRY_SECTOR_SIZE != 0)
			throw new IllegalArgumentException(String.format("Disk size: %d must be multiple of %d", diskSize, VDI_GEOMETRY_SECTOR_SIZE));
		
		this.image 			= vdi;

		fileInfo			= Static.getBytes("<<< Oracle VM VirtualBox Disk Image >>>\n", 64, StandardCharsets.UTF_8);
		signature			= HEADER_SIGNATURE;
		version				= CURRENT_VERSION;
		headerSize			= 400;
		imageType			= VDI_IMAGE_TYPE_STANDARD;
		imageFlags			= 0;
		imageComment		= new byte[256];
		offsetBlocks		= VDI_IMAGE_BLOCK_SIZE * 1;
		offsetData			= VDI_IMAGE_BLOCK_SIZE * 2;
		pchsCylinders		= 0;
		pchsHeads			= 0;
		pchsSectors			= 0;
		pSectorSize			= VDI_GEOMETRY_SECTOR_SIZE;
		unused1				= 0;
		this.diskSize		= diskSize;
		blockSize			= VDI_IMAGE_BLOCK_SIZE;
		blockExtraSize		= 0;
		blocksCount			= (int)Static.ceilDiv(diskSize, VDI_IMAGE_BLOCK_SIZE);
		blocksAllocated		= 0;
		uuidCreate			= UUID.randomUUID();
		uuidModify			= UUID.randomUUID();
		uuidLinkage			= new UUID(0, 0);
		uuidParentModify	= new UUID(0, 0);
		lchsCylinders		= 0;
		lchsHeads			= 0;
		lchsSectors			= 0;
		lSectorSize			= VDI_GEOMETRY_SECTOR_SIZE;
		unused2				= new byte[40];
	}

	VdiHeaderDescriptor(VdiDiskImage vdi, ByteBuffer in) throws IOException, WrongHeaderException {
		this.image = vdi;
		
		if (in.remaining() >= HEADER_SIZE) {
			in.order(VdiDiskImage.BYTE_ORDER);
			
			fileInfo			= Static.getBytes(in, 64);
			signature			= in.getInt();
			version				= in.getInt();
			headerSize			= in.getInt();
			imageType			= in.getInt();
			imageFlags			= in.getInt();
			imageComment		= Static.getBytes(in, 256);
			offsetBlocks		= in.getInt();
			offsetData			= in.getInt();
			pchsCylinders		= in.getInt();
			pchsHeads			= in.getInt();
			pchsSectors			= in.getInt();
			pSectorSize			= in.getInt();
			unused1				= in.getInt();
			diskSize			= in.getLong();
			blockSize			= in.getInt();
			blockExtraSize		= in.getInt();
			blocksCount			= in.getInt();
			blocksAllocated		= in.getInt();
			uuidCreate			= new UUID(in.getLong(), in.getLong());
			uuidModify			= new UUID(in.getLong(), in.getLong());
			uuidLinkage			= new UUID(in.getLong(), in.getLong());
			uuidParentModify	= new UUID(in.getLong(), in.getLong());
			lchsCylinders		= in.getInt();
			lchsHeads			= in.getInt();
			lchsSectors			= in.getInt();
			lSectorSize			= in.getInt();
			unused2				= Static.getBytes(in, 40);
			
			if (signature == HEADER_SIGNATURE 
					&& headerSize > 0 && headerSize <= 400
					&& pSectorSize == VDI_GEOMETRY_SECTOR_SIZE
					&& imageFlags == 0
					&& blocksCount >= 0 // may have more blocksAllocated than blocksCount
					&& offsetBlocks >= HEADER_SIZE
					&& offsetData >= offsetBlocks + 4 * blocksCount
					&& blockSize >= 16384 && Static.isPower2(blockSize) 
					&& blockExtraSize == 0
					&& Static.ceilDiv(diskSize, blockSize) == blocksCount) {
				
				if (imageType != VDI_IMAGE_TYPE_STANDARD)
					throw new InitializationException(String.format("%s: Not a dynamic base image file.", vdi.toString()));
				
				return;
			}
		}
		
		throw new WrongHeaderException(getClass(), image.toString());
	}
	
	long getUpdateOffset() {
		return 0;
	}
	
	byte[] getUpdateBuffer() {
		
		byte[] buffer = new byte[HEADER_SIZE];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(VdiDiskImage.BYTE_ORDER);
		
		bb.put(fileInfo);
		bb.putInt(signature);
		bb.putInt(version);
		bb.putInt(headerSize);
		bb.putInt(imageType);
		bb.putInt(imageFlags);
		bb.put(imageComment);
		bb.putInt(offsetBlocks);
		bb.putInt(offsetData);
		bb.putInt(pchsCylinders);
		bb.putInt(pchsHeads);
		bb.putInt(pchsSectors);
		bb.putInt(pSectorSize);
		bb.putInt(unused1);
		bb.putLong(diskSize);
		bb.putInt(blockSize);
		bb.putInt(blockExtraSize);
		bb.putInt(blocksCount);
		bb.putInt(blocksAllocated);
		bb.putLong(uuidCreate.getMostSignificantBits());
		bb.putLong(uuidCreate.getLeastSignificantBits());
		bb.putLong(uuidModify.getMostSignificantBits());
		bb.putLong(uuidModify.getLeastSignificantBits());
		bb.putLong(uuidLinkage.getMostSignificantBits());
		bb.putLong(uuidLinkage.getLeastSignificantBits());
		bb.putLong(uuidParentModify.getMostSignificantBits());
		bb.putLong(uuidParentModify.getLeastSignificantBits());
		bb.putInt(lchsCylinders);
		bb.putInt(lchsHeads);
		bb.putInt(lchsSectors);
		bb.putInt(lSectorSize);
		bb.put(unused2);
		
		return buffer;
	}
	
	void update() throws IOException {
		image.getMedia().seek(getUpdateOffset());
		image.getMedia().write(getUpdateBuffer());
	}
	
}
