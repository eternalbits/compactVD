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

package io.github.eternalbits.vmware.vmdk;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageJournal;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskLayouts;

/**
 * Implements a {@link DiskImage} of type VMware 
 *  <a href="https://en.wikipedia.org/wiki/VMDK">Virtual Machine Disk</a> (VMDK).
 * <p>
 * VMDK is a file format that describes containers for virtual hard disk drives
 *  to be used in virtual machines like VMware Workstation or VirtualBox.
 * Initially developed by VMware for its virtual appliance products, VMDK
 *  is now an open format and is one of the disk formats used in the Open 
 *  Virtualization Format for virtual appliances.
 *  <p>
 */
public class VmdkDiskImage extends DiskImage {
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	final VmdkSparseHeader header;
	final VmdkEmbeddedDescriptor descriptor;
	final VmdkGrainDirectory directory;
	final VmdkGrainTable grainTable;
	
	public VmdkDiskImage(File file, long diskSize) throws IOException {
		media = new RandomAccessFile(file, "rw");
		try { // Always close media on Exception
			path = file.getPath();
			readOnly = false;
			
			header = new VmdkSparseHeader(this, diskSize);
			descriptor = new VmdkEmbeddedDescriptor(this, new File(path).getName());
			directory = new VmdkGrainDirectory(this);
			grainTable = new VmdkGrainTable(this);
			imageTable = grainTable;
			
			touched = true;
			dirty = true;
			header.update();
			fillTo(header.descriptorOffset);
			descriptor.update();
			fillTo(header.rgdOffset);
			directory.update(true);
			fillTo(header.rgtOffset);
			grainTable.update(true);
			fillTo(header.gdOffset);
			directory.update(false);
			fillTo(header.gtOffset);
			grainTable.update(false);
			fillTo(header.overHead);
			dirty = false;
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	public VmdkDiskImage(File file, String mode) throws IOException, WrongHeaderException {
		media = new RandomAccessFile(file, mode);
		try { // Always close media on Exception
			readOnly = mode.equals("r");
			path = file.getPath();
			
			header = new VmdkSparseHeader(this, readMetadata(0, VmdkSparseHeader.HEADER_SIZE));
			int descSize = (int)(header.descriptorSize * VmdkSparseHeader.SECTOR_LONG);
			descriptor = new VmdkEmbeddedDescriptor(this, readMetadata(header.descriptorOffset, descSize));
			directory = new VmdkGrainDirectory(this, readMetadata(header.gdOffset, header.gdeCount * 4));
			grainTable = new VmdkGrainTable(this, readMetadata(header.gtOffset, header.gteCount * 4));
			imageTable = grainTable;
			
			setLayout(DiskLayouts.open(this));
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}

	@Override
	protected ByteBuffer readMetadata(long offset, int length) throws IOException {
		return super.readMetadata(offset * VmdkSparseHeader.SECTOR_LONG, length);
	}
	
	@Override
	protected void fillTo(long offset) throws IOException {
		super.fillTo(offset * VmdkSparseHeader.SECTOR_LONG);
	}
	
	RandomAccessFile getMedia() {
		return media;
	}

	@Override
	protected synchronized void update() throws IOException {
		grainTable.update(false);
		grainTable.update(true);
		header.update();
		touched = true;
		dirty = false;
	}

	/**
	 * To recover from hardware errors, metadata updates are journaled:<ul>
	 * <li>The image is updated with the ID of a future journal entry.</li> 
	 * <li>The journal entry is written to disk with a copy of the metadata.</li> 
	 * <li>The disk image metadata is updated.</li> 
	 * <li>The journal entry is deleted.</li> 
	 * </ul>
	 * @see	{@link DiskImageJournal#recover(File jrn)}
	 * @param offset where the ID of the journal entry can be found.
	 * @throws IOException if some I/O error occurs.
	 */
	private void journaledUpdate(long offset) throws IOException {
		byte[] id = String.format(JOURNAL_IDENTIFIER, UUID.randomUUID()
				.toString()).getBytes(StandardCharsets.UTF_8);
		media.seek(offset);
		media.write(id);
		media.getFD().sync();
		
		DiskImageJournal journal = new DiskImageJournal(this, offset, id);
		journal.addDataChunk(grainTable.getUpdateOffset(false), grainTable.getUpdateBuffer());
		journal.addDataChunk(grainTable.getUpdateOffset(true), grainTable.getUpdateBuffer());
		journal.addDataChunk(header.getUpdateOffset(), header.getUpdateBuffer());
		journal.write(Static.getWorkingDirectory());
		
		update();
		media.getFD().sync();
		journal.delete();
	}
	
	/**
	 * Returns the index of the grain that starts at {@code sector}.
	 * @param sector	The sector number in the image.
	 * @return			The zero based grain number.
	 */
	int indexOf(int sector) {
		return (sector - header.firstSector) / header.grainSectors;
	}
	
	/**
	 * Returns the first sector of the grain number {@code index}.
	 * @param index		The zero based grain number.
	 * @return			The first sector of the grain.
	 */
	int sectorOf(int index) {
		return header.firstSector + index * header.grainSectors;
	}

	@Override
	public String getType() {
		return "VMDK";
	}

	@Override
	public boolean hasData(long offset, int length) {
		if (length <= 0 || offset >= header.diskSize)
			return false;
		
		if (offset + length > header.diskSize)
			length = (int)(header.diskSize - offset);
		int blockNumber = (int)(offset / header.blockSize);
		int blockOffset = (int)(offset % header.blockSize);
		int read = 0;
		
		while (read < length) {
			if (grainTable.exists(blockNumber)) return true;
			int max = Math.min(length - read, header.blockSize - blockOffset);
			blockOffset = 0;
			blockNumber++;
			read += max;
		}
		
		return false;
	}

	@Override
	protected int read(long offset, byte[] in, int start, int length) throws IOException {
		if (length == 0)
			return 0;
		if (offset >= header.diskSize)
			return -1;
		if (offset + length > header.diskSize)
			length = (int)(header.diskSize - offset);
		int blockNumber = (int)(offset / header.blockSize);
		int blockOffset = (int)(offset % header.blockSize);
		int read = 0;
		
		while (read < length) {
			int max = Math.min(length - read, header.blockSize - blockOffset);
			int get = grainTable.read(blockNumber, blockOffset, in, start + read, max);
			if (get < 0) {
				if (read == 0)
					return -1;
				break;
			}
			read += get;
			if (get < max)
				break;
			blockOffset = 0;
			blockNumber++;
		}
		
		return read;
	}

	@Override
	public synchronized void write(byte[] out, int start, int length) throws IOException {
		int blockNumber = (int)(diskPointer / header.blockSize);
		int blockOffset = (int)(diskPointer % header.blockSize);
		int want = length;
		
		while (want > 0) {
			int max = Math.min(want, header.blockSize - blockOffset);
			if (grainTable.exists(blockNumber)) {
				grainTable.update(blockNumber, blockOffset, out, start, max);
				touched = true;
			}
			else {
				grainTable.create(blockNumber, blockOffset, out, start, max);
				touched = true;
				dirty = true;
			}
			blockOffset = 0;
			blockNumber++;
			start += max;
			want -= max;
		}
		
		diskPointer += length;
	}

	@Override
	public long getDiskSize() {
		return header.diskSize;
	}
	
	@Override
	public int getLogicalBlockSize() {
		return VmdkSparseHeader.SECTOR_SIZE;
	}

	@Override
	public int getImageBlockSize() {
		return header.blockSize;
	}

	@Override
	public int getImageBlocksCount() {
		return header.gteCount;
	}

	@Override
	public int getImageBlocksInFile() {
		return indexOf(header.nextSector);
	}

	@Override
	public int getImageBlocksMapped() {
		return grainTable.getDataGrainsCount();
	}

	@Override
	public long getOptimizedLength() {
		return (header.overHead + grainTable.getDataGrainsCount() 
			* header.grainSize) * VmdkSparseHeader.SECTOR_LONG;
	}

	@Override
	public synchronized void compact() throws IOException {
		if (readOnly)
			throw new IOException(IMAGE_IS_READ_ONLY);

		/* Delay metadata update until a grain swap is about to happen.
		 */
		boolean needsInitialUpdate = dirty;
		boolean needsFinalUpdate = false;
		
		int[] reverseMap = new int[indexOf(header.nextSector)];
		Arrays.fill(reverseMap, -1);
		
		for (int i = 0, s = header.gteCount; i < s; i++) {
			if (grainTable.get(i) != 0) {
				reverseMap[indexOf(grainTable.get(i))] = i;
			}
		}
		
		Progress progress = new Progress(DiskImageProgress.COMPACT, 
				DiskImage.countCompactMoves(reverseMap));
		Thread thisThread = Thread.currentThread();
		
		byte[] buffer = new byte[header.blockSize];
		int s = reverseMap.length;

		for (int i = 0; i < s && !thisThread.isInterrupted(); i++) {
			if (reverseMap[i] == -1) { // Found a "hole" in the image
				for (s = s - 1; s > i; s--) {
					if (reverseMap[s] != -1) { // This is the last mapped block
						media.seek(sectorOf(s) * VmdkSparseHeader.SECTOR_LONG);
						media.readFully(buffer);
						if (needsInitialUpdate) {
						//	put journal id in the block that will be overwritten
							journaledUpdate(sectorOf(i) * VmdkSparseHeader.SECTOR_LONG);
							needsInitialUpdate = false;
						}
						media.seek(sectorOf(i) * VmdkSparseHeader.SECTOR_LONG);
						media.write(buffer);
						progress.step(1);
						touched = true;
						reverseMap[i] = reverseMap[s];
						reverseMap[s] = -1;
						grainTable.map(reverseMap[i], sectorOf(i));
						dirty = needsFinalUpdate = true;
						break;
					}
				}
			}
		}
		
		if (needsFinalUpdate || header.nextSector > sectorOf(s) 
				|| media.length() > sectorOf(s) * VmdkSparseHeader.SECTOR_LONG) {
			
			// s is unreliable if the task was interrupted
			if (header.nextSector > sectorOf(s))
				header.nextSector = sectorOf(s);
			
			journaledUpdate(header.nextSector * VmdkSparseHeader.SECTOR_LONG);
			media.setLength(header.nextSector * VmdkSparseHeader.SECTOR_LONG);
		}
		
		progress.end();
	}
	
	@Override
	public synchronized void copy(DiskImage source) throws IOException {
		if (getDiskSize() != source.getDiskSize())
			throw new IOException(MUST_HAVE_SAME_SIZE);
		if (readOnly)
			throw new IOException(IMAGE_IS_READ_ONLY);
		
		Progress progress = new Progress(DiskImageProgress.COPY, countDataReads(source));
		Thread thisThread = Thread.currentThread();
		
		grainTable.reset();
		dirty = true;
		
		synchronized(source) {
			
			diskPointer = 0L;
			byte[] buffer = new byte[getImageBlockSize()];
			for (int i = 0, s = getImageBlocksCount(); i < s && !thisThread.isInterrupted(); i++) {
				if (!source.hasData(diskPointer, buffer.length)) {
					diskPointer += buffer.length;
				} else {
					int read = source.readAll(diskPointer, buffer, 0, buffer.length);
					if (read < buffer.length) {
						if (read < 0 || diskPointer + read < getDiskSize())
							throw new EOFException(source.toString());
						Arrays.fill(buffer, read, buffer.length, (byte)0);
					}
					write(buffer, 0, buffer.length);
					progress.step(1);
					touched = true;
				}
			}
		}
		
		media.setLength(media.getFilePointer());
		update(); // after setLength, please
		
		progress.end();
	}

}
