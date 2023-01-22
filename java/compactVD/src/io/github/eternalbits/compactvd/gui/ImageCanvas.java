/*
 * Copyright 2016 Rui Baptista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eternalbits.compactvd.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskFileSystemView;
import io.github.eternalbits.disk.DiskImageView;

class ImageCanvas extends JPanel {
	private static final long serialVersionUID = 7519610864760532240L;
	
	private final static float FONT_SIZE = 11F;

	private final static Color BACK_COLOR = new Color(236, 236, 236);
	private final static Color WHITE_COLOR = new Color(255, 255, 255);

	private final static Color BLACK_TEXT = new Color(0, 0, 0);
	private final static Color LIGHT_TEXT = new Color(118, 118, 118);
	private final static Color DARK_TEXT = new Color(36, 36, 36);
	
	private final static Color LINE_COLOR = new Color(192, 192, 192, 128);
	private final static Color GRAY_COLOR = new Color(192, 192, 192);
	
	private final static Color MAPPED_COLOR = new Color(36, 186, 249);
	private final static Color NOT_FS_COLOR = new Color(192, 192, 192);
	private final static Color NO_USE_COLOR = new Color(255, 214, 1);
	private final static Color ZEROED_COLOR = new Color(118, 255, 71);
	
	private final static Stroke STROKE_1 = new BasicStroke(1);
	private final static Stroke STROKE_2 = new BasicStroke(2);

	private static final int MAX_HEIGHT = 360;
	private static final int MAX_WIDTH = 600;
	
	private static final int TITLE_HEIGHT = 64;
	private static final int DISKS_HEIGHT = 48;
	private static final int STATS_HEIGHT = 72;
	private static final int ALL_HEIGHT = TITLE_HEIGHT + DISKS_HEIGHT + STATS_HEIGHT;

	private static final RenderingHints TEXT_ANTIALIAS_ON = new RenderingHints
			(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	private static final RenderingHints ANTIALIAS_OFF = new RenderingHints
			(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	private static final RenderingHints ANTIALIAS_ON = new RenderingHints
			(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	
	private static final HashMap<String, Image> IMAGE_ICON = new HashMap<String, Image>();
	static {
		for (String type: ListItem.IMAGE_TYPE) IMAGE_ICON.put(type, 
				new ImageIcon(FrontEnd.getResource(type.toLowerCase()+".png")).getImage());
	}
	
	private static final Color[] COLORS = new Color[] {MAPPED_COLOR, NOT_FS_COLOR, NO_USE_COLOR, ZEROED_COLOR, WHITE_COLOR};
	private static String[] LABELS;
	
	private final FrontEnd app;
	
	ImageCanvas(FrontEnd frontEnd) {
		app = frontEnd;
		setDoubleBuffered(false);
		setBackground(BACK_COLOR);
		setOpaque(true);
		updateUI();
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (app.list.getSelectedIndex() != -1) {
			LABELS = new String[] {app.res.getString("color_system"), app.res.getString("color_no_system"), 
					app.res.getString("color_not_in_use"), app.res.getString("color_zeros"), 
					app.res.getString("color_free")};
			Graphics2D g2 = (Graphics2D) g;
			g2.addRenderingHints(TEXT_ANTIALIAS_ON);
			g2.addRenderingHints(ANTIALIAS_OFF);
			g2.setStroke(STROKE_1);
			
			g2.setFont(g2.getFont().deriveFont(Font.PLAIN, FONT_SIZE));
			
			DiskImageView view = app.listData.get(app.list.getSelectedIndex()).getView();
			
			// Limit and center the container rectangle
			Rectangle out = getBounds();
			if (out.width > MAX_WIDTH) {
				out.x += (out.width - MAX_WIDTH) / 2;
				out.width = MAX_WIDTH;
			}
			if (out.height > MAX_HEIGHT) {
				out.y += (out.height - MAX_HEIGHT) / 2;
				out.height = MAX_HEIGHT;
			}
			
			// Compute a rectangle for each of the 3 components
			int inset = 30, x = out.x + inset, width = out.width - 2 * inset, y;
			int between = (out.height - 2 * inset - ALL_HEIGHT) / 4;
			g2.setColor(LINE_COLOR);
			Rectangle title = new Rectangle(x, y = out.y + inset, width, TITLE_HEIGHT);
			g2.drawLine(x, y = y + TITLE_HEIGHT + between, x + width, y);
			Rectangle disks = new Rectangle(x, y = y + between, width, DISKS_HEIGHT);
			g2.drawLine(x, y = y + DISKS_HEIGHT + between, x + width, y);
			Rectangle stats = new Rectangle(x, y = y + between, width, STATS_HEIGHT);
			
			// Paint the components
			paintTitle(g2, view, title);
			paintFiles(g2, view, disks);
			paintStats(g2, view, stats);
		}
	}
	
	/* First component: icon, name and path of the disk image
	 */
	
	private void paintTitle(Graphics2D g2, DiskImageView view, Rectangle out) {
		g2.drawImage(IMAGE_ICON.get(view.imageType), out.x, out.y, 64, 64, null);
		g2.setColor(DARK_TEXT);
		g2.setFont(g2.getFont().deriveFont(Font.BOLD, FONT_SIZE * 2.2F));
		g2.drawString(Static.removeExtension(view.filePath), out.x + 80, out.y + 32);
		g2.setColor(LIGHT_TEXT);
		g2.setFont(g2.getFont().deriveFont(Font.PLAIN, FONT_SIZE));
		g2.drawString(Static.getCompressedPath(view.filePath, g2, out.width - 80), out.x + 80, out.y + 52);
	}
	
	/* Second component: a view of each significant file system 
	 */
	
	private void paintFiles(Graphics2D g2, DiskImageView view, Rectangle out) {
		List<DiskFileSystemView> show_fileSystems = new ArrayList<DiskFileSystemView>();
		int gap = 5;
		long show_diskLength = 0;
		int nFS = view.fileSystems.size();
		float avail = out.width - (nFS - 1) * gap;
		for (DiskFileSystemView dfv: view.fileSystems) {
		// Ignore partitions that are too small to be properly shown
			if (avail * dfv.length / view.diskLength > 15) {
				show_diskLength += dfv.length;
				show_fileSystems.add(dfv);
			}
		}
		if ((nFS = show_fileSystems.size()) > 0) {
			
			avail = out.width - (nFS - 1) * gap;
			float fx = out.x;
			for (DiskFileSystemView dfv: show_fileSystems) {
				float fw = avail * dfv.length / show_diskLength;
				paintFileSystem(g2, dfv, fx, out.y, fw, 24);
				fx += gap + fw;
			}
			
			g2.addRenderingHints(ANTIALIAS_ON);
			g2.setFont(g2.getFont().deriveFont(Font.BOLD));
			float[] width = new float[LABELS.length];
			int a = g2.getFontMetrics().getAscent();
			int tab = a + a / 2;
			int top = out.y + 32;
			for (int i = 0; i < LABELS.length; i++)
				width[i] = tab + g2.getFontMetrics().stringWidth(LABELS[i]);
			int[] start = intSpacing(width, out.width);
			for (int i = 0; i < LABELS.length; i++) {
				g2.setColor(COLORS[i]);
				g2.fillOval(out.x + start[i], top, a, a);
				g2.setColor(BLACK_TEXT);
				g2.drawString(LABELS[i], out.x + start[i] + tab, top + a -1);
			}
			g2.setFont(g2.getFont().deriveFont(Font.PLAIN));
			g2.addRenderingHints(ANTIALIAS_OFF);
		}
	}
	
	private int[] intSpacing(float[] value, float avail) {
		int[] start = new int[value.length];
		for (float v: value)
			avail -= v;
		float done = 0, gap = avail / Math.max(1, value.length - 1);
		for (int i = 0; i < start.length; i++) {
			start[i] = Math.round(done);
			done += value[i] + gap;
		}
		return start;
	}
	
	private void paintFileSystem(Graphics2D g2, DiskFileSystemView view, 
			float x, float y, float width, float height) {
		g2.addRenderingHints(ANTIALIAS_ON);
		g2.setClip(new RoundRectangle2D.Float(x, y, width, height, 8, 8));
		g2.setColor(WHITE_COLOR);
		g2.fill(new Rectangle2D.Float(x, y, width, height));
		if (view.blocksCount > 0) {
			float c = view.blocksCount;
			float m = view.blocksMapped * width / c;
			float n = view.blocksUnused * width / c;
			float z = view.blocksZeroed * width / c;
			g2.setColor(view.isFileSystem? MAPPED_COLOR: NOT_FS_COLOR);
			g2.fill(new Rectangle2D.Float(x, y, m, height));
			g2.setColor(NO_USE_COLOR);
			g2.fill(new Rectangle2D.Float(x+m, y, n, height));
			g2.setColor(ZEROED_COLOR);
			g2.fill(new Rectangle2D.Float(x+m+n, y, z, height));
		}
		g2.setClip(null);
		g2.setColor(LINE_COLOR);
		g2.draw(new RoundRectangle2D.Float(x, y, width, height, 8, 8));
		g2.addRenderingHints(ANTIALIAS_OFF);
	}
	
	/* Third component: global disk image statistical data
	 */
	
	private void paintStats(Graphics2D g2, DiskImageView view, Rectangle out) {
		g2.addRenderingHints(ANTIALIAS_ON);
		g2.setColor(WHITE_COLOR);
		g2.fillRoundRect(out.x, out.y, out.width, out.height, 6, 6);
		g2.setColor(GRAY_COLOR);
		g2.drawRoundRect(out.x, out.y, out.width, out.height, 6, 6);
		g2.addRenderingHints(ANTIALIAS_OFF);
		g2.setStroke(STROKE_2);
		g2.setColor(LINE_COLOR);
		int hw = out.width / 2;
		g2.drawLine(out.x + hw, out.y + 8, out.x + hw, out.y + STATS_HEIGHT - 7);
		g2.setStroke(STROKE_1);
		g2.setColor(GRAY_COLOR);
		int s0 = out.x + 7, d0 = out.x + hw - 8;
		int s1 = out.x + hw + 7, d1 = out.x + out.width - 7;
		int y0 = out.y, y1 = y0 + 24, y2 = y1 + 24;
		g2.drawLine(s0, y1, d0, y1);
		g2.drawLine(s1, y1, d1, y1);
		g2.drawLine(s0, y2, d0, y2);
		g2.drawLine(s1, y2, d1, y2);
		g2.setColor(BLACK_TEXT);
		int b = (24 + g2.getFontMetrics().getAscent()) / 2;
		int m = intPercent(view.blocksMapped, view.blocksCount);
		int n = intPercent(view.blocksUnused, view.blocksCount);
		int z = intPercent(view.blocksZeroed, view.blocksCount);
		drawStatStrings(g2, app.res.getString("show_current"), s0, humanSize(view.imageLength), d0, y0 + b);
		drawStatStrings(g2, app.res.getString("show_optimized"), s1, humanSize(view.optimizedLength), d1, y0 + b);
		drawStatStrings(g2, app.res.getString("show_system"), s0, stringPercent(m, m), d0, y1 + b);
		drawStatStrings(g2, app.res.getString("show_full_disk"), s1, humanSize(view.diskLength), d1, y1 + b);
		drawStatStrings(g2, app.res.getString("show_unused"), s0, stringPercent(view.blocksUnused, n), d0, y2 + b);
		drawStatStrings(g2, app.res.getString("show_zeroed"), s1, stringPercent(view.blocksZeroed, z), d1, y2 + b);
	}
	
	private int intPercent(Integer part, Integer all) {
		return Math.round(part==null || all==null || all== 0? 0: part * 100F / all);
	}
	private String stringPercent(Integer part, int value) {
		return part==null? "?": String.format("%d%%", value);
	}
	private String humanSize(long size) {
		double mb = size / 1024.0 / 1024.0;
		double gb = mb / 1024.0;
		double tb = gb / 1024.0;
		if (tb >= 1)
			return String.format("%.2f TB", tb);
		if (gb >= 1)
			return String.format("%.2f GB", gb);
		return String.format("%.2f MB", mb);
	}
	
	private void drawStatStrings(Graphics2D g2, String key, int xk, String value, int xv, int y) {
		g2.drawString(value, xv - g2.getFontMetrics().stringWidth(value), y);
		g2.drawString(key, xk, y);
	}
	
}
