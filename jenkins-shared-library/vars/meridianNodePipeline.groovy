#!/usr/bin/env groovy
/*
 * meridianNodePipeline - the standard pipeline for Angular applications, Angular libraries and
 * Node services in the CSWT organisation.
 *
 * Usage from a Jenkinsfile:
 *
 *   @Library('meridian-pipeline@v3') _
 *   meridianNodePipeline(
 *     agentLabel:        'nodejs16-rhel8',
 *     nodeVersion:       '16.20.2',
 *     appName:           'retail-web',
 *     helmChart:         'platform-tooling/helm/retail-web',
 *     coverageThreshold: 30,
 *     lintCommand:       'npm run lint',
 *     testCommand:       'npm test -- --watch=false --browsers=ChromeHeadlessCI --code-coverage',
 *     buildCommand:      'npm run build -- --configuration production'
 *   )
 *
 * Every parameter has a default in MeridianDefaults. Applications override the ones they need and
 * nothing else; if you find yourself overriding more than five, raise a TOOL ticket, the defaults
 * are probably wrong for your shape of repository.
 *
 * History: TOOL-118 first version (declarative, one big file). TOOL-402 split stages into
 * src/ classes so they could be unit tested. TOOL-655 Checkmarx and Xray gates made blocking after
 * the GIS-2291 audit finding. TOOL-880 Node 18 agents.
 */

import com.meridian.pipeline.MeridianDefaults
import com.meridian.pipeline.QualityGate
import com.meridian.pipeline.ScannerReport
import com.meridian.pipeline.AgentLabels

def call(Map userConfig = [:]) {
    Map config = MeridianDefaults.node() + userConfig

    AgentLabels.validate(config.agentLabel)

    pipeline {
        agent { label config.agentLabel }

        options {
            timestamps()
            ansiColor('xterm')
            timeout(time: config.timeoutMinutes, unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
            disableConcurrentBuilds()
        }

        parameters {
            string(name: 'AGENT_LABEL', defaultValue: config.agentLabel,
                   description: 'Build agent label. See platform-tooling/jenkins-shared-library/README.md for the list.')
            string(name: 'NODE_VERSION', defaultValue: config.nodeVersion,
                   description: 'Node version installed by nvm on the agent. Must match .nvmrc.')
            string(name: 'COVERAGE_THRESHOLD', defaultValue: config.coverageThreshold.toString(),
                   description: 'Minimum line coverage percent. The gate compares against coverage-summary.json.')
            booleanParam(name: 'SKIP_DEPLOY', defaultValue: false,
                         description: 'Run everything up to Helm package, then stop.')
            choice(name: 'TARGET_ENV', choices: ['dev', 'uat'],
                   description: 'Namespace suffix. prod deploys only run from Jenkinsfile.release.')
        }

        environment {
            APP_NAME          = "${config.appName}"
            NPM_CONFIG_REGISTRY = "${config.registryUrl}"
            NPM_CONFIG_USERCONFIG = "${WORKSPACE}/.npmrc"
            CI                = 'true'
            NG_CLI_ANALYTICS  = 'false'
            CHROME_BIN        = '/usr/bin/chromium-browser'
            SONAR_HOST_URL    = "${config.sonarHostUrl}"
            OC_PROJECT        = "${config.openshiftProjectPrefix}-${params.TARGET_ENV}"
            BUILD_TAG_SHORT   = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(8) ?: 'local'}"
        }

        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                    script {
                        env.GIT_SHORT = sh(script: 'git rev-parse --short=8 HEAD', returnStdout: true).trim()
                        env.IMAGE_TAG = "${env.GIT_SHORT}-${env.BUILD_NUMBER}"
                        currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.BRANCH_NAME} ${env.GIT_SHORT}"
                    }
                    meridianNotify.stageStarted('Checkout')
                }
            }

            stage('Registry login') {
                steps {
                    // Artifactory credentials come from the Jenkins credential store, which is fed
                    // by Vault. See platform-tooling/vault/README.md. Never put a token in .npmrc.
                    withCredentials([usernamePassword(credentialsId: config.registryCredentialsId,
                                                      usernameVariable: 'NPM_USER',
                                                      passwordVariable: 'NPM_TOKEN')]) {
                        sh label: 'write scoped npmrc', script: libraryResource('com/meridian/pipeline/scripts/npm-login.sh')
                    }
                }
            }

            stage('Install') {
                steps {
                    sh """
                        set -euo pipefail
                        . /opt/nvm/nvm.sh
                        nvm use ${params.NODE_VERSION}
                        node --version
                        npm --version
                        ${config.installCommand}
                    """
                }
            }

            stage('Lint') {
                when { expression { config.lintCommand } }
                steps {
                    sh """
                        set -euo pipefail
                        . /opt/nvm/nvm.sh && nvm use ${params.NODE_VERSION}
                        ${config.lintCommand} | tee lint.log
                    """
                    // Forbidden institution names, GIS-1180. Same script as the pre-commit hook.
                    sh 'bash platform-tooling/scripts/check-forbidden-strings.sh worktree || bash scripts/check-forbidden-strings.sh worktree'
                }
            }

            stage('Unit tests') {
                steps {
                    sh """
                        set -euo pipefail
                        . /opt/nvm/nvm.sh && nvm use ${params.NODE_VERSION}
                        ${config.testCommand}
                    """
                }
                post {
                    always {
                        junit allowEmptyResults: true, testResults: config.junitPattern
                        script {
                            QualityGate.coverage(this, config.coverageSummary, params.COVERAGE_THRESHOLD as Integer)
                        }
                    }
                }
            }

            stage('Build') {
                steps {
                    sh """
                        set -euo pipefail
                        . /opt/nvm/nvm.sh && nvm use ${params.NODE_VERSION}
                        ${config.buildCommand}
                    """
                    // Webpack stats are parsed by the bundle budget step. TOOL-1207: this breaks
                    // if anyone moves to the esbuild builder, the stats.json shape is different.
                    sh 'test -f dist/**/stats.json && node platform-tooling/jenkins-shared-library/resources/com/meridian/pipeline/scripts/bundle-budget.js dist || true'
                    archiveArtifacts artifacts: 'dist/**', fingerprint: false, allowEmptyArchive: true
                }
            }

            stage('Sonar scan') {
                steps {
                    withSonarQubeEnv(config.sonarServerName) {
                        sh "${config.scannerBin}/sonar-scanner -Dsonar.projectVersion=${env.IMAGE_TAG} -Dsonar.branch.name=${env.BRANCH_NAME}"
                    }
                }
                post {
                    always {
                        script {
                            QualityGate.sonar(this, '.sonar-reports/quality-gate.json')
                        }
                    }
                }
            }

            stage('Checkmarx scan') {
                steps {
                    sh "${config.scannerBin}/cx scan --config checkmarx.yml --source . --output .cx-reports"
                }
                post {
                    always {
                        publishHTML(target: [reportDir: '.cx-reports', reportFiles: 'report.html',
                                             reportName: 'Checkmarx', keepAll: true,
                                             alwaysLinkToLastBuild: true, allowMissing: true])
                        script {
                            ScannerReport report = ScannerReport.fromJson(readFile('.cx-reports/report.json'))
                            QualityGate.checkmarx(this, report, config.checkmarxThresholds)
                        }
                    }
                }
            }

            stage('Dependency audit') {
                steps {
                    sh "${config.scannerBin}/xray scan --type npm --output .xray-reports"
                }
                post {
                    always {
                        archiveArtifacts artifacts: '.xray-reports/**', allowEmptyArchive: true
                        script {
                            QualityGate.xray(this, readJSON(file: '.xray-reports/report.json'), config.xrayThresholds)
                        }
                    }
                }
            }

            stage('Container build') {
                when { anyOf { branch 'develop'; branch 'release/*'; branch 'main'; branch 'hotfix/*' } }
                steps {
                    withCredentials([usernamePassword(credentialsId: config.imageRegistryCredentialsId,
                                                      usernameVariable: 'REG_USER',
                                                      passwordVariable: 'REG_PASS')]) {
                        sh """
                            set -euo pipefail
                            podman login -u "\$REG_USER" -p "\$REG_PASS" ${config.imageRegistry}
                            podman build --pull \
                              --build-arg NODE_VERSION=${params.NODE_VERSION} \
                              --build-arg BUILD_TAG=${env.IMAGE_TAG} \
                              -f ${config.dockerfile} \
                              -t ${config.imageRegistry}/${config.imageRepository}/${config.appName}:${env.IMAGE_TAG} .
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
                    withCredentials([string(credentialsId: "${config.openshiftCredentialsPrefix}-${params.TARGET_ENV}",
                                            variable: 'OC_TOKEN')]) {
                        sh """
                            set -euo pipefail
                            oc login ${config.openshiftApi} --token="\$OC_TOKEN" --insecure-skip-tls-verify=false
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
            success {
                meridianNotify.success(config)
            }
            unstable {
                meridianNotify.unstable(config)
            }
            failure {
                meridianNotify.failure(config)
            }
            always {
                cleanWs(deleteDirs: true, notFailBuild: true,
                        patterns: [[pattern: 'node_modules/**', type: 'INCLUDE'],
                                   [pattern: '.npmrc', type: 'INCLUDE']])
            }
        }
    }
}
