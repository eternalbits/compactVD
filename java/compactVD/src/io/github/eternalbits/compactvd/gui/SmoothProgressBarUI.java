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

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ProgressBarUI;
import javax.swing.plaf.UIResource;

/**
 * WindowsProgressBarUI has a bulky resolution. Without text it is painted in chunks,
 *  even when the progress area is continuous. With text it is painted, at most, 
 *  in 100 distinct steps.
 * <p>
 * If the {@code (maximum - minimum)} range of the progress bar is greater or equal
 *  to the progress bar width, the {@code SmoothProgressBarUI} has a pixel resolution.
 *  To install this UI to an instance {@code progress} of the class {@code JProgressBar} 
 *  do the following:
 * <pre>
 *  progress.setUI(new SmoothProgressBarUI());
 *  progress.setMaximum(progress.getWidth());
 * </pre>
 * The current implementation has these limitations:<ul>
 * <li>No indeterminate state.</li>
 * <li>No vertical orientation.</li>
 * <li>No baseline.</li>
 * </ul>
 */
class SmoothProgressBarUI extends ProgressBarUI implements ChangeListener {
	private static final RenderingHints TEXT_ANTIALIASING = new RenderingHints
			(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

	protected JProgressBar progressBar;
	protected int lastDone = -1;

	@Override
	public void installUI(JComponent c) {
		progressBar = (JProgressBar)c;
		
		LookAndFeel.installProperty(progressBar, "opaque", Boolean.TRUE);
		LookAndFeel.installColorsAndFont(progressBar,
				"ProgressBar.background",
				"ProgressBar.foreground",
				"ProgressBar.font");
		if (c.getBorder() == null || c.getBorder() instanceof UIResource) {
			Border b = UIManager.getBorder("ProgressBar.border"); // remove inside border, if any:
			c.setBorder(b instanceof CompoundBorder? ((CompoundBorder)b).getOutsideBorder(): b);
		}
		
		progressBar.addChangeListener(this);
	}

	@Override
	public void uninstallUI(JComponent c) {
		progressBar.removeChangeListener(this);
		LookAndFeel.uninstallBorder(progressBar);
		progressBar = null;
	}

	@Override
	public Dimension getPreferredSize(JComponent c) {
		Dimension size = new Dimension(146, 14); // as observed
		FontMetrics	fm = progressBar.getFontMetrics(progressBar.getFont());
		int h = fm.getHeight() + fm.getDescent();
		if (h > size.height)
			size.height = h;
		Insets border = progressBar.getInsets();
		size.height += border.top + border.bottom;
		size.width += border.left + border.right;
		return size;
	}
	
	@Override
	public Dimension getMinimumSize(JComponent c) {
		Dimension pref = getPreferredSize(progressBar);
		pref.width = 10; // as observed
		return pref;
	}
	
	@Override
	public Dimension getMaximumSize(JComponent c) {
		Dimension pref = getPreferredSize(progressBar);
		pref.width = Short.MAX_VALUE; // as observed
		return pref;
	}

	@Override
	public void paint(Graphics g, JComponent c) {
		Insets border = progressBar.getInsets();
		int height = progressBar.getHeight() - border.top - border.bottom;
		int width = progressBar.getWidth() - border.left - border.right;
		if (!(g instanceof Graphics2D && height > 0 && width > 0)) {
			return;
		}
		
		BoundedRangeModel model = progressBar.getModel();
		float range = model.getMaximum() - model.getMinimum();
		int done = width;
		if (range != 0) {
			float progress = model.getValue() - model.getMinimum();
			done = Math.round(progress / range * width);
		}
		
		Graphics2D g2 = (Graphics2D)g;
		g2.setColor(progressBar.getForeground());
		int left = progressBar.getComponentOrientation().isLeftToRight()? 
				border.left: border.left + width - done;
		Rectangle bar = new Rectangle(left, border.top, done, height);
		g2.fill(bar);
		
		if (progressBar.isStringPainted()) {
			String string = progressBar.getString();
			g2.addRenderingHints(TEXT_ANTIALIASING);
			g2.setFont(progressBar.getFont());
			FontMetrics fm = g2.getFontMetrics();
			
			float y = border.top + (height + fm.getAscent() - fm.getDescent()) / 2F;
			float x = border.left + (width - fm.stringWidth(string)) / 2F;
			g2.setColor(progressBar.getForeground());
			g2.drawString(string, x, y);
			g2.setClip(bar);
			g2.setColor(progressBar.getBackground());
			g2.drawString(string, x, y);
			g2.setClip(null);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Insets border = progressBar.getInsets();
		int width = progressBar.getWidth() - border.left - border.right;
		if (!(width > 0)) {
			return;
		}
		
		BoundedRangeModel model = progressBar.getModel();
		float range = model.getMaximum() - model.getMinimum();
		int done = width;
		if (range != 0) {
			float progress = model.getValue() - model.getMinimum();
			done = Math.round(progress / range * width);
		}
		
		if (done != lastDone) {
			progressBar.repaint();
			lastDone = done;
		}
	}
}
