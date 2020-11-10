package com.meridian.pipeline

/**
 * Default pipeline configuration. Applications override individual keys from their Jenkinsfile.
 *
 * Keep this boring. Anything environment specific (cluster API URLs, registry hosts) is here so
 * that a Jenkinsfile never needs to know it, and so that the platform team can move a cluster
 * without touching forty repositories. TOOL-712 was the last time we did that and it took a
 * quarter because half the Jenkinsfiles had hard coded the old API host.
 */
class MeridianDefaults implements Serializable {

    private static final long serialVersionUID = 1L

    static final String ARTIFACTORY_HOST = 'artifactory.meridian.internal'
    static final String IMAGE_REGISTRY = 'registry.meridian.internal'
    static final String SONAR_HOST = 'https://sonar.meridian.internal'
    static final String OPENSHIFT_API = 'https://api.ocp-cswt-east.meridian.internal:6443'

    static Map common() {
        return [
            timeoutMinutes             : 60,
            registryCredentialsId      : 'artifactory-ci-reader',
            imageRegistry              : IMAGE_REGISTRY,
            imageRepository            : 'cswt',
            imageRegistryCredentialsId : 'registry-cswt-pusher',
            sonarHostUrl               : SONAR_HOST,
            sonarServerName            : 'sonar-meridian',
            openshiftApi               : OPENSHIFT_API,
            openshiftCredentialsPrefix : 'ocp-cswt-deployer',
            openshiftProjectPrefix     : 'cswt',
            // Scanner binaries. On real agents these are the vendor CLIs installed under /opt.
            // In the demo estate the same names resolve to platform-tooling/mock-scanners/bin.
            scannerBin                 : System.getenv('MERIDIAN_SCANNER_BIN') ?: '/opt/meridian/scanners/bin',
            checkmarxThresholds        : [high: 0, medium: 5],
            xrayThresholds             : [critical: 0, high: 0],
            notifyChannel              : null,
            notifyWebhookCredentialsId : 'chat-webhook-cswt-builds',
        ]
    }

    static Map node() {
        return common() + [
            agentLabel        : 'nodejs16-rhel8',
            nodeVersion       : '16.20.2',
            appName           : null,
            registryUrl       : "https://${ARTIFACTORY_HOST}/artifactory/api/npm/npm-virtual/".toString(),
            installCommand    : 'npm ci --no-audit --no-fund',
            lintCommand       : 'npm run lint',
            testCommand       : 'npm test -- --watch=false --browsers=ChromeHeadlessCI --code-coverage',
            buildCommand      : 'npm run build -- --configuration production',
            junitPattern      : 'karma-results/**/*.xml',
            coverageSummary   : 'coverage/**/coverage-summary.json',
            coverageThreshold : 30,
            dockerfile        : 'Dockerfile',
            helmChart         : null,
        ]
    }

    static Map java() {
        return common() + [
            agentLabel        : 'maven-jdk11-rhel8',
            appName           : null,
            modulePath        : '.',
            coverageThreshold : 20,
            helmChart         : null,
        ]
    }
}
