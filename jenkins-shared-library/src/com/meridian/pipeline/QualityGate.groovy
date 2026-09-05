package com.meridian.pipeline

import groovy.json.JsonSlurperClassic

/**
 * Quality gates. Every gate takes the pipeline `script` object as its first argument so that the
 * decision logic (the pure static methods at the bottom) can be unit tested without Jenkins.
 *
 * Policy, from GIS-STD-014 section 8 and the CSWT engineering standard:
 *   - any high or critical scanner finding fails the build. Not unstable, fails.
 *   - medium findings above the threshold mark the build unstable and are reported to the team.
 *   - coverage below threshold fails the build on release branches and marks it unstable elsewhere.
 *     (TOOL-771: we tried fail-everywhere for one train and the pull request queue stopped moving.)
 *   - a Sonar quality gate ERROR fails the build. WARN is unstable.
 */
class QualityGate implements Serializable {

    private static final long serialVersionUID = 1L

    static final List<String> RELEASE_BRANCH_PREFIXES = ['release/', 'main', 'hotfix/']

    // ---- pipeline facing -----------------------------------------------------------------

    static void coverage(def script, String summaryGlob, int threshold) {
        def files = script.findFiles(glob: summaryGlob)
        if (!files) {
            script.echo "[gate] coverage: no summary matched ${summaryGlob}, treating as 0%"
            apply(script, coverageDecision(0.0d, threshold, script.env.BRANCH_NAME as String), 'coverage 0%')
            return
        }
        double pct = linesPct(script.readFile(files[0].path))
        script.echo "[gate] coverage: ${String.format('%.2f', pct)}% lines, threshold ${threshold}%"
        apply(script, coverageDecision(pct, threshold, script.env.BRANCH_NAME as String),
              "coverage ${String.format('%.2f', pct)}% below ${threshold}%")
    }

    static void jacoco(def script, String xmlPath, int threshold) {
        if (!script.fileExists(xmlPath)) {
            script.echo "[gate] jacoco: ${xmlPath} missing, treating as 0%"
            apply(script, coverageDecision(0.0d, threshold, script.env.BRANCH_NAME as String), 'coverage 0%')
            return
        }
        double pct = jacocoLinesPct(script.readFile(xmlPath))
        script.echo "[gate] jacoco: ${String.format('%.2f', pct)}% lines, threshold ${threshold}%"
        apply(script, coverageDecision(pct, threshold, script.env.BRANCH_NAME as String),
              "coverage ${String.format('%.2f', pct)}% below ${threshold}%")
    }

    static void checkmarx(def script, ScannerReport report, Map<String, Integer> thresholds) {
        List<String> breaches = report.breaches(thresholds)
        script.echo "[gate] checkmarx: high=${report.count('high')} medium=${report.count('medium')} low=${report.count('low')}"
        apply(script, scannerDecision(report, thresholds), "Checkmarx gate: ${breaches.join('; ')}")
    }

    static void xray(def script, Map report, Map<String, Integer> thresholds) {
        ScannerReport view = ScannerReport.fromMap(report)
        script.echo "[gate] xray: critical=${view.count('critical')} high=${view.count('high')} medium=${view.count('medium')}"
        apply(script, scannerDecision(view, thresholds), "Xray gate: ${view.breaches(thresholds).join('; ')}")
    }

    static void sonar(def script, String gatePath) {
        if (!script.fileExists(gatePath)) {
            script.echo "[gate] sonar: ${gatePath} missing, marking unstable"
            script.unstable('Sonar quality gate result missing')
            return
        }
        Map gate = (Map) new JsonSlurperClassic().parseText(script.readFile(gatePath))
        String status = (gate.status ?: gate.projectStatus?.status ?: 'NONE') as String
        script.echo "[gate] sonar: ${status}"
        apply(script, sonarDecision(status), "Sonar quality gate ${status}")
    }

    // ---- pure decisions, unit tested in test/ --------------------------------------------

    static String coverageDecision(double pct, int threshold, String branch) {
        if (pct >= threshold) {
            return 'PASS'
        }
        return isReleaseBranch(branch) ? 'FAIL' : 'UNSTABLE'
    }

    static String scannerDecision(ScannerReport report, Map<String, Integer> thresholds) {
        if (report.count('critical') > (thresholds.critical ?: 0) || report.count('high') > (thresholds.high ?: 0)) {
            return 'FAIL'
        }
        return report.passes(thresholds) ? 'PASS' : 'UNSTABLE'
    }

    static String sonarDecision(String status) {
        switch ((status ?: '').toUpperCase()) {
            case 'OK':    return 'PASS'
            case 'WARN':  return 'UNSTABLE'
            case 'ERROR': return 'FAIL'
            default:      return 'UNSTABLE'
        }
    }

    static boolean isReleaseBranch(String branch) {
        if (!branch) {
            return false
        }
        return RELEASE_BRANCH_PREFIXES.any { branch == it || branch.startsWith(it) }
    }

    /** Istanbul coverage-summary.json -> total.lines.pct */
    static double linesPct(String json) {
        Map parsed = (Map) new JsonSlurperClassic().parseText(json)
        def total = parsed.total
        if (total?.lines?.pct == null) {
            return 0.0d
        }
        return (total.lines.pct as Number).doubleValue()
    }

    /** JaCoCo XML -> LINE counter percent. Regex rather than XmlSlurper so it works in the sandbox. */
    static double jacocoLinesPct(String xml) {
        // The report level counter is the last one in the file.
        def matcher = (xml =~ /<counter type="LINE" missed="(\d+)" covered="(\d+)"\/>/)
        int missed = 0
        int covered = 0
        while (matcher.find()) {
            missed = matcher.group(1) as Integer
            covered = matcher.group(2) as Integer
        }
        int total = missed + covered
        return total == 0 ? 0.0d : (covered * 100.0d) / total
    }

    private static void apply(def script, String decision, String message) {
        switch (decision) {
            case 'PASS':
                break
            case 'UNSTABLE':
                script.unstable(message)
                break
            case 'FAIL':
                script.error(message)
                break
        }
    }
}
