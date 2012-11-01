package com.ghostclient.ghostclient;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class GCUtil {
	static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
	
    public static String hexEncode(byte[] buf)
    {
        char[] chars = new char[2 * buf.length];
        for (int i = 0; i < buf.length; ++i)
        {
            chars[2 * i] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[buf[i] & 0x0F];
        }
        return new String(chars);
    }
    
    public static byte[] hexDecode(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    
    public static byte[] shortToByteArray(short s) {
		return new byte[] { (byte) ((s & 0xFF00) >> 8), (byte) (s & 0x00FF) };
	}
    
	public static int unsignedShort(short s) {
		byte[] array = shortToByteArray(s);
		int b1 = (0x000000FF & ((int) array[0]));
		int b2 = (0x000000FF & ((int) array[1]));
		return (b1 << 8 | b2);
	}

	public static int unsignedByte(byte b) {
		return (0x000000FF & ((int)b));
	}
	
	public static String getTerminatedString(ByteBuffer buf) {
		return new String(getTerminatedArray(buf));
	}

	public static byte[] getTerminatedArray(ByteBuffer buf) {
		int start = buf.position();

		while(buf.position() < buf.capacity() && buf.get() != 0) {}
		int end = buf.position();

		byte[] bytes = new byte[end - start - 1]; //don't include terminator
		buf.position(start);
		buf.get(bytes);

		//put position after array
		buf.position(end); //skip terminator

		return bytes;
	}
	
	public static byte[] strToBytes(String str) {
		try {
			return str.getBytes("UTF-8");
		} catch(UnsupportedEncodingException uee) {
			System.out.println("[GCUtil] UTF-8 is an unsupported encoding: " + uee.getLocalizedMessage());
			return str.getBytes();
		}
	}
	
	public static File getContainingDirectory() {
		String path = GCSetup.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		
		try {
			path = URLDecoder.decode(path, "UTF-8");
		} catch(UnsupportedEncodingException e) {}
		
		File parentDir = new File(path);
		
		if(!parentDir.isDirectory()) {
			parentDir = parentDir.getParentFile();
		}

		return parentDir;
	}
	
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().indexOf("win") >= 0;
	}
	
	public static MessageDigest getSHA1() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch(NoSuchAlgorithmException e) {
			return null;
		}
	}
}
