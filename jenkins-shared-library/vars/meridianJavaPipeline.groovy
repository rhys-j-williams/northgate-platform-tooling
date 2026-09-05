#!/usr/bin/env groovy
/*
 * meridianJavaPipeline - standard pipeline for Spring Boot services in platform-services.
 *
 *   @Library('meridian-pipeline@v3') _
 *   meridianJavaPipeline(
 *     agentLabel:        'maven-jdk11-rhel8',
 *     appName:           'beacon-notifications',
 *     modulePath:        'beacon-notifications',
 *     helmChart:         'platform-tooling/helm/beacon-notifications',
 *     coverageThreshold: 25
 *   )
 *
 * Differences from the Node pipeline, for people who read one and assume the other:
 *   - install is `mvn dependency:go-offline`, against the Artifactory maven-virtual repo via the
 *     settings.xml rendered from platform-tooling/registry
 *   - lint is Checkstyle, and it is non blocking (PLAT-1330, still open, the ruleset has 900 open
 *     violations in txn-posting-service and nobody wants to be the one to fix them)
 *   - coverage comes from JaCoCo's XML rather than Istanbul's JSON
 *   - xray reads the Maven dependency tree instead of running npm audit
 *   - the container build uses the JDK base image from the Red Hat registry, not nginx
 *
 * Owner: @meridian/platform-engineering. Java 17 agents: TOOL-1034.
 */

import com.meridian.pipeline.MeridianDefaults
import com.meridian.pipeline.QualityGate
import com.meridian.pipeline.ScannerReport
import com.meridian.pipeline.AgentLabels

def call(Map userConfig = [:]) {
    Map config = MeridianDefaults.java() + userConfig

    AgentLabels.validate(config.agentLabel)

    pipeline {
        agent { label config.agentLabel }

        options {
            timestamps()
            ansiColor('xterm')
            timeout(time: config.timeoutMinutes, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '30'))
            disableConcurrentBuilds()
        }

        parameters {
            string(name: 'AGENT_LABEL', defaultValue: config.agentLabel,
                   description: 'maven-jdk11-rhel8 or maven-jdk17-rhel9. Nothing else has Maven on it.')
            string(name: 'COVERAGE_THRESHOLD', defaultValue: config.coverageThreshold.toString(),
                   description: 'Minimum JaCoCo line coverage percent for this module.')
            booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: 'Stop after Helm package.')
            choice(name: 'TARGET_ENV', choices: ['dev', 'uat'], description: 'Namespace suffix.')
        }

        environment {
            APP_NAME        = "${config.appName}"
            MAVEN_OPTS      = '-Xmx2g -Djava.awt.headless=true -Dmaven.repo.local=/var/lib/jenkins/.m2/repository'
            MAVEN_SETTINGS  = "${WORKSPACE}/platform-tooling/registry/settings.xml"
            SONAR_HOST_URL  = "${config.sonarHostUrl}"
            OC_PROJECT      = "${config.openshiftProjectPrefix}-${params.TARGET_ENV}"
            SPRING_PROFILES_ACTIVE = 'ci,local-artemis'
        }

        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                    script {
                        env.GIT_SHORT = sh(script: 'git rev-parse --short=8 HEAD', returnStdout: true).trim()
                        env.IMAGE_TAG = "${env.GIT_SHORT}-${env.BUILD_NUMBER}"
                        currentBuild.displayName = "#${env.BUILD_NUMBER} ${config.appName} ${env.GIT_SHORT}"
                    }
                }
            }

            stage('Registry login') {
                steps {
                    withCredentials([usernamePassword(credentialsId: config.registryCredentialsId,
                                                      usernameVariable: 'ARTIFACTORY_USER',
                                                      passwordVariable: 'ARTIFACTORY_TOKEN')]) {
                        // settings.xml references ${env.ARTIFACTORY_USER}; nothing is written to disk.
                        sh "mvn -s ${MAVEN_SETTINGS} -q help:effective-settings -DshowPasswords=false > /dev/null"
                    }
                }
            }

            stage('Install') {
                steps {
                    dir(config.modulePath) {
                        sh "mvn -s ${MAVEN_SETTINGS} -B -q dependency:go-offline"
                    }
                }
            }

            stage('Lint') {
                steps {
                    dir(config.modulePath) {
                        // PLAT-1330: checkstyle is report only. Do not make this fail the build
                        // without talking to the payments platform team first.
                        sh "mvn -s ${MAVEN_SETTINGS} -B checkstyle:checkstyle -Dcheckstyle.failOnViolation=false"
                    }
                    recordIssues tools: [checkStyle(pattern: "${config.modulePath}/target/checkstyle-result.xml")],
                                 qualityGates: [[threshold: 1, type: 'NEW_HIGH', unstable: true]]
                }
            }

            stage('Unit tests') {
                steps {
                    dir(config.modulePath) {
                        sh "mvn -s ${MAVEN_SETTINGS} -B verify -Djacoco.skip=false"
                    }
                }
                post {
                    always {
                        junit allowEmptyResults: true, testResults: "${config.modulePath}/target/surefire-reports/*.xml"
                        jacoco execPattern: "${config.modulePath}/target/jacoco.exec",
                               classPattern: "${config.modulePath}/target/classes",
                               sourcePattern: "${config.modulePath}/src/main/java"
                        script {
                            QualityGate.jacoco(this, "${config.modulePath}/target/site/jacoco/jacoco.xml",
                                               params.COVERAGE_THRESHOLD as Integer)
                        }
                    }
                }
            }

            stage('Build') {
                steps {
                    dir(config.modulePath) {
                        sh "mvn -s ${MAVEN_SETTINGS} -B -DskipTests package"
                    }
                    archiveArtifacts artifacts: "${config.modulePath}/target/*.jar", fingerprint: true
                }
            }

            stage('Sonar scan') {
                steps {
                    dir(config.modulePath) {
                        withSonarQubeEnv(config.sonarServerName) {
                            sh "${config.scannerBin}/sonar-scanner -Dsonar.projectVersion=${env.IMAGE_TAG} -Dsonar.java.binaries=target/classes -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml"
                        }
                    }
                }
                post {
                    always {
                        script {
                            QualityGate.sonar(this, "${config.modulePath}/.sonar-reports/quality-gate.json")
                        }
                    }
                }
            }

            stage('Checkmarx scan') {
                steps {
                    dir(config.modulePath) {
                        sh "${config.scannerBin}/cx scan --config checkmarx.yml --source . --output .cx-reports"
                    }
                }
                post {
                    always {
                        publishHTML(target: [reportDir: "${config.modulePath}/.cx-reports", reportFiles: 'report.html',
                                             reportName: 'Checkmarx', keepAll: true, allowMissing: true])
                        script {
                            ScannerReport report = ScannerReport.fromJson(readFile("${config.modulePath}/.cx-reports/report.json"))
                            QualityGate.checkmarx(this, report, config.checkmarxThresholds)
                        }
                    }
                }
            }

            stage('Dependency audit') {
                steps {
                    dir(config.modulePath) {
                        sh "mvn -s ${MAVEN_SETTINGS} -B -q dependency:tree -DoutputFile=target/dependency-tree.txt -DoutputType=text"
                        sh "${config.scannerBin}/xray scan --type maven --tree target/dependency-tree.txt --output .xray-reports"
                    }
                }
                post {
                    always {
                        script {
                            QualityGate.xray(this, readJSON(file: "${config.modulePath}/.xray-reports/report.json"), config.xrayThresholds)
                        }
                    }
                }
            }

            stage('Container build') {
                when { anyOf { branch 'develop'; branch 'release/*'; branch 'main'; branch 'hotfix/*' } }
                steps {
                    withCredentials([usernamePassword(credentialsId: config.imageRegistryCredentialsId,
                                                      usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                        sh """
                            set -euo pipefail
                            podman login -u "\$REG_USER" -p "\$REG_PASS" ${config.imageRegistry}
                            podman build --pull \
                              --build-arg JAR_FILE=target/${config.appName}.jar \
                              -f ${config.modulePath}/Dockerfile \
                              -t ${config.imageRegistry}/${config.imageRepository}/${config.appName}:${env.IMAGE_TAG} ${config.modulePath}
                            podman push ${config.imageRegistry}/${config.imageRepository}/${config.appName}:${env.IMAGE_TAG}
                        """
                    }
                }
            }

            stage('Helm package') {
                when { anyOf { branch 'develop'; branch 'release/*'; branch 'main'; branch 'hotfix/*' } }
                steps {
                    sh """
                        set -euo pipefail
                        helm lint ${config.helmChart} -f ${config.helmChart}/values-${params.TARGET_ENV}.yaml
                        helm package ${config.helmChart} --version 0.0.0-${env.IMAGE_TAG} --app-version ${env.IMAGE_TAG} -d .helm-out
                    """
                    archiveArtifacts artifacts: '.helm-out/*.tgz'
                }
            }

            stage('Deploy') {
                when {
                    allOf {
                        anyOf { branch 'develop'; branch 'release/*'; branch 'hotfix/*' }
                        expression { !params.SKIP_DEPLOY }
                    }
                }
                steps {
                    withCredentials([string(credentialsId: "${config.openshiftCredentialsPrefix}-${params.TARGET_ENV}", variable: 'OC_TOKEN')]) {
                        sh """
                            set -euo pipefail
                            oc login ${config.openshiftApi} --token="\$OC_TOKEN"
                            oc project ${env.OC_PROJECT}
                            helm upgrade --install ${config.appName} .helm-out/*.tgz \
                              --namespace ${env.OC_PROJECT} \
                              -f ${config.helmChart}/values-${params.TARGET_ENV}.yaml \
                              --set image.tag=${env.IMAGE_TAG} \
                              --wait --timeout 10m
                            oc rollout status deployment/${config.appName} -n ${env.OC_PROJECT} --timeout=600s
                        """
                    }
                }
            }
        }

        post {
            success  { meridianNotify.success(config) }
            unstable { meridianNotify.unstable(config) }
            failure  { meridianNotify.failure(config) }
            always   { cleanWs(deleteDirs: true, notFailBuild: true) }
        }
    }
}
