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

package io.github.eternalbits.uefi.disk.gpt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskLayout;
import io.github.eternalbits.disk.WrongHeaderException;
import io.github.eternalbits.ibmpc.disk.mbr.DiskBootRecord;

public class GptDiskLayout extends DiskLayout { // http://www.uefi.org/sites/default/files/resources/UEFI%20Spec%202_6.pdf
	static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

	private static int GPT_PROTECTIVE_PART = 0xEE;

	final int blockSize;
	final DiskBootRecord pmbr;
	final GuidPartitionHeader header;
	final GuidPartitionTable table;

	public GptDiskLayout(DiskImage img) throws IOException, WrongHeaderException {
		this.image 		= img;
		blockSize 		= img.getLogicalBlockSize();
		long blockCount	= img.getDiskSize() / blockSize;

		byte[] buffer = new byte[DiskBootRecord.bufferSize()];
		int read = image.readAll(0L, buffer, 0, buffer.length);
		pmbr = new DiskBootRecord(this, ByteBuffer.wrap(buffer, 0, read));
		
		if (!isValidPmbr())
			throw new WrongHeaderException(getClass(), image.toString());
		
		buffer = new byte[GuidPartitionHeader.HEADER_SIZE];
		read = image.readAll(blockSize, buffer, 0, buffer.length);
		header = new GuidPartitionHeader(this, blockCount, ByteBuffer.wrap(buffer, 0, read));
		
		buffer = new byte[GuidPartitionTable.ENTRY_SIZE * header.partitionCount];
		read = image.readAll(header.partitionLBA * blockSize, buffer, 0, buffer.length);
		table = new GuidPartitionTable(this, ByteBuffer.wrap(buffer, 0, read));
		
	}

	private boolean isValidPmbr() {
		return pmbr.getType(0) == GPT_PROTECTIVE_PART 
				&& pmbr.getFirstSector(0) == 1
				&& pmbr.getSectorCount(0) + 1 == image.getDiskSize() / image.getLogicalBlockSize()
				&& pmbr.isPartEmpty(1) && pmbr.isPartEmpty(2) && pmbr.isPartEmpty(3);
	}
	
	@Override
	public String getType() {
		return "GPT";
	}
}
