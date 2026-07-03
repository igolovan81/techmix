package com.testingai.kafka.simple

import com.testingai.kafka.util.FailureSimulator
import org.mockito.MockedStatic
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.mockStatic

class SimpleConsumerTest extends Specification {

	def consumer = new SimpleConsumer()

	def "receive does not throw on success"() {
		given:
		MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator)
		mock.when { FailureSimulator.maybeThrow(anyString()) }.thenAnswer({ null })

		when:
		consumer.receive("hello")

		then:
		noExceptionThrown()

		cleanup:
		mock.close()
	}

	def "receive propagates exception on simulated failure"() {
		given:
		MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator)
		mock.when { FailureSimulator.maybeThrow(anyString()) }.thenThrow(new RuntimeException("Simulated"))

		when:
		consumer.receive("hello")

		then:
		def ex = thrown(RuntimeException)
		ex.message.contains("Simulated")

		cleanup:
		mock.close()
	}
}
