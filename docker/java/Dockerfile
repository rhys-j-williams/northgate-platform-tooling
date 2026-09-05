# Spring Boot services. Two JDK lines are in service: 11 for the Boot 2.7 services and 17 for
# entitlements-service (Boot 3). JDK_VERSION comes from the pom's maven.compiler.release via the
# Jenkins library. GIS-STD-021 appendix B lists the approved base images.
ARG JDK_VERSION=11
FROM artifactory.meridian.internal/docker-redhat-remote/ubi9/openjdk-${JDK_VERSION}-runtime:1.20 AS runtime

ARG SERVICE
ARG BUILD_TAG=local
ARG GIT_SHA=unknown
ARG JAR=target/*.jar

LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.vendor="Meridian Trust Bank, CSWT" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.version="${BUILD_TAG}" \
      meridian.bank/scan-policy="GIS-STD-021"

ENV JAVA_OPTS_APPEND="-Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8 -Dlog4j2.formatMsgNoLookups=true" \
    JAVA_MAX_MEM_RATIO=70 \
    TZ=America/New_York

USER 185
WORKDIR /deployments
# The jar is built on the agent by the Maven stage. Building inside the image was dropped in 2022:
# the agents cannot reach the internet and mounting the Maven cache into buildah was flaky (TOOL-870).
COPY --chown=185:0 ${JAR} /deployments/app.jar
COPY --chown=185:0 platform-tooling/docker/java/entrypoint.sh /entrypoint.sh

EXPOSE 8080 8081
ENTRYPOINT ["/entrypoint.sh"]
