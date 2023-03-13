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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disks.DiskImages;

/**
 * Virtual Disk Compact and Copy graphical user interface.
 * A list with known disk images, a visual description of the selected 
 * image, and three command buttons: Open, Compact and Copy.
 * <p>
 */
public class FrontEnd extends JFrame {
	private static final long serialVersionUID = 4457476192315737735L;
	
	private static final String DEFAULT_FILE_FILTER = ".+\\.(?i:vdi|vmdk|vhd|raw)";
	private static final String WINDOWS_FILE_FILTER = "*.vdi;*.vmdk;*.vhd;*.raw";
	
	/* The window: a tool bar with command buttons, a list with known images,
	 *  and a main area with the selected image or a help/about dialog. 
	 */
	private JButton openButton = null;
	private JButton compactButton = null;
	private JButton copyButton = null;
	private JButton settingsButton = null;
	private JButton aboutButton = null;
	
	private Icon compactIcon = null;
	private Icon copyIcon = null;
	private Icon stopIcon = null;
	
	final DefaultListModel<ListItem> listData = new DefaultListModel<ListItem>();
	final JList<ListItem> list = new JList<ListItem>(listData);
	
	private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	
	private final CardLayout deck = new CardLayout();
	private final JPanel main = new JPanel(deck);
	private final JEditorPane about = new JEditorPane();
	private final JToolBar tb = new JToolBar();
	private final ImageCanvas view;
	
	private final JProgressBar progress = new JProgressBar();
	
	private final FileDialog fileDialog = new FileDialog(this);
	
	private transient int savedListIndex = -1;
	private transient final boolean isWindows;
	private transient final boolean isMac;
	
	final Settings settings;
	ResourceBundle res;
	
	/**
	 * The Virtual Disk Compact and Copy graphical user interface.
	 */
	public FrontEnd() {
		setIconImage(new ImageIcon(getResource("drive.png")).getImage());
		String osName = System.getProperty("os.name").toLowerCase();
		isWindows = osName.indexOf("windows") >= 0;
		isMac = osName.indexOf("mac") >= 0;
		if (isMac) 
			new MacAdapter(this);
		
		settings = Settings.read();
		onLocaleChange();
		
		view = new ImageCanvas(this);
		
		setupFrame();
		
		JComponent about = setupAboutDialog();
		deck.addLayoutComponent(about, "about");
		deck.addLayoutComponent(view, "view");
		main.add(about);
		main.add(view);

		setupProgressBar();
		getContentPane().add(progress, BorderLayout.PAGE_END);

		fileDialog.setDirectory(settings.lastDirectory);
		setupFileDialog(settings.filterImageFiles);
		setupFileDrop();
		
		split.setLeftComponent(setupFileList());
		split.setRightComponent(main);
		
		adjustBounds();
		setLocation(settings.windowRect.getLocation());
		setPreferredSize(settings.windowRect.getSize());
		setMinimumSize(new Dimension(580, 420));
		setExtendedState(settings.windowState);
		
		main.setMinimumSize(new Dimension(420, 0));
		split.setDividerLocation(settings.splitLocation);
		getContentPane().add(split, BorderLayout.CENTER);
		
		// Display the window
		pack();
		setVisible(true);
		onSelectListItem();
	}

	/**
	 *  Sets the window according to the Language Interface option that appears in Settings.
	 */
	private void onLocaleChange() {
		res = ResourceBundle.getBundle("res.bundle", 
				SettingsDialog.Languages(settings.selectedLanguage, settings.selectedCountry));
		UIManager.put("OptionPane.yesButtonText", res.getString("yes_text"));
		UIManager.put("OptionPane.noButtonText", res.getString("no_text"));
		UIManager.put("OptionPane.okButtonText", res.getString("ok_text"));
		
		setTitle(res.getString("title"));
		getContentPane().add(setupToolBar(tb), BorderLayout.PAGE_START);
		setComponentPopupMenu(main);
		try {
			about.setContentType("text/html; charset=utf-8");
			about.setPage(getResource(res.getString("about_html")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 *  Sets the Frame behavior.
	 */
	private void setupFrame() {
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				if (canCloseNow()) {
					settings.windowState = getExtendedState();
					setExtendedState(NORMAL);
					saveSettings();
					System.exit(0);
				}
			}
		});
		
		addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				progress.setMaximum(progress.getWidth());
			}
		});

	}

	/**
	 * The progress bar is set to always show a description. The Windows progress bar UI
	 *  has a bulky resolution and is replaced by a smoother user interface. 
	 */
	private void setupProgressBar() {
		if (isWindows)
			progress.setUI(new SmoothProgressBarUI());
		progress.setStringPainted(true);
		progress.setString("");
	}
	
	/**
	 * Sets the File List content and behavior and returns an abstract swing
	 *  {@code JComponent} that can be an enclosing Container, the content 
	 *  scroll bars or the content component itself.
	 *  
	 * @return	The component to be added to the Window hierarchy.
	 */
	private Component setupFileList() {
		
		list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting())
					onSelectListItem();
			}
		});
		
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, 
					int index, boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, 
						index, isSelected, cellHasFocus);
				if (c instanceof JLabel) {
					JLabel j = (JLabel) c;
					j.setBorder(new CompoundBorder(j.getBorder(), new EmptyBorder(4, 4, 4, 0)));
				}
				return c;
			}
		});
		
		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_DELETE && list.getSelectedIndex() != -1) {
					close(list.getSelectedIndex());
				}
			}
		});
		
		JScrollPane jsp = new JScrollPane(list);
		jsp.setMinimumSize(new Dimension(0, 0));
		return jsp;
	}
	
	/**
	 * Sets a file transfer handle for the whole window, to act as an Open alternative.
	 */
	private void setupFileDrop() {
		
		setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = 4476434757526293417L;

			@Override
			public boolean canImport(TransferHandler.TransferSupport support) {
				if (support.isDrop() 
					&& support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					if ((LINK & support.getSourceDropActions()) == LINK) {
						support.setDropAction(LINK);
					}
					return true;
				}
				return false;
			}

			@Override
			public boolean importData(TransferHandler.TransferSupport support) {
				if (canImport(support)) try {
					for (Object item: (List<?>)support.getTransferable()
						.getTransferData(DataFlavor.javaFileListFlavor)) {
						if (item instanceof File) {
							addToList((File)item);
						}
					}
					return true;
				}
				catch (UnsupportedFlavorException | IOException e) {
					e.printStackTrace();
				}
				return false;
			}
		});
	}
	
	/**
	 * The open file to make a translation
	 */
	void addToList(File file) {
		if (file.isFile()) {
			if (isWindows)
				file = WindowShortcut.linkFile(file);
			
			for (int i = 0, s = listData.getSize(); i < s; i++) {
				if (listData.get(i).getFile().equals(file)) {
					refresh(i);
					return;
				}
			}
			
			try (DiskImage image = DiskImages.open(file, "r")) {

				listData.addElement(new ListItem(this, file, image.getView()));
				list.setSelectedIndex(listData.getSize() - 1);
				
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, 
						Static.wordWrap(Static.simpleString(e)), 
						res.getString("error"), JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	/**
	 * The refresh results on a file
	 */
	private void refresh(int i) {
		if (listData.get(i).stopRun("refresh the image from the list")) {
			File file = listData.get(i).getFile();
			
			try (DiskImage image = DiskImages.open(file, "r")) {						
				listData.set(i, new ListItem(this, file, image.getView()));
				list.setSelectedIndex(i);
				updateDiskImage();
				
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, 
						Static.wordWrap(Static.simpleString(e)), 
						res.getString("error"), JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	/**
	 * The close results on a file
	 */
	private void close(int i) {
		if (listData.get(i).stopRun("close the image from the list")) {
			listData.remove(i);
			if (listData.size() > 0) {
				if (i == listData.size())
					i--;
				list.setSelectedIndex(i);
			}
		}
	}
	
	/**
	 * Place the PopupMenu with its dependencies under the JComponent source
	 */
	private void setComponentPopupMenu(JComponent source) {
		final JMenuItem refresh = new JMenuItem(res.getString("refresh"));
		final JMenuItem close = new JMenuItem(res.getString("close"));
		final JPopupMenu popup = new JPopupMenu();
		popup.add(refresh);
		popup.add(close);
		
		source.setComponentPopupMenu(popup);

		refresh.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (list.getSelectedIndex() != -1) {
					refresh(list.getSelectedIndex());
				}
			}
		});
		
		close.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (list.getSelectedIndex() != -1) {
					close(list.getSelectedIndex());
				}
			}
		});

	}
	
	/**
	 * Returns {@code true} if there are no compact or copy operations running.
	 * Otherwise prompts the user for authorization to cancel the task and returns
	 *  {@code false} if the user denies it. If the user allows to cancel the task 
	 *  is terminated in a graceful mode before returning {@code true}. 
	 * 
	 * @return	true if is safe to close the application, false otherwise.
	 */
	boolean canCloseNow() {
		for (int i = 0, s = listData.getSize(); i < s; i++) {
			if (!listData.get(i).stopRun(res.getString("close_now"))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Saves the window geometry and current settings.
	 */
	void saveSettings() {
		settings.windowRect = getBounds();
		settings.splitLocation = split.getDividerLocation();
		settings.lastDirectory = fileDialog.getDirectory();
		settings.write();
	}

	int getOptimizeOptions(int task) {
		switch (task) {
		case DiskImageProgress.OPTIMIZE:
			return (settings.findBlocksNotInUse? DiskImage.FREE_BLOCKS_UNUSED: 0) |
					(settings.findBlocksZeroed? DiskImage.FREE_BLOCKS_ZEROED: 0);
		case DiskImageProgress.COMPACT:
			return (settings.compactBlocksNotInUse? DiskImage.FREE_BLOCKS_UNUSED: 0) |
					(settings.compactBlocksZeroed? DiskImage.FREE_BLOCKS_ZEROED: 0);
		case DiskImageProgress.COPY:
			return (settings.ignoreBlocksNotInUse? DiskImage.FREE_BLOCKS_UNUSED: 0) |
					(settings.ignoreBlocksZeroed? DiskImage.FREE_BLOCKS_ZEROED: 0);
		default:
			return 0;
		}
	}
	
	void onSelectListItem() {
		boolean sel = list.getSelectedIndex() != -1;
		deck.show(main, sel? "view": "about");
		if (!sel)
			about.requestFocusInWindow();
		else view.repaint();
		setProgressValue();
		updateDiskImage();
		updateToolbar();
	}

	void updateToolbar() {
		int index = list.getSelectedIndex();
		boolean sel = index != -1;
		int run = sel? listData.get(index).getActiveTask(): DiskImageProgress.NO_TASK;
		compactButton.setIcon(run == DiskImageProgress.COMPACT? stopIcon: compactIcon);
		copyButton.setIcon(run == DiskImageProgress.COPY? stopIcon: copyIcon);
		compactButton.setEnabled(sel && run != DiskImageProgress.COPY);
		copyButton.setEnabled(sel && run != DiskImageProgress.COMPACT);
		aboutButton.setEnabled(sel);
	}

	void updateListItem(ListItem item) {
		if (list.getSelectedIndex() != -1 && listData.get(list.getSelectedIndex()) == item) {
			view.repaint();
		}
	}
	
	void setProgressValue(ListItem item) {
		if (list.getSelectedIndex() != -1 && listData.get(list.getSelectedIndex()) == item) {
			setProgressValue();
		}
	}

	void setProgressValue() {
		if (list.getSelectedIndex() == -1) {
			progress.setString("");
			progress.setValue(0);
			return;
		}
		ListItem item = listData.get(list.getSelectedIndex());
		progress.setString(item.getProgressString());
		progress.setValue((int) (item.getProgressValue() * progress.getMaximum()));
	}
	
	/**
	 * First, the screen that mostly intersects the window is selected. 
	 * Then, if necessary, the window is moved and resized to entirely
	 *  fit in the screen.
	 */
	private void adjustBounds() {
		Rectangle screen = null, frame = settings.windowRect;
		long fit = 0;
		for (GraphicsDevice gd: GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			GraphicsConfiguration gc = gd.getDefaultConfiguration();
			Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
			Rectangle b = gc.getBounds();
			b.x += i.left;
			b.y += i.top;
			b.width -= i.right + i.left;
			b.height -= i.bottom + i.top;
			Rectangle f = frame.intersection(b);
			if (screen == null || (long)f.width * f.height > fit) {
				fit = (long)f.width * f.height;
				screen = b;
			}
		}
		if (screen != null) {
			frame.x = Math.max(frame.x, screen.x);
			frame.y = Math.max(frame.y, screen.y);
			frame.width = Math.min(frame.width, screen.width);
			frame.height = Math.min(frame.height, screen.height);
			frame.x = Math.min(frame.x, screen.x + screen.width - frame.width);
			frame.y = Math.min(frame.y, screen.y + screen.height - frame.height);
			settings.windowRect = frame;
		}
	}

	private JToolBar setupToolBar(JToolBar tb) {
		tb.invalidate();
		tb.removeAll();
		pack();
		
		tb.add(openButton = new JToolButton(res.getString("open"), "open.png", 
				res.getString("open_msg") + ": " + res.getString("open_txt")));
		openButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openDiskImage();
			}
		});
		
		tb.add(compactButton = new JToolButton(res.getString("compact"), "compact.png", 
				res.getString("compact_msg") + ": " + res.getString("compact_txt")));
		compactButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (compactButton.getIcon() == compactIcon)
					compactDiskImage();
				else cancelTaskInProgress(res.getString("compact_now"));
			}
		});
		
		tb.add(copyButton = new JToolButton(res.getString("copy"), "copy.png", 
				res.getString("copy_msg") + ": " + res.getString("copy_txt")));
		copyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (copyButton.getIcon() == copyIcon)
					copyDiskImage();
				else cancelTaskInProgress(res.getString("copy_now"));
			}
		});
		
		tb.add(settingsButton = new JToolButton(res.getString("settings"), "settings.png", 
				res.getString("settings_msg") + ": " + res.getString("settings_txt")));
		settingsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				editSettings();
			}
		});
		
		tb.add(aboutButton = new JToolButton(res.getString("about"), "about.png", 
				res.getString("about_msg") + "" + ""));
		aboutButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showAboutDialog();
			}
		});
		
		compactIcon = compactButton.getIcon();
		copyIcon = copyButton.getIcon();
		stopIcon = new ImageIcon(getResource("stop.png"));
		
		tb.setFloatable(false);
		tb.setFocusable(false);
		return tb;
	}

	private class JToolButton extends JButton {
		private static final long serialVersionUID = -6228916287454044833L;

		public JToolButton(String text, String icon, String tip) {
			super(new ImageIcon(getResource(icon)));
			setHorizontalTextPosition(SwingConstants.CENTER);
			setVerticalTextPosition(SwingConstants.BOTTOM);
			setRequestFocusEnabled(false);
			setFocusable(false);
			setToolTipText(tip);
			setText(text);
		}
		
	}

	private void openDiskImage() {
		fileDialog.setFile(isWindows && settings.filterImageFiles? WINDOWS_FILE_FILTER: null);
		fileDialog.setTitle(res.getString("open_msg"));
		fileDialog.setMode(FileDialog.LOAD);
		fileDialog.setMultipleMode(true);
		fileDialog.setVisible(true);
		for (File file: fileDialog.getFiles()) {
			addToList(file);
		}
	}
	
	void updateDiskImage() {
		int s = list.getSelectedIndex();
		if (s != -1) {
			listData.get(s).updateView(getOptimizeOptions(DiskImageProgress.OPTIMIZE));
		}
	}

	void compactDiskImage() {
		int s = list.getSelectedIndex();
		if (s != -1) {
			listData.get(s).compact();
		}
	}
	
	void copyDiskImage() {
		int s = list.getSelectedIndex();
		if (s != -1) {
			String source = listData.get(s).getFile().getName();
			fileDialog.setFile(String.format(res.getString("copy_dup"), source));
			fileDialog.setTitle(res.getString("copy_msg"));
			fileDialog.setMode(FileDialog.SAVE);
			fileDialog.setMultipleMode(false);
			fileDialog.setVisible(true);
			for (File file: fileDialog.getFiles()) {
				if (file.compareTo(listData.get(s).getFile()) == 0) {
					JOptionPane.showMessageDialog(this, 
							String.format(res.getString("error_old_image"), file.getName()), 
							res.getString("error"), JOptionPane.ERROR_MESSAGE);
				} else {
					if (Static.getExtension(file.getName()).length() == 0)
						file = new File(file.getPath()+"."+Static.getExtension(source));
					String type = Static.getExtension(file.getName()).toUpperCase();
					if (!ListItem.IMAGE_TYPE.contains(type)) {
						JOptionPane.showMessageDialog(this, 
								String.format(res.getString("error_image_type"), type), 
								res.getString("error"), JOptionPane.ERROR_MESSAGE);
						return;
					}
					listData.get(s).copyTo(file, type);
					break;
				}
			}
		}
	}

	void cancelTaskInProgress(String action) {
		int s = list.getSelectedIndex();
		if (s != -1) {
			listData.get(s).stopRun(action);
		}
	}

	void editSettings() {
		new SettingsDialog(this);
		onLocaleChange();
		
		setupFileDialog(settings.filterImageFiles);
		updateDiskImage();
		
		onSelectListItem();
	}

	void showAboutDialog() {
		savedListIndex = list.getSelectedIndex();
		list.clearSelection();
	}

	/**
	 * Sets the About content and behavior and returns an abstract swing
	 *  {@code JComponent} that can be an enclosing Container, the content 
	 *  scroll bars or the content component itself.
	 *  
	 * @return	The component to be added to the Window hierarchy.
	 */
	private JComponent setupAboutDialog() {
		about.setBorder(BorderFactory.createEmptyBorder());
		about.setTransferHandler(null);
		about.setEditable(false);
		
		about.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) try {
					if (e.getURL().getProtocol().matches("file|jar")) {
						about.setPage(e.getURL());
					} else
					if (Desktop.isDesktopSupported()) {
						Desktop.getDesktop().browse(e.getURL().toURI());
					}
				} catch (IOException | URISyntaxException t) {
					t.printStackTrace();
				}
			}
		});
		
		about.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE && savedListIndex != -1) {
					list.setSelectedIndex(savedListIndex);
					list.requestFocusInWindow();
				}
			}
		});
		
		return new JScrollPane(about);
	}

	/**
	 * Enables or disables the file name filter. This doesn't work on Windows,
	 *  as a workaround the files are filtered with a wildcard pattern in the
	 *  open dialog with {@link FileDialog#setFile(String)}.
	 *  
	 * @param filter {@code true} to enable the file name filter, {@code false} to disable
	 */
	private void setupFileDialog(boolean filter) {
		if (filter)
			fileDialog.setFilenameFilter(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.matches(DEFAULT_FILE_FILTER);
				}
			});
		else fileDialog.setFilenameFilter(null);
	}

	/**
	 * Returns the URL of the resource {@code name} that is in the same folder
	 *  of the class used to create the FronEnd.
	 * 
	 * @param name	The name of the resource.
	 * @return	The resource URL, or {@code null} if the resource does not exist.
	 */
	static URL getResource(String name) {
		String path = FrontEnd.class.getPackage().getName().replace('.', '/');
		return FrontEnd.class.getClassLoader().getResource(path + '/' + name);
	}

}
