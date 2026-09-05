#!/bin/sh
# Sources Vault Agent output when present, then hands over to the Red Hat image's run-java script,
# which handles memory sizing from cgroup limits. The chart's Deployment already sources the env
# files before exec'ing this; the loop here is for podman run on a laptop.
set -eu
for f in /vault/secrets/*.env; do
  # shellcheck disable=SC1090
  [ -f "$f" ] && . "$f"
done
export JAVA_APP_JAR=/deployments/app.jar
exec /opt/jboss/container/java/run/run-java.sh
