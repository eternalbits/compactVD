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

package io.github.eternalbits.disks;

import java.io.IOException;

import io.github.eternalbits.darwin.disk.hfs.HfsFileSystem;
import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.NullFileSystem;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.linux.disk.ext.ExtFileSystem;
import io.github.eternalbits.windos.disk.ntfs.NtfsFileSystem;

public class DiskFileSystems {

	/**
	 * Creates a {@link DiskFileSystem} object that represents a file system mapped 
	 * 	in the disk layout between {@code offset} and {@code offset + length}.
	 * 
	 * @param layout		the disk layout where the file system is mapped.
	 * @param offset		the offset from the start of the disk, in bytes.
	 * @param length		the length of the file system, in bytes.
	 * @return				a {@link DiskFileSystem} object.
	 * @throws InitializationException if no implementation recognizes the file system,
	 * 							or an initialization error occurs.
	 * @throws IOException 	if some I/O error occurs.
	 */
	public static DiskFileSystem open(DiskLayout layout, long offset, long length) throws IOException {
		
		try {
			return new NtfsFileSystem(layout, offset, length);
		} catch (WrongHeaderException e) {}
		
		try {
			return new HfsFileSystem(layout, offset, length);
		} catch (WrongHeaderException e) {}
		
		try {
			return new ExtFileSystem(layout, offset, length);
		} catch (WrongHeaderException e) {}
		
		throw new InitializationException(DiskLayout.class, layout.toString());
	}

	/**
	 * Creates a {@link DiskFileSystem} object that represents the partition mapped 
	 * 	in the disk layout between {@code offset} and {@code offset + length}.
	 * <p>
	 * If no implementation recognizes the partition a {@link NullFileSystem} object 
	 * 	is returned with type {@code null} and the {@code description} provided.
	 * If the partition is mapped to a file system but an initialization error 
	 * 	occurs a {@code NullFileSystem} is returned with type equal to the mapped 
	 * 	file system type and the initialization error as description,
	 * 
	 * @param layout		the disk layout where the file system is mapped.
	 * @param offset		the offset from the start of the disk, in bytes.
	 * @param length		the length of the file system, in bytes.
	 * @param description	description of unrecognized partitions.
	 * @return				a {@link DiskFileSystem} object.
	 * @throws IOException 	if some I/O error occurs.
	 */
	public static DiskFileSystem map(DiskLayout layout, long offset, long length, String description) throws IOException {
		
		try {
			return new NtfsFileSystem(layout, offset, length);
		} catch (InitializationException e) {
			return new NullFileSystem(layout, offset, length, "NTFS", e);
		} catch (WrongHeaderException e) {}
		
		try {
			return new HfsFileSystem(layout, offset, length);
		} catch (InitializationException e) {
			return new NullFileSystem(layout, offset, length, "HFS", e);
		} catch (WrongHeaderException e) {}
		
		try {
			return new ExtFileSystem(layout, offset, length);
		} catch (InitializationException e) {
			return new NullFileSystem(layout, offset, length, "EXT", e);
		} catch (WrongHeaderException e) {}
		
		return new NullFileSystem(layout, offset, length, null, description);
	}

}
