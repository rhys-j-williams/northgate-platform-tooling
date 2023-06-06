# NestJS / Express services (bff-retail, bff-business, iris-orchestrator, documents-service).
# Node 18 only; the front end Node versions do not apply here. GIS-STD-021 appendix B.
ARG NODE_VERSION=18.19.0
FROM artifactory.meridian.internal/docker-remote/library/node:${NODE_VERSION}-bullseye-slim AS build
ARG NPM_REGISTRY=https://artifactory.meridian.internal/artifactory/api/npm/npm-virtual/
ENV CI=true
WORKDIR /workspace
COPY package.json package-lock.json .npmrc* ./
RUN --mount=type=secret,id=npmrc,target=/root/.npmrc \
    npm config set registry "${NPM_REGISTRY}" && npm ci --no-audit --no-fund
COPY . .
RUN npm run build && npm prune --omit=dev

FROM artifactory.meridian.internal/docker-redhat-remote/ubi9/nodejs-18-minimal:1-108 AS runtime
ARG SERVICE
ARG BUILD_TAG=local
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.vendor="Meridian Trust Bank, CSWT" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.version="${BUILD_TAG}" \
      meridian.bank/scan-policy="GIS-STD-021"
ENV NODE_ENV=production
USER 1001
WORKDIR /opt/app-root/src
COPY --from=build --chown=1001:0 /workspace/dist ./dist
COPY --from=build --chown=1001:0 /workspace/node_modules ./node_modules
COPY --from=build --chown=1001:0 /workspace/package.json ./
COPY --chown=1001:0 platform-tooling/docker/node/entrypoint.sh /entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
