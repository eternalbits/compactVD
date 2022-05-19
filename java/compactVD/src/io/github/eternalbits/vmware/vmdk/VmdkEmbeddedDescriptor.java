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

package io.github.eternalbits.vmware.vmdk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.InitializationException;

class VmdkEmbeddedDescriptor {

	private final VmdkDiskImage image;			// Parent object
	private final VmdkSparseHeader header;		// VMDK header
	
	/* VMDK Handbook - Basics
	 *	http://sanbarrow.com/vmdk-basics.html
	 */
	private final byte[] descriptor;			// Text descriptor; contains the nominal size of the disk in sectors
	
	VmdkEmbeddedDescriptor(VmdkDiskImage vmdk, String name/*, int adapterType*/) {
		image 	= vmdk;
		header 	= image.header;
		
		boolean ide = true;
		long sectorCount = header.diskSize / VmdkSparseHeader.SECTOR_LONG;
		long cylinders = ide? Math.min(sectorCount / 16 / 63, 16383): sectorCount / 255 / 63;
		
		StringBuilder sb = new StringBuilder();
		sb.append("# Disk DescriptorFile\n");
		sb.append("version=1\n");
		sb.append(String.format("encoding=\"%s\"\n", "UTF-8"));
		sb.append(String.format("CID=%08x\n", new Random().nextInt()));
		sb.append("parentCID=ffffffff\n");
		sb.append("createType=\"monolithicSparse\"\n");
		sb.append("\n");
		sb.append("# Extent description\n");
		sb.append(String.format("RW %d SPARSE \"%s\"\n", sectorCount, name));
		sb.append("\n");
		sb.append("# The Disk Data Base\n");
		sb.append("#DDB\n");
		sb.append("\n");
		sb.append(String.format("ddb.adapterType = \"%s\"\n", ide? "ide": "lsilogic"));
		sb.append(String.format("ddb.geometry.cylinders = \"%d\"\n", cylinders));
		sb.append(String.format("ddb.geometry.heads = \"%d\"\n", ide? 16: 255));
		sb.append("ddb.geometry.sectors = \"63\"\n");
		sb.append("ddb.virtualHWVersion = \"4\"\n");
		
		descriptor = sb.toString().getBytes(StandardCharsets.UTF_8);
	}
	
	private static Pattern EXTENT_PAT = Pattern.compile("\n\\s*RW\\s+(\\d+)\\s+SPARSE\\s+\"(.+)\"");
	private static Pattern CREATE_PAT = Pattern.compile("\n\\s*createType\\s*=\\s*\"(\\w+)\"");
	
	VmdkEmbeddedDescriptor(VmdkDiskImage vmdk, ByteBuffer in) throws InitializationException {
		image 	= vmdk;
		header 	= image.header;
		
		descriptor = Static.getReservedBytes(in, in.remaining());
		String desc = new String(descriptor, StandardCharsets.UTF_8);
		
		Matcher m = EXTENT_PAT.matcher(desc);
		Matcher c = CREATE_PAT.matcher(desc);
		if (!m.find() || !c.find() || !c.group(1).equals("monolithicSparse"))
			throw new InitializationException(getClass(), vmdk.toString());
		
		header.diskSize = Long.valueOf(m.group(1)) * VmdkSparseHeader.SECTOR_SIZE;
		header.fileName = m.group(2);
		
		header.imageCID					= fromInteger(desc, "CID");
		header.parentCID				= fromInteger(desc, "parentCID");
		header.parentFileName			= fromString(desc, "parentFileNameHint");
		
		header.uuidImage 				= fromUUID(desc, "image");
		header.uuidModification 		= fromUUID(desc, "modification");
		header.uuidParent 				= fromUUID(desc, "parent");
		header.uuidParentModification 	= fromUUID(desc, "parentmodification");
		
		if (header.getFileType() != 0)
			throw new InitializationException(String.format("%s: Not a dynamic base image file.", vmdk.toString()));
	}

	private static UUID fromUUID(String desc, String header) {
		Pattern UUID_PAT = Pattern.compile("\n\\s*ddb.uuid."+header+"\\s*=\\s*\"([0-9A-Fa-f-]{36})\"");
		Matcher m = UUID_PAT.matcher(desc);
		if (!m.find()) return null;
		return UUID.fromString(m.group(1));
	}

	private static Integer fromInteger(String desc, String header) {
		Pattern INTEGER_PAT = Pattern.compile("\n\\s*"+header+"\\s*=\\s*([0-9A-Fa-f]+)");
		Matcher m = INTEGER_PAT.matcher(desc);
		if (!m.find()) return null;
		return (int)Long.parseLong(m.group(1), 16);
	}

	private static String fromString(String desc, String header) {
		Pattern STRING_PAT = Pattern.compile("\n\\s*"+header+"\\s*=\\s*(.+)");
		Matcher m = STRING_PAT.matcher(desc);
		if (!m.find()) return null;
		return m.group(1);
	}

	void update() throws IOException {
		image.getMedia().seek(header.descriptorOffset * VmdkSparseHeader.SECTOR_LONG);
		image.getMedia().write(descriptor);
	}
}
