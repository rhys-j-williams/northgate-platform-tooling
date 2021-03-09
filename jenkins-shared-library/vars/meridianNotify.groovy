#!/usr/bin/env groovy
/*
 * Build notifications. Posts to the team channel named in the pipeline config and, on failure of
 * a release branch, to #cswt-release-desk. The webhook URL is a Jenkins credential, not a config
 * value, after GIS-2107 found one in a Jenkinsfile.
 *
 * TODO(TOOL-1188): the chat platform is changing again in Q1 2027. Keep the payload shape here so
 * there is one place to change.
 */

import com.meridian.pipeline.Notifier

def stageStarted(String stageName) {
    echo "[meridian] stage ${stageName} started at ${new Date().format('HH:mm:ss')}"
}

def success(Map config) {
    send(config, Notifier.payload(config.appName, env.BRANCH_NAME, env.BUILD_URL, 'SUCCESS', currentBuild.durationString))
}

def unstable(Map config) {
    send(config, Notifier.payload(config.appName, env.BRANCH_NAME, env.BUILD_URL, 'UNSTABLE', currentBuild.durationString))
}

def failure(Map config) {
    Map payload = Notifier.payload(config.appName, env.BRANCH_NAME, env.BUILD_URL, 'FAILURE', currentBuild.durationString)
    send(config, payload)
    if (Notifier.isReleaseBranch(env.BRANCH_NAME)) {
        send(config + [notifyChannel: '#cswt-release-desk'], payload)
    }
}

private void send(Map config, Map payload) {
    if (!config.notifyChannel) {
        echo '[meridian] no notifyChannel configured, skipping notification'
        return
    }
    withCredentials([string(credentialsId: config.notifyWebhookCredentialsId, variable: 'WEBHOOK')]) {
        writeJSON file: '.notify-payload.json', json: payload + [channel: config.notifyChannel]
        sh 'curl -sS -f -X POST -H "Content-Type: application/json" --data @.notify-payload.json "$WEBHOOK" || echo "[meridian] notification failed, not failing the build"'
        sh 'rm -f .notify-payload.json'
    }
}
