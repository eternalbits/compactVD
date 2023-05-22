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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.InitializationException;
import io.github.eternalbits.disk.WrongHeaderException;

public class ApfsSpacemanPhys {
	
	private static final int SPACEMAN = 0x80000005;			// The object's type and flags.
	private static final int BLOCK_COUNT = 32768;			// 32768 bytes are expected, i.e. a reset of 32768 * 4096 bytes.
	
	long[] ph_space = null;									// This is a long list with bitmap representation. One case requires attention:
															// There is representation of zero through 0, which is an empty bitmap.
	final ApfsFileSystem fileSystem;

	@SuppressWarnings("unused")
	ApfsSpacemanPhys(ApfsFileSystem apfs, ByteBuffer in) throws IOException, WrongHeaderException {
		this.fileSystem	= apfs;

		if (in.remaining() >= ApfsFileSystem.HEADER_SIZE) {

			long	sm_cksum;								// The Fletcher 64 checksum of the object.
			long	sm_oid;									// The object's identifier.
			long	sm_xid;									// The identifier of the most recent transaction that this object was modified in.
			int		sm_type;								// The object's type and flags. The low 16 bits indicate the type, and the high 16 bits are flags.
			int		sm_subtype;								// The object's subtype. It indicates the type of data stored in a data structure such as a B-tree.
			byte[]	sm_used;								// The spaceman_device_t, spaceman_free_queue_t and a bunch more items. (2488)
			long	ph_xid;									// Copy of the identifier of the most recent transaction that this object was modified in.
			long	ph_sum;									// It almost looks like an accountant, but it's not. This is not part of the data.
			long[]	ph_count;								// Here's a list of items that spaceman are part of, and a few more things... (195)
			
			if (ApfsFileSystem.checkChecksum(in) == 0) {
				sm_cksum						= in.getLong();
				sm_oid							= in.getLong();
				sm_xid							= in.getLong();
				sm_type							= in.getInt();
				sm_subtype						= in.getInt();
				in.position(in.position() + 2488);
				ph_xid							= in.getLong();
				ph_sum							= in.getLong();
				ph_count						= Static.getLongs(in, 195);
				
				if (sm_xid == ApfsVolumeHeader.HEADER_XID 
						&& sm_type == SPACEMAN 
						&& sm_xid == ph_xid) {
					
					ArrayList<Long> space =  new ArrayList<Long>();
					int index = 0;
					long step = 0;
					
					for (int i = 0; i < 195; i++) {
						long read = ph_count[i] * ApfsFileSystem.HEADER_SIZE;
						
						if (read > 0 && read < apfs.header.nx_block_count * ApfsFileSystem.HEADER_SIZE) {
							ByteBuffer buf = fileSystem.readImage(read, ApfsFileSystem.HEADER_SIZE);
							
							if (buf.remaining() == ApfsFileSystem.HEADER_SIZE 
									&& ApfsFileSystem.checkChecksum(buf) == 0 
									&& buf.getLong(16) > 0 && buf.getLong(16) <= ph_xid 
									&& buf.getInt(36) > 0 && buf.getInt(36) <= 126 
									&& buf.getInt(32) == index) {
								
								for (int j = 0; j < buf.getInt(36); j++) {
									if (buf.getLong(48 + j * 32) == step) {
										space.add(buf.getLong(64 + j * 32));
										step += BLOCK_COUNT;
									}
								}
								index++;
							}
						}
					}
					
					ph_space = new long[space.size()];
					for (int i = 0; i < space.size(); i++) 
						ph_space[i] = space.get(i);
					return;
				}
			}
		}
		
		throw new InitializationException("A troubled Spaceman was found");
	}
	
	long getSpaceman(long offset) throws EOFException {
		if (offset >= 0 && offset < fileSystem.header.nx_block_count) {
			if (offset < (long) ph_space.length * 4096) {
				return ph_space[(int) offset / 4096];
			}
			return -1;
		}
		throw new EOFException(String.format("%s@%d", "Spaceman", offset));
	}
}
