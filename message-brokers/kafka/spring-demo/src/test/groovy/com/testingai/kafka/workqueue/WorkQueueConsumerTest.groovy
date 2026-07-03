package com.testingai.kafka.workqueue

import com.testingai.kafka.util.FailureSimulator
import org.mockito.MockedStatic
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.mockStatic

class WorkQueueConsumerTest extends Specification {

	def consumer = new WorkQueueConsumer()

	def "worker1 does not throw on success"() {
		given:
		MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator)
		mock.when { FailureSimulator.maybeThrow(anyString()) }.thenAnswer({ null })

		when:
		consumer.worker1("task")

		then:
		noExceptionThrown()

		cleanup:
		mock.close()
	}

	def "worker1 propagates exception on simulated failure"() {
		given:
		MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator)
		mock.when { FailureSimulator.maybeThrow(anyString()) }.thenThrow(new RuntimeException("Simulated"))

		when:
		consumer.worker1("task")

		then:
		thrown(RuntimeException)

		cleanup:
		mock.close()
	}

	def "worker2 does not throw on success"() {
		given:
		MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator)
		mock.when { FailureSimulator.maybeThrow(anyString()) }.thenAnswer({ null })

		when:
		consumer.worker2("task")

		then:
		noExceptionThrown()

		cleanup:
		mock.close()
	}
}
