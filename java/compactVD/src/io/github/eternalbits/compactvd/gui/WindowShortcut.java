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

package io.github.eternalbits.compactvd.gui;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import io.github.eternalbits.compactvd.Static;

class WindowShortcut {	// https://winprotocoldoc.blob.core.windows.net/productionwindowsarchives/MS-SHLLINK/%5bMS-SHLLINK%5d.pdf

	/**
	 * If data is a shortcut try to read the source of that object.
	 * 
	 * @param	data	the source as it is read.
	 * @return	the source of this object.
	 */
	public static File linkFile(File data) {
		
		if (data.length() > 76 && data.length() < 4096) {
			try (RandomAccessFile check = new RandomAccessFile(data, "r")) {
				
				byte in[] = new byte[(int)data.length()];
				check.read(in);
				ByteBuffer link = ByteBuffer.wrap(in).order(ByteOrder.LITTLE_ENDIAN);
				
				try {	
				//	Tried to read the entire source and compare it to the content
					int header = link.getInt(0);
					int target = link.getShort(header) + 2 + header;
					int info   = link.getInt(target) + target;
					int string = info;
					while (link.getInt(string) > 65535)
						string = link.getShort(string) * 2 + 2 + string;
					int length = string;
					while (link.getInt(length) != 0)
						length = link.getInt(length) + length;
					length = 4 + length;
					
					if (data.length() == length) {
						
					/* Unicode characters are stored in this structure if the data cannot be represented as ANSI characters 
					 *	due to truncation of the values. In this case, the value of the LinkInfoHeaderSize field 
					 *	is greater than or equal to 36
					 */	
						if (link.getInt(target + 4) < 36) {	//	LinkInfoHeaderSize
							link.position(link.getInt(target + 16) + target);	//	LocalBasePathOffset
							return new File(Static.getString(link, info - link.position(), StandardCharsets.ISO_8859_1));
						} else {
							link.position(link.getInt(target + 28) + target);	//	LocalBasePathOffsetUnicode
							return new File(Static.getString(link, info - link.position(), StandardCharsets.UTF_16LE));
						}
						
					}
				} catch (RuntimeException e) { e.printStackTrace(); }
			} catch (IOException e) { e.printStackTrace(); }
		}
		
		return data;
	}
	
}
