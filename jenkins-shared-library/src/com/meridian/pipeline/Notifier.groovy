package com.meridian.pipeline

/**
 * Notification payload builder. Pure so it can be tested; the sending is in vars/meridianNotify.
 */
class Notifier implements Serializable {

    private static final long serialVersionUID = 1L

    static final Map<String, String> COLOURS = [
        SUCCESS : '#2e7d32',
        UNSTABLE: '#f9a825',
        FAILURE : '#c62828',
        ABORTED : '#757575',
    ]

    static Map payload(String app, String branch, String buildUrl, String result, String duration) {
        String verb = result == 'SUCCESS' ? 'passed' : (result == 'UNSTABLE' ? 'is unstable' : 'failed')
        return [
            text       : "${app} ${branch ?: '(no branch)'} ${verb} in ${duration ?: 'unknown time'}".toString(),
            colour     : COLOURS[result] ?: COLOURS.ABORTED,
            result     : result,
            buildUrl   : buildUrl,
            attachments: [[title: "${app} / ${branch}".toString(), title_link: buildUrl]],
        ]
    }

    static boolean isReleaseBranch(String branch) {
        return QualityGate.isReleaseBranch(branch)
    }
}
