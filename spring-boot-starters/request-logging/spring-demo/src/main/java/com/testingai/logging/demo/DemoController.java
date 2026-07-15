package com.testingai.logging.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

	@GetMapping("/hello")
	public String hello() {
		return "Hello from the request-logging demo!";
	}

	@PostMapping("/echo")
	public EchoRequest echo(@RequestBody EchoRequest request) {
		return request;
	}
}
