package com.ghostclient.ghostclient.graphics;

import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class DescripField extends JPanel {
    public JLabel descrip;
    public JTextField field;

    public DescripField(String desc, String f, boolean password) {
        this(desc, f, 20, false);
    }

    public DescripField(String desc, String f, int fsize, boolean password) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        descrip = new JLabel(desc);
        descrip.setAlignmentY(JLabel.TOP_ALIGNMENT);
        
        if(password) field = new JPasswordField(f, fsize);
        else field = new JTextField(f, fsize);
        field.setAlignmentY(JLabel.TOP_ALIGNMENT);
        field.setMaximumSize(new Dimension(10000, field.getPreferredSize().height));

        add(descrip);
        add(field);
    }

    public DescripField(JLabel descrip, JTextField field) {
        this.descrip = descrip;
        this.field = field;
        add(descrip);
        add(field);
    }
    
    public String getField() {
        return field.getText();
    }
    
    public String getPassword() {
    	if(field instanceof JPasswordField) {
    		return new String(((JPasswordField) field).getPassword());
    	} else {
    		return null;
    	}
    }
    
    public void setField(String s) {
        field.setText(s);
    }
}