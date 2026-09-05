#!/usr/bin/env bash
# Smoke tests for the mock scanners. Runs each CLI against the fixtures and checks that the
# findings we planted are still found and the ones we did not plant are not reported.
# TOOL-1188. Called from the Jenkins job platform-tooling/mock-scanners-verify on every push.
set -euo pipefail
cd "$(dirname "$0")"
BIN="$PWD/bin"
fail=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fail=1; fi; }
jq_() { node -e "const r=require(require('path').resolve(process.argv[1])); console.log(JSON.stringify($2))" "$1"; }

export SOURCE_DATE_EPOCH=1700000000
rm -rf fixtures/*/.cx-reports fixtures/*/.sonar-reports fixtures/*/.xray-reports

echo "== cx"
( cd fixtures/angular-app && "$BIN/cx" scan --config checkmarx.yml --quiet ) && rc=0 || rc=$?
check "cx angular exits 1 on high findings" "[ $rc -eq 1 ]"
R=fixtures/angular-app/.cx-reports/report.json
check "cx finds sanitizer bypass"        "[ \"\$(jq_ $R 'r.findings.some(f=>f.ruleId===\"CX-ANG-001\")')\" = true ]"
check "cx finds hard coded credential"   "[ \"\$(jq_ $R 'r.findings.some(f=>f.ruleId===\"CX-SEC-001\")')\" = true ]"
check "cx finds eval"                    "[ \"\$(jq_ $R 'r.findings.some(f=>f.ruleId===\"CX-GEN-001\")')\" = true ]"
check "cx reports commented strict-ssl at Medium" "[ \"\$(jq_ $R 'r.findings.find(f=>f.ruleId===\"CX-NPM-001\").severity')\" = '\"Medium\"' ]"
check "cx finds weakened CSP"            "[ \"\$(jq_ $R 'r.findings.some(f=>f.ruleId===\"CX-ANG-004\")')\" = true ]"
check "cx honours suppression"           "[ \"\$(jq_ $R 'r.summary.suppressed')\" = 1 ]"
check "cx skips spec files"              "[ \"\$(jq_ $R 'r.findings.some(f=>f.file.endsWith(\".spec.ts\"))')\" = false ]"
check "cx placeholder not flagged"       "[ \"\$(jq_ $R 'r.findings.some(f=>f.file===\"sonar-project.properties\")')\" = false ]"
check "cx html written"                  "[ -s fixtures/angular-app/.cx-reports/report.html ]"
( cd fixtures/java-service && "$BIN/cx" scan --config checkmarx.yml --quiet ) && rc=0 || rc=$?
R=fixtures/java-service/.cx-reports/report.json
check "cx finds SQL concatenation"       "[ \"\$(jq_ $R 'r.findings.some(f=>f.ruleId===\"CX-SQL-001\")')\" = true ]"
check "cx finds CSRF disable"            "[ \"\$(jq_ $R 'r.findings.some(f=>f.ruleId===\"CX-WEB-002\")')\" = true ]"
cp "$R" /tmp/cx-first.json
( cd fixtures/java-service && "$BIN/cx" scan --config checkmarx.yml --quiet ) || true
check "cx deterministic"                 "cmp -s /tmp/cx-first.json $R"

echo "== sonar-scanner"
( cd fixtures/angular-app && "$BIN/sonar-scanner" -Dsonar.qualitygate.wait=true >/dev/null ) && rc=0 || rc=$?
check "sonar exits 1 when gate fails and wait=true" "[ $rc -eq 1 ]"
R=fixtures/angular-app/.sonar-reports/report.json
check "sonar imports istanbul coverage"  "[ \"\$(jq_ $R 'r.measures.coverage')\" = 34.02 ]"
check "sonar imports ng lint issues"     "[ \"\$(jq_ $R 'r.issues.length')\" = 4 ]"
check "sonar maps no-eval to CRITICAL"   "[ \"\$(jq_ $R 'r.issues.find(i=>i.externalRule===\"no-eval\").severity')\" = '\"CRITICAL\"' ]"
check "sonar quality-gate.json written"  "[ \"\$(jq_ fixtures/angular-app/.sonar-reports/quality-gate.json 'r.projectStatus.status')\" = '\"ERROR\"' ]"
( cd fixtures/java-service && "$BIN/sonar-scanner" >/dev/null ) && rc=0 || rc=$?
check "sonar exits 0 without wait"       "[ $rc -eq 0 ]"
R=fixtures/java-service/.sonar-reports/report.json
check "sonar imports jacoco coverage"    "[ \"\$(jq_ $R 'r.measures.coverage')\" = 19.8 ]"
check "sonar imports checkstyle"         "[ \"\$(jq_ $R 'r.issues.length')\" = 2 ]"

echo "== xray"
"$BIN/xray" audit --dir fixtures/angular-app --quiet && rc=0 || rc=$?
check "xray npm exits 1"                 "[ $rc -eq 1 ]"
R=fixtures/angular-app/.xray-reports/report.json
check "xray finds minimist"              "[ \"\$(jq_ $R 'r.findings.some(f=>f.package===\"minimist\")')\" = true ]"
check "xray flags Angular 14 lifecycle"  "[ \"\$(jq_ $R 'r.findings.some(f=>f.cve===\"MERIDIAN-EOL-ANGULAR\")')\" = true ]"
check "xray does not flag typescript"    "[ \"\$(jq_ $R 'r.findings.some(f=>f.package===\"typescript\"&&f.policy===\"security\")')\" = false ]"
"$BIN/xray" scan --type maven --tree target/dependency-tree.txt --dir fixtures/java-service --quiet && rc=0 || rc=$?
check "xray maven via library verb"      "[ $rc -eq 1 ]"
R=fixtures/java-service/.xray-reports/report.json
check "xray finds text4shell"            "[ \"\$(jq_ $R 'r.findings.some(f=>f.cve===\"CVE-2022-42889\")')\" = true ]"
check "xray does not flag log4j 2.17.2"  "[ \"\$(jq_ $R 'r.findings.some(f=>f.package.endsWith(\"log4j-core\"))')\" = false ]"
"$BIN/xray" audit --dir fixtures/angular-app --fail-on none --quiet && rc=0 || rc=$?
check "xray --fail-on none exits 0"      "[ $rc -eq 0 ]"

rm -rf fixtures/*/.cx-reports fixtures/*/.sonar-reports fixtures/*/.xray-reports /tmp/cx-first.json
if [ $fail -eq 0 ]; then echo "all scanner checks passed"; else echo "scanner checks FAILED"; exit 1; fi
