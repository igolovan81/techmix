package com.testingai.kafka.util

import spock.lang.Specification

class FailureSimulatorTest extends Specification {

	def "maybeThrow does not throw most of the time"() {
		given:
		int failures = 0

		when:
		1000.times {
			try {
				FailureSimulator.maybeThrow("test")
			} catch (RuntimeException ignored) {
				failures++
			}
		}

		then:
		// With 5% failure rate, expect roughly 50 failures; accept 5-200 range
		failures >= 5 && failures <= 200
	}
}
