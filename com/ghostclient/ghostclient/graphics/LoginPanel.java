package com.ghostclient.ghostclient.graphics;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;

import com.ghostclient.ghostclient.GhostClient;

public class LoginPanel extends GCPanel implements ActionListener {
	GCFrame frame;
	GhostClient client;
	
	DescripField nameField;
	DescripField passwordField;
	JButton connect;

	public LoginPanel(GCFrame frame, GhostClient client) {
		super();
		this.frame = frame;
		this.client = client;
		
		nameField = new DescripField("Name:", "", 20, false);
		nameField.field.addActionListener(this);
		passwordField = new DescripField("Password:", "", 20, true);
		passwordField.field.addActionListener(this);
		connect = new JButton("Login");
		connect.addActionListener(this);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(nameField);
		add(passwordField);
		add(connect);
	}
	
	public void onLoad() {
		nameField.field.requestFocus();
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == connect || e.getSource() == nameField.field || e.getSource() == passwordField.field) {
			String name = nameField.getField();
			String password = passwordField.getPassword();
			
			//update in another thread so we don't mess this up
			new Thread(new ProcessLogin(name, password)).start();
			
			nameField.field.setText("");
			passwordField.field.setText("");
		}
	}
	
	class ProcessLogin implements Runnable {
		String username;
		String password;
		
		public ProcessLogin(String username, String password) {
			this.username = username;
			this.password = password;
		}
		
		public void run() {
			frame.setScreen("chat");
			client.eventWebAuthenticate(username, password);
		}
	}
}
