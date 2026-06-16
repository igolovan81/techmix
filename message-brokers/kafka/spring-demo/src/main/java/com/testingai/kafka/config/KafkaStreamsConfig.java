package com.testingai.kafka.config;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.util.Arrays;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

	@Bean
	public KStream<String, String> wordCountStream(StreamsBuilder builder) {
		KStream<String, String> input = builder.stream(TopicConfig.STREAMS_INPUT_TOPIC);
		input.flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\s+"))).groupBy((key, word) -> word)
				.count(Materialized.as("word-count-store")).toStream().mapValues(Object::toString)
				.to(TopicConfig.STREAMS_WORDCOUNT_OUTPUT);
		return input;
	}
}
