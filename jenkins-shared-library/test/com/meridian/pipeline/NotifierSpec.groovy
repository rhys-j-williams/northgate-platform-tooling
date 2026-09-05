package com.meridian.pipeline

import groovy.test.GroovyTestCase

class NotifierSpec extends GroovyTestCase {

    void testPayloadText() {
        Map p = Notifier.payload('retail-web', 'develop', 'https://jenkins.meridian.internal/job/retail-web/412/', 'FAILURE', '6 min 12 sec')
        assert p.text == 'retail-web develop failed in 6 min 12 sec'
        assert p.colour == '#c62828'
        assert p.attachments[0].title_link.endsWith('/412/')
    }

    void testUnknownResultFallsBackToGrey() {
        assert Notifier.payload('x', 'y', 'z', 'NOT_BUILT', null).colour == '#757575'
    }
}
