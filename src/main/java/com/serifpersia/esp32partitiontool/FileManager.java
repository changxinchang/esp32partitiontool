package com.serifpersia.esp32partitiontool;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;

import processing.app.PreferencesData;
import processing.app.Editor;
import processing.app.BaseNoGui;
import processing.app.Sketch;
import processing.app.helpers.ProcessUtils;
import processing.app.debug.TargetPlatform;

import org.apache.commons.codec.digest.DigestUtils;
import processing.app.helpers.FileUtils;

public class FileManager {

	private UI ui; // Reference to the UI instance
	private Editor editor; // Reference to the Editor instance

	private String imagePath;
	Boolean isNetwork = false;
	private String serialPort;
	File espota;
	File esptool;
	File gen_esp32part;
	String pythonCmd;
	String uploadSpeed;
	long spiStart;

	// Declare spiSize, spiPage, spiBlock here
	long spiSize;
	int spiPage;
	int spiBlock;
	private ArrayList<String> createdPartitionsData;

	// Constructor to initialize FileManager with UI instance and Editor instance
	public FileManager(UI ui, Editor editor) {
		this.ui = ui;
		this.editor = editor;
	}

	public void test() {
		System.out.println("filemanager test");
	}

	private void calculateCSV() {
		// Now you can use 'ui' to access UI components
		int numOfItems = ui.getNumOfItems();
		createdPartitionsData = new ArrayList<>();
		createdPartitionsData.add("# Name,   Type, SubType,  Offset,   Size,  Flags");

		for (int i = 0; i < numOfItems; i++) {
			JCheckBox checkBox = ui.getCheckBox(i);
			JTextField partitionNameField = ui.getPartitionName(i);
			JComboBox<?> partitionTypeComboBox = ui.getPartitionType(i);
			JTextField partitionSubTypeField = ui.getPartitionSubType(i);
			JTextField partitionSizeField = ui.getPartitionSizeHex(i);
			JTextField partitionOffset = ui.getPartitionOffsets(i);

			if (checkBox.isSelected()) {
				String name = partitionNameField.getText();
				String type = (String) partitionTypeComboBox.getSelectedItem();
				String subType = partitionSubTypeField.getText();
				String size = partitionSizeField.getText();
				String offset = "0x" + partitionOffset.getText(); // Assuming offset is same as size

				String exported_csvPartition = name + ", " + type + ", " + subType + ", " + offset + ", " + "0x" + size;
				createdPartitionsData.add(exported_csvPartition);
			}
		}
	}

	public void generateCSV() {

		calculateCSV();
		// Export to CSV
		FileDialog dialog = new FileDialog(new Frame(), "Create Partitions CSV", FileDialog.SAVE);
		dialog.setFile("partitions.csv");
		dialog.setVisible(true);
		String fileName = dialog.getFile();
		if (fileName != null) {
			String filePath = dialog.getDirectory() + fileName;
			try (FileWriter writer = new FileWriter(filePath)) {
				// Write the exported data to the CSV file
				for (String partitionData : createdPartitionsData) {
					writer.write(partitionData + "\n");
				}
				System.out.println("partititons.csv created at: " + filePath);
			} catch (IOException ex) {
				System.err.println("Error creating CSV: " + ex.getMessage());
			}
		}
	}

	private int listenOnProcess(String[] arguments) {
		try {
			final Process p = ProcessUtils.exec(arguments);
			Thread thread = new Thread() {
				public void run() {
					try {
						InputStreamReader reader = new InputStreamReader(p.getInputStream());
						int c;
						while ((c = reader.read()) != -1)
							System.out.print((char) c);
						reader.close();

						reader = new InputStreamReader(p.getErrorStream());
						while ((c = reader.read()) != -1)
							System.err.print((char) c);
						reader.close();
					} catch (Exception e) {
					}
				}
			};
			thread.start();
			int res = p.waitFor();
			thread.join();
			return res;
		} catch (Exception e) {
			return -1;
		}
	}

	private void sysExec(final String[] arguments) {
		Thread thread = new Thread() {
			public void run() {
				try {
					if (listenOnProcess(arguments) != 0) {
						editor.statusError("SPIFFS Upload failed!");
					} else {
						editor.statusNotice("SPIFFS Uploaded");
					}
				} catch (Exception e) {
					editor.statusError("SPIFFS Upload failed!");
				}
			}
		};
		thread.start();
	}

	private String getBuildFolderPath(Sketch s) {
		// first of all try the getBuildPath() function introduced with IDE 1.6.12
		// see commit arduino/Arduino#fd1541eb47d589f9b9ea7e558018a8cf49bb6d03
		try {
			String buildpath = s.getBuildPath().getAbsolutePath();
			return buildpath;
		} catch (IOException er) {
			editor.statusError(er);
		} catch (Exception er) {
			try {
				File buildFolder = FileUtils.createTempFolder("build",
						DigestUtils.md5Hex(s.getMainFilePath()) + ".tmp");
				return buildFolder.getAbsolutePath();
			} catch (IOException e) {
				editor.statusError(e);
			} catch (Exception e) {
				// Arduino 1.6.5 doesn't have FileUtils.createTempFolder
				// String buildPath = BaseNoGui.getBuildFolder().getAbsolutePath();
				java.lang.reflect.Method method;
				try {
					method = BaseNoGui.class.getMethod("getBuildFolder");
					File f = (File) method.invoke(null);
					return f.getAbsolutePath();
				} catch (SecurityException ex) {
					editor.statusError(ex);
				} catch (IllegalAccessException ex) {
					editor.statusError(ex);
				} catch (InvocationTargetException ex) {
					editor.statusError(ex);
				} catch (NoSuchMethodException ex) {
					editor.statusError(ex);
				}
			}
		}
		return "";
	}

	public void createPartitionsBin() {
		TargetPlatform platform = BaseNoGui.getTargetPlatform();

		String gen_esp32partCmd = "gen_esp32part.py";
		gen_esp32part = new File(platform.getFolder() + "/tools", gen_esp32partCmd);
		if (!gen_esp32part.exists() || !gen_esp32part.isFile()) {
			System.err.println();
			editor.statusError("Partitions Bin Generate Error: gen_esp32part not found!");
			return;
		}

		editor.statusNotice("Creating partitions.bin...");

		String buildPath = getBuildFolderPath(editor.getSketch());
		String csvFilePath = buildPath + "/partitions.csv";

		// Assuming you have access to the UI components
		calculateCSV();

		try (FileWriter writer = new FileWriter(csvFilePath)) {
			// Write the exported data to the CSV file
			for (String partitionData : createdPartitionsData) {
				writer.write(partitionData + "\n");
			}
			System.out.println("partitions.scv successfully created at: " + csvFilePath);
		} catch (IOException ex) {
			System.err.println("Error exporting CSV: " + ex.getMessage());
		}

		String partitionsBinPath = buildPath + "/partitions.bin";
		// Command to generate partitions.bin
		String[] command = { "python", gen_esp32part.getAbsolutePath(), buildPath + "/partitions.csv",
				partitionsBinPath };

		try {
			// Execute the command
			int exitCode = listenOnProcess(command);
			if (exitCode == 0) {
				editor.statusNotice("partitions.bin created successfully.");
				System.out.println("partitions.bin created successfully.");
			} else {
				editor.statusError("Failed to create partitions.bin.");
			}
		} catch (Exception e) {
			editor.statusError("An error occurred while creating partitions.bin.");
			e.printStackTrace(); // Print the stack trace for debugging
		}

	}

	public void createSPIFFS() {
		String spiPageSizeSelected = ui.getSpiffsPageSize().getSelectedItem().toString();
		String spiPageBlockSizeSelected = ui.getSpiffsBlockSize().getText();

		spiStart = 0;
		spiSize = 0;
		spiPage = Integer.parseInt(spiPageSizeSelected);
		spiBlock = Integer.parseInt(spiPageBlockSizeSelected);

		if (!PreferencesData.get("target_platform").contentEquals("esp32")) {
			System.err.println();
			editor.statusError("SPIFFS Not Supported on " + PreferencesData.get("target_platform"));
			return;
		}

		TargetPlatform platform = BaseNoGui.getTargetPlatform();

		String toolExtension = ".py";
		if (PreferencesData.get("runtime.os").contentEquals("windows")) {
			toolExtension = ".exe";
		} else if (PreferencesData.get("runtime.os").contentEquals("macosx")) {
			toolExtension = "";
		}

		if (PreferencesData.get("runtime.os").contentEquals("windows"))
			pythonCmd = "python.exe";
		else
			pythonCmd = "python";

		String mkspiffsCmd;
		if (PreferencesData.get("runtime.os").contentEquals("windows"))
			mkspiffsCmd = "mkspiffs.exe";
		else
			mkspiffsCmd = "mkspiffs";

		String espotaCmd = "espota.py";
		if (PreferencesData.get("runtime.os").contentEquals("windows"))
			espotaCmd = "espota.exe";

		isNetwork = false;
		espota = new File(platform.getFolder() + "/tools");
		esptool = new File(platform.getFolder() + "/tools");
		serialPort = PreferencesData.get("serial.port");

		if (!BaseNoGui.getBoardPreferences().containsKey("build.partitions")) {
			System.err.println();
			editor.statusError("Partitions Not Defined for " + BaseNoGui.getBoardPreferences().get("name"));
			return;
		}

		String buildPath = getBuildFolderPath(editor.getSketch());
		String csvFilePath = buildPath + "/partitions.csv";

		calculateCSV();

		try (FileWriter writer = new FileWriter(csvFilePath)) {
			// Write the exported data to the CSV file
			for (String partitionData : createdPartitionsData) {
				writer.write(partitionData + "\n");
			}
			System.out.println("CSV exported successfully to: " + csvFilePath);
		} catch (IOException ex) {
			System.err.println("Error exporting CSV: " + ex.getMessage());
		}

		// Read the partitions.csv file
		try (BufferedReader partitionsReader = new BufferedReader(new FileReader(csvFilePath))) {
			String partitionsLine = "";
			while ((partitionsLine = partitionsReader.readLine()) != null) {
				if (partitionsLine.contains("spiffs")) {
					String[] partitionsData = partitionsLine.split(",\\s*"); // Split by comma with optional spaces
					if (partitionsData.length >= 5) { // Ensure there are enough elements
						String pStart = partitionsData[3].trim(); // Offset value
						String pSize = partitionsData[4].trim(); // Size value
						spiStart = Integer.parseInt(pStart.substring(2), 16); // Convert hex to int
						spiSize = Integer.parseInt(pSize.substring(2), 16); // Convert hex to int
					}
				}
			}
			if (spiSize == 0) {
				System.err.println();
				editor.statusError("SPIFFS Error: partition size could not be found!");
				return;
			}
		} catch (Exception e) {
			editor.statusError(e);
			return;
		}

		File tool = new File(platform.getFolder() + "/tools", mkspiffsCmd);
		if (!tool.exists() || !tool.isFile()) {
			tool = new File(platform.getFolder() + "/tools/mkspiffs", mkspiffsCmd);
			if (!tool.exists()) {
				tool = new File(PreferencesData.get("runtime.tools.mkspiffs.path"), mkspiffsCmd);
				if (!tool.exists()) {
					System.err.println();
					editor.statusError("SPIFFS Error: mkspiffs not found!");
					return;
				}
			}
		}

		// make sure the serial port or IP is defined
		if (serialPort == null || serialPort.isEmpty()) {
			System.err.println();
			editor.statusError("SPIFFS Error: serial port not defined!");
			return;
		}

		// find espota if IP else find esptool
		if (serialPort.split("\\.").length == 4) {
			isNetwork = true;
			espota = new File(platform.getFolder() + "/tools", espotaCmd);
			if (!espota.exists() || !espota.isFile()) {
				System.err.println();
				editor.statusError("SPIFFS Error: espota not found!");
				return;
			}
		} else {
			String esptoolCmd = "esptool" + toolExtension;
			esptool = new File(platform.getFolder() + "/tools", esptoolCmd);
			if (!esptool.exists() || !esptool.isFile()) {
				esptool = new File(platform.getFolder() + "/tools/esptool_py", esptoolCmd);
				if (!esptool.exists()) {
					esptool = new File(PreferencesData.get("runtime.tools.esptool_py.path"), esptoolCmd);
					if (!esptool.exists()) {
						System.err.println();
						editor.statusError("SPIFFS Error: esptool not found!");
						return;
					}
				}
			}
		}

		// load a list of all files
		int fileCount = 0;
		File dataFolder = new File(editor.getSketch().getFolder(), "data");
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		if (dataFolder.exists() && dataFolder.isDirectory()) {
			File[] files = dataFolder.listFiles();
			if (files.length > 0) {
				for (File file : files) {
					if ((file.isDirectory() || file.isFile()) && !file.getName().startsWith("."))
						fileCount++;
				}
			}
		}

		String dataPath = dataFolder.getAbsolutePath();
		String toolPath = tool.getAbsolutePath();
		String sketchName = editor.getSketch().getName();
		imagePath = getBuildFolderPath(editor.getSketch()) + "/" + sketchName + ".spiffs.bin";
		uploadSpeed = BaseNoGui.getBoardPreferences().get("upload.speed");

		Object[] options = { "Yes", "No" };
		String title = "Create SPIFFS";
		String message = "No files have been found in your data folder!\nAre you sure you want to create an empty SPIFFS image?";

		if (fileCount == 0 && JOptionPane.showOptionDialog(editor, message, title, JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != JOptionPane.YES_OPTION) {
			System.err.println();
			editor.statusError("SPIFFS Warning: mkspiffs canceled!");
			return;
		}

		editor.statusNotice("Creating SPIFFS...");
		System.out.println("[SPIFFS] data   : " + dataPath);
		System.out.println("[SPIFFS] start (kB)  : " + spiStart / 1024);
		System.out.println("[SPIFFS] size (kB)   : " + (spiSize / 1024));
		System.out.println("[SPIFFS] page (kB)   : " + spiPage);
		System.out.println("[SPIFFS] block (kB)  : " + spiBlock);

		try {
			if (listenOnProcess(new String[] { toolPath, "-c", dataPath, "-p", spiPage + "", "-b", spiBlock + "", "-s",
					spiSize + "", imagePath }) != 0) {
				System.err.println();
				editor.statusError("Failed to create SPIFFS!");
				return;
			}
		} catch (Exception e) {
			editor.statusError(e);
			editor.statusError("Failed to create SPIFFS!");
			return;
		} finally {
			editor.statusNotice("Completed creating SPIFFS");
			System.out.println("SPIFFS successfully created");
			// Delete the partitions.csv file after reading its contents
			File csvFile = new File(csvFilePath);
			if (csvFile.exists()) {
				if (csvFile.delete()) {
				} else {
					System.err.println("Failed to delete partitions.csv file");
				}
			}
		}
	}

	public void uploadSPIFFS() {
		editor.statusNotice("Uploading SPIFFS...");
		System.out.println("[SPIFFS] upload : " + imagePath);

		if (isNetwork) {
			System.out.println("[SPIFFS] IP     : " + serialPort);
			System.out.println();
			if (espota.getAbsolutePath().endsWith(".py"))
				sysExec(new String[] { pythonCmd, espota.getAbsolutePath(), "-i", serialPort, "-p", "3232", "-s", "-f",
						imagePath });
			else
				sysExec(new String[] { espota.getAbsolutePath(), "-i", serialPort, "-p", "3232", "-s", "-f",
						imagePath });
		} else {
			String mcu = BaseNoGui.getBoardPreferences().get("build.mcu");
			String flashMode = BaseNoGui.getBoardPreferences().get("build.flash_mode");
			String flashFreq = BaseNoGui.getBoardPreferences().get("build.flash_freq");
			System.out.println("[SPIFFS] address: " + spiStart);
			System.out.println("[SPIFFS] port   : " + serialPort);
			System.out.println("[SPIFFS] speed  : " + uploadSpeed);
			System.out.println("[SPIFFS] mode   : " + flashMode);
			System.out.println("[SPIFFS] freq   : " + flashFreq);
			System.out.println();
			if (esptool.getAbsolutePath().endsWith(".py"))
				sysExec(new String[] { pythonCmd, esptool.getAbsolutePath(), "--chip", mcu, "--baud", uploadSpeed,
						"--port", serialPort, "--before", "default_reset", "--after", "hard_reset", "write_flash", "-z",
						"--flash_mode", flashMode, "--flash_freq", flashFreq, "--flash_size", "detect", "" + spiStart,
						imagePath });
			else
				sysExec(new String[] { esptool.getAbsolutePath(), "--chip", mcu, "--baud", uploadSpeed, "--port",
						serialPort, "--before", "default_reset", "--after", "hard_reset", "write_flash", "-z",
						"--flash_mode", flashMode, "--flash_freq", flashFreq, "--flash_size", "detect", "" + spiStart,
						imagePath });
		}
	}
}