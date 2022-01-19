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

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import io.github.eternalbits.compactvd.CompactVD;
import io.github.eternalbits.compactvd.Static;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageObserver;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disk.DiskImageView;
import io.github.eternalbits.disks.DiskImages;

class ListItem {
	static final List<String> IMAGE_TYPE = Arrays.asList(new String[] {"VDI", "VMDK", "VHD", "IMG", "RAW"});
	
	private final static String CONFIRM_COMPACT = 
			"This operation may change the disk image, do you want to proceed?";
	private final static String CANCEL_COMPACT = 
			"The requested action will interrupt a compact operation in progress.\n\n"
			+ "If you %s now, the image beeing compacted may be in an intermediate state "
			+ "that doesn't allow it to be mounted in a virtual machine. You must "
			+ "complete the task as soon as you can.\n\n"
			+ "Do you want to interrupt the compact operation?\n\n";
	private final static String CANCEL_COPY = 
			"The requested action will interrupt a copy operation in progress.\n\n"
			+ "If you %s now, the image beeing created is not complete and will be deleted.\n\n"
			+ "Do you want to interrupt the copy operation?\n\n";
	
	private final static String TASK_ABNORMAL = "The operation ended abnormally.";
	private final static String TASK_CANCELED = "The operation was canceled.";
	private final static String TASK_COMPLETE = "The operation is complete.";
	private final static String IMAGE_CHANGED = "The disk image '%s' was changed.";
	private final static String IMAGE_CREATED = "The disk image '%s' was created in '%s' directory.";
	private final static String IMAGE_NOT_CHANGED = "The disk image '%s' was NOT changed.";
	private final static String IMAGE_NOT_CREATED = "No disk image was created.";
	
	/** Synchronizer to ensure that only one task is running for this item,
	 *  and to wait for the task to end on cancel. */
	private final Object serializer = new Object();
	
	private final FrontEnd app;
	private final File file;
	
	private DiskImageProgress progress;
	private DiskImageWorker worker;
	private DiskImageView view;
	
	private long lastViewModifiedTime;
	private int lastViewOptions;
	
	ListItem(FrontEnd frontEnd, File file, DiskImageView view) {
		this.app  = frontEnd;
		this.file = file;
		this.view = view;
		progress = new DiskImageProgress();
		worker = new DiskImageWorker(DiskImageProgress.OPTIMIZE);
		worker.execute();
	}
	
	@Override
	public String toString() {
		return file.getName();
	}
	
	DiskImageView getView() {
		return view;
	}

	File getFile() {
		return file;
	}

	int getActiveTask() {
		return worker.activeTask;
	}

	String getProgressString() {
		return progress.done? "": worker.progressString;
	}
	
	float getProgressValue() {
		return progress.done? 0F: progress.value;
	}

	/**
	 * If there is a {@code OPTIMIZE} task in progress, cancels the task an returns
	 *  {@code true}. If other kind of task is running, prompts the user for authorization 
	 *  to cancel the task and returns {@code false} if the user denies it. If the user 
	 *  allows to cancel, the task is terminated and this method waits for 
	 *  termination acknowledgement before returning {@code true}.
	 * 
	 * @param action	a context phrase with the action that started the request
	 * 					to end the current operation, like "close the window". 
	 * @return	true if is all right to proceed, false otherwise.
	 */
	boolean stopRun(String action) {
		switch (worker.activeTask) {
		case DiskImageProgress.OPTIMIZE:
			cancelWorker();
			break;
		case DiskImageProgress.COMPACT:
			if (!confirm(String.format(CANCEL_COMPACT, action), "Abort compact?"))
				return false;
			cancelWorker();
			break;
		case DiskImageProgress.COPY:
			if (!confirm(String.format(CANCEL_COPY, action), "Abort copy?"))
				return false;
			cancelWorker();
			break;
		}
		return true;
	}
	
	private boolean confirm(String message, String title) {
		return JOptionPane.showConfirmDialog(app, 
				Static.wordWrap(message), title, 
				JOptionPane.YES_NO_OPTION, 
				JOptionPane.WARNING_MESSAGE) 
				== JOptionPane.YES_OPTION;
	}
	
	private void cancelWorker() {
		worker.cancel(true);
		synchronized (serializer) { // wait for end of task
			worker.activeTask = DiskImageProgress.NO_TASK;
		}
	}
	
	void updateView(int optimizeOptions) {
		if (lastViewModifiedTime != file.lastModified() || lastViewOptions != optimizeOptions) {
			if (worker.activeTask == DiskImageProgress.OPTIMIZE) {
				cancelWorker();
			}
			if (worker.activeTask == DiskImageProgress.NO_TASK) {
				worker = new DiskImageWorker(DiskImageProgress.OPTIMIZE);
				worker.execute();
			}
		}
	}

	void compact() {
		if (JOptionPane.showConfirmDialog(app, 
				Static.wordWrap(CONFIRM_COMPACT), "Compact", 
				JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE) 
				!= JOptionPane.YES_OPTION)
			return;
		if (worker.activeTask == DiskImageProgress.OPTIMIZE) {
			cancelWorker();
		}
		if (worker.activeTask == DiskImageProgress.NO_TASK) {
			worker = new DiskImageWorker(DiskImageProgress.COMPACT);
			worker.execute();
			app.updateToolbar();
		}
	}

	void copyTo(File copy, String format) {
		if (worker.activeTask == DiskImageProgress.OPTIMIZE) {
			cancelWorker();
		}
		if (worker.activeTask == DiskImageProgress.NO_TASK) {
			worker = new DiskImageWorker(copy, format);
			worker.execute();
			app.updateToolbar();
		}
	}

	class DiskImageWorker extends SwingWorker<Void, Void> implements DiskImageObserver {
	// SwingWorker is only used as a thread pool, other functionalities are not used
	// Something like Executors.newFixedThreadPool(10) // TODO consider ExecutorService

		private final int task;
		private String outputType;
		private File outputFile;

		private int activeTask;
		private String progressString = "Searching space not in use or zero filled";
		private String exceptionCause = null;

		DiskImageWorker(int task) {
			this.task = task;
			activeTask = task;
			// Set last view control to avoid false updateViews
			lastViewOptions = app.getOptimizeOptions(task);
			lastViewModifiedTime = file.lastModified();
		}
		
		DiskImageWorker(File copy, String format) {
			this(DiskImageProgress.COPY);
			outputType = format;
			outputFile = copy;
		}

		@Override
		protected Void doInBackground() {
			Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			synchronized (serializer) {
				switch (task) {
				
				case DiskImageProgress.OPTIMIZE:
					try (DiskImage image = DiskImages.open(view.imageType, file, "r")) {
						image.addObserver(this, true);
						image.optimize(lastViewOptions);
						image.removeObserver(this);
						update(image.getView());
						CompactVD.dump(view);
					}
					catch (Exception e) { setExceptionCause(e); showFinalDialog(null, "Error"); }				
					break;
					
				case DiskImageProgress.COMPACT:
					try (RandomAccessFile check = new RandomAccessFile(file, "r")) { // still exists?
						try (DiskImage image = DiskImages.open(view.imageType, file, "rw")) {
							FileLock fileLock = image.tryLock();
							image.addObserver(this, false);
							image.optimize(lastViewOptions);
							image.removeObserver(this);
							if (!isCancelled()) {
								progressString = "Compacting "+file.getName();
								image.addObserver(this, true);
								image.compact();
								image.removeObserver(this);
							}
							update(image.getView());
							fileLock.release();
						}
					}
					catch (Exception e) { setExceptionCause(e); }
					
					showFinalDialog(String.format(file.lastModified() != lastViewModifiedTime? 
							IMAGE_CHANGED: IMAGE_NOT_CHANGED, file.getName()), "Compact");
					break;
					
				case DiskImageProgress.COPY:
					File copy = null;
					try (RandomAccessFile check = new RandomAccessFile(file, "r")) { // still exists?
						// If writable, source is open in write mode for an exclusive file lock
						String mode = file.canWrite()? "rw": "r";
						try (DiskImage image = DiskImages.open(view.imageType, file, mode)) {
							try (DiskImage clone = DiskImages.create(outputType, outputFile, image.getDiskSize())) {
								copy = outputFile; // copy open by DiskImage
								FileLock source = null;
								if (mode.equals("rw"))
									source = image.tryLock();
								image.addObserver(this, false);
								image.optimize(lastViewOptions);
								image.removeObserver(this);
								update(image.getView());
								if (!isCancelled()) {
									progressString = "Copying "+file.getName()+" to "+outputFile.getName();
									FileLock fileLock = clone.tryLock();
									clone.addObserver(this, false);
									clone.copy(image);
									clone.removeObserver(this);
									fileLock.release();
									if (!isCancelled())
										app.addToList(outputFile);
								}
								if (source != null)
									source.release();
							}
						}
					}
					catch (Exception e) { setExceptionCause(e); }
					if (isCancelled() && copy != null && copy.isFile())
						copy.delete();
					
					showFinalDialog(copy != null && copy.isFile()? String.format(IMAGE_CREATED, 
							copy.getName(), copy.getParent()): IMAGE_NOT_CREATED, "Copy");
					break;
					
				}
				if (isCancelled()) 
					lastViewOptions = -1; // invalidate the view
				lastViewModifiedTime = file.lastModified();
				
				activeTask = DiskImageProgress.NO_TASK;
				progressString = "";
			}
			if (task == DiskImageProgress.COMPACT 
					&& lastViewOptions != app.getOptimizeOptions(DiskImageProgress.OPTIMIZE))
				app.addToList(file);
			app.updateToolbar();
			return null;
		}
		
		private void setExceptionCause(final Exception e) {
			exceptionCause = Static.simpleString(e);
			e.printStackTrace();
			this.cancel(false);
		}

		private void showFinalDialog(final String result, final String title) {
			String how = exceptionCause != null? TASK_ABNORMAL: isCancelled()? TASK_CANCELED: TASK_COMPLETE;
			final int type = exceptionCause != null? JOptionPane.ERROR_MESSAGE: JOptionPane.INFORMATION_MESSAGE;
			final String message = result == null? exceptionCause: how + " " + result + 
					(exceptionCause == null? "": "\n\n" + exceptionCause);
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					JOptionPane.showMessageDialog(app, 
							Static.wordWrap(message), 
							title, type);
				}
			});
		}

		private void update(DiskImageView view) {
			ListItem.this.view = view;
			app.updateListItem(ListItem.this);
		}
		
		@Override
		public void update(DiskImage image, Object arg) {
			if (arg instanceof DiskImageView) {
				update((DiskImageView) arg);
				return;
			}
			if (arg instanceof DiskImageProgress) {
				progress = (DiskImageProgress) arg;
				if (progress.done && !isCancelled()) try { // let the user
					Thread.sleep(80); // perceive the full bar
				} catch (InterruptedException e) {}
				app.setProgressValue(ListItem.this);
				return;
			}
		}
	}

}
