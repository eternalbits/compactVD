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

import com.apple.eawt.AboutHandler;
import com.apple.eawt.Application;
import com.apple.eawt.PreferencesHandler;
import com.apple.eawt.QuitStrategy;
import com.apple.eawt.AppEvent.AboutEvent;
import com.apple.eawt.AppEvent.PreferencesEvent;

/**
 * A minimal adapter to delegate the default Mac Menu requests to
 *  the Front End object.
 * <p>
 */
class MacAdapter {

	private final FrontEnd mainFrame;
	
	/**
	 * Delegates the default Mac Menu requests to the {@code FrontEnd}.
	 */
	MacAdapter(FrontEnd frontEnd) {
		mainFrame = frontEnd;
		
		Application macApp = Application.getApplication();
		
		macApp.setDockIconImage(mainFrame.getIconImage());
		macApp.setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
		
		macApp.setAboutHandler(new AboutHandler() {
			@Override
			public void handleAbout(AboutEvent arg0) {
				mainFrame.showAboutDialog();
			}
		});
		
		macApp.setPreferencesHandler(new PreferencesHandler() {
			@Override
			public void handlePreferences(PreferencesEvent arg0) {
				mainFrame.editSettings();
			}
		});
		
	}

}
