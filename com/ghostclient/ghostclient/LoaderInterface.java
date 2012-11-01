package com.ghostclient.ghostclient;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class LoaderInterface implements Runnable {
	public static int LOADER_PORT = 8112;
	
	ServerSocket server;
	
	CloudInterface cloudInterface;
	boolean terminated;
	String loaderAddressString; //null is localhost, empty string is wildcard, otherwise address
	
	public LoaderInterface(CloudInterface cloudInterface) {
		this.cloudInterface = cloudInterface;
		terminated = false;
		
		//configuration
		LOADER_PORT = Config.getInt("loader_port", 8112);
		loaderAddressString = Config.getString("loader_addr", null);
		
		if(loaderAddressString != null && (loaderAddressString.equals("0") || loaderAddressString.equals("*"))) {
			loaderAddressString = "";
		}
	}
	
	public boolean init() {
		System.out.println("[LoaderInterface] Creating server socket...");
		
		try {
			InetAddress loaderAddress = null;
			
			if(loaderAddressString == null) {
				loaderAddress = InetAddress.getLocalHost();
			} else if(!loaderAddressString.isEmpty()) {
				try {
					loaderAddress = InetAddress.getByName(loaderAddressString);
				} catch(UnknownHostException uhe) {
					GhostClient.println("[LoaderInterface] Could not set loader address: " + uhe.getLocalizedMessage());
				}
			}
			
			server = new ServerSocket(LOADER_PORT, 5, loaderAddress);
		} catch(IOException ioe) {
			GhostClient.println("[LoaderInterface] Error while initiating server socket: " + ioe.getLocalizedMessage());
			return false;
		}
		
		new Thread(this).start();
		return true;
	}

	public void deinit() {
		if(!terminated) {
			terminated = true;
			
			if(server != null) {
				try {
					server.close();
				} catch(IOException ioe) {}
			}
		}
	}
	
	public void run() {
		GhostClient.println("[LoaderInterface] Thread starting: " + Thread.currentThread().getName());
		
		while(!terminated && server != null && server.isBound()) {
			try {
				Socket socket = server.accept();
				GhostClient.println("[LoaderInterface] Receiving connection from " + socket.getInetAddress().getHostAddress());
				new LoaderConnection(cloudInterface, socket);
			} catch(IOException ioe) {
				GhostClient.println("[LoaderInterface] Error while accepting connection: " + ioe.getLocalizedMessage());
				break;
			}
		}
		
		GhostClient.println("[LoaderInterface] Thread exiting: " + Thread.currentThread().getName());
	}
}

class LoaderConnection extends Thread {
	CloudInterface cloudInterface;
	Socket socket;
	DataInputStream in;
	
	public LoaderConnection(CloudInterface cloudInterface, Socket socket) {
		this.cloudInterface = cloudInterface;
		this.socket = socket;
		
		try {
			in = new DataInputStream(socket.getInputStream());
			start();
		} catch(IOException ioe) {
			GhostClient.println("[LoaderInterface] Error while opening input stream: " + ioe.getLocalizedMessage());
		}
	}
	
	public void run() {
		try {
			String action = in.readUTF();
			String data = in.readUTF();
			
			if(action.equals("join")) {
				int botId = Integer.parseInt(data);
				cloudInterface.gameQuery(botId, true);
			} else {
				GhostClient.println("[LoaderInterface] Invalid action: " + action);
			}
		} catch(IOException ioe) {
			GhostClient.println("[LoaderInterface] Error while reading: " + ioe.getLocalizedMessage());
		}
	}
}
