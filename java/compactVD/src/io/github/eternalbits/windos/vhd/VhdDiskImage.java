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

package io.github.eternalbits.windos.vhd;

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
 * Implements a {@link DiskImage} of type Microsoft 
 *  <a href="https://en.wikipedia.org/wiki/VHD_(file_format)">Virtual Hard Disk</a> (VHD).
 * <p>
 * VHD is a file format which represents a virtual hard disk drive. It may contain
 *  disk partitions and file systems, which in turn can contain files and folders.
 * The format was created by Connectix for their Virtual PC product. Microsoft has acquired
 *  Connectix and has made the VHD Image Format Specification available to third parties
 *  under the Microsoft Open Specification Promise.
 *  <p>
 */
public class VhdDiskImage extends DiskImage {
	static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
	static final int SECTOR_SIZE = 512;

	final VhdDiskFooter footer;
	final VhdDiskHeader header;
	final VhdBlockAllocationTable blockTable;
	
	public VhdDiskImage(File file, long diskSize) throws IOException {
		media = new RandomAccessFile(file, "rw");
		try { // Always close media on Exception
			path = file.getPath();
			readOnly = false;
			
			footer = new VhdDiskFooter(this, diskSize);
			header = new VhdDiskHeader(this, diskSize);
			blockTable = new VhdBlockAllocationTable(this);
			imageTable = blockTable;
			
			touched = true;
			dirty = true;
			footer.update(true);
			fillTo(footer.dataOffset);
			header.update();
			fillTo(header.tableOffset);
			blockTable.update();
			fillTo(header.getFooterOffset());
			footer.update(false);
			dirty = false;
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	public VhdDiskImage(File file, String mode) throws IOException, WrongHeaderException {
		media = new RandomAccessFile(file, mode);
		try { // Always close media on Exception
			readOnly = mode.equals("r");
			path = file.getPath();
			
			long footerOffset = (media.length() - 1) / SECTOR_SIZE * SECTOR_SIZE;
			footer = new VhdDiskFooter(this, readMetadata(footerOffset, VhdDiskFooter.FOOTER_SIZE));
			header = new VhdDiskHeader(this, readMetadata(footer.dataOffset, VhdDiskHeader.HEADER_SIZE), footerOffset);
			blockTable = new VhdBlockAllocationTable(this, readMetadata(header.tableOffset, header.maxTableEntries * 4));
			imageTable = blockTable;
			
			setLayout(DiskLayouts.open(this));
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	RandomAccessFile getMedia() {
		return media;
	}

	/**
	 * Returns the VHD checksum of {@code length} bytes before the current
	 *  position of byte buffer {@code bb}, ignoring 4 bytes at {@code offset}.
	 * 
	 * @param bb		Byte buffer containing the structure to check.
	 * @param length	Length of the structure, before current position.
	 * @param offset	Absolute position in {@code bb} of 4 bytes to ignore.
	 * @return			The VHD checksum.
	 */
	static int getChecksum(ByteBuffer bb, int length, int offset) {
		int checksum = 0, a = offset, b = offset + 4;
		for (int s = bb.position(), p = s - length; p < s; p++) {
			if (p < a || p >= b) checksum += bb.array()[p] & 0xFF;
		}
		return ~checksum;
	}

	@Override
	protected synchronized void update() throws IOException {
		blockTable.update();
		footer.update(false);
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
		journal.addDataChunk(footer.getUpdateOffset(false), footer.getUpdateBuffer());
		journal.write(Static.getWorkingDirectory());
		
		update();
		media.getFD().sync();
		journal.delete();
	}
	
	/**
	 * Returns the index of the block that starts at {@code sector}.
	 * @param sector	The sector number in the image.
	 * @return			The zero based block number.
	 */
	int indexOf(int sector) {
		return (sector - header.firstSector) / header.blockSectors;
	}
	
	/**
	 * Returns the first sector of the block number {@code index}.
	 * @param index		The zero based block number.
	 * @return			The first sector of the block.
	 */
	int sectorOf(int index) {
		return header.firstSector + index * header.blockSectors;
	}

	@Override
	public String getType() {
		return "VHD";
	}

	@Override
	public boolean hasData(long offset, int length) {
		if (length <= 0 || offset >= footer.currentSize)
			return false;
		
		if (offset + length > footer.currentSize)
			length = (int)(footer.currentSize - offset);
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
		if (offset >= footer.currentSize)
			return -1;
		if (offset + length > footer.currentSize)
			length = (int)(footer.currentSize - offset);
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
		return footer.currentSize;
	}

	@Override
	public int getLogicalBlockSize() {
		return SECTOR_SIZE;
	}

	@Override
	public int getImageBlockSize() {
		return header.blockSize;
	}

	@Override
	public int getImageBlocksCount() {
		return header.maxTableEntries;
	}

	@Override
	public int getImageBlocksInFile() {
		return indexOf(header.nextSector);
	}

	@Override
	public int getImageBlocksMapped() {
		return blockTable.getDataBlocksCount();
	}

	@Override
	public long getOptimizedLength() {
		return (header.firstSector + (long)blockTable.getDataBlocksCount() 
			* header.blockSectors) * SECTOR_SIZE + VhdDiskFooter.FOOTER_SIZE;
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
		
		for (int i = 0, s = header.maxTableEntries; i < s; i++) {
			if (blockTable.get(i) != 0) {
				reverseMap[indexOf(blockTable.get(i))] = i;
			}
		}
		
		Progress progress = new Progress(DiskImageProgress.COMPACT, 
				DiskImage.countCompactMoves(reverseMap));
		Thread thisThread = Thread.currentThread();
		
		byte[] buffer = new byte[header.blockSectors * SECTOR_SIZE];
		int s = reverseMap.length;

		for (int i = 0; i < s && !thisThread.isInterrupted(); i++) {
			if (reverseMap[i] == -1) { // Found a "hole" in the image
				for (s = s - 1; s > i; s--) {
					if (reverseMap[s] != -1) { // This is the last mapped block
						media.seek(sectorOf(s) * (long)SECTOR_SIZE);
						media.readFully(buffer);
						if (needsInitialUpdate) {
						//	put journal id in the block that will be overwritten
							journaledUpdate(sectorOf(i) * (long)SECTOR_SIZE);
							needsInitialUpdate = false;
						}
						media.seek(sectorOf(i) * (long)SECTOR_SIZE);
						media.write(buffer);
						progress.step(1);
						touched = true;
						reverseMap[i] = reverseMap[s];
						reverseMap[s] = -1;
						blockTable.map(reverseMap[i], sectorOf(i));
						dirty = needsFinalUpdate = true;
						break;
					}
				}
			}
		}
		
		if (needsFinalUpdate || header.nextSector > sectorOf(s) 
				|| media.length() > sectorOf(s) * (long)SECTOR_SIZE + VhdDiskFooter.FOOTER_SIZE) {
			
			// s is unreliable if the task was interrupted
			if (header.nextSector > sectorOf(s))
				header.nextSector = sectorOf(s);
			
			journaledUpdate(header.nextSector * (long)SECTOR_SIZE + VhdDiskFooter.FOOTER_SIZE);
			media.setLength(header.nextSector * (long)SECTOR_SIZE + VhdDiskFooter.FOOTER_SIZE);
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
		
		media.setLength(media.getFilePointer() + VhdDiskFooter.FOOTER_SIZE);
		update(); // after setLength, please
		
		progress.end();
	}

}
