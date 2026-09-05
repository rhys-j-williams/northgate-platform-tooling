package com.meridian.pipeline

import groovy.test.GroovyTestCase

class AgentLabelsSpec extends GroovyTestCase {

    void testDocumentedLabelsExist() {
        ['nodejs14-rhel7', 'nodejs16-rhel8', 'nodejs18-rhel9', 'maven-jdk11-rhel8', 'maven-jdk17-rhel9'].each {
            assert AgentLabels.exists(it), "missing ${it}"
        }
    }

    void testNode14IsKnownButUnsupported() {
        assert AgentLabels.exists('nodejs14-rhel7')
        assert !AgentLabels.isSupported('nodejs14-rhel7')
        assert AgentLabels.unsupported() == ['nodejs14-rhel7']
    }

    void testUnknownLabelIsRejectedWithHelpfulMessage() {
        String message = shouldFail(IllegalArgumentException) {
            AgentLabels.validate('nodejs20-rhel9')
        }
        assert message.contains('nodejs20-rhel9')
        assert message.contains('README')
    }
}
