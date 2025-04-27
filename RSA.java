import java.math.BigInteger;
import java.util.Random;
import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RSA implements Serializable {
    private final BigInteger n;  // Modulus
    private final BigInteger d;  // Private exponent
    private final BigInteger e = new BigInteger("65537");  // Fixed public exponent
    private BigInteger otherE, otherN;  // Other party's public key

    public RSA() {
        // Generate two large primes
        int bitLength = 2048;
        Random random = new Random();
        BigInteger p = BigInteger.probablePrime(bitLength, random);
        BigInteger q = BigInteger.probablePrime(bitLength, random);

        // Calculate modulus and Euler's totient
        n = p.multiply(q);
        BigInteger phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));

        // Calculate private exponent
        d = e.modInverse(phi);
    }

    public String getPublicKey() {
        return e.toString() + ":" + n.toString();
    }

    public void setOtherPublicKey(String publicKey) {
        String[] parts = publicKey.split(":");
        otherE = new BigInteger(parts[0]);
        otherN = new BigInteger(parts[1]);
    }

    public String encrypt(String message) throws IOException {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }

        byte[] bytes = message.getBytes("UTF-8");
        int chunkSize = (otherN.bitLength() / 8) - 11;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < bytes.length; i += chunkSize) {
            int length = Math.min(chunkSize, bytes.length - i);
            byte[] chunk = new byte[length];
            System.arraycopy(bytes, i, chunk, 0, length);

            BigInteger m = new BigInteger(1, chunk);
            if (m.compareTo(otherN) >= 0) {
                throw new IllegalArgumentException("Message too large for encryption");
            }

            BigInteger c = m.modPow(otherE, otherN);
            result.append(c.toString(16)).append(":");
        }

        return result.toString();
    }

    public String decrypt(String encrypted) throws IOException {
        if (encrypted == null || encrypted.isEmpty()) {
            throw new IllegalArgumentException("Encrypted message cannot be empty");
        }

        String[] chunks = encrypted.split(":");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        for (String chunk : chunks) {
            if (chunk.isEmpty()) continue;

            BigInteger c = new BigInteger(chunk, 16);
            BigInteger m = c.modPow(d, n);
            byte[] decryptedChunk = m.toByteArray();

            if (decryptedChunk[0] == 0) {
                bos.write(decryptedChunk, 1, decryptedChunk.length - 1);
            } else {
                bos.write(decryptedChunk);
            }
        }

        return bos.toString("UTF-8");
    }
}