package io.github.eternalbits.windos.disk.ntfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

public class NtfsFileSystem extends DiskFileSystem {
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	private static final int RSTR_SIGNATURE = 0x52545352;
	
	private static final int CLUSTER_BITMAP_FILE = 6;
	private static final int LOG_FILE = 2;
	
	final NtfsBootSector header;
	final NtfsFileRecord bitmapFile;
	final NtfsFileRecord logFile;
	
	public NtfsFileSystem(DiskLayout layout, long offset, long length) throws IOException, WrongHeaderException {
		this.layout		= layout;
		this.diskOffset = offset;
		this.diskLength = length;
		
		header = new NtfsBootSector(this, readImage(0, NtfsBootSector.BOOT_SIZE));
		// Oversimplifying, files that matter are always sequentially at the beginning of the $MFT
		bitmapFile = new NtfsFileRecord(this, readImage(header.masterCluster * header.clusterSize
				+ CLUSTER_BITMAP_FILE * header.recordSize, header.recordSize));
		logFile = new NtfsFileRecord(this, readImage(header.masterCluster 
				* header.clusterSize + LOG_FILE * header.recordSize, header.recordSize));
		if ("$Bitmap".equals(bitmapFile.fileName) && "$LogFile".equals(logFile.fileName)) {
			
			if (!isJournalEmpty())
				throw new InitializationException("The journal is not empty");
			return;
			
		}
		
		throw new InitializationException(getClass(), this.toString());
	}

	ByteBuffer readImage(long offset, int length) throws IOException {
		byte[] buffer = new byte[length];
		int read = layout.getImage().readAll(diskOffset + offset, buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, read).order(BYTE_ORDER);
	}
	
	/**
	 * Checks if the journal has transactions. Implementations accessing a journaled volume
	 *  with transactions must either refuse to access the volume, or replay the journal.
	 * 
	 * @return	true if there are no transactions.
	 * @throws IOException if some I/O error occurs.
	 */
	private boolean isJournalEmpty() throws IOException {
		
		ByteBuffer in = readImage(logFile.getCluster(0) * header.clusterSize, 510);
		// No need to fix the record, all data is expected in the first sector
		if (in.getInt(0) != RSTR_SIGNATURE)
			return false;
		int r = in.getShort(24);			// Restart structure
		short inUse = in.getShort(r + 12);	// First client in use
		short flags = in.getShort(r + 14);	// Flags
		if (inUse == -1)					// Before XP the journal is closed if there are no clients.
			return true;
		if ((flags & 2) == 2)				// Since XP the journal is always open and a flag is set
			return true;					//  when the volume is cleanly unmounted.
		return false;
	}

	@Override
	public String getType() {
		return "NTFS";
	}

	@Override
	public String getDescription() {
		return "Windows NT File System";
	}

	private final byte[] leaveMask = new byte[] {(byte)0xFF, 0x1, 0x3, 0x7, 0x0F, 0x1F, 0x3F, 0x7F};
	private final byte[] enterMask = new byte[] {(byte)0xFF, (byte)0xFE, (byte)0xFC, (byte)0xF8, 
			(byte)0xF0, (byte)0xE0, (byte)0xC0, (byte)0x80};
	
	@Override
	public boolean isAllocated(long offset, long length) {
		if (length == 0)
			return false;
		
		/* The allocation bitmap represents logical clusters. Each cluster is represented by a bit.
		 * 	If the bit is set, the cluster has data or metadata. The first cluster is represented
		 *  by the least significant bit of the first byte.
		 */
		long firstCluster = offset / header.clusterSize;					// First cluster to check
		long lastCluster = (offset + length - 1) / header.clusterSize;		// Last cluster to check
		if (firstCluster < 0 || lastCluster >= header.clustersCount)		// Is cluster range valid?
			return true;
		
		long firstByte = firstCluster / 8;									// First byte to read
		long lastByte = lastCluster / 8;									// Last byte to read
		byte firstMask = enterMask[(int) (firstCluster % 8)];				// Bits to ignore in first byte
		byte lastMask = leaveMask[(int) ((lastCluster + 1) % 8)];			// Bits to ignore in last byte
		
		try {
			int want = (int) (lastByte - firstByte + 1), into = 0;
			byte[] buffer = new byte[want];
			long from = firstByte;
			while (want > 0) {
				long readCluster = bitmapFile.getCluster(from);
				int readOffset = (int) (from % header.clusterSize);
				int read = Math.min(want, header.clusterSize - readOffset);
				read = layout.getImage().readAll(diskOffset + readCluster * header.clusterSize 
						+ readOffset, buffer, into, read);
				if (read == -1) return true;
				want -= read;
				from += read;
				into += read;
			}
			
			buffer[0] &= firstMask;
			buffer[buffer.length -1] &= lastMask;
			for (int i = 0; i < buffer.length; i++)
				if (buffer[i] != 0)
					return true;
			return false;
			
		} catch (IOException e) {}
		
		return true;
	}

}
