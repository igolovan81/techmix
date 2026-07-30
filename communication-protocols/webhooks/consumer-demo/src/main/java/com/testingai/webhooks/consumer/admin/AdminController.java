package com.testingai.webhooks.consumer.admin;

import com.testingai.webhooks.consumer.receiver.ReceivedEvent;
import com.testingai.webhooks.consumer.receiver.ReceivedEventStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

	private final ReceivedEventStore receivedEventStore;

	public AdminController(ReceivedEventStore receivedEventStore) {
		this.receivedEventStore = receivedEventStore;
	}

	@GetMapping("/received")
	public List<ReceivedEvent> received() {
		return receivedEventStore.all();
	}
}
