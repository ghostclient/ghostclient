package com.ghostclient.ghostclient;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.ghostclient.ghostclient.deny.AntiFlood;
import com.ghostclient.ghostclient.game.GCConnection;
import com.ghostclient.ghostclient.game.GCHost;
import com.ghostclient.ghostclient.game.GCList;
import com.ghostclient.ghostclient.graphics.ChatPanel;
import com.ghostclient.ghostclient.graphics.SoundManager;

public class CloudInterface implements Runnable {
	public static final int PACKET_AUTHENTICATE = 0;
	public static final int PACKET_CHANNELEVENT = 1;
	public static final int PACKET_SAYCHANNEL = 2;
	public static final int PACKET_JOINGAME = 3;
	public static final int PACKET_QUERYGAME = 4;
	public static final int PACKET_QUERYGAMENAME = 5;
	public static final int PACKET_SAYWHISPER = 6;
	public static final int PACKET_SAYWHISPERBOT = 7;
	public static final int PACKET_IGNORE = 8;
	public static final int PACKET_NOOP = 9;
	public static final int PACKET_TOP = 10;
	public static final int PACKET_LOBBY = 11;
	public static final int PACKET_ERROR = 12;
	public static final int PACKET_WHOIS = 14;
	public static final int PACKET_SETGAME = 15;
	public static final int PACKET_SLOTS = 17;
	public static final int PACKET_KICK = 18;
	public static final int PACKET_BAN = 19;
	public static final int PACKET_DROP = 20;
	public static final int PACKET_IPCHECK = 21;
	public static final int PACKET_USERCHECK = 22;
	public static final int PACKET_PING = 23;
	
	public static final int CHANNELEVENT_ADDUSER = 0;
	public static final int CHANNELEVENT_REMOVEUSER = 1;
	public static final int CHANNELEVENT_LIST = 2;
	public static final int CHANNELEVENT_JOIN = 3;
	public static final int CHANNELEVENT_LEAVE = 4;
	public static final int CHANNELEVENT_PROMOTE = 5;
	
	public static String CLOUD_HOST = "cloud.ghostclient.com";
	public static int CLOUD_PORT = 7115;
	
	//socket objects
	Socket socket;
	DataOutputStream out;
	DataInputStream in;
	
	//client
	GhostClient client;
	
	//sounds
	SoundManager soundManager;
	
	//authentication information to confirm our session with server
	int user_id;
	byte[] sessionKey;
	
	//session information, null until we authenticate
	String username;
	List<String> currentChannels; //channel will be null until we join one explicitly
	boolean authenticated;
	boolean initiated; //whether we already initiated
	
	//prevent client from flooding: it could get us dropped from server
	AntiFlood antiFlood;
	
	//set if we are quitting
	boolean quit;
	
	//games are broadcasted here, if not null
	GCList gcList;
	
	//chat panel to broadcast messages and channel users to
	ChatPanel chatPanel;
	
	//last user that whispered us, or null if none
	String lastWhisperer;
	
	public CloudInterface(GhostClient client, Timer timer, SoundManager soundManager) {
		this.client = client;
		this.soundManager = soundManager;
		
		username = null;
		currentChannels = new ArrayList<String>();
		authenticated = false;
		lastWhisperer = null;
		quit = false;
		
		//don't send more than 500 bytes every 6 sec
		//note: this is actually not just bytes, because we add 20 bytes for each packet even if it's less
		antiFlood = new AntiFlood(500, 6000);
		
		//set host and port
		CLOUD_HOST = Config.getString("cloud_host", CLOUD_HOST);
		CLOUD_PORT = Config.getInt("cloud_port", CLOUD_PORT);
		
		//start noop timer task
		// even though we're not connected, we can still do the task
		timer.schedule(new NoopTask(), 0, 60000);
	}
	
	//user_id and sessionKey are used for authentication
	//newAuth indicates whether these are newly received or whether they were from reconnection attempt
	public void init(int user_id, byte[] sessionKey, boolean newAuth) {
		synchronized(this) {
			if(initiated) {
				//if newAuth, don't reinitiate, but do update our auth info
				if(newAuth) {
					this.user_id = user_id;
					this.sessionKey = sessionKey;
				}
				
				return;
			}
			
			initiated = true;
		}
		
		this.user_id = user_id;
		this.sessionKey = sessionKey;
		
		GhostClient.println("[CloudInterface] Set user_id = " + user_id);
		GhostClient.println("[CloudInterface] Set session_id = " + GCUtil.hexEncode(sessionKey));
		
		GhostClient.println("[CloudInterface] Connecting to GCloud server");
		GhostClient.appendLog("Cloud", "Connecting to GCloud...");
		
		InetAddress address;
		
		try {
			address = InetAddress.getByName(CLOUD_HOST);
		} catch(IOException ioe) {
			GhostClient.println("[CloudInterface] Failed to resolve " + CLOUD_HOST + ": " + ioe.getLocalizedMessage());
			GhostClient.appendLog("Cloud", "Error during connection: failed to resolve address " + CLOUD_HOST);
			new Thread(this).start();
			return;
		}
		
		try {
			socket = new Socket(address, CLOUD_PORT);
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
		} catch(IOException ioe) {
			GhostClient.println("[CloudInterface] Failed to connect to GCloud: " + ioe.getLocalizedMessage());
			GhostClient.appendLog("Cloud", "Error during connection: " + ioe.getLocalizedMessage() + ".");
			new Thread(this).start();
			return;
		}
		
		new Thread(this).start();
	}
	
	public void setChatPanel(ChatPanel chatPanel) {
		this.chatPanel = chatPanel;
	}
	
	public void setGCList(GCList gcList) {
		this.gcList = gcList;
	}
	
	public synchronized void quit() {
		if(!quit) {
			quit = true;
			
			if(socket != null) {
				try {
					socket.close();
				} catch(IOException ioe) {}
			}
			
			//notify so that if we're sleeping for reconnect, it stops
			synchronized(this) {
				this.notify();
			}
		}
	}
	
	public String getUsername() {
		if(username == null) return "Not logged in";
		else return username;
	}
	
	public boolean isAuthenticated() {
		return authenticated;
	}
	
	public String getLastWhisperer() {
		return lastWhisperer;
	}
	
	public void run() {
		GhostClient.println("[CloudInterface] Thread starting: " + Thread.currentThread().getName());
		
		boolean stop = socket == null || !socket.isConnected();
		
		if(!stop) {
			GhostClient.println("[CloudInterface] Authenticating...");
			GhostClient.appendLog("Cloud", "Authenticating with GCloud");
			
			//first authenticate
			try {
				out.write((byte) 165); //client connection header
				out.write((byte) PACKET_AUTHENTICATE); //authenticate packet identifier
				out.writeShort((short) 12 + sessionKey.length); //packet length
				out.writeInt(user_id); //user ID in big endian
				out.writeInt(GhostClient.GCLIENT_VERSION);
				out.write(sessionKey); //session key
			} catch(IOException ioe) {
				ioe.printStackTrace();
				
				GhostClient.println("[CloudInterface] Error while sending authentication packet");
				stop = true;
			}
		}
		
		//create byte buffer
		ByteBuffer buf = ByteBuffer.allocate(65536);
			
		while(!quit && !stop) {
			try {
				//packet structure here is similar to with battle.net
				//except** we use big endian instead of little endian
				int header = in.read();

				if(header == -1) {
					GhostClient.println("[CloudInterface] Remote disconnected");
					break;
				}
				
				int identifier = in.read();
				int length = in.readShort() - 4;
				
				if(length < 0) {
					GhostClient.println("[CloudInterface] Invalid packet length=" + length);
					break;
				}
				
				in.readFully(buf.array(), 0, length);
				buf.position(0);
				
				if(identifier == PACKET_AUTHENTICATE) { //authenticate
					username = GCUtil.getTerminatedString(buf);
					
					if(username.isEmpty()) {
						//authentication failed
						GhostClient.appendLog("Cloud", "Authentication rejected");
						client.webIssue();
						break;
					}
					System.out.println(username);
					
					authenticated = true;
					
					GhostClient.println("[CloudInterface] Successfully authenticated as " + username);
					GhostClient.appendLog("Cloud", "Successfully authenticated as [" + username + "]");
					GhostClient.appendLog("Cloud", "Type /help to list commands.");
					GhostClient.appendLog("Cloud", "To join a channel, type /j channelname.");
					GhostClient.appendLog("Cloud", "To join the default lobby, type /lobby.");
				} else if(identifier == PACKET_CHANNELEVENT) { //channel user event
					int status = GCUtil.unsignedByte(buf.get());
					String channel = GCUtil.getTerminatedString(buf);
					String otherName = GCUtil.getTerminatedString(buf);
					
					chatPanel.channelUsers(channel, otherName, status);
					
					if(status == CHANNELEVENT_JOIN) {
						synchronized(currentChannels) {
							currentChannels.add(channel.toLowerCase());
						}
						
						GhostClient.appendLog("Cloud", "You are now speaking in channel [" + channel + "].");
						
						//add current user to the channel
						chatPanel.channelUsers(channel, username, 0);
					} else if(status == CHANNELEVENT_LEAVE) {
						synchronized(currentChannels) {
							currentChannels.remove(channel.toLowerCase());
						}
					} else if(status == CHANNELEVENT_ADDUSER) {
						soundManager.playSound("userJoined");
					} else if(status == CHANNELEVENT_REMOVEUSER) {
						soundManager.playSound("userLeft");
					}
				} else if(identifier == PACKET_SAYCHANNEL) { //say channel
					String channel = GCUtil.getTerminatedString(buf);
					String otherName = GCUtil.getTerminatedString(buf);
					String message = GCUtil.getTerminatedString(buf);
					chatPanel.append(channel, otherName, message);
				} else if(identifier == PACKET_QUERYGAME && length >= 17) { //query game response
					if(gcList != null) {
						int cookie = buf.get();
						
						byte[] addr = new byte[4];
						buf.get(addr);
						int port = GCUtil.unsignedShort(buf.getShort());
						int hostCounter = buf.getInt();
						String gamename = GCUtil.getTerminatedString(buf);
						
						if(length >= gamename.length() + 16) {
							byte[] statString = GCUtil.getTerminatedArray(buf);
							
							if(length >= gamename.length() + statString.length + 16) {
								int botId = buf.getInt();
								String botname = ""; //botname is optional in this packet
								
								if(length >= gamename.length() + statString.length + 17) {
									botname = GCUtil.getTerminatedString(buf);
								}
								
								//cookie == 0 is to determine whether the bot will immediately display the game
								// we only immediately display it if user did manual request
								// otherwise, two games could show up in user's list
								gcList.addGame(addr, port, hostCounter, gamename, statString, botId, botname, cookie == 0);
								
								if(cookie == 0) { //we send 0 for cookie byte if we wish to display the response in log
									GhostClient.appendLog("Cloud", "Broadcasting game: [" + gamename + "]");
								}
							} else {
								GhostClient.println("[CloudInterface] Bad query game packet (2)");
							}
						} else {
							GhostClient.println("[CloudInterface] Bad query game packet (1)");
						}
					}
				} else if(identifier == PACKET_SAYWHISPER) { //say whisper
					String otherName = GCUtil.getTerminatedString(buf);
					String message = GCUtil.getTerminatedString(buf);
					GhostClient.appendLog(otherName + " -> You", message);
					
					//play sound
					soundManager.playSound("otherChat");
					
					//update last user that whispered us
					//this let's us reply more easily
					lastWhisperer = otherName;
				} else if(identifier == PACKET_ERROR) { //error
					String error = GCUtil.getTerminatedString(buf);
					GhostClient.appendLog("Error", error);
					soundManager.playSound("error");
				} else if(identifier == PACKET_PING) { //ping
					GhostClient.println("[CloudInterface] Responding to ping");
					sendPing(buf.getLong());
				}
			} catch(IOException ioe) {
				GhostClient.println("[CloudInterface] Error while reading packet");
				break;
			}
		}
		
		//make sure socket is closed
		if(socket != null) {
			try {
				socket.close();
			} catch(IOException ioe) {}
		}
		
		synchronized(currentChannels) {
			//clear users in GUI; also reset channel to null
			// and do it for each tab
			for(String channel : currentChannels) {
				chatPanel.channelUsers(channel, "", CHANNELEVENT_LEAVE);
			}
			
			currentChannels.clear();
		}
		
		synchronized(this) {
			username = null;
			authenticated = false;
			initiated = false;
			
			//try to restart server if we're not quitting
			if(!quit) {
				GhostClient.appendLog("Cloud", "Reconnecting in thirty seconds...");
				
				try {
					this.wait(8000);
				} catch(InterruptedException e) {}
				
				//check again in case during the sleep quit was called and woke us
				if(!quit) {
					init(user_id, sessionKey, false);
				}
			}
		}
		
		GhostClient.println("[CloudInterface] Thread exiting: " + Thread.currentThread().getName());
	}
	
	public List<String[]> processMessage(String channel, String message) {
		List<String[]> messageDisplay = new ArrayList<String[]>();
		
		if(message.charAt(0) == '/') {
			String[] messageParts = message.substring(1).split(" ", 2);
			String[] parts = new String[] {messageParts[0], ""};
			
			if(messageParts.length == 2) {
				parts[1] = messageParts[1];
			}
			
			if(parts[0].equalsIgnoreCase("j") || parts[0].equalsIgnoreCase("join")) {
				synchronized(currentChannels) {
					if(parts[1].isEmpty()) {
						messageDisplay.add(new String[] {"System", "Please specify a channel to join."});
						soundManager.playSound("error");
					} else if(currentChannels.contains(parts[1].toLowerCase())) {
						messageDisplay.add(new String[] {"System", "You are already speaking in [" + parts[1] + "]"});
						soundManager.playSound("error");
					} else {
						//leave the old channel
						if(channel != null) {
							channelEvent(channel, false);
						}
						
						//join channel
						channelEvent(parts[1], true);
						messageDisplay.add(new String[] {"System", "Entering channel " + parts[1] + "..."});
					}
				}
			} else if(parts[0].equalsIgnoreCase("help") || parts[0].equalsIgnoreCase("h")) {
				messageDisplay.add(new String[] {"System", "Type /join to switch channels."});
				messageDisplay.add(new String[] {"System", "Type /lobby to join the lobby channel for your country."});
				messageDisplay.add(new String[] {"System", "Type /queryname <botname> to broadcast the game of a bot."});
				messageDisplay.add(new String[] {"System", "Type /whisper <name> <message> to whisper to a player."});
				messageDisplay.add(new String[] {"System", "Type /channel <name> to enter a private channel with a player."});
				messageDisplay.add(new String[] {"System", "Type /say <message> to say a message in channel."});
				messageDisplay.add(new String[] {"System", "Type /bot <name> <message> to whisper to a bot."});
				messageDisplay.add(new String[] {"System", "Type /ignore <name> to ignore a player."});
				messageDisplay.add(new String[] {"System", "Type /top to list the top channels."});
				messageDisplay.add(new String[] {"System", "Type /quiet to not receive Ghost Client messages in-game."});
				messageDisplay.add(new String[] {"System", "Type /silence to not receive any messages in-game."});
				messageDisplay.add(new String[] {"System", "Type /mute <playername> to ignore messages from a certain player in-game."});
				messageDisplay.add(new String[] {"System", "Type /spoofcheck to manually spoofcheck in-game."});
				messageDisplay.add(new String[] {"System", "Type /quit to close the client."});
			} else if(parts[0].equalsIgnoreCase("queryid") || parts[0].equalsIgnoreCase("qid")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify a bot ID to query."});
					soundManager.playSound("error");
				} else {
					try {
						gameQuery(Integer.parseInt(parts[1]), true);
					} catch(NumberFormatException e) {
						messageDisplay.add(new String[] {"System", "Invalid bot ID supplied."});
						soundManager.playSound("error");
					}
				}
			} else if(parts[0].equalsIgnoreCase("queryname") ||
					parts[0].equalsIgnoreCase("qname") ||
					parts[0].equalsIgnoreCase("qn")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify a bot name to query."});
					soundManager.playSound("error");
				} else {
					gameQuery(parts[1]);
				}
			} else if(parts[0].equalsIgnoreCase("say") || parts[0].equalsIgnoreCase("/")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify a message to send to channel."});
					soundManager.playSound("error");
				} else if(channel == null) {
					messageDisplay.add(new String[] {"System", "You are not currently in a channel."});
				} else {
					boolean success = sayChannel(channel, parts[1]);
					messageDisplay.add(new String[] {username, parts[1]});
					
					if(success) soundManager.playSound("youChat");
				}
			} else if(parts[0].equalsIgnoreCase("w") || parts[0].equalsIgnoreCase("whisper") ||
					parts[0].equalsIgnoreCase("b") || parts[0].equalsIgnoreCase("bot")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify a username and message."});
				} else {
					String[] whisperParts = parts[1].split(" ", 2);
					
					if(whisperParts.length < 2 || whisperParts[1].isEmpty()) {
						messageDisplay.add(new String[] {"System", "What would you like to say?"});
					} else {
						if(parts[0].equalsIgnoreCase("b") || parts[0].equalsIgnoreCase("bot")) {
							//we are explicitly whispering a bot, prefix a : to username
							whisperParts[0] = ":" + whisperParts[0];
						}
						
						sayWhisper(whisperParts[0], whisperParts[1]);
						messageDisplay.add(new String[] {"You -> " + whisperParts[0], whisperParts[1]});
						
						//play sound
						soundManager.playSound("youChat");
					}
				}
			} else if(parts[0].equalsIgnoreCase("ignore") || parts[0].equalsIgnoreCase("unignore")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify a target username."});
					soundManager.playSound("error");
				} else {
					boolean add = parts[0].equalsIgnoreCase("ignore");
					String addMessage = add ? "now ignoring" : "no longer ignoring";
					alterIgnore(add, parts[1]);
					messageDisplay.add(new String[] {"System", "You are " + addMessage + " " + parts[1]});
					
					if(add) {
						messageDisplay.add(new String[] {"System", "Use /unignore <username> to unignore the user."});
					}
				}
			} else if(parts[0].equalsIgnoreCase("list") || parts[0].equalsIgnoreCase("top")) {
				sendTopChannels();
			} else if(parts[0].equalsIgnoreCase("lobby") || parts[0].equalsIgnoreCase("l")) {
				//leave the old channel
				if(channel != null) {
					channelEvent(channel, false);
				}
				
				messageDisplay.add(new String[] {"System", "Entering lobby..."});
				sendLobby();
			} else if(parts[0].equalsIgnoreCase("quit")) {
				client.quit();
			} else if(parts[0].equalsIgnoreCase("quiet")) {
				messageDisplay.add(new String[] {"System", "Ghost Client messages will not be forwarded to in-game local chat."});
				
				GCHost host = client.getGameHost();
				
				if(host != null) {
					host.setQuietMode(GCHost.QUIET_GHOSTCLIENT);
				}
			} else if(parts[0].equalsIgnoreCase("silence")) {
				messageDisplay.add(new String[] {"System", "You will not receive Ghost Client messages or messages from other players in-game."});
				
				GCHost host = client.getGameHost();
				
				if(host != null) {
					host.setQuietMode(GCHost.QUIET_ALL);
				}
			} else if(parts[0].equalsIgnoreCase("unsilence") || parts[0].equalsIgnoreCase("unquiet")) {
				messageDisplay.add(new String[] {"System", "All messages will be received in-game."});
				
				GCHost host = client.getGameHost();
				
				if(host != null) {
					host.setQuietMode(GCHost.QUIET_OFF);
				}
			} else if(parts[0].equalsIgnoreCase("channel") || parts[0].equalsIgnoreCase("ch")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to open private channel with."});
					soundManager.playSound("error");
				} else {
					String privateChannel = "w:" + parts[1] + ":" + username;
					
					synchronized(currentChannels) {
						if(currentChannels.contains(privateChannel.toLowerCase())) {
							messageDisplay.add(new String[] {"System", "You are already speaking in [" + parts[1] + "]"});
							soundManager.playSound("error");
						} else {
							//leave the old channel
							if(channel != null) {
								channelEvent(channel, false);
							}
							
							//join channel
							channelEvent(privateChannel, true);
							messageDisplay.add(new String[] {"System", "Entering private channel with " + parts[1] + "..."});
						}
					}
				}
			} else if(parts[0].equalsIgnoreCase("spoofcheck") || parts[0].equalsIgnoreCase("sc")) {
				//attempt to manually spoofcheck by whispering "sc"
				GCHost host = client.getGameHost();
				
				if(host != null) {
					GCConnection currentGame = host.getCurrentGame();
					
					if(currentGame != null) {
						String botName = currentGame.getBotName();
						
						if(botName != null) {
							sayWhisper(":" + botName, "sc");
						}
					}
				}
			} else if(parts[0].equalsIgnoreCase("whois")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to whois."});
					soundManager.playSound("error");
				} else {
					sendWhois(parts[1]);
				}
			} else if(parts[0].equalsIgnoreCase("kick")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to kick."});
					soundManager.playSound("error");
				} else {
					sendKick(channel, parts[1]);
				}
			} else if(parts[0].equalsIgnoreCase("ban")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to ban."});
					soundManager.playSound("error");
				} else {
					sendBan(channel, parts[1], false);
				}
			} else if(parts[0].equalsIgnoreCase("unban")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to unban."});
					soundManager.playSound("error");
				} else {
					sendBan(channel, parts[1], true);
				}
			} else if(parts[0].equalsIgnoreCase("drop")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to drop."});
					soundManager.playSound("error");
				} else {
					sendDrop(parts[1]);
				}
			} else if(parts[0].equalsIgnoreCase("ip") || parts[0].equalsIgnoreCase("ipcheck")) {
				if(parts[1].isEmpty()) {
					messageDisplay.add(new String[] {"System", "Please specify user to check IP of."});
					soundManager.playSound("error");
				} else {
					sendIPCheck(parts[1]);
				}
			} else if(parts[0].equalsIgnoreCase("ping") || parts[0].equalsIgnoreCase("usercheck")) {
				sendUserCheck(parts[1]);
			} else if(parts[0].equalsIgnoreCase("disconnect")) {
				try {
					socket.close();
				} catch(IOException ioe) {}
			} else {
				messageDisplay.add(new String[] {"System", "Unknown command. Type /help for help."});
				soundManager.playSound("error");
			}
		} else {
			if(channel != null) {
				boolean success = sayChannel(channel, message);
				messageDisplay.add(new String[] {username, message});
				
				if(success) soundManager.playSound("youChat");
			} else {
				messageDisplay.add(new String[] {"System", "You are not currently in a channel."});
				soundManager.playSound("error");
			}
		}
		
		return messageDisplay;
	}
	
	public boolean sayChannel(String channel, String message) {
		GhostClient.println("[CloudInterface] Local: " + message);
		
		synchronized(currentChannels) {
			if(channel != null && currentChannels.contains(channel.toLowerCase()) &&
					socket != null && socket.isConnected() && authenticated) {
				synchronized(out) {
					byte[] messageBytes = GCUtil.strToBytes(message);
					byte[] channelBytes = GCUtil.strToBytes(channel);
					int len = channelBytes.length + messageBytes.length + 6;
					
					if(!antiFlood.checkFlood(len + 75)) {
						try {
							out.write((byte) 165); //client connection header
							out.write((byte) PACKET_SAYCHANNEL); //say channel packet identifier
							out.writeShort((short) len); //packet length
							out.write(channelBytes);
							out.write(0);
							out.write(messageBytes);
							out.write((byte) 0);
						} catch(IOException ioe) {}
						
						return true;
					} else {
						GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
						soundManager.playSound("error");
						return false;
					}
				}
			} else {
				GhostClient.appendLog("System", "Could not send message: not in channel or not connected.");
				soundManager.playSound("error");
				return false;
			}
		}
	}
	
	public void channelEvent(String channel, boolean join) {
		GhostClient.println("[CloudInterface] Sending channel event: " + channel + ", " + join);
		
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				byte[] channelBytes = GCUtil.strToBytes(channel);
				int len = channelBytes.length + 9;
				int status = join ? CHANNELEVENT_JOIN : CHANNELEVENT_LEAVE; //int determining if we're joining or leaving channel

				//check flood, but if we're leaving channel make sure it goes through
				if(!antiFlood.checkFlood(len + 30) || !join) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_CHANNELEVENT); //join channel packet identifier
						out.writeShort((short) len); //packet length
						out.writeInt(status);
						out.write(channelBytes);
						out.write((byte) 0);
					} catch(IOException ioe) {}
				} else {
					GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
					soundManager.playSound("error");
				}
			}
		} else {
			GhostClient.appendLog("System", "Could not join channel: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void spoofCheck(int botId) {
		if(socket != null && socket.isConnected() && authenticated) {
			antiFlood.checkFlood(8 + 75);
			
			synchronized(out) {
				try {
					out.write((byte) 165); //client connection header
					out.write((byte) PACKET_JOINGAME); //spoof check packet identifier
					out.writeShort((short) 8); //packet length
					out.writeInt(botId);
				} catch(IOException ioe) {}
			}
		} else {
			GhostClient.appendLog("System", "Could not query game info: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void gameQuery(int botId, boolean displayResponse) {
		if(socket != null && socket.isConnected() && authenticated) {
			//we don't want game query to fail, so just notify that we're sending this many bytes
			//also we say we're sending more because game query might be more expensive
			antiFlood.checkFlood(9 + 100);
			
			synchronized(out) {
				try {
					out.write((byte) 165); //client connection header
					out.write((byte) PACKET_QUERYGAME); //game query packet identifier
					out.writeShort((short) 9); //packet length
					out.write(displayResponse ? 0 : 1); //cookie byte is 0 if we wish to display response
					out.writeInt(botId);
				} catch(IOException ioe) {}
			}
		} else {
			GhostClient.appendLog("System", "Could not query game info: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void gameQuery(String botname) {
		if(socket != null && socket.isConnected() && authenticated) {
			//bot name is lowercase in our queries
			byte[] nameBytes = GCUtil.strToBytes(botname.toLowerCase());
			
			//we don't want game query to fail, so just notify that we're sending this many bytes
			//also we say we're sending more because game query might be more expensive
			antiFlood.checkFlood(5 + nameBytes.length + 100);
			
			synchronized(out) {
				try {
					out.write((byte) 165); //client connection header
					out.write((byte) PACKET_QUERYGAMENAME); //game query (by NAME) packet identifier
					out.writeShort((short) 5 + nameBytes.length); //packet length
					out.write(nameBytes);
					out.write((byte) 0); //null terminator
				} catch(IOException ioe) {}
			}
		} else {
			GhostClient.appendLog("System", "Could not query game info: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void sayWhisper(String target, String message) {
		GhostClient.println("[CloudInterface] You -> " + target + ": " + message);
		
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				byte[] messageBytes = GCUtil.strToBytes(message);
				byte[] targetBytes = GCUtil.strToBytes(target);
				int len = targetBytes.length + messageBytes.length + 6;
				
				if(!antiFlood.checkFlood(len + 75)) {
					try {
						
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_SAYWHISPER); //say whisper packet identifier
						out.writeShort((short) len); //packet length
						out.write(targetBytes);
						out.write((byte) 0);
						out.write(messageBytes);
						out.write((byte) 0);
					} catch(IOException ioe) {}
				} else {
					GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
					soundManager.playSound("error");
				}
			}
		} else {
			GhostClient.appendLog("System", "Could not send whisper: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void alterIgnore(boolean add, String user) {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				byte[] userBytes = GCUtil.strToBytes(user);
				int len = userBytes.length + 6;
				
				if(!antiFlood.checkFlood(len)) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_IGNORE); //ignore packet identifier
						out.writeShort((short) len); //packet length
						out.write(add ? 1 : 0);
						out.write(userBytes);
						out.write((byte) 0);
					} catch(IOException ioe) {}
				} else {
					GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
					soundManager.playSound("error");
				}
			}
		} else {
			GhostClient.appendLog("System", "Could not ignore: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void sendNoop() {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				if(!antiFlood.checkFlood(4)) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_NOOP); //no-op packet identifier
						out.writeShort((short) 4); //packet length
					} catch(IOException ioe) {}
				} else {
					//probably don't need no-op packet if we're flooding :P
				}
			}
		} else {
			//ignore
		}
	}
	
	class NoopTask extends TimerTask {
		public void run() {
			sendNoop();
		}
	}
	
	public void sendTopChannels() {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				if(!antiFlood.checkFlood(4)) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_TOP); //query top channels packet identifier
						out.writeShort((short) 4); //packet length
					} catch(IOException ioe) {}
				} else {
					GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
					soundManager.playSound("error");
				}
			}
		} else {
			GhostClient.appendLog("System", "Could not query top channels: not connected.");
			soundManager.playSound("error");
		}
	}
	
	public void sendLobby() {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				if(!antiFlood.checkFlood(100)) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_LOBBY); //lobby packet identifier
						out.writeShort((short) 4); //packet length
					} catch(IOException ioe) {}
				} else {
					GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
					soundManager.playSound("error");
				}
			}
		} else {
			GhostClient.appendLog("System", "Could not request lobby: not connected.");
			soundManager.playSound("error");
		}
	}
	
	
	public void sendWhois(String username) {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				byte[] userBytes = GCUtil.strToBytes(username);
				int len = userBytes.length + 5;

				if(!antiFlood.checkFlood(len)) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_WHOIS); //whois packet identifier
						out.writeShort((short) len); //packet length
						out.write(userBytes);
						out.write((byte) 0);
					} catch(IOException ioe) {}
				} else {
					GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
					soundManager.playSound("error");
				}
			}
		}
	}
	
	public void sendSetGame(String gamename) {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				byte[] gamenameBytes = GCUtil.strToBytes(gamename);
				int len = gamenameBytes.length + 5;
				antiFlood.checkFlood(len); //ignore flood checking, but still pass it through
				
				try {
					out.write((byte) 165); //client connection header
					out.write((byte) PACKET_SETGAME); //set game packet identifier
					out.writeShort((short) len); //packet length
					out.write(gamenameBytes);
					out.write((byte) 0);
				} catch(IOException ioe) {}
			}
		}
	}
	
	public void sendSlots(int botid, int slotsTaken, int slotsTotal) {
		if(socket != null && socket.isConnected() && authenticated) {
			if(!antiFlood.checkFlood(12)) {
				synchronized(out) {
					try {
						out.write((byte) 165); //client connection header
						out.write((byte) PACKET_SLOTS); //slots packet identifier
						out.writeShort((short) 16); //packet length
						out.writeInt(botid);
						out.writeInt(slotsTaken);
						out.writeInt(slotsTotal);
					} catch(IOException ioe) {}
				}
			}
		}
	}
	
	public void sendKick(String channel, String name) {
		synchronized(currentChannels) {
			if(channel != null && currentChannels.contains(channel.toLowerCase()) &&
					socket != null && socket.isConnected() && authenticated) {
				synchronized(out) {
					byte[] channelBytes = GCUtil.strToBytes(channel);
					byte[] nameBytes = GCUtil.strToBytes(name);
					int len = channelBytes.length + nameBytes.length + 6;
					
					if(!antiFlood.checkFlood(len + 30)) {
						try {
							out.write((byte) 165); //client connection header
							out.write((byte) PACKET_KICK); //kick packet identifier
							out.writeShort((short) len); //packet length
							out.write(channelBytes);
							out.write(0);
							out.write(nameBytes);
							out.write((byte) 0);
						} catch(IOException ioe) {}
					} else {
						GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
						soundManager.playSound("error");
					}
				}
			} else {
				GhostClient.appendLog("System", "Could not kick: you are not in channel or not connected.");
				soundManager.playSound("error");
			}
		}
	}
	
	public void sendBan(String channel, String name, boolean unban) {
		synchronized(currentChannels) {
			if(channel != null && currentChannels.contains(channel.toLowerCase()) &&
					socket != null && socket.isConnected() && authenticated) {
				synchronized(out) {
					byte[] channelBytes = GCUtil.strToBytes(channel);
					byte[] nameBytes = GCUtil.strToBytes(name);
					int len = channelBytes.length + nameBytes.length + 7;
					
					if(!antiFlood.checkFlood(len + 30)) {
						try {
							out.write((byte) 165); //client connection header
							out.write((byte) PACKET_BAN); //ban packet identifier
							out.writeShort((short) len); //packet length
							out.write(unban ? 1 : 0); //whether we're banning or unbanning
							out.write(channelBytes);
							out.write(0);
							out.write(nameBytes);
							out.write((byte) 0);
						} catch(IOException ioe) {}
					} else {
						GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
						soundManager.playSound("error");
					}
				}
			} else {
				GhostClient.appendLog("System", "Could not ban: you are not in channel or not connected.");
				soundManager.playSound("error");
			}
		}
	}
	
	public void sendDrop(String name) {
		synchronized(currentChannels) {
			if(socket != null && socket.isConnected() && authenticated) {
				synchronized(out) {
					byte[] nameBytes = GCUtil.strToBytes(name);
					int len = nameBytes.length + 5;
					
					if(!antiFlood.checkFlood(len)) {
						try {
							out.write((byte) 165); //client connection header
							out.write((byte) PACKET_DROP); //drop packet identifier
							out.writeShort((short) len); //packet length
							out.write(nameBytes);
							out.write((byte) 0);
						} catch(IOException ioe) {}
					} else {
						GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
						soundManager.playSound("error");
					}
				}
			} else {
				GhostClient.appendLog("System", "Could not drop: you are not connected.");
				soundManager.playSound("error");
			}
		}
	}
	
	public void sendIPCheck(String name) {
		synchronized(currentChannels) {
			if(socket != null && socket.isConnected() && authenticated) {
				synchronized(out) {
					byte[] nameBytes = GCUtil.strToBytes(name);
					int len = nameBytes.length + 5;
					
					if(!antiFlood.checkFlood(len)) {
						try {
							out.write((byte) 165); //client connection header
							out.write((byte) PACKET_IPCHECK); //ip check packet identifier
							out.writeShort((short) len); //packet length
							out.write(nameBytes);
							out.write((byte) 0);
						} catch(IOException ioe) {}
					} else {
						GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
						soundManager.playSound("error");
					}
				}
			} else {
				GhostClient.appendLog("System", "Could not check IP: you are not connected.");
				soundManager.playSound("error");
			}
		}
	}
	
	public void sendUserCheck(String name) {
		synchronized(currentChannels) {
			if(socket != null && socket.isConnected() && authenticated) {
				synchronized(out) {
					byte[] nameBytes = GCUtil.strToBytes(name);
					int len = nameBytes.length + 5;
					
					if(!antiFlood.checkFlood(len)) {
						try {
							out.write((byte) 165); //client connection header
							out.write((byte) PACKET_USERCHECK); //user check packet identifier
							out.writeShort((short) len); //packet length
							out.write(nameBytes);
							out.write((byte) 0);
						} catch(IOException ioe) {}
					} else {
						GhostClient.appendLog("System", "Please wait a few seconds before sending more messages.");
						soundManager.playSound("error");
					}
				}
			} else {
				GhostClient.appendLog("System", "Could not check IP: you are not connected.");
				soundManager.playSound("error");
			}
		}
	}
	
	public void sendPing(long cookie) {
		if(socket != null && socket.isConnected() && authenticated) {
			synchronized(out) {
				try {
					out.write((byte) 165); //client connection header
					out.write((byte) PACKET_PING); //ping packet identifier
					out.writeShort((short) (12)); //packet length
					out.writeLong(cookie);
				} catch(IOException ioe) {}
			}
		}
	}
}