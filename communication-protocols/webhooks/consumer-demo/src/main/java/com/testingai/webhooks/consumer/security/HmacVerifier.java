package com.testingai.webhooks.consumer.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@Slf4j
public class HmacVerifier {

	private static final String ALGORITHM = "HmacSHA256";

	public boolean verify(String secret, String payload, String signatureHeader) {
		if (signatureHeader == null) {
			log.debug("signature verification failed: no signature header present");
			return false;
		}
		String expected = "sha256=" + sign(secret, payload);
		boolean matches = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				signatureHeader.getBytes(StandardCharsets.UTF_8));
		log.debug("signature verification {}", matches ? "passed" : "failed");
		return matches;
	}

	private String sign(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Unable to compute HMAC signature", e);
		}
	}
}
