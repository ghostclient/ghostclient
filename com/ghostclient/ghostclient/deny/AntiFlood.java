package com.ghostclient.ghostclient.deny;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class AntiFlood {
	Queue<Packet> antifloodPackets; //list of past packets for antiflood, or null if it's disabled
	
	int antifloodBytes; //maximum bytes in antifloodInterval ms; 0 means no antiflood
	int antifloodInterval; //milliseconds to enforce antiflood bytes
	
	public AntiFlood(int antifloodBytes, int antifloodInterval) {
		this.antifloodBytes = antifloodBytes;
		this.antifloodInterval = antifloodInterval;
		
		antifloodPackets = new LinkedList<Packet>();
	}

	//called whenever a packet is received
	//true means flood is detected and we should disconnect, as well as increment infraction
	public boolean checkFlood(int length) {
		//append our packet
		antifloodPackets.add(new Packet(length));
		
		//delete old packtes
		//older packets are in front of queue, so we take from front until we reach one that's not too old
		while(true) {
			Packet p = antifloodPackets.peek();
			
			if(p != null) {
				if(p.getAge() > antifloodInterval) {
					//ok, this one's too old, delete and continue looping
					antifloodPackets.poll();
				} else {
					break;
				}
			} else {
				break;
			}
		}
		
		//count total length in queue, check with antifloodBytes
		int totalLength = 0;
		Iterator<Packet> it = antifloodPackets.iterator();
		
		while(it.hasNext()) {
			totalLength += it.next().getLength();
		}
		
		return totalLength > antifloodBytes;
	}
}
