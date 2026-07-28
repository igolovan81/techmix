package com.testingai.reactor

import spock.lang.Specification

class ReactorDemoApplicationTest extends Specification {

    def "main class exists"() {
        expect:
        new ReactorDemoApplication()
    }
}
