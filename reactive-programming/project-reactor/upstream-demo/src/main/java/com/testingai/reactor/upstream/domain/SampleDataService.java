package com.testingai.reactor.upstream.domain;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SampleDataService {

	private final List<Product> catalog = List.of(new Product("P-100", "Wireless Mouse", 2499),
			new Product("P-101", "Mechanical Keyboard", 8999), new Product("P-102", "USB-C Hub", 3499),
			new Product("P-103", "27-inch Monitor", 24999), new Product("P-104", "Webcam", 5499));

	public List<Product> catalog() {
		return catalog;
	}
}
