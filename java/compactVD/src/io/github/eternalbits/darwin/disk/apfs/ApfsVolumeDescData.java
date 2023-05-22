/*
 * Copyright 2023 Rui Baptista
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

package io.github.eternalbits.darwin.disk.apfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

public class ApfsVolumeDescData {

	final ApfsFileSystem fileSystem;

	@SuppressWarnings("unused")
	ApfsVolumeDescData(ApfsFileSystem apfs, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= apfs;
		
		if (in.remaining() >= ApfsFileSystem.HEADER_SIZE) {
			in.order(ApfsFileSystem.BYTE_ORDER);
			
			long	dd_cksum;								// The Fletcher 64 checksum of the object.
			long	dd_oid;									// The object's identifier.
			long	dd_xid;									// The identifier of the most recent transaction that this object was modified in.
			int		dd_type;								// The object's type and flags. The low 16 bits indicate the type, and the high 16 bits are flags.
			int		dd_subtype;								// The object's subtype. It indicates the type of data stored in a data structure such as a B-tree.
			int		dd_block_size;							// The logical block size used in the Apple File System container.
			
			if (ApfsFileSystem.checkChecksum(in) == 0) {
				dd_cksum						= in.getLong();
				dd_oid							= in.getLong();
				dd_xid							= in.getLong();
				dd_type							= in.getInt();
				dd_subtype						= in.getInt();
				dd_block_size					= in.getInt();
				
				if (dd_xid == ApfsVolumeHeader.HEADER_XID) {
					return;
				}
			}
		}
		
		throw new InitializationException("A different XID was found");
	}
	
}
