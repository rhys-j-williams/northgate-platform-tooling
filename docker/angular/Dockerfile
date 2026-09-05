# Multi-stage build for the Angular front ends. Every front end repo's Jenkinsfile passes this
# file with --build-arg NODE_VERSION=$(cat .nvmrc) and --build-arg APP=<name>. Do not hard code the
# Node version here; the repos disagree with each other on purpose (business-web is still on 14,
# MBZ-2231) and the pipeline reads .nvmrc so the container build matches the agent.
#
# Owner: Platform Engineering. Base images: GIS approved list GIS-STD-021 appendix B.
#
# Build:   podman build -f platform-tooling/docker/angular/Dockerfile \
#            --build-arg NODE_VERSION=16.20.2 --build-arg APP=retail-web --build-arg BUILD_CONFIG=production .
# Run:     podman run -p 8080:8080 -e CSP_CONNECT_SRC="'self' https://api.example.com" retail-web

ARG NODE_VERSION
ARG NGINX_IMAGE=registry.access.redhat.com/ubi9/nginx-124
ARG NGINX_TAG=1-30

# ---- stage 1: build --------------------------------------------------------------------------
# Pulled through Artifactory (docker-remote). The internal registry mirror is the only one the
# build agents can reach. Locally, replace the prefix or log in to artifactory.meridian.internal.
FROM artifactory.meridian.internal/docker-remote/library/node:${NODE_VERSION}-bullseye-slim AS build

ARG APP
ARG BUILD_CONFIG=production
ARG NPM_REGISTRY=https://artifactory.meridian.internal/artifactory/api/npm/npm-virtual/
ENV CI=true \
    NG_CLI_ANALYTICS=false \
    NODE_OPTIONS=--max-old-space-size=4096

WORKDIR /workspace

# .npmrc from the repo carries registry, always-auth and any legacy-peer-deps. The token comes in
# as a build secret, never as an ARG (GIS-2911). --mount=type=secret needs BuildKit / podman 4.
COPY package.json package-lock.json .npmrc* ./
RUN --mount=type=secret,id=npmrc,target=/root/.npmrc \
    npm config set registry "${NPM_REGISTRY}" && \
    npm ci --no-audit --no-fund --prefer-offline

COPY . .
RUN npx ng build --configuration "${BUILD_CONFIG}" && \
    # Angular's outputPath varies: dist/<app>, dist/<app>/browser (17+), dist/<app>/en-US (localize).
    # Normalise to /out so the runtime stage does not care which repo it is building.
    set -e; \
    if [ -d "dist/${APP}/browser" ]; then src="dist/${APP}/browser"; \
    elif [ -f "dist/${APP}/index.html" ] || [ -d "dist/${APP}/en-US" ]; then src="dist/${APP}"; \
    else src="$(dirname "$(find dist -name index.html -print -quit)")"; fi; \
    mkdir -p /out && cp -R "${src}/." /out/ && \
    # Localised builds put index.html under dist/<app>/en-US; hoist the default locale so / works
    # and keep /es/ as-is (MOL-3311).
    if [ -d /out/en-US ]; then cp -R /out/en-US/. /out/; fi && \
    ls -la /out

# ---- stage 2: runtime ------------------------------------------------------------------------
# Unprivileged, uid 1001, listens on 8080. Red Hat's image, pulled through the Artifactory mirror
# of registry.access.redhat.com. GIS-STD-021 forbids docker.io/nginx.
FROM artifactory.meridian.internal/docker-redhat-remote/ubi9/nginx-124:${NGINX_TAG} AS runtime

ARG APP
ARG BUILD_TAG=local
ARG GIT_SHA=unknown

LABEL org.opencontainers.image.title="${APP}" \
      org.opencontainers.image.vendor="Meridian Trust Bank, CSWT" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.version="${BUILD_TAG}" \
      meridian.bank/app="${APP}" \
      meridian.bank/base-image="ubi9/nginx-124" \
      meridian.bank/scan-policy="GIS-STD-021"

USER 0
COPY platform-tooling/docker/nginx/nginx.conf /etc/nginx/nginx.conf
COPY platform-tooling/docker/nginx/security-headers.inc /etc/nginx/conf.d/security-headers.inc
COPY platform-tooling/docker/nginx/40-csp.sh /usr/local/bin/40-csp.sh
# The ubi image ships a default.conf that listens on 8080 as well; remove it or nginx refuses to start.
RUN rm -f /etc/nginx/conf.d/default.conf /etc/nginx/nginx.conf.default && \
    chmod 0755 /usr/local/bin/40-csp.sh && \
    chown -R 1001:0 /opt/app-root/src && chmod -R g=u /opt/app-root/src
USER 1001

COPY --from=build --chown=1001:0 /out/ /opt/app-root/src/
# env.json is replaced at runtime by the chart's ConfigMap mount. The one from the build is the
# developer default and must not point anywhere real.
COPY --from=build --chown=1001:0 /workspace/src/assets/env.json /opt/app-root/src/env.json

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD curl -fs http://localhost:8080/healthz || exit 1

# The ubi image's own entrypoint runs nginx; we wrap it to render the CSP first.
ENTRYPOINT ["/bin/sh", "-c", "/usr/local/bin/40-csp.sh && exec nginx -g 'daemon off;'"]
