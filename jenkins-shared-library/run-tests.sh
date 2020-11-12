#!/usr/bin/env bash
# Compiles src/ and test/ and runs the specs under JUnit 4. Same thing the library's own Jenkins job
# does (job: platform-tooling/jenkins-shared-library-ci, agent maven-jdk17-rhel9).
#
#   ./run-tests.sh            compile + test
#   ./run-tests.sh compile    compile only, includes vars/ (syntax check for the pipeline steps)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

GROOVY_HOME="${GROOVY_HOME:-/opt/groovy}"
OUT="${OUT:-build/classes}"
rm -rf "${OUT}" && mkdir -p "${OUT}"

echo "== groovyc src/ + test/"
mapfile -t sources < <(find src test -name '*.groovy' | sort)
"${GROOVY_HOME}/bin/groovyc" -d "${OUT}" "${sources[@]}"

echo "== groovyc vars/ (syntax only, Jenkins steps resolve at runtime)"
"${GROOVY_HOME}/bin/groovyc" -cp "${OUT}" -d "${OUT}/vars" vars/*.groovy

if [[ "${1:-test}" == "compile" ]]; then
  echo "compile OK"; exit 0
fi

echo "== junit"
specs=$(cd "${OUT}" && find com -name '*Spec.class' | sed 's#/#.#g; s#\.class$##' | sort | tr '\n' ' ')
# shellcheck disable=SC2086
java -cp "${OUT}:${GROOVY_HOME}/lib/*" org.junit.runner.JUnitCore ${specs}
