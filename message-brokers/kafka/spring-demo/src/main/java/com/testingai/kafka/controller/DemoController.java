package com.testingai.kafka.controller;

import com.testingai.kafka.compaction.CompactionProducer;
import com.testingai.kafka.partitioning.PartitioningProducer;
import com.testingai.kafka.pubsub.PubSubProducer;
import com.testingai.kafka.simple.SimpleProducer;
import com.testingai.kafka.streams.StreamsProducer;
import com.testingai.kafka.transactions.TransactionalProducer;
import com.testingai.kafka.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Tag(name = "Kafka Demo", description = "Triggers for the seven Kafka messaging patterns")
public class DemoController {

    private final SimpleProducer simpleProducer;
    private final WorkQueueProducer workQueueProducer;
    private final PubSubProducer pubSubProducer;
    private final PartitioningProducer partitioningProducer;
    private final TransactionalProducer transactionalProducer;
    private final CompactionProducer compactionProducer;
    private final StreamsProducer streamsProducer;

    @PostMapping("/simple")
    @Operation(summary = "Send to simple topic")
    public ResponseEntity<String> simple(
            @Parameter(description = "Text to send") @RequestParam String message) {
        simpleProducer.send(message);
        return ResponseEntity.ok("Sent to simple.topic: " + message);
    }

    @PostMapping("/work")
    @Operation(summary = "Send to work topic")
    public ResponseEntity<String> work(
            @Parameter(description = "Text to send; dots simulate work (e.g. task..)") @RequestParam String message,
            @Parameter(description = "Number of messages (default 5)") @RequestParam(defaultValue = "5") int count) {
        workQueueProducer.send(message, count);
        return ResponseEntity.ok("Sent " + count + " messages to work.topic");
    }

    @PostMapping("/pubsub")
    @Operation(summary = "Broadcast to both consumer groups")
    public ResponseEntity<String> pubsub(
            @Parameter(description = "Text to broadcast") @RequestParam String message) {
        pubSubProducer.send(message);
        return ResponseEntity.ok("Broadcast to pubsub.topic: " + message);
    }

    @PostMapping("/partition")
    @Operation(summary = "Route by key to a specific partition")
    public ResponseEntity<String> partition(
            @Parameter(description = "Routing key — one of: info, warning, error",
                       schema = @Schema(allowableValues = {"info", "warning", "error"}))
            @RequestParam String key,
            @Parameter(description = "Text to route") @RequestParam String message) {
        partitioningProducer.send(key, message);
        return ResponseEntity.ok("Sent to partition.topic with key=" + key + ": " + message);
    }

    @PostMapping("/transaction")
    @Operation(summary = "Send a batch atomically (exactly-once)")
    public ResponseEntity<String> transaction(
            @Parameter(description = "Text to send") @RequestParam String message,
            @Parameter(description = "Batch size (default 3)") @RequestParam(defaultValue = "3") int count) {
        transactionalProducer.send(message, count);
        return ResponseEntity.ok("Committed " + count + " messages to tx-output.topic");
    }

    @PostMapping("/compaction")
    @Operation(summary = "Upsert a key/value pair to the compacted topic")
    public ResponseEntity<String> compaction(
            @Parameter(description = "Record key") @RequestParam String key,
            @Parameter(description = "Record value") @RequestParam String value) {
        compactionProducer.send(key, value);
        return ResponseEntity.ok("Sent key=" + key + " value=" + value + " to compacted.topic");
    }

    @PostMapping("/streams")
    @Operation(summary = "Send text to the Kafka Streams word-count topology")
    public ResponseEntity<String> streams(
            @Parameter(description = "Space-separated words to count") @RequestParam String message) {
        streamsProducer.send(message);
        return ResponseEntity.ok("Sent to streams-input.topic: " + message);
    }
}
