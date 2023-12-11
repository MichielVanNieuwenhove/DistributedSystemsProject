package Interface;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class publicCryptographicHash {

    private static final String HASH_ALGORITHM = "SHA-256";

    public static String hashPreimageTag(String preimageTag) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] tagBytes = preimageTag.getBytes();
        byte[] hashBytes = digest.digest(tagBytes);

        StringBuilder hashSb = new StringBuilder();
        for (byte hashByte : hashBytes) {
            hashSb.append(Integer.toHexString((hashByte & 0xFF) | 0x100));
        }

        return hashSb.toString();
    }
}