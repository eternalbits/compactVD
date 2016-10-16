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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The {@code DiskImageJournal} records changes to a disk image before they
 *  are committed to disk. If an hardware failure occurs while the changes
 *  are written, the correct data can be recovered from the journal. 
 */
public class DiskImageJournal  implements Serializable {
	private static final long serialVersionUID = 6957171140438581661L;
	
	private final String subject;
	private final long timestamp;
	private final long idOffset;
	private final int idLength;
	private final byte[] idData;
	
	private class DataChunk implements Serializable {
		private static final long serialVersionUID = 360731567398681640L;
		private long offset;
		private int length;
		private byte[] data;
	}
	private final List<DataChunk> chunk = new ArrayList<DataChunk>();
	
	private transient File jrn = null;
	
	/**
	 * Creates a {@code DiskImageJournal} entry with an optional ID.
	 * 
	 * @param image		The {@code DiskImage} being journaled.
	 * @param offset	The offset in the disk image where the ID is recorded. 
	 * @param id		The ID; zero length and null IDs are handled as "no ID".
	 */
	public DiskImageJournal(DiskImage image, long offset, byte[] id) {
		subject = image.getPath();
		timestamp = System.currentTimeMillis();
		idOffset = offset;
		idLength = id == null? 0: id.length;
		idData = id;
	}
	
	/**
	 * Adds the array of bytes {@code data} to the journal copy. If the journal is
	 *  played against a matching disk image, the disk image is updated with the
	 *  journal copy starting at {@code offset} position.
	 * 
	 * @param offset	The offset in the disk image where the data will be updated.
	 * @param data		Array of bytes with data to update the disk image.
	 * @see #recover(File)
	 */
	public void addDataChunk(long offset, byte[] data) {
		DataChunk dc = new DataChunk();
		dc.offset = offset;
		dc.length = data.length;
		dc.data = data;
		chunk.add(dc);
	}

	/**
	 * Writes this journal entry in the directory {@code dir} with an unique name.
	 * 
	 * @param dir	The directory where this journal entry will be saved.
	 * @return	{@code true} if the entry was saved, {@code false} otherwise.
	 */
	public boolean write(File dir) {
		jrn = new File(dir, UUID.randomUUID().toString() + ".jrn");
		try (	FileOutputStream fstr = new FileOutputStream(jrn);
				ObjectOutputStream out = new ObjectOutputStream(fstr);
				) {
			out.writeObject(this);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Deletes the journal entry. The owner of the {@code DiskImageJournal} must
	 *  invalidate the ID or delete the file after the data is committed to disk.
	 */
	public void delete() {
		if (jrn != null)
			jrn.delete();
	}
	
	/**
	 * Scans the directory {@code dir} for matching journal entry files
	 *  and executes {@link #recover(File)} for each file found.
	 *  
	 * @param dir	The directory to scan.
	 */
	public static void scanDirectory(File dir) {
		for (File file: dir.listFiles()) if (file.getName().endsWith(".jrn")) recover(file);
	}
	
	/**
	 * Recovers incomplete or erroneous updates to a disk image:
	 * <ul>
	 * <li>Attempts to read a {@code DiskImageJournal} entry from the file {@code jrn}.</li>
	 * <li>Opens the subject disk image file for standard read and write access.</li>
	 * <li>If a ID was recorded, checks the disk image with the ID of the journal entry.</li>
	 * <li>The image data is updated with the journaled copy, if necessary.</li>
	 * <li>The journal entry file is deleted.</li>
	 * </ul>
	 * @param jrn	A file representing a disk image journal entry.
	 * @return	{@code true} if the disk image was updated, {@code false} otherwise.
	 */
	public static boolean recover(File jrn) {
		
		boolean linkMatch = false;
		boolean touched = false;
		
		try (	FileInputStream fstr = new FileInputStream(jrn);
				ObjectInputStream in = new ObjectInputStream(fstr);
				) {
			
			DiskImageJournal journal = (DiskImageJournal) in.readObject();
			File image = new File(journal.subject);
			
			if (image.isFile() // Check if the image was modified after the journal entry was recorded
					&& image.lastModified() - (journal.timestamp + 6666) < 0) { // unsigned <
				
				try (	RandomAccessFile update = new RandomAccessFile(image, "rw");	) {
					
					if (journal.idLength == 0) {
						linkMatch = true;
					} else {
						byte[] id = new byte[journal.idLength];
						update.seek(journal.idOffset);
						update.readFully(id);
						linkMatch = Arrays.equals(id, journal.idData);
					}
					
					if (linkMatch) {
						for (DataChunk dc: journal.chunk) {
							byte[] data = new byte[dc.length];
							update.seek(dc.offset);
							update.readFully(data);
							if (!Arrays.equals(data, dc.data)) {
								update.seek(dc.offset);
								update.write(dc.data);
								touched = true;						
							}
						}
						
						update.getFD().sync();
					}
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		if (linkMatch)
			jrn.delete();
		return touched;
	}
	
}
