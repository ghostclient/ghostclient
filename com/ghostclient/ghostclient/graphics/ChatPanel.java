package com.ghostclient.ghostclient.graphics;


import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import com.ghostclient.ghostclient.CloudInterface;
import com.ghostclient.ghostclient.Config;
import com.ghostclient.ghostclient.GhostClient;

public class ChatPanel extends GCPanel implements ActionListener, ChangeListener, KeyListener {
	CloudInterface cloudInterface;
	
	JTabbedPane tabPane;
	List<TabPanel> chatTabs;
	JTextField submitText;
	
	JPopupMenu channelMenu; //shown when user right-clicks channel userlist
	JPopupMenu adminMenu; //similar to channelMenu, but also has moderator commands
	JMenuItem cmWhisperMenuItem;
	JMenuItem cmIgnoreMenuItem;
	JMenuItem cmUnignoreMenuItem;

	JMenuItem amWhisperMenuItem;
	JMenuItem amIgnoreMenuItem;
	JMenuItem amUnignoreMenuItem;
	JMenuItem amKickMenuItem;
	JMenuItem amBanMenuItem;
	
	SimpleAttributeSet nameAttribute;
	SimpleAttributeSet messageAttribute;
	
	ArrayList<SpecialTabPanel> specialTabs;
	
	boolean hideTabPanel; //if enabled, hide tab panel when possible
	
	public ChatPanel(CloudInterface cloudInterface) {
		super();
		this.cloudInterface = cloudInterface;
		
		channelMenu = new JPopupMenu();
		adminMenu = new JPopupMenu();
		
		cmWhisperMenuItem = new JMenuItem("Whisper");
		cmWhisperMenuItem.addActionListener(this);
		cmIgnoreMenuItem = new JMenuItem("Ignore");
		cmIgnoreMenuItem.addActionListener(this);
		cmUnignoreMenuItem = new JMenuItem("Unignore");
		cmUnignoreMenuItem.addActionListener(this);
		
		amWhisperMenuItem = new JMenuItem("Whisper");
		amWhisperMenuItem.addActionListener(this);
		amIgnoreMenuItem = new JMenuItem("Ignore");
		amIgnoreMenuItem.addActionListener(this);
		amUnignoreMenuItem = new JMenuItem("Unignore");
		amUnignoreMenuItem.addActionListener(this);
		amKickMenuItem = new JMenuItem("Kick");
		amKickMenuItem.addActionListener(this);
		amBanMenuItem = new JMenuItem("Ban");
		amBanMenuItem.addActionListener(this);

		channelMenu.add(cmWhisperMenuItem);
		channelMenu.add(cmIgnoreMenuItem);
		channelMenu.add(cmUnignoreMenuItem);
		adminMenu.add(amWhisperMenuItem);
		adminMenu.add(amIgnoreMenuItem);
		adminMenu.add(amUnignoreMenuItem);
		adminMenu.add(amKickMenuItem);
		adminMenu.add(amBanMenuItem);
		
		specialTabs = new ArrayList<SpecialTabPanel>();
		specialTabs.add(new PreferencesPanel());
		specialTabs.add(new SpecialTabPanel("+"));
		
		tabPane = new JTabbedPane();
		tabPane.addChangeListener(this);
		tabPane.addKeyListener(this);
		chatTabs = new ArrayList<TabPanel>();
		
		//add first log
		TabPanel firstPanel = new TabPanel(this);
		chatTabs.add(firstPanel);
		
		submitText = new JTextField();
		submitText.addActionListener(this);
		submitText.getDocument().addDocumentListener(new ReplyDocumentListener());
		submitText.setMinimumSize(new Dimension(0, submitText.getPreferredSize().height));
		submitText.setMaximumSize(new Dimension(10000, submitText.getPreferredSize().height));
		submitText.addKeyListener(this);
		
		//set attributes to bold the name and have regular message
		nameAttribute = new SimpleAttributeSet();
		StyleConstants.setBold(nameAttribute, true);
		
		messageAttribute = new SimpleAttributeSet();
		
		//configuration
		hideTabPanel = Config.getBoolean("chat_hidetabpanel", false);
		
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		addComponents();
	}
	
	public void addComponents() {
		synchronized(chatTabs) {
			removeAll();
			tabPane.removeAll();
			
			for(int i = 0; i < chatTabs.size(); i++) {
				TabPanel tabPanel = chatTabs.get(i);
				tabPane.addTab(tabPanel.getTitle(), tabPanel);
				tabPane.setTabComponentAt(i, new ButtonTabComponent(tabPane, this));
			}
			
			for(int i = 0; i < specialTabs.size(); i++) {
				SpecialTabPanel specialPanel = specialTabs.get(i);
				tabPane.addTab(specialPanel.getTabTitle(), specialPanel);
			}
			
			if(chatTabs.size() == 1 && hideTabPanel) {
				add(chatTabs.get(0));
			} else {
				add(tabPane);
			}
			
			add(submitText);
		}
		
		submitText.requestFocus();
		repaint();
	}
	
	public void refreshTab(TabPanel tabPanel) {
		synchronized(chatTabs) {
			int index = chatTabs.indexOf(tabPanel);
			
			if(index != -1) {
				tabPane.setTitleAt(index, tabPanel.getTitle());
				tabPane.setTabComponentAt(index, new ButtonTabComponent(tabPane, this));
				repaint();
			}
		}
	}
	
	private void updateLogAppend(int tab, String name, String message) {
		synchronized(chatTabs) {
			if(tab >= 0 && tab < chatTabs.size()) {
				chatTabs.get(tab).appendLog(name,  message);
			}
		}
	}
	
	private void updateChannelUsers(String channel, String username, int status) {
		synchronized(chatTabs) {
			boolean found = false;
			
			for(TabPanel panel : chatTabs) {
				if(panel.channel != null && panel.channel.equalsIgnoreCase(channel)) {
					panel.updateChannelUsers(channel, username, status);
					found = true;
					break;
				}
			}
			
			//if we didn't find any other tab and we're joining a channel, use current tab
			//but only if the current tab has no channel
			if(!found && status == CloudInterface.CHANNELEVENT_JOIN) {
				//okay, change current tab to this channel and do the update
				int selectedTab = getSelectedTab();
				
				//have to check with chat tabs size in case it's extra tab
				if(selectedTab < chatTabs.size()) {
					TabPanel panel = chatTabs.get(selectedTab);
					
					if(panel.channel == null) {
						panel.updateChannelUsers(channel, username, status);
						
						if(panel.channel != channel) {
							//usually, updateChannelUsers call will change the channel
							//sometimes there might be error though?
							panel.channel = channel;
						}
					} else {
						//ok, weird; leave the new channel then
						cloudInterface.channelEvent(channel, false);
					}
				} else {
					//ok, weird; leave the new channel then
					cloudInterface.channelEvent(channel, false);
				}
			}
		}
	}
	
	public void append(String name, String message) {
		GhostClient.println("[ChatPanel] [" + name + "] " + message);
		
		UpdateLog update = new UpdateLog(getSelectedTab(), name, message);
		SwingUtilities.invokeLater(update);
	}
	
	public void append(String channel, String name, String message) {
		GhostClient.println("[ChatPanel] [" + name + "] " + message);
		
		int targetTab = getSelectedTab();
		
		synchronized(chatTabs) {
			for(int i = 0; i < chatTabs.size(); i++) {
				if(chatTabs.get(i).channel != null && chatTabs.get(i).channel.equalsIgnoreCase(channel)) {
					targetTab = i;
				}
			}
			
			//if this isn't the selected one, notify with asterick
			if(targetTab != getSelectedTab()) {
				tabPane.setTitleAt(targetTab, channel + "*");
				chatTabs.get(targetTab).notifying = true;
				tabPane.setTabComponentAt(targetTab, new ButtonTabComponent(tabPane, this));
				tabPane.repaint();
			}
		}
		
		UpdateLog update = new UpdateLog(targetTab, name, message);
		SwingUtilities.invokeLater(update);
	}
	
	public void channelUsers(String channel, String username, int status) {
		UpdateChannelUsers update = new UpdateChannelUsers(channel, username, status);
		SwingUtilities.invokeLater(update);
	}
	
	public void newTab() {
		synchronized(chatTabs) {
			chatTabs.add(new TabPanel(this));
			
			//this has to be within synchronized so that chatTabs and tabPane remain synchronized
			addComponents();
			
			tabPane.setSelectedIndex(chatTabs.size() - 1);
		}
	}
	
	public void closeTab() {
		closeTab(-1);
	}
	
	public void closeTab(int closedTab) {
		synchronized(chatTabs) {
			if(chatTabs.size() > 1) {
				if(closedTab == -1) closedTab = getSelectedTab();
				
				if(closedTab < chatTabs.size()) {
					TabPanel panel = chatTabs.remove(closedTab); //remove tab at this index
					
					//leave channel if needed
					if(panel.channel != null) {
						cloudInterface.channelEvent(panel.channel, false);
					}
					
					//this has to be within synchronized so that chatTabs and tabPane remain synchronized
					addComponents();
					
					//set previous tab as the current tab
					if(!chatTabs.isEmpty()) {
						int currentTab = closedTab - 1;
						if(currentTab < 0) currentTab = 0;
						tabPane.setSelectedIndex(currentTab);
					}
				}
			} else {
				updateLogAppend(getSelectedTab(), "System", "You only have one tab open!");
			}
		}
	}
	
	public void nextTab(boolean right) {
		synchronized(chatTabs) {
			if(chatTabs.size() > 1) {
				int nextTab;
				
				if(right) {
					nextTab = (getSelectedTab() + 1) % chatTabs.size();
				} else {
					nextTab = (getSelectedTab() - 1 + chatTabs.size()) % chatTabs.size();
				}
				
				tabPane.setSelectedIndex(nextTab);
			}
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == submitText) {
			String str = submitText.getText().trim();
			
			//submit message to cloud interface for processing
			if(!str.isEmpty()) {
				//intercept tab-control commands
				if(str.equalsIgnoreCase("/newtab") || str.equalsIgnoreCase("/nt")) {
					newTab();
				} else if(str.equalsIgnoreCase("/closetab") || str.equalsIgnoreCase("/ct")) {
					closeTab();
				} else {
					String channel = null;
					int tab;
					
					synchronized(chatTabs) {
						tab = getSelectedTab();
						
						if(tab < chatTabs.size()) {
							channel = chatTabs.get(tab).channel;
						}
					}
					
					//this is what we should display
					//if not a command, it's just the message that we sent along with our username
					List<String[]> response = cloudInterface.processMessage(channel, str);
					
					for(String[] part : response) {
						updateLogAppend(tab, part[0], part[1]);
					}
				}
			}
			
			submitText.setText("");
		} else if(e.getSource() == cmWhisperMenuItem || e.getSource() == amWhisperMenuItem) {
			String target = getSelectedChannelUser();
			submitText.setText("/w " + target + " ");
		} else if(e.getSource() == cmIgnoreMenuItem || e.getSource() == amIgnoreMenuItem) {
			String target = getSelectedChannelUser();
			cloudInterface.alterIgnore(true, target);
			append("System", "You are now ignoring " + target);
		} else if(e.getSource() == cmUnignoreMenuItem || e.getSource() == amUnignoreMenuItem) {
			String target = getSelectedChannelUser();
			cloudInterface.alterIgnore(false, target);
			append("System", "You are no longer ignoring " + target);
		} else if(e.getSource() == amKickMenuItem) {
			String target = getSelectedChannelUser();
			int tabIndex = getSelectedTab();
			
			//we have to check with chat tabs size because this may be one of the extra tabs
			if(tabIndex < chatTabs.size()) {
				TabPanel tabPanel = chatTabs.get(tabIndex);
				cloudInterface.sendKick(tabPanel.channel, target);
			}
		} else if(e.getSource() == amBanMenuItem) {
			String target = getSelectedChannelUser();
			int tabIndex = getSelectedTab();
			
			//we have to check with chat tabs size because this may be one of the extra tabs
			if(tabIndex < chatTabs.size()) {
				TabPanel tabPanel = chatTabs.get(tabIndex);
				cloudInterface.sendBan(tabPanel.channel, target, false);
			}
		}
	}
	
	public void keyPressed(KeyEvent e) {
		if((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
			if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
				nextTab(true);
			} else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
				nextTab(false);
			} else if(e.getKeyCode() == KeyEvent.VK_T) {
				newTab();
			} else if(e.getKeyCode() == KeyEvent.VK_W) {
				closeTab();
			}
		}
	}
	
	public void keyReleased(KeyEvent e) {

	}
	
	public void keyTyped(KeyEvent e) {

	}
	
	public void stateChanged(ChangeEvent e) {
		if(e.getSource() == tabPane) {
			synchronized(chatTabs) {
				//if we were notifying on this tab, then stop
				int tabIndex = getSelectedTab();
				
				//we have to check with chat tabs size because this may be one of the extra tabs
				if(tabIndex < chatTabs.size()) {
					TabPanel tabPanel = chatTabs.get(tabIndex);
					
					if(tabPanel.notifying) {
						tabPane.setTitleAt(tabIndex, tabPanel.getTitle());
						tabPane.setTabComponentAt(tabIndex, new ButtonTabComponent(tabPane, this));
						tabPane.repaint();
					}
				} else {
					//maybe it's a special tab
					if(tabIndex < chatTabs.size() + specialTabs.size()) {
						SpecialTabPanel specialPanel = specialTabs.get(tabIndex - chatTabs.size());
						String title = specialPanel.getTabTitle();
						
						if(title.equals("+")) {
							//add a new tab
							newTab();
						}
					}
				}
			}
		}
	}
	
	public String getSelectedChannelUser() {
		synchronized(chatTabs) {
			int tab = getSelectedTab();
			
			if(tab < chatTabs.size()) {
				return ((ChannelUserElement) chatTabs.get(tab).channelUsers.getSelectedValue()).getUsername();
			} else {
				return "uakf.b";
			}
		}
	}
	
	public int getSelectedTab() {
		synchronized(chatTabs) {
			if(chatTabs.size() > 1 || !hideTabPanel){ 
				int tab = tabPane.getSelectedIndex();
				
				if(tab != -1) {
					return tab;
				}
			}
		}
		
		return 0;
	}
	
	//called when screen is loaded, so that we can focus the chat box
	public void onLoad() {
		submitText.requestFocus();
	}
	
	class UpdateLog implements Runnable {
		int tab;
		String name;
		String message;
		
		public UpdateLog(int tab, String name, String message) {
			this.tab = tab;
			this.name = name;
			this.message = message;
		}
		
		public void run() {
			updateLogAppend(tab, name, message);
		}
	}
	
	class UpdateChannelUsers implements Runnable {
		String channel;
		String username;
		int status;
		
		public UpdateChannelUsers(String channel, String username, int status) {
			this.channel = channel;
			this.username = username;
			this.status = status;
		}
		
		public void run() {
			updateChannelUsers(channel, username, status);
		}
	}
	
	//used with reply document listener below to update text field properly
	class UpdateTextField implements Runnable {
		public void run() {
			if(submitText.getText().startsWith("/r ")) {
				String lastWhisperer = cloudInterface.getLastWhisperer();
				String remaining = submitText.getText().substring(3);
				
				if(lastWhisperer != null) {
					submitText.setText("/w " + lastWhisperer + " " + remaining);
				}
			}
			
			if(submitText.getText().length() > 256) {
				submitText.setText(submitText.getText().substring(0, 256));
			}
		}
	}
	
	//used to update text field to /w [username] when user types /r (reply)
	class ReplyDocumentListener implements DocumentListener {
		public void changedUpdate(DocumentEvent e) {
			checkField();
		}
		
		public void removeUpdate(DocumentEvent e) {
			checkField();
		}
		
		public void insertUpdate(DocumentEvent e) {
			checkField();
		}
		
		public void checkField() {
			if(submitText.getText().equalsIgnoreCase("/r ") || submitText.getText().length() > 256) {
				//we can't actually set text here, or it'll screw up apparently
				//so we invoke later
				SwingUtilities.invokeLater(new UpdateTextField());
			}
		}
	}
}
