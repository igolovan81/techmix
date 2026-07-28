package com.testingai.reactor.upstream

import spock.lang.Specification

class ReactorUpstreamDemoApplicationTest extends Specification {

    def "main class exists"() {
        expect:
        new ReactorUpstreamDemoApplication()
    }
}
