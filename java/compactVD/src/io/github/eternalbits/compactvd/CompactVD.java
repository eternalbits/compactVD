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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.eternalbits.compactvd.gui.FrontEnd;
import io.github.eternalbits.disk.DiskImage;
import io.github.eternalbits.disk.DiskImageJournal;
import io.github.eternalbits.disk.DiskImageObserver;
import io.github.eternalbits.disk.DiskImageProgress;
import io.github.eternalbits.disks.DiskImages;

/**
 * Virtual Disk Compact and Copy command line interface. Without parameters
 * the GUI takes precedence, use {@code --help} to get the command list.
 * <p>
 */
public class CompactVD implements DiskImageObserver {
	
	// TODO: move this stuff to DiskImage[s] and merge with ListItem methods
	
	private Thread mainThread = null;
	private int task = DiskImageProgress.NO_TASK;
	
	private final static String SEARCHING_SPACE = "Searching space not in use or zero filled";
	private final static String IMAGE_CHANGED = "The disk image '%s' was changed.";
	private final static String IMAGE_CREATED = "The disk image '%s' was created in '%s' directory.";
	private final static String IMAGE_NOT_CHANGED = "The disk image '%s' was NOT changed.";
	private final static String IMAGE_NOT_CREATED = "No disk image was created.";
	private boolean verbose;
	
	private boolean isCancelled() {
		return mainThread.isInterrupted();
	}
	
	private void verboseProgress(String verb) {
		if (verbose) System.out.println(verb);
	}
	
	private void getRuntime() {
		Runtime.getRuntime().addShutdownHook(new Thread() { // Ctrl+C
			@Override
			public void run() {
				try {
					mainThread.interrupt();
					mainThread.join();
				} catch (InterruptedException e) {}
			}
		});
	}
	
	private void showView(File file, int options) throws IOException {
		task = DiskImageProgress.OPTIMIZE;
		try (DiskImage image = DiskImages.open(file, "r")) {
			verboseProgress(SEARCHING_SPACE);
			image.addObserver(this, false);
			image.optimize(options);
			image.removeObserver(this);
			dump(image.getView());
		}
		task = DiskImageProgress.NO_TASK;
	}
	
	private void compact(File file, int options) throws IOException {
		long mtime = file.lastModified();
		task = DiskImageProgress.COMPACT;
		getRuntime();
		try (RandomAccessFile check = new RandomAccessFile(file, "r")) { // is file?
			try (DiskImage image = DiskImages.open(file, "rw")) {
				FileLock fileLock = image.tryLock();
				verboseProgress(SEARCHING_SPACE);
				image.addObserver(this, false);
				image.optimize(options);
				image.removeObserver(this);
				if (!isCancelled()) {
					verboseProgress("Compacting "+file.getName());
					image.addObserver(this, false);
					image.compact();
					image.removeObserver(this);
				}
				fileLock.release();
			}
		}
		finally {
			System.out.println(String.format(mtime != file.lastModified()? 
					IMAGE_CHANGED: IMAGE_NOT_CHANGED, file.getName()));
		}
	}
	
	private void copy(File from, int options, File to, String type) throws IOException {
		task = DiskImageProgress.COPY;
		File copy = null;
		getRuntime();
		try (RandomAccessFile check = new RandomAccessFile(from, "r")) { // is file?
			// If writable, source is open in write mode for an exclusive file lock
			String mode = from.canWrite()? "rw": "r";
			try (DiskImage image = DiskImages.open(from, mode)) {
				if (type == null) type = image.getType();
				try (DiskImage clone = DiskImages.create(type, to, image.getDiskSize())) {
					copy = to; // copy open by DiskImage
					FileLock source = null;
					if (mode.equals("rw"))
						source = image.tryLock();
					verboseProgress(SEARCHING_SPACE);
					image.addObserver(this, false);
					image.optimize(options);
					image.removeObserver(this);
					if (!isCancelled()) {
						verboseProgress("Copying "+from.getName()+" to "+to.getName());
						FileLock fileLock = clone.tryLock();
						clone.addObserver(this, false);
						clone.copy(image);
						clone.removeObserver(this);
						fileLock.release();
					}
					if (source != null) 
						source.release();
					if (!isCancelled()) 
						copy = null;
				}
			}
		}
		finally {
			if (copy != null && copy.isFile()) 
				copy.delete();
			System.out.println(to != null && to.isFile()? String.format(IMAGE_CREATED, 
					to.getName(), to.getAbsoluteFile().getParent()): IMAGE_NOT_CREATED);
		}
	}
	
	private String lastProgress = null;
	private char[] bar = new char[58];
	
	private int boundedInt(float f, int m) {
		int b = Math.round(f * m);
		if (b > m) b = m;
		if (b < 0) b = 0;
		return b;
	}
	
	@Override
	public void update(DiskImage image, Object arg) {
		if (arg instanceof DiskImageProgress) {
			DiskImageProgress progress = (DiskImageProgress) arg;
			Arrays.fill(bar, '.');
			Arrays.fill(bar, 0, boundedInt(progress.value, bar.length), '#');
			String pb = String.format("\rProgress: [%3d%%] [%s]", boundedInt(progress.value, 100), String.valueOf(bar));
			if (!isCancelled() && !pb.equals(lastProgress)) {
				System.out.print(pb);
				lastProgress = pb;
			}
			if (progress.done) {
				if (verbose || progress.task == task) System.out.println();
				lastProgress = null;
			}
		}
	}
	
	private final static String[] DEFAULT_FILE_FILTER = {null, "vdi", "vmdk", "vhd", "raw"};
	private final static String FILES_ARE_DUPLICATED = "File \"%s\" is the same as the old image!";
	private final static String FILE_ALREADY_EXISTS = "File \"%s\" already exists";
	private final static String INCORRECT_COMMAND = "The syntax of the command is incorrect.";	
	private final static String TOO_MANY_OPTIONS = "There are too many options: %s.";	
	
	private File getOptionValues(CommandLine cmd, String opt) throws ParseException {
		if (cmd.getOptionValues(opt).length != 1)
			throw new ParseException(String.format(TOO_MANY_OPTIONS, opt));
		return new File(cmd.getOptionValue(opt));
	}
	
	private static final String version = "1.9";
	private static final String jar = new java.io.File(CompactVD.class.getProtectionDomain()
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
		
		new CompactVD().commandLine(args);
		
	}
	
	private static Options buildHelpers() {
		Options helpers = new Options();
		helpers.addOption(Option.builder("h").longOpt("help").desc("print this help and exit").build());
		helpers.addOption(Option.builder().longOpt("version").desc("print version information and exit").build());
		return helpers;
	}

	private static Options buildOptions() {
		Options options = new Options();
		OptionGroup source = new OptionGroup();
		source.addOption(Option.builder("i").longOpt("inplace").desc("compact <src> image file in place").hasArgs().argName("src").build());
		source.addOption(Option.builder("c").longOpt("copy").desc("copy <src> to a new, optimized image").hasArgs().argName("src").build());
		source.addOption(Option.builder("d").longOpt("dump").desc("print <src> disk image details").hasArgs().argName("src").build());
		source.setRequired(true);
		options.addOptionGroup(source);
		options.addOptionGroup(new OptionGroup()
				.addOption(Option.builder("u").longOpt("drop-unused").desc("drop space not in use by system and files").build())
				.addOption(Option.builder("U").longOpt("keep-unused").desc("keep space not in use by system and files").build())
			);
		options.addOptionGroup(new OptionGroup()
				.addOption(Option.builder("z").longOpt("drop-zeroed").desc("drop space filled with zeros").build())
				.addOption(Option.builder("Z").longOpt("keep-zeroed").desc("keep space filled with zeros").build())
			);
		options.addOption(Option.builder("w").longOpt("write").desc("set <out> as destination file for copy").hasArgs().argName("out").build());
		options.addOption(Option.builder("f").longOpt("format").desc("copy output format: VDI, VMDK, VHD or RAW").hasArgs().argName("fmt").build());
		options.addOption(Option.builder("o").longOpt("overwrite").desc("overwrite existing file on copy").build());
		options.addOption(Option.builder("v").longOpt("verbose").desc("explain what is being done").build());
		return options;
	}
	
	private void commandLine(String[] args) {
		mainThread = Thread.currentThread();
		
		Options options = buildOptions();
		Options helpers = buildHelpers();
		for (Option opt: helpers.getOptions()) {
			options.addOption(opt);
		}
		try {
			CommandLine cmd = new DefaultParser().parse(helpers, args, true);
			if (cmd.hasOption("version")) {
				printAbout();
				return;
			}
			if (cmd.hasOption("h")) {
				printHelp(options);
				return;
			}
			
			cmd = new DefaultParser().parse(options, args);
			
			int opt = DiskImage.FREE_BLOCKS_UNUSED;
			if (cmd.hasOption("U")) opt &= ~DiskImage.FREE_BLOCKS_UNUSED;
			if (cmd.hasOption("z")) opt |= DiskImage.FREE_BLOCKS_ZEROED;
			
			verbose = cmd.hasOption("v");
			
			if (cmd.hasOption("c")) {
				opt |= DiskImage.FREE_BLOCKS_ZEROED; // drop-zeroed is implied
				
				if (!cmd.hasOption("w"))
					throw new ParseException(INCORRECT_COMMAND);
				
				File from = getOptionValues(cmd, "c");
				File to = getOptionValues(cmd, "w");
				
				if (!cmd.hasOption("o") && to.exists())
					throw new ParseException(String.format(FILE_ALREADY_EXISTS, to));
				
				if (from.equals(to))
					throw new ParseException(String.format(FILES_ARE_DUPLICATED, to));
				
				String f = cmd.hasOption("f")? cmd.getOptionValue("f").toLowerCase(): null;
				if (cmd.hasOption("f") && cmd.getOptionValues("f").length != 1)
					throw new ParseException(String.format(TOO_MANY_OPTIONS, "f"));
				if (!Arrays.asList(DEFAULT_FILE_FILTER).contains(f))
					throw new ParseException(INCORRECT_COMMAND);
				
				copy(from, opt, to, f);
				return;
			}
			
			if (cmd.hasOption("w") || cmd.hasOption("o") || cmd.hasOption("f"))
				throw new ParseException(INCORRECT_COMMAND);
			
			if (cmd.hasOption("i")) {
				compact(getOptionValues(cmd, "i"), opt);
				return;
			}
			
			if (cmd.hasOption("d")) {
				showView(getOptionValues(cmd, "d"), opt);
				return;
			}
			
		} catch (ParseException | IOException e) {
			printHelp(options);
			System.out.println("\n\n"+Static.simpleString(e));
			System.exit(1);
		}

	}

	private static void printAbout() {
		System.out.println("CompactVD version " + version + " copyright 2016-2022 Rui Baptista");
		System.out.println("Licensed under the Apache License, Version 2.0.");
	}
	
	private static void printHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.setSyntaxPrefix("Usage: ");
		final String prefix = "--";
		String header = "\nTo reduce the size of dynamic disk images. Version "+version+"\n\n";
		String footer = ("\nOne of ^inplace, ^copy or ^dump is required. For ^inplace and ^dump"
				+ " the default options are ^drop-unused ^keep-zeroed. For ^copy the default"
				+ " is ^drop-unused and ^drop-zeroed is implied.\n").replace("^", prefix);
		formatter.setLongOptPrefix(" "+prefix);
		formatter.printHelp("java -jar "+jar, header, options, footer, true);
	}

	public static void dump(Object obj) { dump(obj, ""); }
	private static void dump(Object obj, String in) {
		for (Field fld: obj.getClass().getDeclaredFields()) {
			try {
				if (!Modifier.isPrivate(fld.getModifiers())) {
					if (fld.getAnnotation(Deprecated.class) == null) {
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
					}
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}
}
