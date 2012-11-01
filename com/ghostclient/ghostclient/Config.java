package com.ghostclient.ghostclient;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class Config {
	static String propertiesFile;
	public static Properties properties;
	
	public static boolean init(String propertiesFile) {
		Config.propertiesFile = propertiesFile;
		properties = new Properties();
		GhostClient.println("[Config] Loading configuration file " + propertiesFile);
		
		try {
			properties.load(new FileReader(propertiesFile));
			return true;
		} catch(FileNotFoundException e) {
			GhostClient.println("[Config] Fatal error: could not find configuration file " + propertiesFile);
			return false;
		} catch(IOException e) {
			GhostClient.println("[Config] Fatal error: error while reading from configuration file " + propertiesFile);
			return false;
		}
	}
	
	public static void deinit() {
		try {
			properties.store(new FileWriter(propertiesFile), null);
		} catch(IOException ioe) {
			GhostClient.println("[Config] Fatal error: error while storing configuration file " + propertiesFile);
		}
	}
	
	public static String getString(String key, String defaultValue) {
		String str = properties.getProperty(key, defaultValue);
		
		if(str == null || str.trim().equals("")) {
			return defaultValue;
		} else {
			return str;
		}
	}
	
	public static int getInt(String key, int defaultValue) {
		try {
			String result = properties.getProperty(key, null);
			
			if(result != null) {
				return Integer.parseInt(result);
			} else {
				return defaultValue;
			}
		} catch(NumberFormatException nfe) {
			System.out.println("[Config] Warning: invalid integer for key " + key);
			return defaultValue;
		}
	}
	
	public static long getLong(String key, long defaultValue) {
		try {
			String result = properties.getProperty(key, null);
			
			if(result != null) {
				return Long.parseLong(result);
			} else {
				return defaultValue;
			}
		} catch(NumberFormatException nfe) {
			System.out.println("[Config] Warning: invalid long for key " + key);
			return defaultValue;
		}
	}
	
	public static boolean getBoolean(String key, boolean defaultValue) {
		String result = properties.getProperty(key, null);
		
		if(result != null) {
			if(result.equals("true") || result.equals("1")) return true;
			else if(result.equals("false") || result.equals("0")) return false;
			else {
				System.out.println("[Config] Warning: invalid boolean for key " + key);
				return defaultValue;
			}
		} else {
			return defaultValue;
		}
	}
	
	public static void set(String key, String val) {
		properties.setProperty(key, val);
	}
	
	public static void setBoolean(String key, boolean val) {
		properties.setProperty(key, val ? "true" : "false");
	}
	
	public static boolean containsKey(String key) {
		return properties.containsKey(key);
	}
}