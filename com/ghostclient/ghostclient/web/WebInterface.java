package com.ghostclient.ghostclient.web;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.ghostclient.ghostclient.GhostClient;

public class WebInterface implements Runnable {
	public static final String API_URL = "http://ghostclient.com/api";
	public static final String MOTD_URL = "http://ghostclient.com/motd.txt";

	public static final int STATE_INIT = 0;
	public static final int STATE_AUTHENTICATED = 1;
	public static final int STATE_QUIT = 2;
	public static final int STATE_ISSUE = 3;

	GhostClient client;
	int state;
	
	WebAuthenticator authenticator; //used for authentication
	
	String status; //status that will be broadcasted to API
	ArrayList<String> friends; //friendlist retrieved
	DefaultHttpClient http;
	
	WebSynchronize synchronize; //used to synchronize activation/destruction of our threads

	public WebInterface(GhostClient client) {
		this.client = client;
		
		friends = new ArrayList<String>();
		status = "Online";
		state = STATE_INIT;

		//prefer IPv4 connection whenever possible
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");

		//setup http client
		HttpParams httpParams = new BasicHttpParams();
	    HttpConnectionParams.setConnectionTimeout(httpParams, 30000); //timeout HTTP requests after 30 seconds
		http = new DefaultHttpClient();
		
		synchronize = new WebSynchronize(this);
	}
	
	public boolean init() {
		GhostClient.appendLog("System", "Authenticating with website");
		
		boolean success = authenticator.authenticate();
		
		if(!success) {
			return false;
		}
		
		//confirm login by checking index page
		boolean authenticated = authenticator.isAuthenticated();
		
		if(!authenticated) {
			GhostClient.appendLog("System", "Login failed - invalid login information");
			return false;
		}

		GhostClient.appendLog("System", "Authentication with web interface successful");
		
		synchronized(this) {
			//sometimes we might initialize while quit is called
			//in that case, running thread should de-initialize instead of persisting
			//this is accomplished by not updating the state
			if(state != STATE_QUIT) {
				state = STATE_AUTHENTICATED;
			}
		}
		
		new Thread(this).start();
		return true;
	}

	public boolean init(String username, String password) {
		authenticator = new LoginAuthenticator(http, username, password);
		return init();
	}
	
	public boolean init(int memberId, String passwordHash) {
		authenticator = new CookieAuthenticator(http, memberId, passwordHash);
		return init();
	}
	
	//returns {member id, session id} needed to authenticate with server
	public String[] getCookieInformation() {
		String[] array = new String[] {"fail", "fail"};
		
		if(state == STATE_AUTHENTICATED) {
			List<Cookie> cookies = http.getCookieStore().getCookies();
			
			for(Cookie cookie : cookies) {
				if(cookie.getName().equals("member_id")) {
					array[0] = cookie.getValue();
				} else if(cookie.getName().equals("session_id")) {
					array[1] = cookie.getValue();
				}
			}
		}
		
		return array;
	}

	public void setStatus(String newStatus) {
		status = newStatus;
	}

	public Iterator<String> getFriendsIterator() {
		return friends.iterator();
	}
	
	public void authIssue() {
		//there is some problem with our auth information
		// we should try and reauthenticate with website
		synchronized(this) {
			if(state == STATE_AUTHENTICATED) {
				state = STATE_ISSUE;
				this.notifyAll();
			}
		}
	}
	
	//if force is true, we cannot re-initialize
	public void quit(boolean force) {
		synchronized(this) {
			state = force ? STATE_QUIT : STATE_INIT;
			this.notifyAll();
		}
	}
	
	public void quitAndWait() {
		synchronize.destroyThread();
	}
	
	public void run() {
		synchronize.activateThread();
		GhostClient.println("[WebInterface] Thread starting: " + Thread.currentThread().getName());
		
		//notify client that we have authenticated with website
		if(state == STATE_AUTHENTICATED) {
			client.eventWebAuthenticated();
			
			//get the motd from server
			try {
				HttpGet getMotd = new HttpGet(MOTD_URL);
				HttpResponse responseMotd = http.execute(getMotd);
				BufferedReader in = new BufferedReader(new InputStreamReader(responseMotd.getEntity().getContent()));
				String line;
				
				for(int i = 0; i < 8 && (line = in.readLine()) != null; i++) {
					if(!line.trim().isEmpty()) {
						GhostClient.appendLog("Server", line);
					}
				}
				
				in.close();
			} catch(IOException ioe) {
				GhostClient.println("Failed to load MOTD: " + ioe.getLocalizedMessage());
			}
		}
		
		//loop as long as we can
		while(state == STATE_AUTHENTICATED) {
			try {
				HttpGet getMotd = new HttpGet(MOTD_URL);
				HttpResponse responseMotd = http.execute(getMotd);
				responseMotd.getEntity().getContent().close();
			} catch(IOException ioe) {
				GhostClient.println("Failed to load MOTD: " + ioe.getLocalizedMessage());
			}
			
			synchronized(this) {
				try {
					this.wait(60000);
				} catch(InterruptedException e) {}
			}
		}
		
		synchronize.deactivateThread();
		
		synchronized(this) {
			if(state == STATE_AUTHENTICATED || state == STATE_ISSUE) {
				state = STATE_INIT;
				GhostClient.appendLog("System", "Problem occured with web interface. Reauthenticating in ten seconds.");
				
				try {
					this.wait(5000);
				} catch(InterruptedException e) {}
				
				//in case we were woken from waiting via quit() function, we use another if statement
				if(state == STATE_INIT) {
					boolean success = init();
					
					if(!success) {
						client.eventWebDisconnected();
					}
				}
			}
		}
		
		GhostClient.println("[WebInterface] Thread exiting: " + Thread.currentThread().getName());
	}
}

class WebSynchronize {
	WebInterface webInterface;
	boolean threadActive;
	
	public WebSynchronize(WebInterface webInterface) {
		this.webInterface = webInterface;
	}
	
	//called by thread when it wants to start
	public void activateThread() {
		synchronized(this) {
			if(threadActive) {
				destroyThread();
			}
			
			threadActive = true;
		}
	}
	
	//called by thread when it's ending.
	public void deactivateThread() {
		synchronized(this) {
			threadActive = false;
			this.notifyAll();
		}
	}
	
	//called when we want to destroy the current thread
	//also wait for it to end
	public void destroyThread() {
		synchronized(this) {
			webInterface.quit(false);
			
			while(threadActive) {
				try {
					this.wait();
				} catch(InterruptedException e) {}
			}
		}
	}
}