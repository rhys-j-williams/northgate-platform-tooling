package com.meridian.pipeline

import groovy.test.GroovyTestCase

class QualityGateSpec extends GroovyTestCase {

    void testCoveragePassesAtThreshold() {
        assert QualityGate.coverageDecision(30.0d, 30, 'feature/MOL-1') == 'PASS'
        assert QualityGate.coverageDecision(48.2d, 45, 'develop') == 'PASS'
    }

    void testCoverageBelowThresholdIsUnstableOnFeatureBranches() {
        assert QualityGate.coverageDecision(21.9d, 22, 'feature/MBZ-3310-payroll') == 'UNSTABLE'
        assert QualityGate.coverageDecision(0.0d, 30, 'develop') == 'UNSTABLE'
    }

    void testCoverageBelowThresholdFailsReleaseBranches() {
        assert QualityGate.coverageDecision(29.9d, 30, 'release/2026.09') == 'FAIL'
        assert QualityGate.coverageDecision(10.0d, 30, 'main') == 'FAIL'
        assert QualityGate.coverageDecision(10.0d, 30, 'hotfix/MOL-4412-session-timeout') == 'FAIL'
    }

    void testScannerHighFindingFails() {
        ScannerReport r = ScannerReport.fromMap([summary: [high: 1, medium: 0]])
        assert QualityGate.scannerDecision(r, [high: 0, medium: 5]) == 'FAIL'
    }

    void testScannerMediumOverThresholdIsUnstable() {
        ScannerReport r = ScannerReport.fromMap([summary: [high: 0, medium: 6, low: 40]])
        assert QualityGate.scannerDecision(r, [high: 0, medium: 5]) == 'UNSTABLE'
    }

    void testScannerWithinThresholdsPasses() {
        ScannerReport r = ScannerReport.fromMap([summary: [high: 0, medium: 5, low: 40]])
        assert QualityGate.scannerDecision(r, [high: 0, medium: 5]) == 'PASS'
    }

    void testSonarDecision() {
        assert QualityGate.sonarDecision('OK') == 'PASS'
        assert QualityGate.sonarDecision('WARN') == 'UNSTABLE'
        assert QualityGate.sonarDecision('ERROR') == 'FAIL'
        assert QualityGate.sonarDecision(null) == 'UNSTABLE'
    }

    void testIstanbulSummaryParsing() {
        String json = '{"total":{"lines":{"total":1200,"covered":576,"skipped":0,"pct":48},"statements":{"pct":47.1}}}'
        assert QualityGate.linesPct(json) == 48.0d
        assert QualityGate.linesPct('{}') == 0.0d
    }

    void testJacocoParsingUsesReportLevelCounter() {
        String xml = '''<report name="beacon-notifications">
          <package name="com/meridian/beacon"><counter type="LINE" missed="10" covered="90"/></package>
          <counter type="INSTRUCTION" missed="500" covered="200"/>
          <counter type="LINE" missed="750" covered="250"/>
        </report>'''
        assert QualityGate.jacocoLinesPct(xml) == 25.0d
        assert QualityGate.jacocoLinesPct('<report/>') == 0.0d
    }

    void testReleaseBranchDetection() {
        assert QualityGate.isReleaseBranch('release/2026.09')
        assert QualityGate.isReleaseBranch('main')
        assert !QualityGate.isReleaseBranch('develop')
        assert !QualityGate.isReleaseBranch(null)
    }
}
