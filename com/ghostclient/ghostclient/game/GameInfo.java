package com.ghostclient.ghostclient.game;


import java.net.InetAddress;
import java.net.UnknownHostException;

public class GameInfo {
	int uid;
	InetAddress remoteAddress;
	int remotePort;
	int botId; //for spoofchecking via gcloud
	String botName; //for manual spoofchecking (/w botname sc)
	int hostCounter;
	
	String gamename;
	byte[] statString;
	
	public GameInfo(int uid, byte[] addr, int port, int hostCounter, String gamename, byte[] statString, int botId, String botName) {
		this.uid = uid;
		this.remotePort = port;
		this.hostCounter = hostCounter;
		
		try {
			remoteAddress = InetAddress.getByAddress(addr);
		} catch(UnknownHostException uhe) {
			System.out.println("[GameInfo] Error: unknown host on addr bytes: " + uhe.getLocalizedMessage());
			remoteAddress = null;
		}
		
		this.gamename = gamename;
		this.statString = statString;
		this.botId = botId;
		this.botName = botName;
	}
}
