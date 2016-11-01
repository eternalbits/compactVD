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

package io.github.eternalbits.linux.disk.lvm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.eternalbits.compactvd.Static;

class LvmPhysicalVolume {
	String name 	= null;
	String id 		= null;
	long devSize 	= -1;		// Volume or partition size in 512 byte sectors
	long peStart 	= -1;		// Start of data area in 512 byte sectors
	int peCount 	= -1;		// Number of extents in this device
}

class LvmLogicalVolume {
	String name 	= null;
	String id 		= null;
	List<LvmLogicalSegment> segments = new ArrayList<LvmLogicalSegment>();
}

class LvmLogicalSegment {
	int startExtent = -1;		// First logical extent in this segment
	int extentCount = -1;		// Count of extents in this segment
	String type = null;			// Simple volumes are "striped" with 1 stripe in 1 segment
	List<LvmPhysicalStripe> stripes = new ArrayList<LvmPhysicalStripe>();
}

class LvmPhysicalStripe {
	String pvName 	= null;		// Physical volume name
	long pvStart 	= -1;		// First physical extent of this stripe
}

class LvmVolumeGroupMetadata {
	
	final LvmSimpleDiskLayout layout;
	
	String name 	= null;
	String id 		= null;
	int extentSize	= -1;		// Extent size in 512 byte sectors
	
	final List<LvmPhysicalVolume> devices = new ArrayList<LvmPhysicalVolume>();
	final List<LvmLogicalVolume> volumes = new ArrayList<LvmLogicalVolume>();
	
	final HashMap<String, LvmPhysicalVolume> pvMap = new HashMap<String, LvmPhysicalVolume>();
	
	private final MetadataParser parser;
	
	LvmVolumeGroupMetadata(LvmSimpleDiskLayout lvm, ByteBuffer in) {
		this.layout		= lvm;
		
		parser = new MetadataParser(Static.getString(in, in.remaining(), 
				StandardCharsets.US_ASCII));
		parseVolumeGroup();
	}
	
	private void parseVolumeGroup() {
		this.name = parser.getNext();
		if ("{".equals(parser.getNext())) {
			while (parser.hasNext()) {
				switch (parser.getNext()) {
				case "id":
					this.id = getStringValue();
					break;
				case "extent_size":
					this.extentSize = getIntValue();
					break;
				case "physical_volumes":
					if ("{".equals(parser.getNext())) {
						while (parser.hasNext() && !"}".equals(parser.next()))
							devices.add(parsePhysicalVolume());
						parser.getNext(); //}
					}
					break;
				case "logical_volumes":
					if ("{".equals(parser.getNext())) {
						while (parser.hasNext() && !"}".equals(parser.next()))
							volumes.add(parseLogicalVolume());
						parser.getNext(); //}
					}
					break;
				case "}":
					return;
				default:
					parseIgnore();
				}
			}
		}
	}
	
	private LvmPhysicalVolume parsePhysicalVolume() {
		LvmPhysicalVolume pv = new LvmPhysicalVolume();
		pv.name = parser.getNext();
		if ("{".equals(parser.getNext())) {
			while (parser.hasNext()) {
				switch (parser.getNext()) {
				case "id":
					pv.id = getStringValue();
					break;
				case "dev_size":
					pv.devSize = getLongValue();
					break;
				case "pe_start":
					pv.peStart = getLongValue();
					break;
				case "pe_count":
					pv.peCount = getIntValue();
					break;
				case "}":
					pvMap.put(pv.name, pv);
					return pv;
				default:
					parseIgnore();
				}
			}
		}
		pvMap.put(pv.name, pv);
		return pv; //exception
	}
	
	private LvmLogicalVolume parseLogicalVolume() {
		LvmLogicalVolume lv = new LvmLogicalVolume();
		lv.name = parser.getNext();
		if ("{".equals(parser.getNext())) {
			while (parser.hasNext()) {
				switch (parser.getNext()) {
				case "id":
					lv.id = getStringValue();
					break;
				case "}":
					return lv;
				default:
					if (!"{".equals(parser.next())) {
						parseIgnore();
					} else { // All sub-sections are assumed to be segments
						lv.segments.add(parseLogicalSegment());
					}
				}
			}
		}
		return lv; // exception
	}
	
	private LvmLogicalSegment parseLogicalSegment() {
		LvmLogicalSegment ls = new LvmLogicalSegment();
		if ("{".equals(parser.getNext())) {
			while (parser.hasNext()) {
				switch (parser.getNext()) {
				case "start_extent":
					ls.startExtent = getIntValue();
					break;
				case "extent_count":
					ls.extentCount = getIntValue();
					break;
				case "type":
					ls.type = getStringValue();
					break;
				case "stripes":
					if ("=".equals(parser.getNext()) && "[".equals(parser.getNext())) {
						while (parser.hasNext()) {
							ls.stripes.add(parsePhysicalStripe());
							if ("]".equals(parser.getNext())) {
								break;
							}
						}
					}
					break;
				case "}":
					return ls;
				default:
					parseIgnore();
				}
			}
		}
		return ls; // exception
	}
	
	private LvmPhysicalStripe parsePhysicalStripe() {
		LvmPhysicalStripe ps = new LvmPhysicalStripe();
		ps.pvName = parser.getNext();
		if (",".equals(parser.getNext())) {
			if (parser.hasNext()) {
				ps.pvStart = Long.parseLong(parser.getNext());
			}
		}
		return ps;
	}
	
	private void parseIgnore() {
		String next = parser.getNext();
		if ("=".equals(next)) {
			if ("[".equals(parser.next())) {
				while (parser.hasNext() && !"]".equals(parser.getNext()));
			} else {
				parser.getNext();
			}
			return;
		}
		if ("{".equals(next)) {
			while (parser.hasNext()) {
				switch (parser.next()) {
				case "=":
				case "{":
					parseIgnore();
					break;
				case "}":
					parser.getNext();
					return;
				default:
					parser.getNext();
				}
			}
		}
	}
	
	private String getStringValue() {
		if ("=".equals(parser.getNext()))
			return parser.getNext();
		return null;
	}
	
	private long getLongValue() {
		if ("=".equals(parser.getNext()))
			return Long.parseLong(parser.getNext());
		return -1;
	}
	
	private int getIntValue() {
		long v = getLongValue();
		return (int)v == v? (int)v: -1;
	}
	
	private class MetadataParser {
		private final CharSequence md;
		private int p = 0;

		MetadataParser(String md) {
			this.md = md + "\0";
			skipSpace();
		}
		
		boolean hasNext() {
			return md.charAt(p) != '\0';
		}
		
		String getNext() {
			String n = parse();
			skipSpace();
			return n;
		}
		
		String next() {
			int p = this.p;
			String n = parse();
			this.p = p;
			return n;
		}
		
		private void skipSpace() {
			while (isWhitespace()) p++;
			while (md.charAt(p) == '#') {
				while (md.charAt(p) != '\n' && md.charAt(p) != '\0') p++;
				while (isWhitespace()) p++;
			}
		}
		
		private String parse() {
			// https://git.fedorahosted.org/cgit/lvm2.git/tree/libdm/libdm-config.c#_get_token
			if (md.charAt(p) == '\0') {
				return null;
			}
			int s = p;
			switch (md.charAt(p)) {
			case '{':
			case '}':
			case '[':
			case ']':
			case ',':
			case '=':
				p++;
				return md.subSequence(s, p).toString();
			case '"':
				p++;
				s++;
				StringBuilder dquote = new StringBuilder();
				while (md.charAt(p) != '\0' && md.charAt(p) != '"') {
					if (md.charAt(p-1) == '\\' && md.charAt(p) != '\0') p++;
					dquote.append(md.charAt(p++));
				}
				if (md.charAt(p) != '\0') p++;
				return dquote.toString();
			case '\'':
				p++;
				s++;
				while (md.charAt(p) != '\0' && md.charAt(p) != '\'') p++;
				String squote = md.subSequence(s, p).toString();
				if (md.charAt(p) != '\0') p++;
				return squote;
			default:
				p++;
				while (md.charAt(p) != '\0' && !isWhitespace()
						&& md.charAt(p) != '#' && md.charAt(p) != '='
						&& md.charAt(p) != '{' && md.charAt(p) != '}'
						&& md.charAt(p) != ',' && md.charAt(p) != ']')
					p++;
				return md.subSequence(s, p).toString();
			}
		}
		
		private boolean isWhitespace() {
			return md.charAt(p) == ' ' || md.charAt(p) >= '\t' && md.charAt(p) <= '\r';
		}
	}

}
