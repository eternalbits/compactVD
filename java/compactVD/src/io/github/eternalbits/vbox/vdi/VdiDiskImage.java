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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import io.github.eternalbits.compacttu.DebugAccessFile;
import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageJournal;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskLayouts;

/**
 * Implements a {@link DiskImage} of type Oracle  
 *  <a href="https://en.wikipedia.org/wiki/VDI_(file_format)">
 *  VirtualBox Disk Image</a> (VDI).
 * <p>
 * VDI is the VirtualBox-specific drive format used by Oracle VM
 *  VirtualBox, an open-source desktop virtualization program. With 
 *  VirtualBox, VDI disk images can be mounted as a hard disk on Mac, 
 *  Windows, and Unix platforms.
 * <p>
 */
public class VdiDiskImage extends DiskImage {
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	
	final VdiHeaderDescriptor header;
	final VdiImageBlockTable blockTable;

	public VdiDiskImage(File file, long diskSize) throws IOException {
		media = new DebugAccessFile(file, "rw");
		try { // Always close media on Exception
			path = file.getPath();
			readOnly = false;
			
			header = new VdiHeaderDescriptor(this, diskSize);
			blockTable = new VdiImageBlockTable(this);
			imageTable = blockTable;
			
			touched = true;
			dirty = true;
			header.update();
			fillTo(header.offsetBlocks);
			blockTable.update();
			fillTo(header.offsetData);
			dirty = false;
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	public VdiDiskImage(File file, String mode) throws IOException, WrongHeaderException {
		media = new DebugAccessFile(file, mode);
		try { // Always close media on Exception
			readOnly = mode.equals("r");
			path = file.getPath();
			
			header = new VdiHeaderDescriptor(this, readMetadata(0, VdiHeaderDescriptor.HEADER_SIZE));
			blockTable = new VdiImageBlockTable(this, readMetadata(header.offsetBlocks, header.blocksCount * 4));
			imageTable = blockTable;
			
			// Allows media length to be greater than needed by the number of allocated blocks, but not smaller
			if (media.length() < header.offsetData + header.blocksAllocated * (long)header.blockSize)
				throw new InitializationException(getClass(), toString());
			
			setLayout(DiskLayouts.open(this));
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	DebugAccessFile getMedia() {
		return media;
	}

	@Override
	protected synchronized void update() throws IOException {
		blockTable.update();
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
		journal.addDataChunk(blockTable.getUpdateOffset(), blockTable.getUpdateBuffer());
		journal.addDataChunk(header.getUpdateOffset(), header.getUpdateBuffer());
		journal.write(Static.getWorkingDirectory());
		
		update();
		media.getFD().sync();
		journal.delete();
	}
	
	@Override
	public String getType() {
		return "VDI";
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
			if (blockTable.exists(blockNumber)) return true;
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
			int get = blockTable.read(blockNumber, blockOffset, in, start + read, max);
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
			if (blockTable.exists(blockNumber)) {
				blockTable.update(blockNumber, blockOffset, out, start, max);
				touched = true;
			}
			else if (!isZero(out, start, max)) {
				blockTable.create(blockNumber, blockOffset, out, start, max);
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
		return header.pSectorSize;
	}

	@Override
	public int getImageBlockSize() {
		return header.blockSize;
	}

	@Override
	public int getImageBlocksCount() {
		return header.blocksCount;
	}

	@Override
	public int getImageBlocksInFile() {
		return header.blocksAllocated;
	}

	@Override
	public int getImageBlocksMapped() {
		return blockTable.getDataBlocksCount();
	}

	@Override
	public long getOptimizedLength() {
		return header.offsetData + (long)blockTable.getDataBlocksCount() 
			* VdiHeaderDescriptor.VDI_IMAGE_BLOCK_SIZE;
	}

	@Override
	public synchronized void compact() throws IOException {
		if (readOnly)
			throw new IOException(IMAGE_IS_READ_ONLY);
		
		/* Delay metadata update until a block swap is about to happen.
		 */
		boolean needsInitialUpdate = dirty;
		boolean needsFinalUpdate = false;
		
		int[] reverseMap = new int[header.blocksAllocated];
		Arrays.fill(reverseMap, -1);
		
		for (int i = 0, s = header.blocksCount; i < s; i++) {
			if (blockTable.get(i) != -1) {
				reverseMap[blockTable.get(i)] = i;
			}
		}
		
		Progress progress = new Progress(DiskImageProgress.COMPACT, 
				DiskImage.countCompactMoves(reverseMap));
		Thread thisThread = Thread.currentThread();

		byte[] buffer = new byte[header.blockSize];
		long length = header.blockSize;
		int s = reverseMap.length;

		for (int i = 0; i < s && !thisThread.isInterrupted(); i++) {
			if (reverseMap[i] == -1) { // Found a "hole" in the image
				for (s = s - 1; s > i; s--) {
					if (reverseMap[s] != -1) { // This is the last mapped block
						media.seek(header.offsetData + s * length);
						media.readFully(buffer);
						if (needsInitialUpdate) {
						//	put journal id in the block that will be overwritten
							journaledUpdate(header.offsetData + i * length);
							needsInitialUpdate = false;
						}
						media.seek(header.offsetData + i * length);
						media.write(buffer);
						progress.step(1);
						touched = true;
						reverseMap[i] = reverseMap[s];
						reverseMap[s] = -1;
						blockTable.map(reverseMap[i], i);
						dirty = needsFinalUpdate = true;
						break;
					}
				}
			}
		}
		
		if (needsFinalUpdate || header.blocksAllocated > s 
				|| media.length() > header.offsetData + s * length) {
			
			// s is unreliable if the task was interrupted
			if (header.blocksAllocated > s)
				header.blocksAllocated = s;
			
			journaledUpdate(header.offsetData + header.blocksAllocated * length);
			media.setLength(header.offsetData + header.blocksAllocated * length);
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
		
		blockTable.reset();
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
