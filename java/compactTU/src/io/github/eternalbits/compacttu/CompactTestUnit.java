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
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageJournal;
import io.github.eternalbits.disk.DiskImageObserver;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disks.DiskImages;

public class CompactTestUnit {

	public static void main(String[] args) throws Exception {
		
		DiskImageJournal.scanDirectory(Static.getWorkingDirectory());
		
		if (args.length == 0)
			return;
		
		if ("MD5VDI".equals(args[0])) {
			System.exit(md5Check(args[1], args[2], new long[]{392}, new int[]{32}));
		}
		
		if ("MD5VMDK".equals(args[0])) {
			System.exit(md5Check(args[1], args[2], new long[]{512}, new int[]{2048}));
		}
		
		if ("MD5VHD".equals(args[0])) {
			long df = (new File(args[1]).length() - 1) / 512 * 512;
			System.exit(md5Check(args[1], args[2], new long[]{24,24+df}, new int[]{60,60}));
		}
		
		if ("COPY".equals(args[0])) {
			long timer = System.currentTimeMillis();
			Stop sync = new Stop(System.getProperty("stop"));
			Copy test = new Copy(args[1], args[2], sync);
			Thread t = new Thread(test);
			t.start();
			if (sync.progress != 1F) {
				synchronized (sync) {
					sync.wait();
				}
				if (t.isAlive()) {
					System.out.println("INTERRUPT");
					t.interrupt();
				}
			}
			if (t.isAlive())
				t.join();
			exit(String.format("%s » %s", args[1], args[2]), timer);
		}
		
		if ("INLINE".equals(args[0])) {
			long timer = System.currentTimeMillis();
			Stop sync = new Stop(System.getProperty("stop"));
			Inline test = new Inline(args[1], args[2], sync);
			Thread t = new Thread(test);
			t.start();
			if (sync.progress != 1F) {
				synchronized (sync) {
					sync.wait();
				}
				if (t.isAlive()) {
					System.out.println("INTERRUPT");
					t.interrupt();
				}
			}
			if (t.isAlive())
				t.join();
			exit(String.format("%s %s » %s", args[0], args[2], args[1]), timer);
		}
		
		System.exit(0);
	}
	
	static void exit(String msg, long timer) {
		timer = System.currentTimeMillis() - timer;
		System.out.println(String.format("%s: %d", msg, timer));
		System.exit(0);
	}
	
	static int md5Check(String path, String sum, long[] ignore, int[] length) throws Exception {
		try (RandomAccessFile in = new RandomAccessFile(new File(path), "r")) {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] buffer = new byte[1048576];
			for (int read, c = 0; (read = in.read(buffer)) != -1; c++) {
				for (int i = 0; i < ignore.length; i++) {
					int count = (int)(ignore[i] / buffer.length);
					int start = (int)(ignore[i] % buffer.length);
					if (c == count)
						Arrays.fill(buffer, start, start + length[i], (byte)0);
				}
				md.update(buffer, 0, read);
			}
			String out = bytesToHex(md.digest());
			System.out.println(path);
			System.out.println(String.format("Expected MD5: %s", sum));
			System.out.println(String.format("Computed MD5: %s", out));
			return out.equals(sum)? 0: 1;
		}
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

}

class Stop {
	final float progress;
	final boolean exit;
	public Stop (String stop) {
		progress = stop == null? 1F: new Random().nextInt(Integer.parseInt(stop.replaceAll("[^\\d]", ""))%100)/100F;
		exit = stop != null && stop.toLowerCase().contains("x");
	}
}

class Copy implements Runnable, DiskImageObserver {
	private final String args_1_;
	private final String args_2_;
	private final Stop sync;
	public Copy(String source, String clone, Stop sync) {
		this.sync = sync;
		args_1_ = source;
		args_2_ = clone;
	}
	@Override
	public void run() {
		try (DiskImage source = DiskImages.open(new File(args_1_), "rw")) {
			try (DiskImage clone = DiskImages.create(source.getType(), new File(args_2_), source.getDiskSize())) {
				System.out.println(source.toString());
				source.optimize(DiskImage.FREE_BLOCKS_UNUSED);
				clone.addObserver(this, false);
				clone.copy(source);
				clone.removeObserver(this);
			}
			catch (Exception e) { e.printStackTrace(); }
		}
		catch (Exception e) { e.printStackTrace(); }
	}
	@Override
	public void update(DiskImage image, Object arg) {
		if (arg instanceof DiskImageProgress) {
			if (((DiskImageProgress) arg).value >= sync.progress) {
				if (sync.exit) { System.out.println("EXIT"); System.exit(-1); return; }
				synchronized(sync) { sync.notifyAll(); }
			}
		}
	}
}

class Inline implements Runnable, DiskImageObserver {
	private final String args_1_;
	private final String args_2_;
	private final Stop sync;
	public Inline(String source, String clone, Stop sync) {
		this.sync = sync;
		args_1_ = source;
		args_2_ = clone;
	}
	@Override
	public void run() {
		try (DiskImage image = DiskImages.open(new File(args_1_), "rw")) {
			System.out.println(image.toString());
			image.optimize(options(args_2_));
			image.addObserver(this, false);
			image.compact();
			image.removeObserver(this);
		}
		catch (Exception e) { e.printStackTrace(); }
	}
	static int options(String arg) {
		int opt = 0;
		if (arg.indexOf('N') != -1) opt |= DiskImage.FREE_BLOCKS_UNUSED;
		if (arg.indexOf('Z') != -1) opt |= DiskImage.FREE_BLOCKS_ZEROED;
		return opt;
	}
	@Override
	public void update(DiskImage image, Object arg) {
		if (arg instanceof DiskImageProgress) {
			if (((DiskImageProgress) arg).value >= sync.progress) {
				if (sync.exit) { System.out.println("EXIT"); System.exit(-1); return; }
				synchronized(sync) { sync.notifyAll(); }
			}
		}
	}
}
