package com.ghostclient.ghostclient.graphics;


import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.ghostclient.ghostclient.CloudInterface;
import com.ghostclient.ghostclient.GCUtil;
import com.ghostclient.ghostclient.GhostClient;

public class GCFrame extends JFrame implements WindowListener {
	GhostClient client;
	CloudInterface cloudInterface;
	HashMap<String, GCPanel> panels;
	
	boolean launched; //whether we should display login screen or not
	boolean isInitialized; //whether or not the frame is initialized
	
	boolean quit;
	
	//panels
	ChatPanel chatPanel;
	LoginPanel loginPanel;
	
	GCPanel loadedPanel;

	public GCFrame(GhostClient client, CloudInterface cloudInterface, boolean launched) {
		super(GhostClient.GCLIENT_VERSION_STRING);
		
		this.client = client;
		this.cloudInterface = cloudInterface;
		this.launched = launched;
		
		quit = false;
		isInitialized = false;
		panels = new HashMap<String, GCPanel>();
	}

	public void init() {
		chatPanel = new ChatPanel(cloudInterface);
		addScreen("chat", chatPanel);
		
		loginPanel = new LoginPanel(this, client);
		addScreen("login", loginPanel);

		if(launched) {
			updateScreen("chat");
		} else {
			updateScreen("login");
		}
		
		//set icon
		try {
			URL iconURL = new File(GCUtil.getContainingDirectory(), "icon.png").toURI().toURL();
			Toolkit kit = Toolkit.getDefaultToolkit();
			Image img = kit.createImage(iconURL);
			setIconImage(img);
		} catch(MalformedURLException e) {
			e.printStackTrace();
			GhostClient.println("[GCFrame] Error while setting icon, continuing");
		}
		
		pack();
		addWindowListener(this);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setVisible(true);
		
		isInitialized = true;
	}
	
	public void quit() {
		if(!quit) {
			quit = true;
			dispose();
		}
	}

	public void addScreen(String name, GCPanel pane) {
		panels.put(name, pane);
	}
	
	public void setScreen(String name) {
		SwingUtilities.invokeLater(new UpdateScreen(name));
	}
	
	private void updateScreen(String name) {
		//make sure we aren't setting to the same screen
		if(loadedPanel == panels.get(name)) {
			return;
		}
		
		if(loadedPanel != null) {
			loadedPanel.onUnload();
		}
		
		loadedPanel = panels.get(name);
		
		getContentPane().removeAll();
		getContentPane().add(loadedPanel);
		panels.get(name).revalidate();
		repaint();
		pack();
		requestFocus();
		
		loadedPanel.onLoad();
		
		GhostClient.println("[GCFrame] Set screen to " + name + " (" + panels.get(name) + ")");
		GhostClient.println("[GCFrame] Size: " + getSize() + ", " + panels.get(name).getSize());
	}
	
	public ChatPanel getChatPanel() {
		return chatPanel;
	}

	public void windowClosed(WindowEvent e) {
		//shut down everything
		client.quit();
	}
	
	public void windowActivated(WindowEvent e) {
		repaint();
	}
	
	public void windowClosing(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	
	class UpdateScreen implements Runnable {
		String targetScreen;
		
		public UpdateScreen(String targetScreen) {
			this.targetScreen = targetScreen;
		}
		
		public void run() {
			updateScreen(targetScreen);
		}
	}
}