package com.ghostclient.ghostclient.web;


import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

import com.ghostclient.ghostclient.GhostClient;

public class CookieAuthenticator extends WebAuthenticator {
	public static final String TARGET_URL = "http://ghostclient.com/forums/";
	public static final String COOKIE_DOMAIN = "ghostclient.com";
	
	int memberId;
	String passwordHash;
	
	public CookieAuthenticator(DefaultHttpClient client, int memberId, String passwordHash) {
		super(client);
		this.memberId = memberId;
		this.passwordHash = passwordHash;
	}
	
	public boolean authenticate() {
		client.getCookieStore().addCookie(makeCookie("member_id", memberId + ""));
		client.getCookieStore().addCookie(makeCookie("pass_hash", passwordHash));
		
		//access page so that we start the session
		HttpGet getLogin = new HttpGet(TARGET_URL);
		
		try {
			HttpResponse responseLogin = client.execute(getLogin);
		} catch(IOException ioe) {
			GhostClient.println("[CookieAuthenticator] Error while authenticated by cookie: " + ioe.getLocalizedMessage());
			return false;
		}
		
		getLogin.abort();
		
		return true;
	}
	
	public Cookie makeCookie(String key, String value) {
		BasicClientCookie cookie = new BasicClientCookie(key, value);
		cookie.setDomain(COOKIE_DOMAIN);
		return cookie;
	}
	
	public boolean isAuthenticated() {
		//todo: check API and see if we're authenticated...
		return true;
	}
}
