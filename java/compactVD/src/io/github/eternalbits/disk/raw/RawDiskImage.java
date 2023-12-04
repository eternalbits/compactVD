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

package io.github.eternalbits.disk.raw;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disks.DiskLayouts;

/**
 * Implements a {@link DiskImage} from a raw
 *  <a href="https://en.wikipedia.org/wiki/Rawdisk">dump of a hard drive </a> (RAW).
 * <p>
 * Since RAW files hold no additional data beyond the disk contents, these files can
 *  only be automatically handled by programs that can detect their file systems.
 *  For instance, a typical raw disk image of a floppy disk begins with a FAT boot
 *  sector, which can be used to identify its file system.
 * <p>
 */
public class RawDiskImage extends DiskImage {
	private static final int MAX_BUFFER_SIZE = 0x100000;
	
	long diskStart;
	long diskSize;
	final int blockSize;
	final int clusterSize;
	final int clustersCount;
	final RawVirtualBlockTable clusterTable;
	
	public RawDiskImage(File file, long diskSize, int blockSize) throws IOException {
		if (!isValidBlockSize(blockSize))
			throw new IllegalArgumentException(String.format("Block size: %d", blockSize));
		if (diskSize < 0 || diskSize % blockSize != 0)
			throw new IllegalArgumentException(String.format("Disk size: %d must be multiple of block size %d", diskSize, blockSize));
		
		media = new RandomAccessFile(file, "rw");
		try { // Always close media on Exception
			path = file.getPath();
			readOnly = false;
			
			this.diskStart = 0L;
			this.diskSize = diskSize;
			this.blockSize = blockSize;
			this.clusterSize = bestBufferSize();
			this.clustersCount = (int)(diskSize / clusterSize);
			clusterTable = new RawVirtualBlockTable(this);
			imageTable = clusterTable;
			
			touched = true;
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	public RawDiskImage(File file, String mode, int blockSize) throws IOException, WrongHeaderException {
		if (!isValidBlockSize(blockSize))
			throw new IllegalArgumentException(String.format("Block size: %d", blockSize));

		media = new RandomAccessFile(file, mode);
		try { // Always close media on Exception
			readOnly = mode.equals("r");
			path = file.getPath();
			
			this.diskStart = 0L;
			this.diskSize = file.length();
			otherSmallDifferences(media);
			this.blockSize = blockSize;
			this.clusterSize = bestBufferSize();
			this.clustersCount = (int)(diskSize / clusterSize);
			clusterTable = new RawVirtualBlockTable(this);
			imageTable = clusterTable;
			
			// Check the first sector of the image, otherwise any file is classified as raw
			if (!canBeADiskImage(readMetadata(diskStart, 512)))
				throw new WrongHeaderException(getClass(), toString());
			
			setLayout(DiskLayouts.open(this));
		}
		catch (Exception e) {
			media.close();
			throw e;
		}
	}
	
	/**
	 * A VDI structure occupying the entire schema follows the same principles as a simple,
	 *  dynamic structure that has all blocks filled and ordered.
	 * <br>
	 * In the case of VHD, the initial image is the disk image adding a 512 byte structure
	 *  of the "conectix" type.
	 * <br>
	 * In the case of VMDK the structure is simply the complete disk image.
	 * 
	 * @param media		the media where the file system is mapped.
	 * @throws IOException if some I/O error occurs.
	 */
	private void otherSmallDifferences(RandomAccessFile media) throws IOException {
		if (media.length() >= 512) {
			byte[] buffer = new byte[512];
			
			int read = read(0, buffer, 0, 512);
			ByteBuffer in = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN);
			if (in.getInt(64) == 0xBEDA107F 								// VDI signature
					&& in.getInt(76) == 2) {								// Pre-allocate full size
				if (vdiBlockTable(media, in.getInt(340), in.getInt(384))) {	// 340=offsetBlocks, 384=blocksCount
					diskStart = in.getInt(344);
					diskSize = in.getLong(368);
					return;
				}
			}
			
			read = read(media.length() - 512, buffer, 0, 512);
			in = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.BIG_ENDIAN);
			if (in.getLong(0) == 0x636F6E6563746978L 						// VHD signature "conectix"
					&& in.getInt(60) == 2 									// Pre-allocate full size
					&& in.getLong(40) == media.length() - 512) {			// Binary level less 512
				diskSize -= 512;
				return;
			}
		}
	}
	
	private boolean vdiBlockTable(RandomAccessFile media, int offsetBlocks, int blocksCount) throws IOException {
		byte[] buffer = new byte[blocksCount*4];
		int read = read(offsetBlocks, buffer, 0, blocksCount*4);
		ByteBuffer in = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0, s = buffer.length/4; i < s; i++) 
			if (in.getInt() != i) 
				return false;
		return true;
	}
	
	private static boolean isValidBlockSize(int blockSize) {
		return blockSize >= 512 && blockSize <= 8192
				&& Static.isPower2(blockSize);
	}

	private boolean canBeADiskImage(ByteBuffer in) {
		if (in.remaining() >= 512) {
			in.order(ByteOrder.BIG_ENDIAN);
			for (int i = 0; i < 512; i++) {
				if (in.get(i) >= 0 && in.get(i) < 10) {
					return (in.getShort(510) == 0x55AA || in.getShort(0) == 0x4552);
				}
			}
		}
		return false;
	}
	
	private int bestBufferSize() {
		int bs = MAX_BUFFER_SIZE;
		while (bs > blockSize && diskSize % bs != 0)
			bs >>= 1;
		return bs;
	}

	@Override
	public String getType() {
		return "RAW";
	}

	@Override
	public boolean hasData(long offset, int length) {
		if (length <= 0 || offset >= diskSize)
			return false;
		
		if (offset + length > diskSize)
			length = (int)(diskSize - offset);
		int blockNumber = (int)(offset / clusterSize);
		int blockOffset = (int)(offset % clusterSize);
		int read = 0;
		
		while (read < length) {
			if (clusterTable.exists(blockNumber)) return true;
			int max = Math.min(length - read, clusterSize - blockOffset);
			blockOffset = 0;
			blockNumber++;
			read += max;
		}
		
		return false;
	}

	@Override
	public long getDiskSize() {
		return diskSize;
	}

	@Override
	public int getLogicalBlockSize() {
		return blockSize;
	}
	
	@Override
	public int getImageBlockSize() {
		return clusterSize;
	}

	@Override
	public int getImageBlocksCount() {
		return clustersCount;
	}

	@Override
	public int getImageBlocksInFile() {
		return clustersCount;
	}

	@Override
	public int getImageBlocksMapped() {
		return clusterTable.getDataClustersCount();
	}

	@Override
	public long getOptimizedLength() {
		return getImageLength();
	}

	@Override
	protected int read(long offset, byte[] in, int start, int length) throws IOException {
		media.seek(diskStart + offset);
		return media.read(in, start, length);
	}

	@Override
	public void write(byte[] out, int start, int length) throws IOException {
		media.seek(diskPointer);
		media.write(out, start, length);
		diskPointer += length;
		touched = true;
	}

	@Override
	protected void update() throws IOException {
		// nothing to do
	}

	@Override
	public void compact() {
		// nothing to do
	}

	@Override
	public synchronized void copy(DiskImage source) throws IOException {
		if (getDiskSize() != source.getDiskSize())
			throw new IOException(MUST_HAVE_SAME_SIZE);
		if (readOnly)
			throw new IOException(IMAGE_IS_READ_ONLY);
		
		Progress progress = new Progress(DiskImageProgress.COPY, getImageBlocksCount());
		
		synchronized(source) {
			
			diskPointer = 0L;
			byte[] buffer = new byte[getImageBlockSize()];
			for (int i = 0, s = getImageBlocksCount(); i < s; i++) {
				int read = source.readAll(diskPointer, buffer, 0, buffer.length);
				if (read < buffer.length) {
					if (read < 0 || diskPointer + read < getDiskSize())
						throw new EOFException(source.toString());
				}
				write(buffer, 0, buffer.length);
				progress.step(1);
				touched = true;
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
			}
		}
		
		media.setLength(media.getFilePointer());
		
		progress.end();
	}

}
