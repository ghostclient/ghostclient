package com.ghostclient.ghostclient.deny;

public class Packet {
	//packet used for flood detection
	int length;
	long time;
	
	public Packet(int length) {
		this.length = length;
		this.time = System.currentTimeMillis();
	}
	
	public int getAge() {
		return (int) (System.currentTimeMillis() - time);
	}
	
	public int getLength() {
		return length;
	}
}