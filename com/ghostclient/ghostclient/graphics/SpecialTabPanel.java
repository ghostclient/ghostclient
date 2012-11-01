package com.ghostclient.ghostclient.graphics;

import javax.swing.JPanel;

public class SpecialTabPanel extends JPanel {
	String tabTitle;
	
	public SpecialTabPanel(String tabTitle) {
		super();
		this.tabTitle = tabTitle;
	}
	
	public String getTabTitle() {
		return tabTitle;
	}
}
