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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.disk.raw.RawDiskImage;
import io.github.eternalbits.vbox.vdi.VdiDiskImage;
import io.github.eternalbits.vmware.vmdk.VmdkDiskImage;
import io.github.eternalbits.windos.vhd.VhdDiskImage;

public class DiskImages {
	private static String UNKNOWN_TYPE = "Unknown disk image type";
	
	public static DiskImage open(String path, String mode) throws IOException {
		return open(new File(path), mode);
	}

	public static DiskImage open(File file, String mode) throws IOException {
		
		try (RandomAccessFile media = new RandomAccessFile(file, "r")) {
			switch (media.readInt()) {
			case 0x3C3C3C20:								// '<<< ' for VDI
				return new VdiDiskImage(file, mode);
			case 0x4B444D56:								// 'KDMV' for VMDK
				return new VmdkDiskImage(file, mode);
			case 0x636F6E65:								// 'cone' for VHD
				return new VhdDiskImage(file, mode);
			}
		} catch (WrongHeaderException e) {}
		
		try {
			return new VdiDiskImage(file, mode);
		} catch (WrongHeaderException e) {}
		
		try {
			return new VmdkDiskImage(file, mode);
		} catch (WrongHeaderException e) {}
		
		try {
			return new VhdDiskImage(file, mode);
		} catch (WrongHeaderException e) {}
		
		try {
			return new RawDiskImage(file, mode, 512);
		} catch (WrongHeaderException e) {}
		
		throw new InitializationException(DiskImage.class, file.getPath());
	}

	public static DiskImage open(String type, String path, String mode) throws IOException, WrongHeaderException {
		return open(type, new File(path), mode);
	}

	public static DiskImage open(String type, File file, String mode) throws IOException, WrongHeaderException {
		
		if (type.equalsIgnoreCase("vdi")) {
			return new VdiDiskImage(file, mode);
		}
		
		if (type.equalsIgnoreCase("vmdk")) {
			return new VmdkDiskImage(file, mode);
		}
		
		if (type.equalsIgnoreCase("vhd")) {
			return new VhdDiskImage(file, mode);
		}
		
		if (type.equalsIgnoreCase("raw")) {
			return new RawDiskImage(file, mode, 512);
		}
		
		throw new IllegalArgumentException(String.format("%s: %s", UNKNOWN_TYPE, type));
	}

	public static DiskImage create(String type, String path, long diskSize) throws IOException {
		return create(type, new File(path), diskSize);
	}

	public static DiskImage create(String type, File file, long diskSize) throws IOException {
		
		if (type.equalsIgnoreCase("vdi")) {
			return new VdiDiskImage(file, diskSize);
		}
		
		if (type.equalsIgnoreCase("vmdk")) {
			return new VmdkDiskImage(file, diskSize);
		}
		
		if (type.equalsIgnoreCase("vhd")) {
			return new VhdDiskImage(file, diskSize);
		}
		
		if (type.equalsIgnoreCase("raw")) {
			return new RawDiskImage(file, diskSize, 512);
		}
		
		throw new IllegalArgumentException(String.format("%s: %s", UNKNOWN_TYPE, type));
	}
}
