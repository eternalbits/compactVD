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

package io.github.eternalbits.disk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Vector;

import java.io.RandomAccessFile;
import io.github.eternalbits.compactvd.Static;

/**
 * Abstract class that represents a disk image. Disk images are computer files
 * with the structure and contents of disk volumes. A disk image can be a 
 * sector-by-sector copy of the source medium, or may contain additional metadata
 * that describes how the computer file is mapped to the virtual disk volume.
 * <p>
 */
public abstract class DiskImage implements AutoCloseable {
	
	protected DiskImageBlockTable imageTable = null;
	
	protected RandomAccessFile media = null;
	protected String path = null;

	protected long diskPointer = 0;			// The public disk pointer. Changed by seek, read and write.
	
	protected boolean readOnly = true;		// 
	protected boolean touched = false;		// Indicates if the image was ever changed by this object.
	protected boolean dirty = false;		// Indicates if the image metadata has uncommitted changes.
	
	private DiskLayout layout = null;
	
	/* Statistical data to be used by viewers
	 */
	Integer blocksNotInUse = null;
	Integer blocksZeroed = null;
	HashMap<DiskFileSystem, FileSysData> blockView = new HashMap<DiskFileSystem, FileSysData>();
	class FileSysData {
		int blockStart = 0;
		int blockEnd = 0;
		int blocksMapped = 0;
		int blocksNotInUse = 0;
		int blocksZeroed = 0;
	}
	
	protected void setLayout(DiskLayout layout) {
		this.layout = layout;
		if (layout != null) {
			long length = getImageBlockSize();
			for (DiskFileSystem fs: layout.getFileSystems()) {
				FileSysData fsd = new FileSysData();
				long offset = fs.getOffset();
				fsd.blockStart = (int)Static.ceilDiv(offset, length);;
				fsd.blockEnd = (int)((offset + fs.getLength()) / length);
				if (imageTable != null) {
					fsd.blocksMapped = imageTable.countBlocksMapped(fsd.blockStart, fsd.blockEnd);
				}
				blockView.put(fs, fsd);
			}
		}
	}
	
	DiskLayout getLayout() {
		return layout;
	}

	public DiskImageView getView() {
		return new DiskImageView(this);
	}


	public String getPath() {
		return path;
	}

	public abstract String getType();
	public abstract long getDiskSize();
	public abstract int getLogicalBlockSize();
	public abstract int getImageBlockSize();
	public abstract int getImageBlocksCount();
	public abstract int getImageBlocksInFile();
	public abstract int getImageBlocksMapped();

	/**
	 * Returns the length of the image file, in bytes. This information is only
	 *  informative and the caller should not use it for other purposes.
	 *  
	 * @return	The length of the image file.
	 */
	public long getImageLength() {
		try {
			return media.length();
		} catch (IOException e) {
			return -1;
		}
	}

	/**
	 * Returns the optimized length of the image file, in bytes. This information is
	 *  valid only for a {@code compact} operation after calling {@link #optimize(int)}. 
	 *  The result of {@code copy} may vary depending on the output format.
	 *  
	 * @return	The expected length of the image file after a {@code compact} 
	 * 				or {@code copy} operation.
	 */
	public abstract long getOptimizedLength();

	/**
	 * Sets the image data offset, measured from the beginning of the disk
	 *  data, at which the next read or write occurs.  The offset may be
	 *  set beyond the end of the data. Setting the offset beyond the end
	 *  of the data does not change the disk length.
	 *
	 * @param	offset	the offset position, measured in bytes from the
	 *					 beginning of the disk.
	 * @throws	IOException	if {@code offset} is less than {@code 0}.
	 */
	public synchronized void seek(long offset) throws IOException {
		if (offset < 0)
			throw new IOException();
		diskPointer = offset;
	}
	
	/**
	 * Returns the current offset in this image data. This method
	 *  is not synchronized to avoid blocking and the result is only
	 *  informative outside a synchronized block.
	 *
	 * @return	 the offset from the beginning of the image data, in bytes,
	 *			 at which the next read or write occurs.
	 */
	public final long getFilePointer() {
		return diskPointer;
	}

	/**
	 * This method is the {@code read} implementation for concrete disk images.
	 *  It behaves like described in the {@link DiskImage#read(byte[],int,int)}
	 *  method, with an explicit start equal to {@code offset} and no data 
	 *  pointer update.
	 * 
	 * @param	offset	the start offset in the image data.
	 * @param	in		the byte array into which the data is read.
	 * @param	start	the start offset in the byte array.
	 * @param	length	the number of bytes to read.
	 * @return	the number of bytes read, or {@code -1}
	 *			 if the data pointer is at end of data.
	 * @throws	IOException if some I/O error occurs.
	 */
	protected abstract int read(long offset, byte[] in, int start, int length) throws IOException;
	
	/**
	 * Reads {@code length} bytes from this image data into an array of bytes,
	 *  starting at position {@code offset}. This method blocks until the
	 *  requested number of bytes are read or an impossibility is met.
	 * <p>
	 * The number of bytes actually read is returned as an integer. The current
	 * pointer of the image data is not changed.
	 * <p>
	 * If {@code length} is zero, then no bytes are read and 0 is returned;
	 *  otherwise, if {@code offset} is equal to or greater than the data size,
	 *  the value -1 is returned; otherwise, the image data is repeatedly read
	 *  and the bytes read are stored into the buffer {@code in} until the
	 *  requested number of bytes are read, the end of data is reached, 
	 *  or an exception is thrown.
	 * <p>
	 * The bytes are stored in elements {@code in[start]} through
	 *  {@code in[start+length-1]}, leaving the other elements unaffected.
	 * 
	 * @param	offset	the start offset in the image data.
	 * @param	in		the byte array into which the data is read.
	 * @param	start	the start offset in the byte array.
	 * @param	length	the number of bytes to read.
	 * @return	the number of bytes read, or {@code -1}
	 *			 if {@code offset} is at end of data.
	 * @throws	IOException if some I/O error occurs.
	 */
	public synchronized int readAll(long offset, byte[] in, int start, int length) throws IOException {//
		if (length == 0)
			return 0;
		if (offset >= getDiskSize())
			return -1;
		int read = 0;
		
		while (read < length) {
			int some = read(offset + read, in, start + read, length - read);
			if (some < 0)
				break;
			read += some;
		}
		
		return read;
	}
	
	/**
	 * Reads up to {@code length} bytes from this image data into
	 *  an array of bytes, starting at the current data pointer. This method
	 *  blocks until at least one byte is available.
	 * <p>
	 * The number of bytes actually read is returned as an integer and the data 
	 *  pointer is updated.
	 * <p>
	 * If {@code length} is zero, then no bytes are read and 0 is returned;
	 *  otherwise, if the current data pointer is equal to or greater than the data
	 *  size, the value -1 is returned; otherwise, at least one byte is read and
	 *  stored into the buffer {@code in}; the number of bytes read is,
	 *  at most, equal to {@code length}.
	 * <p>
	 * The bytes are stored in elements {@code in[start]} through
	 *  {@code in[start+length-1]}, leaving the other elements unaffected.
	 * 
	 * @param	in		the byte array into which the data is read.
	 * @param	start	the start offset in the byte array.
	 * @param	length	the number of bytes to read.
	 * @return	the number of bytes read, or {@code -1}
	 *			 if the data pointer is at end of data.
	 * @throws	IOException if some I/O error occurs.
	 */
	public synchronized int read(byte[] in, int start, int length) throws IOException {
		int read = read(diskPointer, in, start, length);
		if (read > 0)
			diskPointer += read;
		return read;
	}
	
	/**
	 * This method calls {@link DiskImage#read(byte[],int,int)} with {@code start}
	 *  equal to zero and {@code length} equal to {@code in.length}.
	 * 
	 * @param	in		the buffer into which the data is read.
	 * @return	the number of bytes read, or {@code -1}
	 *			 if the data pointer is at end of data.
	 * @throws	IOException if some I/O error occurs.
	 */
	public int read(byte[] in) throws IOException {
		return read(in, 0, in.length);
	}
	
	protected ByteBuffer readMetadata(long offset, int length) throws IOException {
		media.seek(offset);
		byte[] buffer = new byte[length];
		int read = media.read(buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, read < 0? 0: read);
	}
	
	public abstract void write(byte[] out, int start, int length) throws IOException;
	
	public void write(byte[] out) throws IOException {
		write(out, 0, out.length);
	}
	
	protected final static boolean isZero(byte[] buffer, int start, int length) {
		for (int i = start, s = start + length; i < s; i++)
			if (buffer[i] != 0) return false;
		return true;
	}

	protected abstract void update() throws IOException;
	
	/**
	 * Returns {@code true} if the virtual disk device has space allocated in the
	 *  disk image starting at device {@code offset} and ending at {@code (offset
	 *  + length - 1)}.
	 * <p>
	 * If {@code length} is less or equal to zero, or {@code offset} is greater
	 *  or equal to the device length, {@code false} is returned. Otherwise a check
	 *  is made for each device cluster that might be allocated in the tested range.
	 *  If an allocated cluster is found, {@code true} is returned; if no allocated
	 *  clusters are found, {@code false} is returned.
	 *  
	 * @param offset	the offset position, measured in bytes from the
	 *					 beginning of the virtual disk device.
	 * @param length	the number of bytes to check.
	 * @return	{@code true} if space is allocated in the disk image for the 
	 * 					specified range, {@code false} otherwise.
	 */
	public abstract boolean hasData(long offset, int length);
	
	public static final int FREE_BLOCKS_ZEROED = 1;
	public static final int FREE_BLOCKS_NOT_IN_USE = 2;
	
	public synchronized void optimize(int options) throws IOException {
		if (imageTable == null)
			return;
		
		final boolean freeBlocksNotInUse = (options & FREE_BLOCKS_NOT_IN_USE) != 0 && layout != null;
		final boolean freeBlocksZeroed = (options & FREE_BLOCKS_ZEROED) != 0;
		
		/* Initializes a global Progress for the selected options. Finding zeroed blocks is
		 *  much slower then finding blocks not in use by file systems, and a different weight
		 *  is applied for each option. Effective speed depends heavily on system cache.
		 */
		final long ZW = freeBlocksZeroed? 256: 0;
		long maxValue = ZW * getImageBlocksMapped();
		for (FileSysData fsd: blockView.values())
			maxValue += freeBlocksNotInUse? fsd.blocksMapped: 0;
		Progress progress = new Progress(DiskImageProgress.OPTIMIZE, maxValue);
		Thread thisThread = Thread.currentThread();
		
		if (freeBlocksNotInUse == true) {
			if (blocksNotInUse == null)
				blocksNotInUse = 0;
			
			// Calls DiskFileSystem.isAllocated for each data block completely included in
			//	each file system, and frees the block if not in use by the file system
			
			long length = getImageBlockSize();
			
			for (DiskFileSystem fs: layout.getFileSystems()) {
				FileSysData fsd = blockView.get(fs);
				long offset = fs.getOffset();
				for (int i = fsd.blockStart, s = fsd.blockEnd; i < s && !thisThread.isInterrupted(); i++) {
					if (imageTable.exists(i)) {
						progress.step(1);
						if (!fs.isAllocated(i * length - offset, length)) {
							imageTable.free(i);
							blocksNotInUse++;
							dirty = true;
							fsd.blocksMapped--;
							fsd.blocksNotInUse++;
							progress.step(ZW);
							progress.view();
						}
					}
				}
			}
		}
		
		if (freeBlocksZeroed == true) {
			if (blocksZeroed == null)
				blocksZeroed = 0;
			
			// Each block is zeroed if all the bytes in the block are zero

			byte[] buffer = new byte[getImageBlockSize()];
			
			for (int i = 0, s = getImageBlocksCount(); i < s && !thisThread.isInterrupted(); i++) {
				if (imageTable.exists(i)) {
					progress.step(ZW);
					media.seek(imageTable.getOffset(i));
					media.readFully(buffer, 0, 4096);
					if (isZero(buffer, 0, 4096)) {
						media.readFully(buffer, 4096, buffer.length - 4096);
						if (isZero(buffer, 4096, buffer.length - 4096)) {
							imageTable.free(i);
							blocksZeroed++;
							dirty = true;
							// Only a few partitions and discardable blocks
							//	are expected, a linear search is adequate
							for (FileSysData fsd: blockView.values()) {
								if (i >= fsd.blockStart && i < fsd.blockEnd) {
									fsd.blocksMapped--;
									fsd.blocksZeroed++;
									progress.view();
									break;
								}
							}
						}
					}
				}
			}
		}
		
		progress.end();
	}
	
	public abstract void compact() throws IOException;

	public abstract void copy(DiskImage source) throws IOException;
	
	/**
	 * Attempts to acquire an exclusive lock on this image file. 
	 * 
	 * @return	An exclusive {@link FileLock} on the whole image.
	 * @throws	IOException if some I/O error occurs.
	 */
	public FileLock tryLock() throws IOException {
		FileLock lock = media.getChannel().tryLock();
		if (lock != null)
			return lock;
		throw new IOException(String.format("File %s is locked by other process.", path));
	}
	
	@Override
	public synchronized void close() throws IOException {
		if (media != null) {
			if (dirty && touched && !readOnly) {
				update();
			}
			media.close();
			media = null;
		}
	}
	
	@Override
	public String toString() {
		if (layout == null)
			return String.format("%s [%s]", path, getType());
		return String.format("%s [%s] %s", path, getType(), layout.toString());
	}
	
	protected void fillTo(long offset) throws IOException {
		long want = offset - media.getFilePointer();
		if (want > 0) {
			byte[] buffer = new byte[(int)Math.min(4096, want)];
			while (want > 0) {
				int max = (int)Math.min(buffer.length, want);
				media.write(buffer, 0, max);
				want -= max;
			}
		}
	}
	
	protected static final String MUST_HAVE_SAME_SIZE = "Disks must have the same size";
	protected static final String IMAGE_IS_READ_ONLY = "Image file is read-only";
	protected static final String JOURNAL_IDENTIFIER = "CompactVD.Journal = %s";
	
	
	/**
	 * Counts the number of clusters that will be moved from the end of the
	 *  image to unallocated space by a compact operation.
	 *  
	 * @param reverseMap A map of the virtual device clusters, with -1 when
	 * 					there is no data in the image. This is the reverse
	 * 					of the block table.
	 * @return	The number of clusters that will be moved by compact.
	 */
	protected static int countCompactMoves(int[] reverseMap) {
		int count = 0;
		for (int i = 0, s = reverseMap.length; i < s; i++) { // These blocks will be mapped
			if (reverseMap[i] == -1) {
				for (s = s - 1; s > i; s--) { // These blocks will be free
					if (reverseMap[s] != -1) {
						count++; // This block will be moved
						break;
					}
				}
			}
		}
		return count;
	}
	
	/**
	 * Counts the number of clusters that will be read from the source image
	 *  by a copy operation. Clusters not allocated in the source image are
	 *  skipped.
	 *  
	 * @param source	The source disk image.
	 * @return			The number of clusters that will be read.
	 */
	protected int countDataReads(DiskImage source) {
		int length = getImageBlockSize(), count = 0;
		long offset = 0L;
		for (int i = 0, s = getImageBlocksCount(); i < s; i++) {
			if (source.hasData(offset, length))
				count++;
			offset += length;
		}
		return count;
	}
	
	private final Vector<DiskImageObserver> obsProgress = new Vector<DiskImageObserver>();
	private final Vector<DiskImageObserver> obsChange = new Vector<DiskImageObserver>();
	
	/**
	 * Specific implementation like the {@link java.util.Observable} class. If the
	 *  parameter {@code view} is {@code true}, the observer will be notified 
	 *  with changes in view; otherwise it will be only notified with 
	 *  {@link DiskImageProgress#task} progress.
	 *  
	 * @param observer	 An observer to be added.
	 * @param view	Is the observer interested in views?
	 */
	public void addObserver(DiskImageObserver observer, boolean view) {
		if (observer == null)
			throw new NullPointerException();
		synchronized (obsProgress) {
			if (!obsProgress.contains(observer))
				obsProgress.add(observer);
			if (view) {
				if (!obsChange.contains(observer))
					obsChange.add(observer);
			}
		}
	}
	
	/**
	 * Removes an observer from the set of observers of this object.
	 * @param observer The observer to be removed.
	 */
	public void removeObserver(DiskImageObserver observer) {
		synchronized (obsProgress) {
			obsProgress.remove(observer);
			obsChange.remove(observer);
		}
	}
	
	/**
	 * A class to notify observers of the progress of a {@link DiskImageProgress#task}.
	 *  or changes in the {@link DiskImageView} while the task is running. To minimize
	 *  the impact of observers over the task, notifications occur at regular intervals
	 *  in time.
	 */
	protected class Progress {
		private final static long UPDATE_INTERVAL = 1000 / 60;
		
		private final int task;		// Current task
		private final long maximum;	// Maximum value
		private final long start;	// Start time in milliseconds

		private final int notInUse;
		private final int zeroed;
		
		private long lastChange;	// Last time observers were notified of a change in view
		private long lastValue;		// Last time observers were notified of a progress
		private long value = 0;		// Current value
		
		public Progress(int task, long max) {
			this.task 	= task;
			maximum 	= max;
			start 		= System.currentTimeMillis();
			notInUse 	= blocksNotInUse == null? 0: blocksNotInUse;
			zeroed 		= blocksZeroed == null? 0: blocksZeroed;
		}
		
		public void step(long add) {
			value += add;
			if (obsProgress.size() > 0 && maximum > 0 && value >= 0) {
				long now = System.currentTimeMillis();
				if (now - lastValue > UPDATE_INTERVAL || value == maximum) {
					lastValue = now;
					float pct = Math.min(1F, (float)value / maximum);
					notifyProgress(new DiskImageProgress(task, start, pct));
					if (task == DiskImageProgress.COMPACT) {
						fakeCompactView(1F - pct);
					}
				}
			}
		}
		
		private void fakeCompactView(float pct) { // Could this be real?
			int n = blocksNotInUse == null? 0: blocksNotInUse - Math.round(notInUse * pct);
			int z = blocksZeroed == null? 0: blocksZeroed - Math.round(zeroed * pct);
			if (n != 0 || z != 0) {
				if (blocksNotInUse != null) blocksNotInUse -= n;
				if (blocksZeroed != null) blocksZeroed -= z;
				for (FileSysData fsd: blockView.values()) {
					int dn = Math.min(fsd.blocksNotInUse, n);
					int dz = Math.min(fsd.blocksZeroed, z);
					fsd.blocksNotInUse -= dn; n -= dn;
					fsd.blocksZeroed -= dz; z -= dz;
					if (n == 0 && z == 0)
						break;
				}
				view();
			}
		}
		
		public void end() {
			if (obsProgress.size() > 0) {
				notifyProgress(new DiskImageProgress(task, start));
			}
		}

		public void view() {
			if (obsChange.size() > 0) {
				long now = System.currentTimeMillis();
				if (now - lastChange > UPDATE_INTERVAL) {
					lastChange = now;
					notifyChange(new DiskImageView(DiskImage.this));
				}
			}
		}
		
		private void notifyProgress(DiskImageProgress dip) {
			for (int i = obsProgress.size()-1; i>=0; i--)
				obsProgress.get(i).update(DiskImage.this, dip);
		}
		
		private void notifyChange(DiskImageView div) {
			for (int i = obsChange.size()-1; i>=0; i--)
				obsChange.get(i).update(DiskImage.this, div);
		}
		
	}

}
