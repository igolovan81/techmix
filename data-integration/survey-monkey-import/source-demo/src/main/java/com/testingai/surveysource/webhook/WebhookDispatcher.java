package com.testingai.surveysource.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class WebhookDispatcher {

	private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

	private final RestClient restClient;
	private final String webhookUrl;
	private final String secret;
	private final ObjectMapper objectMapper;

	public WebhookDispatcher(RestClient.Builder builder, @Value("${importer.webhook-url}") String webhookUrl,
			@Value("${importer.webhook-secret}") String secret, ObjectMapper objectMapper) {
		this.restClient = builder.build();
		this.webhookUrl = webhookUrl;
		this.secret = secret;
		this.objectMapper = objectMapper;
	}

	public void dispatch(String surveyId, String responseId) {
		String body = writeValue(new WebhookEvent(surveyId, responseId, "response_completed"));
		String signature = "sha256=" + hmacSha256Hex(body, secret);
		log.info("Dispatching webhook to {} for survey {} response {}", webhookUrl, surveyId, responseId);
		try {
			restClient.post().uri(webhookUrl).header("X-SurveyMonkey-Signature", signature)
					.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
			log.info("Webhook delivered for survey {} response {}", surveyId, responseId);
		} catch (RestClientException e) {
			log.warn("Webhook delivery failed for survey {} response {}: {}", surveyId, responseId, e.getMessage());
			throw e;
		}
	}

	private String writeValue(WebhookEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize webhook event", e);
		}
	}

	static String hmacSha256Hex(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC computation failed", e);
		}
	}
}
