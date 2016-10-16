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

/**
 * The {@code NullFileSystem} is a placeholder for disk partitions that are not
 *  intended to hold files like the Linux {@code swap} area; unrecognized or not 
 *  implemented file systems; or implemented file systems with initialization 
 *  errors.  
 */
public class NullFileSystem extends DiskFileSystem {

	private final String type;
	private final String description;
	private boolean isError = false;
	
	/**
	 * Initializes a {@code NullFileSystem} representing a partition that is not
	 *  intended to hold files, is unrecognized or is not implemented. 
	 * 
	 * @param layout		the disk layout where the partition is located.
	 * @param offset		the offset from the start of the disk, in bytes.
	 * @param length		the length of the partition, in bytes.
	 * @param type			the string returned by {@code getType}.
	 * @param description	string returned by {@code getDescription}.
	 */
	public NullFileSystem(DiskLayout layout, long offset, long length, String type, String description) {
		this.layout		= layout;
		this.diskOffset = offset;
		this.diskLength = length;
		
		this.type = type;
		this.description = description;
	}
	
	/**
	 * Initializes a {@code NullFileSystem} representing an implemented file system
	 *  with initialization errors.  
	 * 
	 * @param layout		the disk layout where the file system is located.
	 * @param offset		the offset from the start of the disk, in bytes.
	 * @param length		the length of the file system, in bytes.
	 * @param type			the string returned by {@code getType}.
	 * @param description	the initialization exception.
	 */
	public NullFileSystem(DiskLayout layout, long offset, long length, String type, InitializationException description) {
		this(layout, offset, length, type, description.getLocalizedMessage());
		this.isError = true;
	}
	
	@Override
	public String getType() {
		return type == null? "NULL": type;
	}

	@Override
	public String getDescription() {
		return description == null? "Unknown file system": description;
	}

	@Override
	public boolean isAllocated(long offset, long length) {
		return true;
	}

	@Override
	public String toString() {
		if (isError)
			return getType() +":"+ getDescription();
		return type == null? getDescription(): type;
	}

}
