package com.ghostclient.ghostclient;


import java.awt.Frame;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import com.ghostclient.ghostclient.game.GCHost;
import com.ghostclient.ghostclient.game.GCList;
import com.ghostclient.ghostclient.graphics.GCFrame;
import com.ghostclient.ghostclient.graphics.SoundManager;
import com.ghostclient.ghostclient.web.WebInterface;

public class GhostClient {
	public static int GCLIENT_VERSION = 4;
	public static String GCLIENT_VERSION_STRING = "gclient 4";
	public static File logTarget = null;
	
	static GhostClient client;
	
	LoaderInterface loaderInterface;
	CloudInterface cloudInterface;
	WebInterface webInterface;
	GCFrame frame;
	GCHost host;
	GCList list;
	Timer timer;
	SoundManager soundManager;
	
	boolean quit = false;
	
	public GhostClient(String action, String[] args, int argsIndex) {
		//timer to time things
		timer = new Timer();
		
		//sound manager
		soundManager = new SoundManager();
		
		//first initialize list and host
		cloudInterface = new CloudInterface(this, timer, soundManager);
		
		//initiate loader interface to lock the loader port
		loaderInterface = new LoaderInterface(cloudInterface);
		boolean succeed = true;
		
		if(Config.getBoolean("loader_enable", true)) {
			succeed = loaderInterface.init();
		}
		
		//initialize web interface that will interface with website
		webInterface = new WebInterface(this);
		
		//these variables will be used to add one game
		int botId = -1;
		
		if(action != null) {
			if(action.equals("dosetup")) {
				GCSetup.setup();
			} else if((action.equals("join")) && args.length >= argsIndex + 1) {
				//client has clicked join, and we should display game in LAN
				//PROBLEM: Ghost Client might already be running
				//so, we check if loader interface initialized; if not, we use LoaderClient to connect to existing
				botId = Integer.parseInt(args[argsIndex]);
				argsIndex++;
				
				if(!succeed) {
					LoaderClient loaderClient = new LoaderClient();
					loaderClient.execute("join", botId + "");
					System.exit(0);
				} else {
					//currently we cannot initialize by ourself
					//this is because we are not passed the member ID and password hash
					JOptionPane.showMessageDialog(null, "Ghost Client is not already running. You will have to login manually.", "Join error", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
		
		//quit if we didn't succeed to lock with loader interface
		if(!succeed) {
			JOptionPane.showMessageDialog(null, "Ghost Client is already running. Close the other client instance first.", "Initialization error", JOptionPane.ERROR_MESSAGE);
			System.exit(-1);
		}
		
		//try to identify username and password
		//if we can't find these, user will have to login manually
		boolean launched = false;
		int memberId = -1;
		String passwordHash = null;
		
		if(args.length >= argsIndex + 2) {
			try {
				memberId = Integer.parseInt(args[0 + argsIndex]);
				passwordHash = args[1 + argsIndex];
				launched = true;
				
				//we'll launch web interface after GUI so it works properly
			} catch(NumberFormatException e) {
				//in case memberId parsing failed
			}
		}
		
		//initiate GUI
		frame = new GCFrame(this, cloudInterface, launched); //launched=true indicates that we don't want to show login panel
		frame.init();
		
		//cloud interface tells chat panel about current users in channel
		cloudInterface.setChatPanel(frame.getChatPanel());
		
		//load sounds
		soundManager.init();
		
		//startup game host that acts as proxy
		host = new GCHost(cloudInterface);
		host.init();
		
		//stores games currently being broadcasted
		list = new GCList(host);
		timer.schedule(list, 0, 10000);
		
		//allow cloud interface to send query responses to game list
		cloudInterface.setGCList(list);
		
		if(launched) {
			//launch web interface if needed
			webInterface.init(memberId, passwordHash);
		}
	}
	
	public static void main(String args[]) {
		println(GCLIENT_VERSION_STRING);
		
		String action = null;
		int argsIndex = 0; //index in args that we're currently examining
		String propertiesFile = null;
		
		if(args.length >= argsIndex + 1) {
			//search for this
			//if current argument starts with this, it means it's
			// a join request through protocol handler
			String urlString = "ghostclient:?connect=";
			
			if(args[argsIndex].startsWith(urlString)) {
				action = "join";
				args = new String[] {args[argsIndex].substring(urlString.length())}; //bot id
				argsIndex = 0; //let it load the target bot ID as the next argument
			} else {
				propertiesFile = args[argsIndex];
				argsIndex++;
				
				if(args.length >= argsIndex + 1 && args[argsIndex].startsWith("action=")) {
					action = args[argsIndex].substring(7);
					argsIndex++;
				}
			}
		}
		
		//set default config file if needed
		if(propertiesFile == null) {
			propertiesFile = new File(GCUtil.getContainingDirectory(), "gclient.cfg").getAbsolutePath();
		}
		
		boolean result = Config.init(propertiesFile);
		if(!result) return; //fatal error
		
		String logFile = Config.getString("log", new File(GCUtil.getContainingDirectory(), "gclient.log").getAbsolutePath());
		
		if(logFile != null) {
			logTarget = new File(logFile);
		}
		
		//set look and feel
		String lookAndFeelClass = Config.getString("ui_lookandfeel", "com.seaglasslookandfeel.SeaGlassLookAndFeel");
		
		try {
			UIManager.setLookAndFeel(lookAndFeelClass);
		} catch(Exception e) {
			e.printStackTrace();
			//just use default then
		}
		
		println("[GhostClient] Starting up");
		
		client = new GhostClient(action, args, argsIndex);
	}
	
	//called when we should authenticate
	public void eventWebAuthenticate(String username, String password) {
		boolean success = webInterface.init(username, password);
		
		if(!success) {
			frame.setScreen("login");
			JOptionPane.showMessageDialog(frame, "Username or password incorrect.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	//called when web authentication is complete and successful
	public void eventWebAuthenticated() {
		println("[GhostClient] Web interface authenticated");
		
		//get cookie information
		String[] cookieInfo = webInterface.getCookieInformation();
		
		if(cookieInfo[0].equals("fail") || cookieInfo[1].equals("fail") || cookieInfo[0].equals("0")) {
			webInterface.quit(false); //close web interface
			eventWebDisconnected();
			return;
		}
		
		int user_id = Integer.parseInt(cookieInfo[0]);
		byte[] sessionKey = GCUtil.hexDecode(cookieInfo[1]);
		
		//set screen on frame to show that we're done logging on to website (if it hasn't been done already)
		//we only do this if frame is initialized though
		// if it's not initialized, it probably means that username and password were passed through command line
		// in this case, frame will automatically be set to chat by default
		if(frame != null) {
			frame.setScreen("chat");
		}
		
		//startup cloud interface with the user id and session key to authenticate
		if(!cloudInterface.isAuthenticated()) cloudInterface.init(user_id, sessionKey, true);
	}
	
	//called from web interface to let us know that it disconnected
	public void eventWebDisconnected() {
		println("[GhostClient] Web interface disconnected");
		
		if(frame != null) {
			frame.setScreen("login");
			JOptionPane.showMessageDialog(frame, "Disconnected from website, please login again.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	//called from cloud interface in case there was issue with web authentication information
	public void webIssue() {
		println("[GhostClient] Web interface issue");
		webInterface.authIssue();
	}
	
	public synchronized void quit() {
		//make sure that we haven't quit already
		//if we don't do this, we could trigger an infinite loop of quitting!
		if(!quit) {
			quit = true;
			println("[GhostClient] Quit called, shutting down");
			
			//shut down all the threads
	        host.deinit();
	        cloudInterface.quit();
	        frame.quit();
	        loaderInterface.deinit();
	        webInterface.quit(true);
	        timer.cancel();
	        
	        //save preferences
	        Config.deinit();
	        
	        //in case there's frames still active that we don't know about, list and terminate
	        Frame[] frames = Frame.getFrames();
	        
	        for(Frame frame : frames) {
	        	frame.dispose();
	        }
		} else {
			println("[GhostClient] Quit called but we already have quit the application");
		}
    }
	
	//mostly called from CloudInterface
	public GCHost getGameHost() {
		return host;
	}
	
	public static void appendLog(String username, String message) {
		if(client != null && client.frame != null && client.frame.getChatPanel() != null) {
			client.frame.getChatPanel().append(username, message);
			
			if(client.host != null) {
				client.host.sendLocalChat(username + ": " + message);
			}
		} else {
			println("[GhostClient] [" + username + "] " + message);
		}
	}
	
	public static void println(String message) {
		System.out.println(message);
		
		//output to file
		if(logTarget != null) {
			DateFormat dateFormat = new SimpleDateFormat("d MMM HH:mm:ss");
			Calendar cal = Calendar.getInstance();
			String strDate = dateFormat.format(cal.getTime());
			
			try {
				PrintWriter out = new PrintWriter(new FileWriter(logTarget, true));
				out.println("[" + strDate + "] " + message);
				out.close();
			} catch(IOException ioe) {
				System.out.println("[GhostClient] Output to " + logTarget + " failed; disabling");
				logTarget = null;
			}
		}
	}
}