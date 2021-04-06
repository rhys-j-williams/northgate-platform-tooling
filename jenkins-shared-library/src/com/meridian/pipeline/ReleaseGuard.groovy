package com.meridian.pipeline

import java.time.LocalDate
import java.time.ZoneId

/**
 * Preconditions for a production or uat release build. Pure, tested in test/.
 *
 * Freeze windows mirror governance/RELEASE_CALENDAR.md: the last two weeks of March, June,
 * September and December. If the calendar changes, change both. TOOL-1155 wanted this read from
 * the calendar file; it is not, because the calendar is markdown and nobody wanted to parse it.
 */
class ReleaseGuard implements Serializable {

    private static final long serialVersionUID = 1L

    static final String CAB_PATTERN = /^CHG\d{7}E?$/
    static final ZoneId NEW_YORK = ZoneId.of('America/New_York')

    static List<String> validate(String cabReference, String targetEnv, String branch, Date now) {
        List<String> problems = []
        String cab = (cabReference ?: '').trim()

        if (!cab) {
            problems << 'CAB_REFERENCE is required for a release build'
        } else if (!(cab ==~ CAB_PATTERN)) {
            problems << "CAB_REFERENCE '${cab}' is not a CHG number".toString()
        }

        if (!isReleaseSource(branch)) {
            problems << "release builds run from release/*, hotfix/* or main, not '${branch}'".toString()
        }

        LocalDate today = now.toInstant().atZone(NEW_YORK).toLocalDate()
        if (targetEnv == 'prod' && inFreeze(today) && cab && !cab.endsWith('E')) {
            problems << "${today} is inside a quarter end freeze; only emergency changes (CHG…E) may deploy to prod".toString()
        }

        return problems
    }

    static boolean isReleaseSource(String branch) {
        if (!branch) {
            return false
        }
        return branch == 'main' || branch.startsWith('release/') || branch.startsWith('hotfix/')
    }

    /** Last fourteen days of March, June, September and December. */
    static boolean inFreeze(LocalDate day) {
        if (!(day.monthValue in [3, 6, 9, 12])) {
            return false
        }
        int lengthOfMonth = day.lengthOfMonth()
        return day.dayOfMonth > lengthOfMonth - 14
    }
}
