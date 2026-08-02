package com.testingai.graphql.controller;

import com.testingai.graphql.domain.ProductImageService;
import java.io.IOException;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST sidecar for {@code Product} image transfer. GraphQL has no native support for binary payloads, so the schema
 * only ever exposes {@code Product.imageUrl} — a pointer to these two plain endpoints (see
 * {@link DemoController#productImageUrl}). Deliberately outside the GraphQL controller/schema entirely.
 */
@RestController
@RequiredArgsConstructor
public class ProductImageController {

	private static final int MAX_FILE_SIZE_MB = 5;

	private final ProductImageService productImageService;

	@PostMapping("/api/products/{id}/image")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file)
			throws IOException {
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("Uploaded file must be an image, got content type: " + contentType);
		}
		productImageService.upload(id, contentType, file.getBytes());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/products/{id}/image")
	public ResponseEntity<byte[]> download(@PathVariable Long id) {
		return productImageService.find(id).map(image -> ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.contentType())).body(image.data()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body("Uploaded file exceeds the " + MAX_FILE_SIZE_MB + "MB limit");
	}
}
