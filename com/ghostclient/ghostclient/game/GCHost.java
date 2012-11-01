package com.ghostclient.ghostclient.game;



import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import com.ghostclient.ghostclient.CloudInterface;
import com.ghostclient.ghostclient.Config;
import com.ghostclient.ghostclient.GCUtil;
import com.ghostclient.ghostclient.GhostClient;

public class GCHost implements Runnable {
	public static final int QUIET_OFF = 0;
	public static final int QUIET_GHOSTCLIENT = 1; //ignore GC messages
	public static final int QUIET_ALL = 2; //ignore all messages
	
	ArrayList<GameInfo> games;
	ArrayList<GCConnection> connections;

	ServerSocket server;
	ByteBuffer buf;
	
	CloudInterface cloudInterface; //when user joins a game, we notify cloud interface to spoofcheck
	
	//UDP broadcast socket
	DatagramSocket udpSocket;
	SocketAddress udpTarget;

	int serverPort = 7112;
	int war3version = 26;
	
	boolean terminated = false;
	
	int quietMode = 0; //0: show all messages; 1: do not show ghost client messages; 2: also ignore player chat

	public GCHost(CloudInterface cloudInterface) {
		this.cloudInterface = cloudInterface;
		
		serverPort = Config.getInt("serverPort", 7112);
		war3version = Config.getInt("war3version", 26);
		
		try {
			udpTarget = new InetSocketAddress(InetAddress.getLocalHost(), 6112);
		} catch(UnknownHostException uhe) {
			GhostClient.println("[GCHost] UDP broadcast target error: " + uhe.getLocalizedMessage());
			GhostClient.appendLog("System", "Problem while trying to broadcast games to localhost: UDP broadcast target error: " + uhe.getLocalizedMessage() + ".");
			
		}
		
		games = new ArrayList<GameInfo>();
		connections = new ArrayList<GCConnection>();
		buf = ByteBuffer.allocate(65536);
	}
	
	public void init() {
		GhostClient.println("[GCHost] Creating server socket...");
		
		try {
			server = new ServerSocket(serverPort);
		} catch(IOException ioe) {
			GhostClient.println("[GCHost] Error while initiating server socket: " + ioe.getLocalizedMessage());
			GhostClient.appendLog("System", "Error while initiating server socket for game proxy: " + ioe.getLocalizedMessage() + ".");
			new Thread(this).start(); //this will attempt to re-init the server on different port
			return;
		}

		GhostClient.println("[GCHost] Creating UDP socket...");
		
		try {
			udpSocket = new DatagramSocket();
		} catch(IOException ioe) {
			GhostClient.println("[GCHost] Error while initiating UDP socket: " + ioe.getLocalizedMessage());
			GhostClient.appendLog("System", "Error while initiating UDP socket for game proxy: " + ioe.getLocalizedMessage() + ".");
			new Thread(this).start(); //this will attempt to re-init the server on different port
			return;
		}
		
		new Thread(this).start();
	}
	
	public void deinit() {
		clearGames();
		
		terminated = true;
		
		synchronized(this) {
			this.notify();
		}
		
		if(server != null) {
			try {
				server.close();
			} catch(IOException ioe) {}
		}
		
		if(udpSocket != null) {
			udpSocket.close();
		}
	}
	
	//deletes all games from LAN screen
	public void clearGames() {
		synchronized(games) {
			for(GameInfo game : games) {
				//decreate this
				ByteBuffer lbuf = ByteBuffer.allocate(8);
				lbuf.order(ByteOrder.LITTLE_ENDIAN);
				
				lbuf.put((byte) 247); //W3GS constant
				lbuf.put((byte) 51); //DECREATE
				lbuf.putShort((short) 8); //packet length
				
				lbuf.putInt(game.uid);
				
				try {
					DatagramPacket packet = new DatagramPacket(lbuf.array(), 8, udpTarget);
					udpSocket.send(packet);
				} catch(IOException ioe) {
					GhostClient.println("[GCHost] Decreate error: " + ioe.getLocalizedMessage());
				}
			}
			
			games.clear();
		}
	}

	//broadcasts game to LAN
	public void broadcastGame(GameInfo game) {
		buf.clear(); //use buf to create our own packet
		buf.order(ByteOrder.LITTLE_ENDIAN);

		buf.put((byte) 247); //W3GS
		buf.put((byte) 48); //GAMEINFO
		buf.putShort((short) 0); //packet size; do later
		
		buf.putInt(1462982736); //product ID (WC3 TFT)
		buf.putInt(war3version); //version
		buf.putInt(game.uid); //replace hostcounter with uid
		buf.putInt(0); //entry key
		
		byte[] bytes = GCUtil.strToBytes(game.gamename);
		buf.put(bytes);
		buf.put((byte) 0); //null terminator

		buf.put((byte) 0); //game password null terminator

		buf.put(game.statString); //StatString
		buf.put((byte) 0); //null terminator
		
		buf.putInt(12); //slots total
		buf.putInt(1); //map game type (parameter, game type)
		buf.putInt(1); //unknown
		buf.putInt(12); //slots open
		buf.putInt(100); //up time
		buf.putShort((short) serverPort); //port

		//assign length in little endian
		int length = buf.position();
		buf.putShort(2, (short) length);

		//get bytes
		byte[] packetBytes = new byte[length];
		buf.position(0);
		buf.get(packetBytes);
		
		//add game to games list
		synchronized(games) {
			games.add(game);
		}

		//send packet to LAN, or to udpTarget
		GhostClient.println("[GCHost] Broadcasting with gamename [" + game.gamename + "]");
		
		try {
			DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length, udpTarget);
			udpSocket.send(packet);
		} catch(IOException ioe) {
			ioe.printStackTrace();
			GhostClient.println("[GCHost] Error while broadcast UDP: " + ioe.getLocalizedMessage());
			GhostClient.appendLog("System", "Failed to broadcast game: " + ioe.getLocalizedMessage() + ".");
		}
	}
	
	//called when REQJOIN is received to identify the game
	public GameInfo searchGame(int uid) {
		synchronized(games) {
			for(GameInfo game : games) {
				if(game.uid == uid) {
					return game;
				}
			}
		}
		
		return null;
	}
	
	//called when SLOTINFOJOIN is received by a connection to spoof check
	public void eventJoinedGame(int botId, String gamename) {
		//first spoofcheck so we aren't kicked
		cloudInterface.spoofCheck(botId);
		
		//also set current game for others to /whois
		cloudInterface.sendSetGame(gamename);
	}
	
	public void connectionTerminated(GCConnection connection) {
		synchronized(connections) {
			connections.remove(connection);
			
			if(connections.isEmpty()) {
				//clear the current game
				cloudInterface.sendSetGame("");
			}
		}
	}
	
	public void sendLocalChat(String message) {
		if(quietMode == QUIET_OFF) { //only display if user isn't ignoring Ghost Client messages
			synchronized(connections) {
				for(GCConnection connection : connections) {
					connection.sendLocalChat(message);
				}
			}
		}
	}
	
	public GCConnection getCurrentGame() {
		//get first game that hasn't started yet
		synchronized(connections) {
			for(GCConnection connection : connections) {
				if(!connection.gameStarted) {
					return connection;
				}
			}
		}
		
		return null;
	}
	
	public synchronized void setQuietMode(int mode) {
		quietMode = mode;
	}
	
	public void run() {
		GhostClient.println("[GCHost] Thread starting: " + Thread.currentThread().getName());
		
		while(!terminated && server != null && server.isBound()) {
			try {
				Socket socket = server.accept();
				GhostClient.println("[GCHost] Receiving connection from " + socket.getInetAddress().getHostAddress());
				GhostClient.appendLog("System", "Accepting your local connection.");
				GCConnection connection = new GCConnection(this, socket);
				
				synchronized(connections) {
					connections.add(connection);
				}
				
				//new game, reset quiet mode
				setQuietMode(QUIET_OFF);
			} catch(IOException ioe) {
				GhostClient.println("[GCHost] Error while accepting connection: " + ioe.getLocalizedMessage());
				GhostClient.appendLog("System", "Failed to accept connection: " + ioe.getLocalizedMessage() + ".");
				break;
			}
		}

		//make sure sockets are closed (might have had init() during terminate call)
		//have to make sure they are not null in case init() failed and we are restarting
		if(server != null) {
			try {
				server.close();
			} catch(IOException ioe) {}
		}
		
		if(udpSocket != null) {
			udpSocket.close();
		}
		
		if(!terminated) {
			//attempt to restart server
			serverPort++;
			GhostClient.appendLog("System", "Restarting game proxy in ten seconds");
			
			synchronized(this) {
				try {
					this.wait(10000);
				} catch(InterruptedException e) {}
			}
			
			//add another if statement in case we were interrupted in above statement
			if(!terminated) {
				init();
			}
		}
		
		GhostClient.println("[GCHost] Thread exiting: " + Thread.currentThread().getName());
	}
}