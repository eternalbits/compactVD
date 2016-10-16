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

package io.github.eternalbits.compacttu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Random;

public class DebugAccessFile extends RandomAccessFile {
	private boolean touched = false;
	private boolean closed = false;
	private final String path;
	
	public DebugAccessFile(File file, String mode) throws FileNotFoundException {
		super(file, mode);
		path = file.getPath();
	}
	@Override
	public void close() throws IOException {
		super.close();
		if (touched || closed) return;
		System.out.println(path+" CLOSED");
		closed = true;
	}
	private void touch() {
		if (touched) return;
		System.out.println(path+" TOUCHED");
		touched = true;
	}
	private void crash(byte b[]) throws IOException {
		Random r = new Random();
		IntBuffer ib = ByteBuffer.wrap(b).asIntBuffer();
		for (int i=0, s=ib.capacity(); i<s; i++)
			ib.put(i, r.nextInt());
		super.write(b);
		touch();
		System.exit(-1);
	}
	@Override
	public void write(int b) throws IOException {
		super.write(b);
		touch();
	}
	@Override
	public void write(byte b[]) throws IOException {
		String crash = System.getProperty("crash");
		if (crash != null && b.length % 4 == 0) {
			byte[] h = new byte[512];
			long pos = getFilePointer();
			seek(0); readFully(h); seek(pos);
			ByteBuffer hb = ByteBuffer.wrap(h); //ByteOrder.BIG_ENDIAN
			if (hb.getInt(64) == 0x7F10DABE) { //VDI
				hb.order(ByteOrder.LITTLE_ENDIAN);
				long tab = (long)hb.getInt(340) & 0xFFFFFFFF;
				if (crash.equals("header") && pos == 0 || crash.equals("table") && pos == tab) {
					System.out.println("CRASH "+crash.toUpperCase());
					crash(b);
					return;
				}
			}
			else
			if (hb.getInt(0) == 0x4B444D56) { //VMDK
				hb.order(ByteOrder.LITTLE_ENDIAN);
				long tab = hb.getLong(56);
				long cap = hb.getLong(12);
				long gs = hb.getLong(20);
				int gpg = hb.getInt(44);
				long tec = cap / gs;
				long dec = (tec + gpg -1) / gpg;
				tab = tab * 512 + dec * 4;
				if (crash.equals("header") && pos == 0 || crash.equals("table") && pos == tab) {
					System.out.println("CRASH "+crash.toUpperCase());
					crash(b);
					return;
				}
			}
			else // TODO check VHD stuff when implemented
			if (hb.getLong(16) <= 0x1000L) {
				long dyn = hb.getLong(16); 
				seek(dyn); readFully(h); seek(pos);
				if (hb.getLong(0) == 0x6378737061727365L) { //VHD
					long tab = hb.getLong(16);
					if (crash.equals("header") && pos == dyn || crash.equals("table") && pos == tab) {
						System.out.println("CRASH "+crash.toUpperCase());
						crash(b);
						return;
					}
				}
			}
		}
		super.write(b);
		touch();
	}
	@Override
	public void write(byte b[], int off, int len) throws IOException {
		super.write(b, off, len);
		touch();
	}
}
