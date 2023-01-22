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
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

class SettingsDialog extends JDialog {
	private static final long serialVersionUID = -6334838729023774629L;

	private final JComboBox<Language> selectedString;
	
	private final JCheckBox filterImageFiles;
	private final JCheckBox visibleCompactCopy;	
	private final JCheckBox findBlocksNotInUse;
	private final JCheckBox findBlocksZeroed;
	
	private final JCheckBox compactBlocksNotInUse;
	private final JCheckBox compactBlocksZeroed;
	
	private final JCheckBox ignoreBlocksNotInUse;
	private final JCheckBox ignoreBlocksZeroed;
	
	static private final Language[] languages = new Language[] {
			new Language("zh", "CN", "中国人"), 
			new Language("en", "US", "English"), 
			new Language("fr", "FR", "Français"), 
			new Language("de", "DE", "Deutsch"), 
			new Language("it", "IT", "Italiano"), 
			new Language("ja", "JP", "日本語"), 
			new Language("pt", "PT", "Português"), 
			new Language("ru", "RU", "Русский"), 
			new Language("es", "ES", "Español"), 
			new Language("tr", "TR", "Türkçe"), 
		};
	
	static private class Language {
		private String language;
		private String country;
		private String string;
		public Language(String language, String country, String string) {
			this.language = language;
			this.country = country;
			this.string = string;
		}
		public String toString() {
			return string;
		}
	}
	
	static Locale Languages(String language, String country) {
		for (int i = 0; i < languages.length; i++) {
			if (languages[i].language.equals(language) && languages[i].country.equals(country)) {
				return new Locale(language, country);
			}
		}
		return new Locale("", "");
	}
	
	static int LanguageCode(String language, String country) {
		for (int i = 0; i < languages.length; i++) {
			if (languages[i].language.equals(language) && languages[i].country.equals(country)) {
				return i;
			}
		}
		return 1; // en-US
	}
	
	SettingsDialog(final FrontEnd app) {
		super(app, app.res.getString("set_settings"), ModalityType.APPLICATION_MODAL);
		
		getContentPane().setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		
		Border boxBorder = BorderFactory.createEtchedBorder();
		
		Box language = Box.createVerticalBox();
		language.setBorder(new TitledBorder(boxBorder, app.res.getString("set_language")));
		language.add(selectedString = new JComboBox<Language>(languages));
		selectedString.setSelectedIndex(LanguageCode(app.settings.selectedLanguage, app.settings.selectedCountry));
		
		Box open = Box.createVerticalBox();
		open.setBorder(new TitledBorder(boxBorder, app.res.getString("open")));
		open.add(filterImageFiles 	= new JCheckBox(app.res.getString("set_filter_image"), app.settings.filterImageFiles));
		open.add(visibleCompactCopy = new JCheckBox(app.res.getString("set_same_as_open"), app.settings.visibleCompactCopy));
		open.add(findBlocksNotInUse = new JCheckBox(app.res.getString("set_find_unused"), app.settings.findBlocksNotInUse));
		open.add(findBlocksZeroed 	= new JCheckBox(app.res.getString("set_find_zeroed"), app.settings.findBlocksZeroed));
		
		Box compact = Box.createVerticalBox();
		compact.setBorder(new TitledBorder(boxBorder, app.res.getString("compact")));
		compact.add(compactBlocksNotInUse = new JCheckBox(app.res.getString("set_compact_unused"), app.settings.compactBlocksNotInUse));
		compact.add(compactBlocksZeroed   = new JCheckBox(app.res.getString("set_compact_zeroed"), app.settings.compactBlocksZeroed));
		
		Box copy = Box.createVerticalBox();
		copy.setBorder(new TitledBorder(boxBorder, app.res.getString("copy")));
		copy.add(ignoreBlocksNotInUse = new JCheckBox(app.res.getString("set_ignore_unused"), app.settings.ignoreBlocksNotInUse));
		copy.add(ignoreBlocksZeroed   = new JCheckBox(app.res.getString("set_ignore_zeroed"), app.settings.ignoreBlocksZeroed));
		
		Box cmd = Box.createHorizontalBox();
		JButton apply = new JButton(app.res.getString("apply_text"));
		JButton cancel = new JButton(app.res.getString("cancel_text"));
		cmd.add(new JLabel(app.res.getString("author")));
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
				app.settings.selectedLanguage 		= ((Language)selectedString.getSelectedItem()).language;
				app.settings.selectedCountry 		= ((Language)selectedString.getSelectedItem()).country;
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
		getContentPane().add(language, gbc);
		gbc.insets = new Insets(0,p,p,p);
		getContentPane().add(open, gbc);
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
