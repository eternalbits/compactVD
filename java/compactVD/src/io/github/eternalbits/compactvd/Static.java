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

package io.github.eternalbits.compactvd;

import java.awt.Graphics;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * Utility static functions for CompactVD.
 */
public class Static {

	public static long[] getLongs(ByteBuffer in, int length) {
		long[] buffer = new long[length];
		for (int i = 0; i < buffer.length; i++)
			buffer[i] = in.getLong();
		return buffer;
	}

	public static int[] getInts(ByteBuffer in, int length) {
		int[] buffer = new int[length];
		for (int i = 0; i < buffer.length; i++)
			buffer[i] = in.getInt();
		return buffer;
	}

	public static short[] getShorts(ByteBuffer in, int length) {
		short[] buffer = new short[length];
		for (int i = 0; i < buffer.length; i++)
			buffer[i] = in.getShort();
		return buffer;
	}

	public static byte[] getBytes(ByteBuffer in, int length) {
		byte[] buffer = new byte[length];
		in.get(buffer);
		return buffer;
	}

	/**
	 * Creates a byte array and fills it with bytes transferred from the byte buffer 
	 * 	{@code in}, starting at the current position.
	 * <p>
	 * Up to {@code length} bytes are transferred. The trailing null bytes are ignored, 
	 * 	and the length of the returned byte array is set according. The byte buffer
	 *  position is always incremented with {@code length}.
	 * 
	 * @param in		The source byte buffer
	 * @param length	The maximum number of bytes to transfer.
	 * @return			A byte array with length up to {@code length}.
	 */
	public static byte[] getReservedBytes(ByteBuffer in, int length) {
		int n = in.position() + length;
		for (int p = in.position(); length > 0; length--)
			if (in.get(p + length - 1) != 0)
				break;
		byte[] buffer = new byte[length];
		in.get(buffer);
		in.position(n);
		return buffer;
	}

	public static byte[] getBytes(String text, int length, Charset charset) {
		byte[] buffer = new byte[length];
		byte[] temp = text.getBytes(charset);
		System.arraycopy(temp, 0, buffer, 0, Math.min(temp.length, length));
		return buffer;
	}
	
	public static long ceilDiv(long num, long div) {
		return (num + div - 1) / div;
	}
	
	public static boolean isPower2(long num) {
		return num > 0 && (num & (num - 1)) == 0;
	}

	/**
	 * Decodes a {@code String} from the byte buffer {@code in} using at most
	 *  {@code length} bytes. If the resulting string is null terminated then
	 *  the first null and the remaining characters are ignored. The byte
	 *  buffer position is always incremented with {@code length}.
	 * 
	 * @param in		The source byte buffer.
	 * @param length	The maximum number of bytes to decode.
	 * @param charset	The charset to decode the bytes.
	 * @return			The decoded string.
	 */
	public static String getString(ByteBuffer in, int length, Charset charset) {
		byte[] buffer = new byte[length];
		in.get(buffer);
		String text = new String(buffer, charset);
		int i = text.indexOf(0);
		return i == -1? text: text.substring(0, i);
	}

	public static String removeExtension(String path) {
		return new File(path).getName().replaceFirst("([^.]+)[.][^.]+$", "$1");
	}
	
	public static String getExtension(String path) {
		String name = new File(path).getName();
		String part = name.replaceFirst("([^.]+)[.][^.]+$", "$1");
		return part.equals(name)? "": name.substring(part.length()+1);
	}
	
	public static String getCompressedPath(String path, Graphics g, int width) {
		String s = Pattern.quote(File.separator), p = "[^" + s + "]*";
		String r = "(" + p+s + p+s + ")(?:…" +s+ "|)" +p+ "(" +s+ ")";
		while (g.getFontMetrics().stringWidth(path) > width) {
			String comp = path.replaceFirst(r, "$1…$2");
			if (comp.equals(path)) break;
			path = comp;
		}
		return path;
	}
	
	/**
	 * Returns a simple description of exception {@code e}, like {@link Throwable#toString()}
	 *  with exception simpleName instead of name.
	 * @param e	The exception object.
	 * @return	A simple representation of {@code e}.
	 */
	public static String simpleString(Exception e) {
		return e.getClass().getSimpleName()+": "+e.getLocalizedMessage();
	}
	
	/**
	 * Returns a {@code String} with {@code text} inside a fixed width html body, to word wrap
	 *  long lines. Intended for {@code JOptionPane.show*Dialog} messages.
	 * 
	 * @param text	The text to word wrap.
	 * @return	A fixed width representation of the text.
	 */
	public static String wordWrap(String text) {
		return "<html><body style='width:260px;'>" + text
				.replaceAll("&", "&amp;")
				.replaceAll("<", "&lt;")
				.replaceAll(">", "&gt;")
				.replaceAll("\n", "<br>")
				+ "</body></html>";
	}
	
	private static File workDir = null;
	
	/**
	 * Returns the application working directory. The location is operating system dependent.
	 *  
	 * @return	The application working directory. It is created if does not exist.
	 */
	public static File getWorkingDirectory() {
		return workDir;
	}
	
	static { // Get it once at first Static invocation
		String path = System.getenv("AppData"); // windows
		if (path == null || !new File(path).isDirectory()) {
			String home = System.getProperty("user.home");
			if (home != null && new File(home).isDirectory()) {
				path = home + "/Library/Application Support"; // mac
				if (!new File(path).isDirectory()) {
					path = home + "/.config"; // unix
				}
			}
		}
		if (path == null || !new File(path).isDirectory()) {
			path = System.getProperty("java.io.tmpdir"); // other
		}
		if (path != null && new File(path).isDirectory()) {
			File dir = new File(path, "eternalbits");
			if (!dir.exists()) dir.mkdirs();
			if (dir.isDirectory()) {
				workDir = dir;
			}
		}
	}
}
