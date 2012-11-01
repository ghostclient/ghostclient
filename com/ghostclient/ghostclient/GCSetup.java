package com.ghostclient.ghostclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import javax.swing.JOptionPane;

public class GCSetup {
	public static final int OS_WINDOWS = 1;
	public static final int OS_MACX = 2;
	public static final int OS_LINUX = 3;
	
	//static method to set up everything for Ghost Client
	//most importantly: protocol handler
	
	public static void setup() {
		//detect operating system
		String os = System.getProperty("os.name").toLowerCase();
		GhostClient.println("[GCSetup] LOWER(os.name): " + os);
		int osType = -1;
		
		if(os.indexOf("win") >= 0) {
			osType = OS_WINDOWS;
		} else if(os.indexOf("nux") >= 0) {
			osType = OS_LINUX;
		} else if(os.indexOf("mac") >= 0) {
			//osType = OS_MACX; //todo: setup doesn't work on MAC OS X
		}
		
		if(osType == -1) {
			JOptionPane.showMessageDialog(null, "You seem to be using an unsupported operating system.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		//try to find our JAR file's path and it's parent directory
		File parentDir = GCUtil.getContainingDirectory();
		
		GhostClient.println("[GCSetup] PARENT:" + parentDir.getAbsolutePath() + "; " + parentDir.exists());
		
		if(!parentDir.isDirectory()) {
			JOptionPane.showMessageDialog(null, "Could not determine directory containing executing JAR.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		File jarFile = new File(parentDir, "ghostclient.jar");
		GhostClient.println("[GCSetup] JAR:" + jarFile.getAbsolutePath() + "; " + jarFile.exists());
		
		if(!jarFile.exists()) {
			JOptionPane.showMessageDialog(null, "Could not determine location of currently executing JAR.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		//allow program to be executed
		jarFile.setExecutable(true);
		
		//setup needs configuration file to execute Ghost Client with
		File configFile = new File(parentDir, "gclient.cfg");
		GhostClient.println("[GCSetup] CFG:" + configFile.getAbsolutePath() + "; " + configFile.exists());
		
		if(!configFile.exists()) {
			JOptionPane.showMessageDialog(null, "Your configuration file does not exist.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		//first, setup the protocol handler
		if(osType == OS_WINDOWS) {
			File regFile = new File(parentDir, "install.reg");
			
			//we also need to get the filenames of JAR and config file, replacing \ with \\
			String jarFilename = jarFile.getAbsolutePath().replace("\\", "\\\\");
			
			//delete last part if needed
			if(jarFilename.endsWith("\\\\")) {
				jarFilename = jarFilename.substring(0, jarFilename.length() - 2);
			}
			
			//create a .reg file
			try {
				PrintWriter out = new PrintWriter(new FileWriter(regFile));
				
				out.println("REGEDIT4");
				out.println();
				out.println("[HKEY_CLASSES_ROOT\\ghostclient]");
				out.println("@=\"URL:ghostclient Protocol\"");
				out.println("\"URL Protocol\"=\"\"");
				out.println();
				out.println("[HKEY_CLASSES_ROOT\\ghostclient\\shell]");
				out.println();
				out.println("[HKEY_CLASSES_ROOT\\ghostclient\\shell\\open]");
				out.println();
				out.println("[HKEY_CLASSES_ROOT\\ghostclient\\shell\\open\\command]");
				out.println("@=\"\\\"javaw\\\" \\\"-jar\\\" \\\"" + jarFilename + "\\\" \\\"%1\\\"\"");
				
				out.close();
			} catch(IOException ioe) {
				ioe.printStackTrace();
				JOptionPane.showMessageDialog(null, "Error while writing to install file.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			//now execute the reg file
			String[] cmd = {"cmd.exe", "/C", "regedit", "/S", regFile.getAbsolutePath()};
			
			try {
				execute(cmd);
			} catch(IOException ioe) {
				ioe.printStackTrace();
				JOptionPane.showMessageDialog(null, "Failed to setup registry settings for protocol handler.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
				return;
			} catch(InterruptedException e) {}
			
			GhostClient.println("[GCSetup] Windows installation finished.");
		} else if(osType == OS_LINUX) {
			String configFilename = configFile.getAbsolutePath();
			String jarFilename = jarFile.getAbsolutePath();
			
			String[] cmd1 = new String[] {"gconftool-2", "-s", "/desktop/gnome/url-handlers/ghostclient/command",
					"java -jar " + jarFilename + " " + configFilename + " action=join %s", "--type", "String"};
			String[] cmd2 = new String[] {"gconftool-2", "-s", "/desktop/gnome/url-handlers/ghostclient/enabled", "--type", "Boolean", "true"};

			try {
				execute(cmd1);
				execute(cmd2);
			} catch(IOException ioe) {
				ioe.printStackTrace();
				JOptionPane.showMessageDialog(null, "Failed to setup Gnome protocol handler settings.\nYou must install manually.", "Setup error", JOptionPane.ERROR_MESSAGE);
				return;
			} catch(InterruptedException e) {}
			
			GhostClient.println("[GCSetup] Linux installation finished.");
		}
	}
	
	private static void execute(String[] cmd) throws IOException, InterruptedException {
		String cmdStr = "'" + cmd[0];
		
		for(int i = 1; i < cmd.length; i++) {
			cmdStr += "' '" + cmd[i];
		}
		
		cmdStr += "'";
		GhostClient.println("[GCSetup] Executing: " + cmdStr);
		
		Process p = Runtime.getRuntime().exec(cmd);
		BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		
		while((line = in.readLine()) != null) {
			System.out.println("[GCSetup] Piped output: " + line);
		}
		
		in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		
		while((line = in.readLine()) != null) {
			System.out.println("[GCSetup] Piped error: " + line);
		}
		
		p.waitFor();
		
		int exit = p.exitValue();
		System.out.println("[GCSetup] Exit value: " + exit);
	}
}
