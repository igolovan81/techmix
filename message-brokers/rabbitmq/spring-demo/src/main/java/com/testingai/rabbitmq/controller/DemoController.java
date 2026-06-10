package com.testingai.rabbitmq.controller;

import com.testingai.rabbitmq.pubsub.PubSubProducer;
import com.testingai.rabbitmq.routing.RoutingProducer;
import com.testingai.rabbitmq.simple.SimpleProducer;
import com.testingai.rabbitmq.workqueue.WorkQueueProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final SimpleProducer simpleProducer;
    private final WorkQueueProducer workQueueProducer;
    private final PubSubProducer pubSubProducer;
    private final RoutingProducer routingProducer;

    @PostMapping("/simple")
    public ResponseEntity<String> simple(@RequestParam String message) {
        simpleProducer.send(message);
        return ResponseEntity.ok("Sent to simple.queue: " + message);
    }

    @PostMapping("/work")
    public ResponseEntity<String> work(
            @RequestParam String message,
            @RequestParam(defaultValue = "5") int count) {
        workQueueProducer.send(message, count);
        return ResponseEntity.ok("Sent " + count + " messages to work.queue");
    }

    @PostMapping("/pubsub")
    public ResponseEntity<String> pubsub(@RequestParam String message) {
        pubSubProducer.send(message);
        return ResponseEntity.ok("Broadcast to pubsub.fanout: " + message);
    }

    @PostMapping("/routing")
    public ResponseEntity<String> routing(
            @RequestParam String key,
            @RequestParam String message) {
        routingProducer.send(key, message);
        return ResponseEntity.ok("Routed to routing.direct with key=" + key + ": " + message);
    }
}
