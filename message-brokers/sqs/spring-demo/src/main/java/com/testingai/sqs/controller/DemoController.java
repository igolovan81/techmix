package com.testingai.sqs.controller;

import com.testingai.sqs.dlq.DlqProducer;
import com.testingai.sqs.fanout.FanoutPublisher;
import com.testingai.sqs.fifo.FifoProducer;
import com.testingai.sqs.pubsub.PubSubPublisher;
import com.testingai.sqs.simple.SimpleProducer;
import com.testingai.sqs.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Tag(name = "SQS Demo")
public class DemoController {

    private final SimpleProducer      simpleProducer;
    private final WorkQueueProducer   workQueueProducer;
    private final FanoutPublisher     fanoutPublisher;
    private final DlqProducer         dlqProducer;
    private final FifoProducer        fifoProducer;
    private final PubSubPublisher     pubSubPublisher;

    @PostMapping("/simple")
    @Operation(summary = "Simple queue — send to simple-queue, single consumer receives it")
    public ResponseEntity<String> simple(@RequestParam String message) {
        simpleProducer.send(message);
        return ResponseEntity.ok("sent: " + message);
    }

    @PostMapping("/work")
    @Operation(summary = "Work queue — competing consumers A and B share work-queue")
    public ResponseEntity<String> work(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") int count) {
        for (int i = 0; i < count; i++) {
            workQueueProducer.send(message);
        }
        return ResponseEntity.ok("sent " + count + " message(s)");
    }

    @PostMapping("/fanout")
    @Operation(summary = "Fanout — SNS fanout-topic delivers to fanout-queue-a AND fanout-queue-b")
    public ResponseEntity<String> fanout(@RequestParam String message) {
        fanoutPublisher.publish(message);
        return ResponseEntity.ok("published: " + message);
    }

    @PostMapping("/retry")
    @Operation(summary = "DLQ — retry-queue consumer fails ~50%, message goes to retry-dlq after 3 attempts")
    public ResponseEntity<String> retry(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") int count) {
        for (int i = 0; i < count; i++) {
            dlqProducer.send(message);
        }
        return ResponseEntity.ok("sent " + count + " message(s) to retry queue");
    }

    @PostMapping("/fifo")
    @Operation(summary = "FIFO queue — ordered delivery per message group")
    public ResponseEntity<String> fifo(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-group") String groupId) {
        fifoProducer.send(message, groupId);
        return ResponseEntity.ok("sent (group=" + groupId + "): " + message);
    }

    @PostMapping("/pubsub")
    @Operation(summary = "Pub/Sub — SNS pubsub-topic delivers to pubsub-queue-a AND pubsub-queue-b")
    public ResponseEntity<String> pubsub(@RequestParam String message) {
        pubSubPublisher.publish(message);
        return ResponseEntity.ok("published: " + message);
    }
}
