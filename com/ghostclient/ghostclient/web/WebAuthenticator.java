package com.ghostclient.ghostclient.web;

import org.apache.http.impl.client.DefaultHttpClient;

public abstract class WebAuthenticator {
	DefaultHttpClient client;
	
	public WebAuthenticator(DefaultHttpClient client) {
		this.client = client;
	}
	
	//returns whether authentication was successful
	public abstract boolean authenticate();
	
	//returns whether we are currently authenticated
	public abstract boolean isAuthenticated();
}
