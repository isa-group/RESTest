package es.us.isa.restest.util;


import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;

public class IDGenerator {

	
	/**
	 * @param rand Random generator
	 * @return a short ID (13 characters)
	 */
	public static String generateId(Random rand) {
		byte[] bytes = new byte[16];
		rand.nextBytes(bytes);
		UUID uuid = UUID.nameUUIDFromBytes(bytes);
		long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
		return Long.toString(l, Character.MAX_RADIX);
	}
}
