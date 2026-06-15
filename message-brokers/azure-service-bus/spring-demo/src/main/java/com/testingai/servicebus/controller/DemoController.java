package com.testingai.servicebus.controller;

import com.testingai.servicebus.dlq.DlqProducer;
import com.testingai.servicebus.pubsub.PubSubPublisher;
import com.testingai.servicebus.routing.RoutingPublisher;
import com.testingai.servicebus.session.SessionProducer;
import com.testingai.servicebus.simple.SimpleProducer;
import com.testingai.servicebus.transactions.TransactionalProducer;
import com.testingai.servicebus.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Azure Service Bus Demo")
public class DemoController {

    private final SimpleProducer simpleProducer;
    private final WorkQueueProducer workQueueProducer;
    private final PubSubPublisher pubSubPublisher;
    private final RoutingPublisher routingPublisher;
    private final DlqProducer dlqProducer;
    private final SessionProducer sessionProducer;
    private final TransactionalProducer transactionalProducer;

    @PostMapping("/simple/send")
    @Operation(summary = "Send a message to the simple queue")
    public String simpleSend(@RequestParam String message) {
        simpleProducer.send(message);
        return "sent to simple-queue: " + message;
    }

    @PostMapping("/work/send")
    @Operation(summary = "Send a task to the work queue (competing consumers)")
    public String workSend(@RequestParam String message) {
        workQueueProducer.send(message);
        return "sent to work-queue: " + message;
    }

    @PostMapping("/pubsub/publish")
    @Operation(summary = "Publish to pubsub-topic (fan-out to sub-a and sub-b)")
    public String pubSubPublish(@RequestParam String message) {
        pubSubPublisher.publish(message);
        return "published to pubsub-topic: " + message;
    }

    @PostMapping("/routing/publish")
    @Operation(summary = "Publish to routing-topic with level property (error -> sub-error only)")
    public String routingPublish(@RequestParam String level, @RequestParam String message) {
        routingPublisher.publish(level, message);
        return "published to routing-topic level=" + level + ": " + message;
    }

    @PostMapping("/dlq/send")
    @Operation(summary = "Send to dlq-queue (50% chance of abandonment → DLQ after 3 attempts)")
    public String dlqSend(@RequestParam String message) {
        dlqProducer.send(message);
        return "sent to dlq-queue: " + message;
    }

    @PostMapping("/session/send")
    @Operation(summary = "Send to session-queue with a session ID (FIFO per session)")
    public String sessionSend(@RequestParam String message, @RequestParam String sessionId) {
        sessionProducer.send(message, sessionId);
        return "sent to session-queue sessionId=" + sessionId + ": " + message;
    }

    @PostMapping("/tx/send")
    @Operation(summary = "Send N messages to tx-queue in a single transaction")
    public String txSend(@RequestParam String message, @RequestParam(defaultValue = "3") int count) {
        transactionalProducer.send(message, count);
        return "committed " + count + " transactional messages: " + message;
    }
}
