package com.ghostclient.ghostclient.graphics;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.ghostclient.ghostclient.Config;
import com.ghostclient.ghostclient.GCUtil;
import com.ghostclient.ghostclient.GhostClient;

public class SoundManager {
	public static final int BUFFER_SIZE = 128000;
	Map<String, File> audioFiles;
	
	boolean isPlayingSound = false;
	
	public SoundManager() {
		audioFiles = new HashMap<String, File>();
	}
	
	public void init() {
		if(Config.getBoolean("sound_enabled", true)) {
			if(Config.getBoolean("sound_otherchat", true)) {
				addSound("otherChat", "sounds/sound_1.wav");
			}
			
			if(Config.getBoolean("sound_youchat", true)) {
				addSound("youChat", "sounds/sound_2.wav");
			}
			
			if(Config.getBoolean("sound_userjoined", true)) {
				addSound("userJoined", "sounds/sound_3.wav");
			}
			
			if(Config.getBoolean("sound_userleft", true)) {
				addSound("userLeft", "sounds/sound_4.wav");
			}
			
			if(Config.getBoolean("sound_5", true)) {
				addSound("sound_5", "sounds/sound_5.wav");
			}
			
			if(Config.getBoolean("sound_error", true)) {
				addSound("error", "sounds/sound_6.wav");
			}
		}
	}
	
	public void addSound(String name, String filename) {
		GhostClient.println("[SoundManager] Loading " + name + " from " + filename);
		File file = new File(GCUtil.getContainingDirectory(), filename);
		
		if(file.exists() && file.canRead()) {
			audioFiles.put(name, file);
		}
	}
	
	public void playSound(String name) {
		//double check to see if sounds are enabled
		//need to do this because maybe user changed via preferences panel
		if(!Config.getBoolean("sound_enabled", true)) {
			return;
		}
		
		File file = audioFiles.get(name);
		
		if(file != null) {
			new ThreadedPlaySound(file).start();
		}
	}
	
	class ThreadedPlaySound extends Thread {
		File file;
		
		public ThreadedPlaySound(File file) {
			this.file = file;
		}
		
		public void run() {
			doPlaySound(file);
		}
	}
	
	public void doPlaySound(File file) {
		synchronized(this) {
			if(isPlayingSound) {
				return;
			}
			
			isPlayingSound = true;
		}
		
		AudioInputStream audioStream = null;
		
		try {
			audioStream = AudioSystem.getAudioInputStream(file);
		} catch(IOException ioe) {
			GhostClient.println("[SoundManager] Failed to load sound: " + ioe.getLocalizedMessage());
			return;
		} catch(UnsupportedAudioFileException e) {
			GhostClient.println("[SoundManager] Failed to load sound: " + e.getLocalizedMessage());
			return;
		}
		
		AudioFormat audioFormat = audioStream.getFormat();
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		SourceDataLine sourceLine = null;
		
		try {
			sourceLine = (SourceDataLine) AudioSystem.getLine(info);
			sourceLine.open(audioFormat);
		} catch (LineUnavailableException e) {
			GhostClient.println("[SoundManager] Failed to get line: " + e.getLocalizedMessage());
			return;
		}
		
		sourceLine.start();
		int nBytesRead = 0;
		byte[] abData = new byte[BUFFER_SIZE];
		while (nBytesRead != -1) {
			try {
				nBytesRead = audioStream.read(abData, 0, abData.length);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (nBytesRead >= 0) {
				sourceLine.write(abData, 0, nBytesRead);
			}
		}
		
		sourceLine.drain();
		sourceLine.close();
		
		synchronized(this) {
			isPlayingSound = false;
		}
	}
}
