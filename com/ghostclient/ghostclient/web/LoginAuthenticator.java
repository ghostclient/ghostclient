package com.ghostclient.ghostclient.web;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.ghostclient.ghostclient.GhostClient;

public class LoginAuthenticator extends WebAuthenticator {
	public static final String PRELOGIN_URL = "http://ghostclient.com/forums/index.php?app=core&module=global&section=login";
	public static final String LOGIN_URL = "http://ghostclient.com/forums/index.php?app=core&module=global&section=login&do=process";
	public static final String INDEX_URL = "http://ghostclient.com/forums/";
	
	String username;
	String password;
	
	public LoginAuthenticator(DefaultHttpClient client, String username, String password) {
		super(client);
		
		this.username = username;
		this.password = password;
	}
	
	public boolean authenticate() {
		String line;
		
		try {
			//access the login page and attempt to gather all the needed fields
			ArrayList<String> searchFields = new ArrayList<String>();
			searchFields.add("auth_key");
			searchFields.add("referer");
			
			List<NameValuePair> formparams = new ArrayList<NameValuePair>();
			formparams.add(new BasicNameValuePair("ips_username", username));
			formparams.add(new BasicNameValuePair("ips_password", password));

			HttpGet getPreLogin = new HttpGet(PRELOGIN_URL);
			HttpResponse responsePreLogin = client.execute(getPreLogin);
			BufferedReader in = new BufferedReader(new InputStreamReader(responsePreLogin.getEntity().getContent()));
			
			while((line = in.readLine()) != null) {
				for(int i = 0; i < searchFields.size(); i++) {
					if(line.contains(searchFields.get(i))) {
						String[] parts = line.split(" ");
						
						for(String part : parts) {
							if(part.startsWith("value=")) {
								//in the form value="val" or value='val'
								//so we skip value=' and the last qoute
								String latter = part.substring(7);
								int quoteIndex = latter.indexOf("'");
								
								if(quoteIndex == -1) {
									quoteIndex = latter.indexOf("\"");
									
									if(quoteIndex == -1) {
										quoteIndex = latter.length();
									}
								}
								
								latter = latter.substring(0, quoteIndex);
								formparams.add(new BasicNameValuePair(searchFields.get(i), latter));
							}
						}
						
						searchFields.remove(i);
						i--;
					}
				}
				
				//quit once we've found what we need
				if(searchFields.isEmpty()) {
					break;
				}
			}
			
			in.close();
			
			//using the fields we got, now we do the actual login
			UrlEncodedFormEntity loginEntity = new UrlEncodedFormEntity(formparams, "UTF-8");
			HttpPost postLogin = new HttpPost(LOGIN_URL);
			postLogin.setEntity(loginEntity);
			HttpResponse postResponse = client.execute(postLogin);
			postLogin.abort(); //we don't want the actual page, anyway it looks blank
		} catch(IOException ioe) {
			GhostClient.appendLog("System", "Error while authenticating with web interface: " + ioe.getLocalizedMessage());
			return false;
		}
		
		return true;
	}
	
	//checks if we are logged in by accessing the index page
	public boolean isAuthenticated() {
		try {
			String line;
			HttpGet getCheckLogin = new HttpGet(INDEX_URL);
			HttpResponse responseCheckLogin = client.execute(getCheckLogin);
			BufferedReader in = new BufferedReader(new InputStreamReader(responseCheckLogin.getEntity().getContent()));
			boolean loggedIn = false;
			String profileSearch = "title=\'Your Profile\'>";
			
			while((line = in.readLine()) != null) {
				int index = line.indexOf(profileSearch);
				
				if(index != -1) {
					//ok, there should be a username after this profile thing
					int usernameIndex = index + profileSearch.length();
					
					if(usernameIndex + username.length() <= line.length()) {
						String testUsername = line.substring(usernameIndex, usernameIndex + username.length());
						
						if(testUsername.equalsIgnoreCase(username)) {
							loggedIn = true;
							break;
						}
					}
				}
			}
			
			in.close();
			
			return loggedIn;
		} catch(IOException ioe) {
			GhostClient.println("[LoginAuthenticator] Error while checking if we are logged in: " + ioe.getLocalizedMessage());
			return false;
		}
	}
}
