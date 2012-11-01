package com.ghostclient.ghostclient.graphics;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import com.ghostclient.ghostclient.Config;

public class PreferencesPanel extends SpecialTabPanel implements ActionListener {
	DescripCheckBox pEnableGProxy;
	DescripField pServerPort;
	DescripField pWar3Version;
	DescripCheckBox pEnableSound;
	DescripCheckBox pHideTabPanel;
	JButton bSavePrefs;
	
	public PreferencesPanel() {
		super("Preferences");
		
		pEnableGProxy = new DescripCheckBox("Enable GProxy++", Config.getBoolean("gproxy", true));
		pEnableGProxy.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		
		pServerPort = new DescripField("Local GC port", Config.getInt("serverPort", 7112) + "", 20, false);
		pServerPort.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		
		pWar3Version = new DescripField("Warcraft version", Config.getInt("war3version", 26) + "", 20, false);
		pWar3Version.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		
		pEnableSound = new DescripCheckBox("Enable sounds", Config.getBoolean("sound_enabled", true));
		pEnableSound.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		
		pHideTabPanel = new DescripCheckBox("Hide tabs when only one open", Config.getBoolean("chat_hidetabpanel", false));
		pHideTabPanel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		
		bSavePrefs = new JButton("Save preferences");
		bSavePrefs.addActionListener(this);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(pEnableGProxy);
		add(pServerPort);
		add(pWar3Version);
		add(pEnableSound);
		add(pHideTabPanel);
		add(bSavePrefs);
		add(Box.createVerticalGlue()); //so that boxlayout doesn't stretch our components
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == bSavePrefs) {
			Config.setBoolean("gproxy", pEnableGProxy.isChecked());
			Config.set("serverPort", pServerPort.getField());
			Config.set("war3version", pWar3Version.getField());
			Config.setBoolean("enable_sound", pEnableSound.isChecked());
			Config.setBoolean("chat_hidetabpanel", pHideTabPanel.isChecked());
			
			JOptionPane.showMessageDialog(null, "Preferences have been saved.");
		}
	}
}
