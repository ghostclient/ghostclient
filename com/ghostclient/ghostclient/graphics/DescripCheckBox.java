package com.ghostclient.ghostclient.graphics;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class DescripCheckBox extends JPanel {
	public JLabel descrip;
	public JCheckBox checkBox;

	public DescripCheckBox(String desc) {
		this(desc, false);
	}

	public DescripCheckBox(String desc, boolean checked) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		descrip = new JLabel(desc);
		descrip.setAlignmentX(JLabel.TOP_ALIGNMENT);
		
		checkBox = new JCheckBox();
		checkBox.setSelected(checked);
		checkBox.setAlignmentX(JLabel.TOP_ALIGNMENT);

		add(descrip);
		add(checkBox);
	}

	public DescripCheckBox(JLabel descrip, JCheckBox checkBox) {
		this.descrip = descrip;
		this.checkBox = checkBox;
		add(descrip);
		add(checkBox);
	}
	
	public boolean isChecked() {
		return checkBox.isSelected();
	}
}