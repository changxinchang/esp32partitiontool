/*
    ESP32 Partition Tool was developed to facilitate the creation of custom partition schemes 
    for ESP32 projects within the Arduino IDE 1.8.x environment. 

    Copyright (c) 2024 serifpersia, github.com/serifpersia

	This program is open-source software distributed under the terms of the MIT License. 
	You are free to redistribute and/or modify it under the conditions of the MIT License, 
	provided that this disclaimer remains intact in the source files.

	MIT License
	
	Copyright (c) 2024 serifpersia
	
	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:
	
	The above copyright notice and this permission notice shall be included in all
	copies or substantial portions of the Software.
	
	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
	SOFTWARE.
*/

package com.serifpersia.esp32partitiontool;

import processing.app.Editor;
import processing.app.tools.Tool;

import javax.swing.JFrame;

public class ESP32PartitionTool implements Tool {

	private UI contentPane = new UI();
	private FileManager fileManager; // FileManager instance

	Editor editor;

	// uncomment If you want to work on the tool in Eclipse, IntelliJ
	// or any other Java supported IDE otherwise comment out for Arduino IDE use

	// public static void main(String[] args) {
	// ESP32PartitionTool tool = new ESP32PartitionTool();
	// tool.run();
	// }

	public void init(Editor editor) {
		this.editor = editor;
		// Pass the Editor instance when creating FileManager
		this.fileManager = new FileManager(contentPane, editor);
	}

	public String getMenuTitle() {
		return "ESP32 Partition Tool";
	}

	private void initGUI() {
		// Create and show the JFrame
		JFrame frame = new JFrame("ESP32 Partition Tool");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		// Size and display the frame
		frame.setSize(800, 400);

		// Set frame position to the center of the screen
		frame.setLocationRelativeTo(null);

		// Add panel to frame
		frame.getContentPane().add(contentPane);

		new UIController(contentPane, fileManager);

		frame.setVisible(true);
	}

	public void run() {
		initGUI();
	}
}