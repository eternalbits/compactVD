/*
 * Copyright 2020 Rui Baptista
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

package io.github.eternalbits.apple.disk.apfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.disk.DiskFileSystem;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.WrongHeaderException;

public class ApfsFileSystem extends DiskFileSystem { // https://developer.apple.com/support/downloads/Apple-File-System-Reference.pdf
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	final ApfsVolumeHeader header;

	public ApfsFileSystem(DiskLayout layout, long offset, long length) throws IOException, WrongHeaderException {
		this.layout		= layout;
		this.diskOffset	= offset;
		this.diskLength	= length;
		
		header = new ApfsVolumeHeader(this, readImage(0, ApfsVolumeHeader.HEADER_SIZE));
	}
	
	@Override
	public String getType() {
		return "APFS";
	}

	@Override
	public String getDescription() {
		return "Apple File System";
	}

	ByteBuffer readImage(long offset, int length) throws IOException {
		byte[] buffer = new byte[length];
		int read = layout.getImage().readAll(diskOffset + offset, buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, read).order(BYTE_ORDER);
	}
	
	@Override
	public boolean isAllocated(long offset, long length) {
		return true;
	}

}
