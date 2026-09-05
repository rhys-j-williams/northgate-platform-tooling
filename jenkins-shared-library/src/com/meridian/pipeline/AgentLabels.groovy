package com.meridian.pipeline

/**
 * The build agent labels that exist on the CSWT Jenkins controller. If a Jenkinsfile asks for a
 * label that is not here the build fails at load time with a readable message rather than sitting
 * in the queue for ever waiting for an agent that will never come, which is what happened for two
 * days in TOOL-598.
 *
 * Keep in step with README.md and with the agent image build in platform-tooling/ansible.
 */
class AgentLabels implements Serializable {

    private static final long serialVersionUID = 1L

    /** label -> [runtime, os, supported] */
    static final Map<String, Map> LABELS = [
        'nodejs14-rhel7'   : [runtime: 'Node 14.21.3', os: 'RHEL 7.9', supported: false,
                              note: 'Out of support since June 2024. Kept alive for business-web only. See README, TOOL-1041.'],
        'nodejs16-rhel8'   : [runtime: 'Node 16.20.2', os: 'RHEL 8.10', supported: true,
                              note: 'Default for Angular 14 and 15 repositories.'],
        'nodejs18-rhel9'   : [runtime: 'Node 18.19.0', os: 'RHEL 9.4', supported: true,
                              note: 'Angular 16+, NestJS BFFs, mock-external.'],
        'maven-jdk11-rhel8': [runtime: 'OpenJDK 11.0.28, Maven 3.9.9', os: 'RHEL 8.10', supported: true,
                              note: 'Spring Boot 2.7 services.'],
        'maven-jdk17-rhel9': [runtime: 'OpenJDK 17.0.13, Maven 3.9.9', os: 'RHEL 9.4', supported: true,
                              note: 'Spring Boot 3.1 services. Only entitlements-service uses it today.'],
    ]

    static boolean exists(String label) {
        return LABELS.containsKey(label)
    }

    static boolean isSupported(String label) {
        return exists(label) && LABELS[label].supported
    }

    static void validate(String label) {
        if (!exists(label)) {
            throw new IllegalArgumentException(
                "Unknown agent label '${label}'. Known labels: ${LABELS.keySet().join(', ')}. " +
                'See platform-tooling/jenkins-shared-library/README.md.')
        }
    }

    static List<String> unsupported() {
        return LABELS.findAll { !it.value.supported }.keySet().toList()
    }
}
