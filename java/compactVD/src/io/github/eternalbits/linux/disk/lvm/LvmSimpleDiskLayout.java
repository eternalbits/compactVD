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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.NullFileSystem;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskFileSystems;

/**
 * The {@code LvmSimpleDiskLayout} class allows to reach file systems inside
 *  Logical Volumes that are completely mapped to a unique Physical Volume.
 *  Although not interesting from a LVM point of view, many Linux servers
 *  start with a configuration like that.
 */
public class LvmSimpleDiskLayout extends DiskLayout {
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	static final long SECTOR_LONG = 512;
	
	final int blockSize;
	final LvmPhysicalVolumeLabel label;
	final LvmMetadaAreaHeader 	 header;
	final LvmVolumeGroupMetadata group;
	
	/**
     * Initializes a {@code LvmSimpleDiskLayout} from a block device inside
     *  a whole disk image.
	 * 
	 * @param img	The disk image.
	 * @throws WrongHeaderException if the block device is not a LVM physical volume.
	 * @throws IOException if some I/O error occurs.
     */
	public LvmSimpleDiskLayout(DiskImage img) throws IOException, WrongHeaderException {
		this(img, 0, img.getDiskSize());
	}

	/**
     * Initializes a {@code LvmSimpleDiskLayout} from a block device inside
     *  a disk image partition.
	 * 
	 * @param img		The disk image.
	 * @param offset	The partition offset, in bytes.
	 * @param length	The partition length, in bytes.
	 * @throws WrongHeaderException if the block device is not a LVM physical volume.
	 * @throws IOException if some I/O error occurs.
	 */
	public LvmSimpleDiskLayout(DiskImage img, long offset, long length) throws IOException, WrongHeaderException {
		this.image 		= img;
		blockSize 		= img.getLogicalBlockSize();
		
		long position 	= offset;
		byte[] buffer = new byte[4 * LvmPhysicalVolumeLabel.LABEL_SIZE];
		int read = image.readAll(position, buffer, 0, buffer.length);
		label = new LvmPhysicalVolumeLabel(this, ByteBuffer.wrap(buffer, 0, read));
		
		position += label.metaStart;
		buffer = new byte[LvmMetadaAreaHeader.HEADER_SIZE];
		read = image.readAll(position, buffer, 0, buffer.length);
		header = new LvmMetadaAreaHeader(this, ByteBuffer.wrap(buffer, 0, read));
		
		position += header.metadataOffset;
		buffer = new byte[(int) header.metadataSize];
		read = image.readAll(position, buffer, 0, buffer.length);
		group = new LvmVolumeGroupMetadata(this, ByteBuffer.wrap(buffer, 0, read));

		for (LvmLogicalVolume lv: group.volumes) {						// For each stripe
			for (LvmLogicalSegment ls: lv.segments) {					// ...
				for (LvmPhysicalStripe ps: ls.stripes) {				// ...
					LvmPhysicalVolume pv = group.pvMap.get(ps.pvName);	// Get physical volume
					if (pv != null && pv.id.equals(label.uuid)) {		// If PV is this device
						
						long lvOffset = SECTOR_LONG * (pv.peStart + ps.pvStart * group.extentSize);
						long lvLength = SECTOR_LONG * ls.extentCount / ls.stripes.size() * group.extentSize;
						if (lvOffset < label.dataOffset || lvOffset + lvLength > offset + length)
							throw new InitializationException(getClass(), this.toString());
						
						if (lv.segments.size() == 1 && ls.stripes.size() == 1 && "striped".equals(ls.type))
							getFileSystems().add(DiskFileSystems.map(this, offset + lvOffset, lvLength, lv.name));
						else getFileSystems().add(new NullFileSystem(this, offset + lvOffset, lvLength, null, "Complex LVM"));
						
					}
				}
			}
		}
		
	}
	
	@Override
	public String getType() {
		return "LVM";
	}

}
