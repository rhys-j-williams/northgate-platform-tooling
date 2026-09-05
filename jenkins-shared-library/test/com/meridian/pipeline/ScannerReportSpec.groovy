package com.meridian.pipeline

import groovy.test.GroovyTestCase

class ScannerReportSpec extends GroovyTestCase {

    static final String SAMPLE = '''{
      "scanId": "cx-20260901-000123",
      "project": "meridian-retail-web",
      "findings": [
        {"ruleId": "CX-ANG-001", "severity": "High", "file": "src/app/disclosure.component.ts", "line": 41, "cwe": "CWE-79"},
        {"ruleId": "CX-NPM-002", "severity": "Medium", "file": ".npmrc", "line": 6, "cwe": "CWE-295"},
        {"ruleId": "CX-GEN-010", "severity": "low", "file": "src/main.ts", "line": 3, "cwe": "CWE-489"}
      ]
    }'''

    void testSummaryIsDerivedFromFindingsWhenAbsent() {
        ScannerReport r = ScannerReport.fromJson(SAMPLE)
        assert r.scanId == 'cx-20260901-000123'
        assert r.count('high') == 1
        assert r.count('medium') == 1
        assert r.count('low') == 1
        assert r.count('critical') == 0
    }

    void testExplicitSummaryWins() {
        ScannerReport r = ScannerReport.fromMap([summary: [high: 3], findings: []])
        assert r.count('high') == 3
    }

    void testBreachesAreDescriptive() {
        ScannerReport r = ScannerReport.fromJson(SAMPLE)
        List<String> b = r.breaches([high: 0, medium: 5])
        assert b == ['high: 1 found, threshold 0']
        assert !r.passes([high: 0])
        assert r.passes([high: 1, medium: 1])
    }

    void testBySeverityIsCaseInsensitive() {
        ScannerReport r = ScannerReport.fromJson(SAMPLE)
        assert r.bySeverity('LOW')*.ruleId == ['CX-GEN-010']
    }
}
