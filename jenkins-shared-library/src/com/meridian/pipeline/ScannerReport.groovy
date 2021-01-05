package com.meridian.pipeline

import groovy.json.JsonSlurperClassic

/**
 * Typed view over a Checkmarx JSON report. The real Checkmarx CLI and the mock cx in
 * platform-tooling/mock-scanners emit the same shape, which is the whole point of the mock.
 *
 * Shape:
 *   { "scanId": "...", "project": "...", "summary": {"high": 1, "medium": 3, "low": 7, "info": 2},
 *     "findings": [ {"ruleId": "CX-ANG-001", "severity": "High", "file": "...", "line": 12, ...} ] }
 */
class ScannerReport implements Serializable {

    private static final long serialVersionUID = 1L

    static final List<String> SEVERITIES = ['critical', 'high', 'medium', 'low', 'info']

    String scanId
    String project
    Map<String, Integer> summary = [:]
    List<Map> findings = []

    static ScannerReport fromJson(String text) {
        Map parsed = (Map) new JsonSlurperClassic().parseText(text)
        return fromMap(parsed)
    }

    static ScannerReport fromMap(Map parsed) {
        ScannerReport report = new ScannerReport()
        report.scanId = parsed.scanId as String
        report.project = parsed.project as String
        report.findings = (parsed.findings ?: []) as List<Map>
        Map given = (parsed.summary ?: [:]) as Map
        SEVERITIES.each { sev ->
            report.summary[sev] = given.containsKey(sev)
                ? (given[sev] as Integer)
                : report.findings.count { (it.severity as String).toLowerCase() == sev } as Integer
        }
        return report
    }

    int count(String severity) {
        return summary[severity.toLowerCase()] ?: 0
    }

    List<Map> bySeverity(String severity) {
        return findings.findAll { (it.severity as String).equalsIgnoreCase(severity) }
    }

    /**
     * Returns the list of threshold breaches, empty when the gate passes.
     * thresholds is e.g. [high: 0, medium: 5].
     */
    List<String> breaches(Map<String, Integer> thresholds) {
        List<String> out = []
        thresholds.each { String sev, Integer max ->
            int actual = count(sev)
            if (actual > max) {
                out << "${sev}: ${actual} found, threshold ${max}".toString()
            }
        }
        return out
    }

    boolean passes(Map<String, Integer> thresholds) {
        return breaches(thresholds).isEmpty()
    }
}
