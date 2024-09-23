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

import java.awt.Frame;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

import io.github.eternalbits.compactvd.Static;

/**
 * FrontEnd window geometry and current settings are saved
 *  under the user home folder.
 * <p>
 */
class Settings implements Serializable {
	private static final long serialVersionUID = -7557970546910169483L;
	
	// Window geometry
	Rectangle windowRect = new Rectangle(20, 20, 20, 20);
	int windowState = Frame.NORMAL;
	int splitLocation = 120;
	
	// File dialog
	String lastDirectory = null;
	String selectedLanguage = Locale.getDefault().getLanguage();
	String selectedCountry = Locale.getDefault().getCountry();
	boolean filterImageFiles = true;
	
	// Open options
	boolean visibleCompactCopy = true;
	boolean findBlocksNotInUse = true;
	boolean findBlocksZeroed = false;
	
	// Compact options
	boolean compactBlocksNotInUse = true;
	boolean compactBlocksZeroed = false;
	
	// Copy options
	boolean ignoreBlocksNotInUse = true;
	boolean ignoreBlocksZeroed = false;
	
	/**
	 * Writes the FrontEnd geometry and current settings.
	 */
	final void write() {
		try (	FileOutputStream cfg = new FileOutputStream(getConfigFile());
				ObjectOutputStream out = new ObjectOutputStream(cfg);
				) {
			
			out.writeObject(this);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns a Settings object with FrontEnd geometry and current settings.
	 *
	 * @return	A Settings object.
	 */
	static final Settings read() {
		try (	FileInputStream cfg = new FileInputStream(getConfigFile());
				ObjectInputStream in = new ObjectInputStream(cfg);
				) {
			
			return (Settings) in.readObject();
			
		} catch (FileNotFoundException e) {
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new Settings();
	}

	/**
	 * Returns a File to save the application user preferences. The location is operating
	 *  system dependent, with {@code <user.home>/.config/eternalbits/compactvd.ser}
	 *  as default. 
	 * @return	The settings file path.
	 */
	static File getConfigFile() {
		return new File(Static.getWorkingDirectory(), "compactvd.ser");
	}
	
}
