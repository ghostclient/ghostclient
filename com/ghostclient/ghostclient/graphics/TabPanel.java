package com.ghostclient.ghostclient.graphics;


import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;

import com.ghostclient.ghostclient.CloudInterface;

public class TabPanel extends JPanel implements MouseListener {
	String channel;
	
	ChatPanel chatPanel;
	JTextPane chatLog;
	JList channelUsers;
	
	DefaultListModel usersModel; //list model for channelUsers
	
	boolean notifying; //if we received a new chat in here, but user is in different tab
	boolean isPromoted = false; //whether we are a moderator of this channel
	
	public TabPanel(ChatPanel chatPanel) {
		super();
		
		this.chatPanel = chatPanel;
		channel = null;
		
		chatLog = new JTextPane();
		chatLog.setEditable(false);
		chatLog.addKeyListener(chatPanel);
		
		usersModel = new DefaultListModel();
		channelUsers = new JList(usersModel);
		channelUsers.addMouseListener(this);
		channelUsers.addKeyListener(chatPanel);
		
		JScrollPane logScrollPane = new JScrollPane(chatLog);
		logScrollPane.setPreferredSize(new Dimension(600, 500));
		
		JScrollPane listScrollPane = new JScrollPane(channelUsers);
		listScrollPane.setPreferredSize(new Dimension(200, 500));
		listScrollPane.setMaximumSize(new Dimension(200, 10000));
		listScrollPane.setMinimumSize(new Dimension(150, 0));
		
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(logScrollPane);
		add(listScrollPane);
	}
	
	public String getTitle() {
		if(channel == null) {
			return "New Tab";
		} else {
			return channel;
		}
	}
	
	public void appendLog(String name, String message) {
		StyledDocument document = chatLog.getStyledDocument();
		
		try {
			document.insertString(document.getLength(), name, chatPanel.nameAttribute);
			document.insertString(document.getLength(), ": " + message + "\n", chatPanel.messageAttribute);
		} catch(BadLocationException e) {
			chatLog.setText(chatLog.getText() + name + ": " + message + "\n");
		}
		
		chatLog.setCaretPosition(document.getLength());
	}
	
	public void updateChannelUsers(String channel, String username, int status) {
		if(status == CloudInterface.CHANNELEVENT_ADDUSER || status == CloudInterface.CHANNELEVENT_LIST) {
			usersModel.addElement(new ChannelUserElement(username));
			
			if(status == CloudInterface.CHANNELEVENT_ADDUSER) {
				appendLog("Server", "[" + username + "] has joined the channel.");
			}
		} else if(status == CloudInterface.CHANNELEVENT_REMOVEUSER) {
			usersModel.removeElement(new ChannelUserElement(username));
			appendLog("Server", "[" + username + "] has left the channel.");
		} else if(status == CloudInterface.CHANNELEVENT_JOIN) {
			this.channel = channel;
			usersModel.clear();
			
			chatPanel.refreshTab(this);
		} else if(status == CloudInterface.CHANNELEVENT_LEAVE) {
			this.channel = null;
			usersModel.clear();
			appendLog("Server", "You have left the channel.");
			
			chatPanel.refreshTab(this);
		} else if(status == CloudInterface.CHANNELEVENT_PROMOTE) {
			usersModel.removeElement(new ChannelUserElement(username));
			usersModel.insertElementAt(new ChannelUserElement(username, "<html><b><font color=\"#008000\">" + username + "</font></b></html>"), 0);
			
			//check if we got promoted
			if(chatPanel.cloudInterface.getUsername().equalsIgnoreCase(username)) {
				isPromoted = true;
			}
		}
	}
	
	public void mousePressed(MouseEvent e) {
		mouseCheck(e);
	}
	
	public void mouseReleased(MouseEvent e) {
		mouseCheck(e);
	}

	public void mouseClicked(MouseEvent e) {
		mouseCheck(e);
	}
	
	public void mouseCheck(MouseEvent e) {
		if(e.isPopupTrigger()) {
			channelUsers.setSelectedIndex(channelUsers.locationToIndex(e.getPoint()));
			
			if(!isPromoted) {
				chatPanel.channelMenu.show(channelUsers, e.getX(), e.getY());
			} else {
				chatPanel.adminMenu.show(channelUsers, e.getX(), e.getY());
			}
		}
	}

	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
}

class ChannelUserElement {
	String username;
	String display;
	
	public ChannelUserElement(String username) {
		this(username, username);
	}
	
	public ChannelUserElement(String username, String display) {
		this.username = username;
		this.display = display;
	}
	
	public String getUsername() {
		return username;
	}
	
	public String toString() {
		return display;
	}
	
	public boolean equals(Object o) {
		if(o instanceof ChannelUserElement) {
			ChannelUserElement other = (ChannelUserElement) o;
			return username.equalsIgnoreCase(other.username);
		} else {
			return false;
		}
	}
}