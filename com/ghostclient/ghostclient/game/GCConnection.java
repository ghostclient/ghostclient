package com.ghostclient.ghostclient.game;



import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.ghostclient.ghostclient.Config;
import com.ghostclient.ghostclient.GCUtil;
import com.ghostclient.ghostclient.GhostClient;

public class GCConnection {
	public static int PLAYERLEAVE_GPROXY = 100;
	
	public static byte[] EMPTYACTION = new byte[] {(byte) 0xF7, (byte) 0x0C, (byte) 0x06, 0, 0, 0};
	
	GCHost host;
	
	Socket localSocket;
	Socket remoteSocket;
	
	DataOutputStream localOut;
	Integer remoteSync;
	Integer localSync;
	DataOutputStream remoteOut;
	
	int sessionkey; //entry key to use to connect to server; GC uses spoofcheck so this should by 0
	GameInfo gameInfo; //host's game information, includes host counter and GCloud bot ID
	
	boolean terminated;
	
	//gproxy variables
	boolean gproxyEnabled;
	boolean gproxy; //whether we're using GProxy++; set to true only if we receive GPS_INIT packet from server
	boolean leaveGameSent; //whether player has already sent the leave game packet
	boolean gameStarted; //whether the game has started
	int pid; //our game PID
	boolean isSynchronized; //false if we are reconnecting to server
	
	int numEmptyActions; //number of empty actions server is using
	int numEmptyActionsUsed; //number we have sent for the last action packet
	boolean actionReceived; //whether we've received an action packet
	long lastActionTime; //last time we received action packet
	long lastConnectionAttemptTime; //last time we tried to connect to server
	
	int totalLocalPackets;
	int totalRemotePackets;
	Queue<byte[]> localBuffer; //local packet buffer
	
	List<Integer> laggers; //list of ID's of players currently lagging
	
	boolean remoteConnected;
	InetAddress remoteAddress;
	int remotePort;
	int remoteKey;
	String remoteName; //host bot name
	int remoteId; //host bot id
	
	List<String> slotPlayers; //from PID to player name
	List<Integer> mutedList; //list of muted PIDs
	
	public GCConnection(GCHost host, Socket socket) {
		this.host = host;
		this.localSocket = socket;
		sessionkey = (int) Config.getLong("sessionkey", 0); //for unsigned int support
		gproxyEnabled = Config.getBoolean("gproxy", true);
		
		gameInfo = null;
		terminated = false;
		
		//initialize GProxy++ settings
		remoteSync = new Integer(0);
		localSync = new Integer(0);
		gproxy = false;
		gameStarted = false;
		actionReceived = false;
		remoteConnected = false;
		leaveGameSent = false;
		isSynchronized = true;
		
		laggers = new ArrayList<Integer>();
		localBuffer = new LinkedList<byte[]>();
		
		slotPlayers = new ArrayList<String>();
		mutedList = new ArrayList<Integer>();
		
		//set initial 12 players
		// the index at 0 should not be used
		for(int i = 0; i < 13; i++) {
			slotPlayers.add("unknown");
		}
		
		try {
			localOut = new DataOutputStream(localSocket.getOutputStream());
		} catch(IOException ioe) {
			GhostClient.println("[GCConnection] Initialization error: " + ioe.getLocalizedMessage());
		}
		
		new GCForward(this, false, localSocket);
	}
	
	public synchronized void eventRemoteDisconnect(DataOutputStream currentRemoteOut) {
		//eventRemoteDisconnect will sometimes be triggered multiple times on the same socket
		//here we make sure that this is the first trigger
		//NOTE: actually this shouldn't be a problem anymore because all disconnect events
		// come from GCForward, but just to make sure we keep the if statement
		if(currentRemoteOut != remoteOut) {
			return;
		}
		
		
		if(remoteSocket != null && remoteSocket.isConnected()) {
			try {
				remoteSocket.close();
			} catch(IOException ioe) {}
		}
		
		remoteSocket = null;
		remoteOut = null;
		
		if(gproxy && !leaveGameSent && actionReceived) {
			sendLocalChat("You have been disconnected from the server.");
			GhostClient.println("[GCConnection] You have been disconnected from the server.");
			
			//calculate time we have remaining to reconnect
			long timeRemaining = (numEmptyActions - numEmptyActionsUsed + 1) * 60 * 1000 - (System.currentTimeMillis() - lastActionTime);
			
			if(timeRemaining < 0) {
				timeRemaining = 0;
			}
			
			sendLocalChat("GProxy++ is attempting to reconnect... (" + (timeRemaining / 1000) + " seconds remain)");
			GhostClient.println("GProxy++ is attempting to reconnect... (" + (timeRemaining / 1000) + " seconds remain)");
			
			//update time
			lastConnectionAttemptTime = System.currentTimeMillis();
			
			//reconnect
			gproxyReconnect();
		} else {
			terminate();
		}
	}
	
	public synchronized void eventLocalDisconnect() {
		// ensure a leavegame message was sent, otherwise the server may wait for our reconnection which will never happen
		// if one hasn't been sent it's because Warcraft III exited abnormally
		synchronized(remoteSync) {
			if(!leaveGameSent && remoteOut != null) {
				leaveGameSent = true;
				
				try {
					ByteBuffer buf = ByteBuffer.allocate(8);
					buf.order(ByteOrder.LITTLE_ENDIAN);
					buf.put((byte) 0xF7);
					buf.put((byte) 0x21);
					buf.put((byte) 0x08);
					buf.put((byte) 0x00);
					buf.putInt(PLAYERLEAVE_GPROXY);
					
					remoteOut.write(buf.array());
				} catch(IOException ioe) {}
			}
		}
		
		//terminate the connection, since our local client disconnected
		terminate();
	}
	
	public void gproxyReconnect() {
		//only reconnect if we're using gproxy and local hasn't disconnected
		if(localSocket != null && localSocket.isConnected() && !terminated && gproxy) {
			GhostClient.println("[GCConnection] Reconnecting to remote server...");
			
			try {
				remoteSocket = new Socket(remoteAddress, remotePort);
				remoteSocket.setSoTimeout(15000);
				
				synchronized(remoteSync) {
					remoteOut = new DataOutputStream(remoteSocket.getOutputStream());
					isSynchronized = false;
				}
			} catch(IOException ioe) {
				GhostClient.println("[GCConnection] Connection to remote failed: " + ioe.getLocalizedMessage());
				
				//sleep for a while so we don't spam reconnect
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e) {}
				
				eventRemoteDisconnect(remoteOut);
				return;
			}
			
			new GCForward(this, true, remoteSocket);
			
			sendLocalChat("GProxy++ reconnected to the server!");
			sendLocalChat("==================================================");
			
			//send reconnect packet
			try {
				synchronized(remoteSync) {
					ByteBuffer pbuf = ByteBuffer.allocate(13);
					pbuf.order(ByteOrder.LITTLE_ENDIAN);
					pbuf.put((byte) 248);
					pbuf.put((byte) 2);
					pbuf.putShort((short) 13);
					pbuf.put((byte) pid);
					pbuf.putInt(remoteKey);
					pbuf.putInt(totalRemotePackets);
					
					remoteOut.write(pbuf.array());
				}
			} catch(IOException ioe) {
				ioe.printStackTrace();
				
				//close remote socket and let the GCForward instance we made take care of triggering another reconnect
				try { remoteSocket.close(); } catch(IOException e) {}
			}
		}
	}
	
	public void sendLocalChat(String message) {
		//send message to our local player
		
		if(localSocket != null) {
			ByteBuffer buf;
			byte[] messageBytes = message.getBytes();
			
			//different packets are used depending on if we're in-game
			if(gameStarted) {
				buf = ByteBuffer.allocate(13 + messageBytes.length);
				buf.order(ByteOrder.LITTLE_ENDIAN);
				
				buf.put((byte) 247); //header constant
				buf.put((byte) 15); //chat from host header
				buf.putShort((short) (13 + messageBytes.length)); // packet length, including header
			
				buf.put((byte) 1);
				buf.put((byte) pid);
			
				buf.put((byte) pid);
				buf.put((byte) 32);
			
				buf.put((byte) 0);
				buf.put((byte) 0);
				buf.put((byte) 0);
				buf.put((byte) 0);
				
				buf.put(messageBytes);
				buf.put((byte) 0);
			} else {
				buf = ByteBuffer.allocate(9 + messageBytes.length);
				buf.order(ByteOrder.LITTLE_ENDIAN);
				
				buf.put((byte) 247); //header constant
				buf.put((byte) 15); //chat from host header
				buf.putShort((short) (9 + messageBytes.length)); // packet length, including header
			
				buf.put((byte) 1);
				buf.put((byte) pid);
			
				buf.put((byte) pid);
				buf.put((byte) 16);
			
				buf.put(messageBytes);
				buf.put((byte) 0);
			}
			
			try {
				synchronized(localSync) {
					if(localOut != null) {
						localOut.write(buf.array());
					}
				}
			} catch(IOException ioe) {
				GhostClient.println("[GCConnection] Local disconnected: " + ioe.getLocalizedMessage());
				eventLocalDisconnect();
			}
		}
	}
	
	public synchronized void terminate() {
		if(!terminated) {
			terminated = true;
			GhostClient.println("[GCConnection] Terminating connection");
			
			try {
				if(localSocket != null) localSocket.close();
			} catch(IOException e) {}
			
			try {
				if(remoteSocket != null) remoteSocket.close();
			} catch(IOException e) {}
			
			//set everything to null so that we know
			synchronized(localSync) {
				localSocket = null;
				localOut = null;
			}
			
			synchronized(remoteSync) {
				remoteOut = null;
				remoteSocket = null;
			}
			
			host.connectionTerminated(this);
		}
	}
	
	public void remoteRec(int header, int identifier, int len, ByteBuffer buf) {
		buf.order(ByteOrder.LITTLE_ENDIAN);
		
		if(header == 247) {
			//synchronize just in case
			//shouldn't be needed because remoteRec will only be called by one thread, and that
			// thread won't be executing if we're reconnecting, but better safe than sorry
			synchronized(remoteSync) {
				totalRemotePackets++;
			}
			
			//acknowledge packet
			if(gproxy) {
				synchronized(remoteSync) {
					try {
						ByteBuffer pbuf = ByteBuffer.allocate(8);
						pbuf.order(ByteOrder.LITTLE_ENDIAN);
						pbuf.put((byte) 248);
						pbuf.put((byte) 3);
						pbuf.putShort((byte) 8);
						pbuf.putInt(totalRemotePackets);
						
						remoteOut.write(pbuf.array());
					} catch(IOException ioe) {
						GhostClient.println("[GCConnection] Remote disconnected: " + ioe.getLocalizedMessage());
						eventRemoteDisconnect(remoteOut);
					}
					
					if(totalRemotePackets % 50 == 0) {
						GhostClient.println("[GCConnection] Acknowledged " + totalRemotePackets + " remote packets");
					}
				}
			}
			
			if(identifier == 4) { //SLOTINFOJOIN
				if(len >= 2) {
					int slotInfoSize = buf.get(0) + buf.get(1) * 256;
					
					if(len >= 3 + slotInfoSize) {
						pid = buf.get(2 + slotInfoSize);
						GhostClient.println("[GCConnection] Found PID=" + pid);
					}
				}
				
				synchronized(remoteSync) {
					if(gproxyEnabled && remoteOut != null) {
						try {
							remoteOut.write((byte) 248);
							remoteOut.write((byte) 1); //GPS_INIT
							remoteOut.write((byte) 8);
							remoteOut.write((byte) 0);
							remoteOut.write((byte) 1); //version
							remoteOut.write((byte) 0);
							remoteOut.write((byte) 0);
							remoteOut.write((byte) 0);
						} catch(IOException ioe) {
							GhostClient.println("[GCConnection] Local disconnected: " + ioe.getLocalizedMessage());
							eventLocalDisconnect();
						}
					}
				}
				
				//tell gcloud to spoofcheck us
				host.eventJoinedGame(gameInfo.botId, gameInfo.gamename);
			} else if(identifier == 11) { //COUNTDOWN_END
				GhostClient.println("[GCConnection] The game has started.");
				gameStarted = true;
			} else if(identifier == 12) { //INCOMING_ACTION
				if(gproxy) {
					for(int i = numEmptyActionsUsed; i < numEmptyActions; i++) {
						try {
							synchronized(localSync) {
								if(localOut != null) {
									localOut.write(EMPTYACTION);
								}
							}
						} catch(IOException ioe) {
							GhostClient.println("[GCConnection] Local disconnected: " + ioe.getLocalizedMessage());
							eventLocalDisconnect();
						}
					}
					
					numEmptyActionsUsed = 0;
				}
				
				actionReceived = true;
				lastActionTime = System.currentTimeMillis();
			} else if(identifier == 16) { //START_LAG
				if(gproxy) {
					if(len >= 1) {
						int numLaggers = buf.get(0);
						
						if(len == 1 + numLaggers * 5) {
							for(int i = 0; i < numLaggers; i++) {
								boolean laggerFound = false;
								
								for(Integer x : laggers) {
									if(x == buf.get(1 + i * 5)) laggerFound = true;
								}
								
								if(laggerFound) {
									GhostClient.println("[GCConnection] warning - received start_lag on known lagger");
								} else {
									laggers.add((int) buf.get(1 + i * 5));
								}
							}
						} else {
							GhostClient.println("[GCConnection] warning - unhandled start_lag (2)");
						}
					} else {
						GhostClient.println("[GCConnection] warning - unhandled start_lag (1)");
					}
				}
			} else if(identifier == 17) { //STOP_LAG
				if(gproxy) {
					if(len == 5) {
						boolean laggerFound = false;
						
						for(int i = 0; i < laggers.size(); ) {
							if(laggers.get(i) == buf.get(0)) {
								laggers.remove(i);
								laggerFound = true;
							} else {
								i++;
							}
						}
						
						if(!laggerFound) {
							GhostClient.println("warning - received stop_lag on unknown lagger");
						}
					} else {
						GhostClient.println("[GCConnection] warning - unhandled stop_lag");
					}
				}
			} else if(identifier == 72) { //INCOMING_ACTION 2
				if(gproxy) {
					for(int i = numEmptyActionsUsed; i < numEmptyActions; i++) {
						try {
							synchronized(localSync) {
								if(localOut != null) {
									localOut.write(EMPTYACTION);
								}
							}
						} catch(IOException ioe) {
							GhostClient.println("[GCConnection] Local disconnected: " + ioe.getLocalizedMessage());
							eventLocalDisconnect();
						}
					}
					
					numEmptyActionsUsed = numEmptyActions;
				}
			} else if(identifier == 15) { //CHAT_FROM_HOST
				//we intercept chat messages to local player in case
				// player wants to ignore the chat
				if(host.quietMode == GCHost.QUIET_ALL){
					//in this case, all chat is ignored
					return;
				} else {
					//at least 3 for number to PID, one to PID, and the from PID
					if(len >= 3) {
						int numToPID = buf.get(0);
						
						if(len >= 2 + numToPID) {
							int fromPID = buf.get(1 + numToPID);
							
							synchronized(mutedList) {
								if(mutedList.contains(fromPID)) {
									//player is muting this person
									return;
								}
							}
						}
					}
				}
			} else if(identifier == 6) { //PLAYERINFO
				if(len >= 6) {
					buf.position(4);
					int otherPid = buf.get();
					String otherName = GCUtil.getTerminatedString(buf);
					
					if(otherPid >= 0 && otherPid < slotPlayers.size()) {
						synchronized(slotPlayers) {
							slotPlayers.set(otherPid, otherName);
						}
					}
				}
			} else if(identifier == 9) { //SLOTINFO
				if(len >= 2) {
					int slotInfoLength = buf.getShort();
					
					if(len >= 2 + slotInfoLength) {
						byte[] slotInfo = new byte[slotInfoLength];
						buf.get(slotInfo);
						
						//process the slot info
						//slotstaken = number of occupied slots that aren't computers
						//slotstotal = slotstaken + number of open slots
						
						if(slotInfoLength >= 1) {
							int numSlots = GCUtil.unsignedByte(slotInfo[0]);
							int slotsTaken = 0;
							int slotsOpen = 0;
							
							for(int i = 0; i < numSlots; i++) {
								int startIndex = i * 9 + 1;
								
								if(slotInfoLength >= startIndex + 9) {
									if(slotInfo[startIndex + 2] == 0) { //status is open
										slotsOpen++;
									} else if(slotInfo[startIndex + 2] == 2 && slotInfo[startIndex + 3] == 0) { //status is occupied, no computer
										slotsTaken++;
									}
								}
							}
							
							int slotsTotal = slotsTaken + slotsOpen;
							
							GhostClient.println("[GCConnection] Detected slots: " + slotsTaken + "/" + slotsTotal);
							//now we notify the server of the slots that we have detected
							// this way slots can be shown in gamelist
							host.cloudInterface.sendSlots(remoteId, slotsTaken, slotsTotal);
						}
					} else {
						GhostClient.println("[GCConnection] Failed(1) to process slots: len=" + len);
					}
				} else {
					GhostClient.println("[GCConnection] Failed(2) to process slots: len=" + len);
				}
			}
			
			//forward data to local
			try {
				synchronized(localSync) {
					if(localOut != null) {
						localOut.write(header);
						localOut.write(identifier);
						byte[] lenBytes = GCUtil.shortToByteArray((short) (len + 4));
						localOut.write(lenBytes[1]);
						localOut.write(lenBytes[0]);
						localOut.write(buf.array(), 0, len);
					}
				}
			} catch(IOException ioe) {
				GhostClient.println("[GCConnection] Local disconnected: " + ioe.getLocalizedMessage());
				eventLocalDisconnect();
			}
		} else if(header == 248 && gproxyEnabled) { //GPROXY
			if(identifier == 1 && len == 8) { //GPS_INIT
				remotePort = buf.getShort(0);
				remoteKey = buf.getInt(3);
				numEmptyActions = buf.get(7);
				gproxy = true;
				
				//set socket timeout so we disconnect from server
				try {
					remoteSocket.setSoTimeout(15000);
					GhostClient.println("[GCConnection] Set SO_TIMEOUT=15000ms");
				} catch(IOException ioe) {} //ignore because it's not important
				
				GhostClient.println("[GCConnection] handshake complete, disconnect protection ready (num=" + numEmptyActions + ")");
				sendLocalChat("Disconnect protection ready, with " + numEmptyActions + " empty actions.");
			} else if(identifier == 2 && len == 4) { //GPS_RECONNECT
				synchronized(localBuffer) {
					GhostClient.println("[GCConnection] Received GPS_RECONNECT");
					int lastPacket = buf.getInt(0);
					int packetsAlreadyUnqueued = totalLocalPackets - localBuffer.size();
					
					if(lastPacket > packetsAlreadyUnqueued) {
						int packetsToUnqueue = lastPacket - packetsAlreadyUnqueued;
						
						if(packetsToUnqueue > localBuffer.size()) {
							packetsToUnqueue = localBuffer.size();
						}
						
						while(packetsToUnqueue > 0) {
							localBuffer.poll();
							packetsToUnqueue--;
						}
					}
					
					if(remoteOut != null) {
						// send remaining packets from buffer, preserve buffer
						// note: any packets in m_LocalPackets are still sitting at the end of this buffer because they haven't been processed yet
						// therefore we must check for duplicates otherwise we might (will) cause a desync
						Iterator<byte[]> it = localBuffer.iterator();
						
						while(it.hasNext()) {
							try {
								synchronized(remoteSync) {
									if(remoteOut != null) {
										remoteOut.write(it.next());
									}
								}
							} catch(IOException ioe) {
								GhostClient.println("[GCConnection] Remote disconnected: " + ioe.getLocalizedMessage());
								
								//let GCForward deal with reconnecting again
								synchronized(remoteSync) {
									if(remoteSocket != null) {
										try { remoteSocket.close(); } catch(IOException e) {}
									}
								}
							}
						}
						
						//synchronize again so that we don't check isSynchronized incorrectly
						synchronized(remoteSync) {
							isSynchronized = true;
						}
					}
				}
			} else if(identifier == 3 && len == 4) { //GPS_ACK
				int lastPacket = buf.getInt(0);
				
				synchronized(localBuffer) {
					int packetsAlreadyUnqueued = totalLocalPackets - localBuffer.size();
					GhostClient.println("[GCConnection] Received GPS_ACK, lastpacket = " + lastPacket + "/" + totalLocalPackets);
					
					if(lastPacket > packetsAlreadyUnqueued) {
						int packetsToUnqueue = lastPacket - packetsAlreadyUnqueued;
						
						if(packetsToUnqueue > localBuffer.size()) {
							packetsToUnqueue = localBuffer.size();
						}
						
						while(packetsToUnqueue > 0) {
							localBuffer.poll();
							packetsToUnqueue--;
						}
					}
				}
			} else if(identifier == 4 && len == 4) { //GPS_REJECT
				int reason = buf.getInt(0);
				GhostClient.println("[GCConnection] Reconnect rejected: " + reason);
				terminate();
			}
		}
	}
	
	public void localRec(int header, int identifier, int len, ByteBuffer buf) {
		if(header == 247 && identifier == 30) { //REQJOIN
			//we received REQJOIN from client, we can now connect to the game host
			//this is because host counter is unique game host identifier for us
			//we get original host counter before forwarding the packet
			buf.order(ByteOrder.LITTLE_ENDIAN);

			int gameId = buf.getInt(); //client's hostcounter is actually our game identifier
			gameInfo = host.searchGame(gameId); //find the game
			
			if(gameInfo == null) {
				GhostClient.println("[GCConnection] Invalid game requested");
				GhostClient.appendLog("GProxy", "Invalid game requested, try again.");
				terminate();
				return;
			}
			
			buf.getInt(); //ignore entrykey
			byte unknown = buf.get();
			short listenPort = buf.getShort();
			int peerKey = buf.getInt();
			String name = GCUtil.getTerminatedString(buf);

			int remainderLength = len - buf.position();

			//rewrite data for Ghost Client
			byte[] rewrittenUsername = GCUtil.strToBytes(host.cloudInterface.getUsername()); //replace LAN name with actual GC name
			int rewrittenLength = 20 + remainderLength + rewrittenUsername.length;
			ByteBuffer lbuf = ByteBuffer.allocate(rewrittenLength);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);

			lbuf.put((byte) header);
			lbuf.put((byte) identifier);
			lbuf.putShort((short) rewrittenLength); //W3GS packet length must include header
			
			//replace hostcounter ID with the actual host's
			lbuf.putInt(gameInfo.hostCounter);

			//replace entry key with sessionkey
			lbuf.putInt(sessionkey);

			lbuf.put(unknown);
			lbuf.putShort(listenPort);
			lbuf.putInt(peerKey);
			lbuf.put(rewrittenUsername);
			lbuf.put((byte) 0); //null terminator
			
			lbuf.put(buf.array(), buf.position(), remainderLength);
			
			GhostClient.println("[GCConnection] User is requesting " + gameId + " through " + name);
			
			remoteConnected = true;
			remoteAddress = gameInfo.remoteAddress;
			remotePort = gameInfo.remotePort;
			remoteName = gameInfo.botName;
			remoteId = gameInfo.botId;
			
			//null the remote name if it's not properly set
			if(remoteName != null && remoteName.trim().isEmpty()) {
				remoteName = null;
			}
			
			try {
				GhostClient.println("[GCConnection] Found game: " + gameInfo.remoteAddress.getHostAddress() + ":" + gameInfo.remotePort + "; connecting");
				GhostClient.appendLog("GProxy", "Connecting to game [" + gameInfo.gamename + "]");
				
				remoteSocket = new Socket(remoteAddress, remotePort);
				remoteOut = new DataOutputStream(remoteSocket.getOutputStream());
				
				new GCForward(this, true, remoteSocket);
			} catch(IOException ioe) {
				GhostClient.println("[GCConnection] Connection to remote failed: " + ioe.getLocalizedMessage());
				terminate();
				return;
			}

			totalLocalPackets++;
			
			try {
				remoteOut.write(lbuf.array());
			} catch(IOException ioe) {
				GhostClient.println("[GCConnection] Remote disconnected at localRec: " + ioe.getLocalizedMessage());
				//simply close the socket so that the GCForward remote instance can handle the error
				try { remoteSocket.close(); } catch(IOException e) {}
			}
		} else if(remoteConnected) {
			//intercept chat events
			if(header == 247 && identifier == 40) {
				int originalPosition = buf.position(); //save position to reset later
				int total = buf.get(); //total PID's
				
				for(int i = 0; i < total; i++) {
					buf.get();
				}
				
				buf.get(); //from PID
				int flag = buf.get(); //flag
				
				if(flag == 32) {
					buf.getInt(); //extra flags
				}
				
				String message = GCUtil.getTerminatedString(buf);
				
				//messages starting with / get processed
				if(!message.isEmpty() && message.charAt(0) == '/') {
					//first check if we should intercept the chat event
					// instead of forwarding to cloud interface
					
					if(message.startsWith("/mute") || message.startsWith("/unmute")) {
						//mute (or unmute) chat in-game from a certain player
						String[] parts = message.split(" ", 2);
						boolean isMuting = message.startsWith("/mute"); //whether mute or unmute
						
						if(parts.length == 2) {
							String target = parts[1].toLowerCase();
							
							//try to find the name in the slot list that we have
							int foundPid = 0;
							String foundName = null;
							
							synchronized(slotPlayers) {
								for(int i = 0; i < slotPlayers.size(); i++) {
									if(slotPlayers.get(i).toLowerCase().equals(target)) {
										foundPid = i;
										foundName = slotPlayers.get(i);
										break;
									} else if(slotPlayers.get(i).toLowerCase().contains(target)) {
										foundPid = i;
										foundName = slotPlayers.get(i);
									}
								}
							}

							if(foundName != null) {
								synchronized(mutedList) {
									if(isMuting) {
										if(!mutedList.contains(foundPid)) {
											mutedList.add(foundPid);
										}
									} else {
										//make sure to use the function that takes object
										// and not remove at index
										mutedList.remove((Integer) foundPid);
									}
								}

								String ignoreType = isMuting ? "Ignoring" : "No longer ignoring";
								sendLocalChat(ignoreType + " chat from " + foundName);
								
								if(isMuting) {
									sendLocalChat("Use /unmute to stop ignoring the player's chat.");
								}
							} else {
								sendLocalChat("Could not find any players matching [" + parts[1] + "]");
							}
						}
					} else {
						List<String[]> response = host.cloudInterface.processMessage(null, message);
						
						for(String[] part : response) {
							sendLocalChat(part[0] + ": " + part[1]);
						}
					}
					
					//don't show this message in chat
					return;
				}
				
				buf.position(originalPosition);
			}
			
			//buffer packets if using gproxy
			if(gproxy) {
				//synchronize so that we don't add a packet to buffer while we're reconnecting
				synchronized(localBuffer) {
					byte[] packet = new byte[4 + len];
					packet[0] = (byte) header;
					packet[1] = (byte) identifier;
					byte[] lenBytes = GCUtil.shortToByteArray((short) (len + 4));
					packet[2] = lenBytes[1];
					packet[3] = lenBytes[0];
					System.arraycopy(buf.array(), 0, packet, 4, len);
					
					localBuffer.add(packet);
				}
			}
			
			//increment number of local packets received
			totalLocalPackets++;
			
			if(header == 247 && identifier == 33) { //LEAVEGAME
				leaveGameSent = true;
				GhostClient.println("[GCConnection] Local left the game");
			}
			
			//send packets to remote server
			//check to make sure we're synchronized with the server
			//(if we're using gproxy, then we might be unsynchronized after we reconnect
			// because we'll be sending the localBuffer packets)
			synchronized(remoteSync) {
				if(isSynchronized && remoteOut != null) {
					try {
						remoteOut.writeByte(header);
						remoteOut.writeByte(identifier);
						byte[] lenBytes = GCUtil.shortToByteArray((short) (len + 4));
						remoteOut.write(lenBytes[1]);
						remoteOut.write(lenBytes[0]);
						remoteOut.write(buf.array(), 0, len);
					} catch(IOException ioe) {
						GhostClient.println("[GCConnection] Remote disconnected at localRec: " + ioe.getLocalizedMessage());
						//simply close the socket so that the GCForward remote instance can handle the error
						try { remoteSocket.close(); } catch(IOException e) {}
					}
				}
			}
		} else {
			GhostClient.println("[GCConnection] Bad packet received before REQJOIN: " + identifier + "/" + header);
		}
	}
	
	public String getBotName() {
		return remoteName;
	}
}

class GCForward extends Thread {
	GCConnection connection;
	boolean isRemote; //whether socket is the remote/host socket (otherwise, it's local/player socket)
	
	Socket socket;
	DataInputStream in;
	
	public GCForward(GCConnection connection, boolean isRemote, Socket socket) {
		this.connection = connection;
		this.isRemote = isRemote;
		
		this.socket = socket;
		try {
			in = new DataInputStream(socket.getInputStream());
		} catch(IOException ioe) {
			GhostClient.println("[GCForward] Init error: " + ioe.getLocalizedMessage());
		}
		
		start();
	}
	
	public void run() {
		ByteBuffer buf = ByteBuffer.allocate(65536);
		
		while(true) {
			try {
				int header = in.read();
				
				if(header == -1) {
					GhostClient.println("[GCForward] Socket disconnected");
					connection.terminate();
					break;
				}
				
				int identifier = in.read();
				//read unsigned short in little endian
				int len = (in.read() + in.read() * 256) - 4;
				
				if(len >= 0) {
					in.readFully(buf.array(), 0, len);
					buf.position(0);
					
					if(isRemote) {
						connection.remoteRec(header, identifier, len, buf);
					} else {
						connection.localRec(header, identifier, len, buf);
					}
					
					buf.clear();
				} else {
					GhostClient.println("[GCForward] Ignoring bad packet, len=" + len);
				}
			} catch(SocketTimeoutException e) {
				GhostClient.println("[GCForward] Timed out: " + e.getLocalizedMessage());
				
				if(isRemote) {
					connection.eventRemoteDisconnect(connection.remoteOut);
				} else {
					connection.eventLocalDisconnect();
				}
				
				break;
			} catch(IOException ioe) {
				GhostClient.println("[GCForward] Error: " + ioe.getLocalizedMessage());
				
				if(isRemote) {
					connection.eventRemoteDisconnect(connection.remoteOut);
				} else {
					connection.eventLocalDisconnect();
				}
				
				break;
			}
		}
	}
}