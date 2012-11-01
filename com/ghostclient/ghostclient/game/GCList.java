package com.ghostclient.ghostclient.game;



import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.TimerTask;

public class GCList extends TimerTask {
	GCHost host;
	ByteBuffer buf;
	
	int nextId; //uid to assign to next game
	ArrayList<GameInfo> broadcastGames;
	
	public GCList(GCHost host) {
		this.host = host;
		broadcastGames = new ArrayList<GameInfo>();
		
		buf = ByteBuffer.allocate(65536);
		nextId = 1;
	}
	
	//immediate is whether to immediately display the new game
	public void addGame(byte[] addr, int port, int hostCounter, String gamename, byte[] statString, int botId, String botname, boolean immediate) {
		GameInfo game = new GameInfo(nextId, addr, port, hostCounter, gamename, statString, botId, botname);
		nextId++;
		
		synchronized(broadcastGames) {
			if(immediate) { //broadcast game if we're told to
				host.broadcastGame(game);
			}
			
			//remove any other games with same bot ID or gamename
			for(int i = 0; i < broadcastGames.size(); i++) {
				GameInfo curr = broadcastGames.get(i);
				
				if(curr.botId == game.botId || curr.gamename.equals(game.gamename)) {
					broadcastGames.remove(i);
					i--;
				}
			}
			
			broadcastGames.add(game);
			
			//limit queued games to 12
			while(broadcastGames.size() > 12) {
				//remove from beginning so newest games are shown
				broadcastGames.remove(0);
			}
		}
	}

	public void run() {
		System.out.println("[GCList] Updating games...");
		
		//get new games from server for each bot
		synchronized(broadcastGames) {
			for(GameInfo game : broadcastGames) {
				host.cloudInterface.gameQuery(game.botId, false);
			}
		}
		
		//refresh all the games
		synchronized(broadcastGames) {
			host.clearGames();
			
			for(GameInfo game : broadcastGames) {
				host.broadcastGame(game);
			}
		}
	}
}
