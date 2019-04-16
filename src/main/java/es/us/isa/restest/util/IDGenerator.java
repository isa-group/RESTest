package es.us.isa.restest.util;

/**
 * 
 * @author Sergio Segura
 */
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;

public class IDGenerator {

	
	static long seed = -1;
	static Random rand = new Random();
	
	/**
	 * @param rand Random generator
	 * @return a short ID (13 characters)
	 */
	public static String generateId() {
		byte[] bytes = new byte[16];
		rand.nextBytes(bytes);
		UUID uuid = UUID.nameUUIDFromBytes(bytes);
		long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
		return Long.toString(l, Character.MAX_RADIX);
	}
	
	public static void setSeed(long s) {
		seed=s;
		rand.setSeed(seed);
	}
}
