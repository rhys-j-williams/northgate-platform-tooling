package com.meridian.pipeline

import groovy.test.GroovyTestCase
import java.time.LocalDate

class ReleaseGuardSpec extends GroovyTestCase {

    // 15 July 2026, a Wednesday, mid quarter, 14:00 New York.
    static final Date MID_QUARTER = Date.from(java.time.ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, ReleaseGuard.NEW_YORK).toInstant())
    // 24 September 2026, inside the Q3 freeze.
    static final Date IN_FREEZE = Date.from(java.time.ZonedDateTime.of(2026, 9, 24, 10, 0, 0, 0, ReleaseGuard.NEW_YORK).toInstant())

    void testHappyPath() {
        assert ReleaseGuard.validate('CHG0031877', 'prod', 'release/2026.07', MID_QUARTER) == []
    }

    void testMissingCabReference() {
        List<String> p = ReleaseGuard.validate('', 'uat', 'release/2026.07', MID_QUARTER)
        assert p.size() == 1
        assert p[0].contains('required')
    }

    void testMalformedCabReference() {
        assert ReleaseGuard.validate('CAB-123', 'uat', 'release/2026.07', MID_QUARTER)[0].contains('not a CHG number')
        assert ReleaseGuard.validate('CHG12345678', 'uat', 'main', MID_QUARTER).size() == 1
    }

    void testFeatureBranchIsRefused() {
        List<String> p = ReleaseGuard.validate('CHG0031877', 'uat', 'feature/TOOL-1290-itsm', MID_QUARTER)
        assert p.any { it.contains('release/*') }
    }

    void testFreezeRequiresEmergencyChange() {
        assert ReleaseGuard.validate('CHG0031900', 'prod', 'release/2026.09', IN_FREEZE).any { it.contains('freeze') }
        assert ReleaseGuard.validate('CHG0031900E', 'prod', 'hotfix/MOL-4499-otp', IN_FREEZE) == []
        // uat is not frozen
        assert ReleaseGuard.validate('CHG0031900', 'uat', 'release/2026.09', IN_FREEZE) == []
    }

    void testFreezeWindowBoundaries() {
        assert !ReleaseGuard.inFreeze(LocalDate.of(2026, 3, 17))
        assert ReleaseGuard.inFreeze(LocalDate.of(2026, 3, 18))
        assert ReleaseGuard.inFreeze(LocalDate.of(2026, 12, 31))
        assert !ReleaseGuard.inFreeze(LocalDate.of(2026, 1, 31))
    }
}
