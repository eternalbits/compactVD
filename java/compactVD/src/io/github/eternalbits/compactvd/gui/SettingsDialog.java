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

package io.github.eternalbits.compactvd.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

class SettingsDialog extends JDialog {
	private static final long serialVersionUID = -6334838729023774629L;

	private final JCheckBox filterImageFiles;
	private final JCheckBox visibleCompactCopy;	
	private final JCheckBox findBlocksNotInUse;
	private final JCheckBox findBlocksZeroed;
	
	private final JCheckBox compactBlocksNotInUse;
	private final JCheckBox compactBlocksZeroed;
	
	private final JCheckBox ignoreBlocksNotInUse;
	private final JCheckBox ignoreBlocksZeroed;
	
	SettingsDialog(final FrontEnd app) {
		super(app, "CompactVD Settings", ModalityType.APPLICATION_MODAL);
		
		getContentPane().setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		
		Border boxBorder = BorderFactory.createEtchedBorder();
		
		Box open = Box.createVerticalBox();
		open.setBorder(new TitledBorder(boxBorder, "Open"));
		open.add(filterImageFiles 	= new JCheckBox("Filter image files in open dialog", app.settings.filterImageFiles));
		open.add(visibleCompactCopy = new JCheckBox("Compact and Copy settings are the same as Open", app.settings.visibleCompactCopy));
		open.add(findBlocksNotInUse = new JCheckBox("Search blocks not in use by System and Files", app.settings.findBlocksNotInUse));
		open.add(findBlocksZeroed 	= new JCheckBox("Search blocks completely filled with zeros", app.settings.findBlocksZeroed));
		
		Box compact = Box.createVerticalBox();
		compact.setBorder(new TitledBorder(boxBorder, "Compact"));
		compact.add(compactBlocksNotInUse = new JCheckBox("Drop blocks not in use by System and Files", app.settings.compactBlocksNotInUse));
		compact.add(compactBlocksZeroed   = new JCheckBox("Drop blocks completely filled with zeros", app.settings.compactBlocksZeroed));
		
		Box copy = Box.createVerticalBox();
		copy.setBorder(new TitledBorder(boxBorder, "Copy"));
		copy.add(ignoreBlocksNotInUse = new JCheckBox("Ignore blocks not in use by System and Files", app.settings.ignoreBlocksNotInUse));
		copy.add(ignoreBlocksZeroed   = new JCheckBox("Ignore blocks completely filled with zeros", app.settings.ignoreBlocksZeroed));
		
		Box cmd = Box.createHorizontalBox();
		JButton apply = new JButton("Apply");
		JButton cancel = new JButton("Cancel");
		cmd.add(Box.createHorizontalGlue());
		cmd.add(apply);
		cmd.add(cancel);

		visibleCompactCopy.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				compact.setVisible(!visibleCompactCopy.isSelected());
				copy.setVisible(!visibleCompactCopy.isSelected());
				pack();
				setLocationRelativeTo(app);
			}
		});
		
		apply.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				app.settings.filterImageFiles 		= filterImageFiles.isSelected();
				app.settings.visibleCompactCopy 	= visibleCompactCopy.isSelected();
				app.settings.findBlocksNotInUse 	= findBlocksNotInUse.isSelected();
				app.settings.findBlocksZeroed 		= findBlocksZeroed.isSelected();
				if (visibleCompactCopy.isSelected()) {
					app.settings.compactBlocksNotInUse 	= findBlocksNotInUse.isSelected();
					app.settings.compactBlocksZeroed 	= findBlocksZeroed.isSelected();
					app.settings.ignoreBlocksNotInUse 	= findBlocksNotInUse.isSelected();
					app.settings.ignoreBlocksZeroed 	= findBlocksZeroed.isSelected();
				} else {
					app.settings.compactBlocksNotInUse 	= compactBlocksNotInUse.isSelected();
					app.settings.compactBlocksZeroed 	= compactBlocksZeroed.isSelected();
					app.settings.ignoreBlocksNotInUse 	= ignoreBlocksNotInUse.isSelected();
					app.settings.ignoreBlocksZeroed 	= ignoreBlocksZeroed.isSelected();
				}
				dispose();
			}
		});

		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		
		int p = 12;
		gbc.insets = new Insets(p,p,p,p);
		getContentPane().add(open, gbc);
		gbc.insets = new Insets(0,p,p,p);
		getContentPane().add(compact, gbc);
		getContentPane().add(copy, gbc);
		getContentPane().add(cmd, gbc);
		
		compact.setVisible(!visibleCompactCopy.isSelected());
		copy.setVisible(!visibleCompactCopy.isSelected());
		pack();
		setLocationRelativeTo(app);
		
		setResizable(false);
		setVisible(true);
	}
}
