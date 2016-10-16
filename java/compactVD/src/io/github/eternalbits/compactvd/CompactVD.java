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

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import io.github.eternalbits.compactvd.gui.FrontEnd;
import io.github.eternalbits.disk.DiskImageJournal;

/**
 * Virtual Disk Compact and Copy command line interface. Without parameters
 * the GUI takes precedence, use {@code -help} to show the command list.
 * <p>
 */
public class CompactVD {

	private static String jar = new java.io.File(CompactVD.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath()).getName();

	public static void main(String[] args) throws Exception {
		
		DiskImageJournal.scanDirectory(Static.getWorkingDirectory());
		
		if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					new FrontEnd();
				}
			});
			return;
		}
		
		if (args.length == 0) {
			showHelp();
			return;
		}
	}

	private static void showHelp() {
		System.out.println(jar);
	}

	public static void dump(Object obj) { dump(obj, ""); }
	private static void dump(Object obj, String in) {
		for (Field fld: obj.getClass().getDeclaredFields()) {
			try {
				if (!fld.getType().isAssignableFrom(List.class)) {
					System.out.println(in+fld.getName()+": "+fld.get(obj));
				} else {
					int i = 0;
					for (Object item: (List<?>)fld.get(obj)) {
						System.out.println(in+fld.getName()+"["+i+"]");
						dump(item, in+"    ");
						i++;
					}
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}
}
