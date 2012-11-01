package com.ghostclient.ghostclient;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class LoaderClient {
	InetAddress localhost;
	
	Socket socket;
	DataOutputStream out;
	
	public LoaderClient() {
		try {
			localhost = InetAddress.getLocalHost();
		} catch(IOException ioe) {
			GhostClient.println("[LoaderClient] Failed to resolve localhost: " + ioe.getLocalizedMessage());
		}
	}
	
	public boolean execute(String action, String data) {
		try {
			socket = new Socket(localhost, LoaderInterface.LOADER_PORT);
			out = new DataOutputStream(socket.getOutputStream());
			
			out.writeUTF(action);
			out.writeUTF(data);
			
			socket.close();
			
			return true;
		} catch(IOException ioe) {
			return false;
		}
	}
}
