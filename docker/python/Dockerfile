# statements-api and exposure-calc. No pipeline (TOOL-1444), built by the Makefile in
# platform-services. Kept here so at least the base image is the approved one.
FROM artifactory.meridian.internal/docker-redhat-remote/ubi9/python-311:1-66 AS runtime
ARG SERVICE
ARG BUILD_TAG=local
LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.version="${BUILD_TAG}" \
      meridian.bank/scan-policy="GIS-STD-021"
ENV PIP_INDEX_URL=https://artifactory.meridian.internal/artifactory/api/pypi/pypi-virtual/simple \
    PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1
USER 1001
WORKDIR /opt/app-root/src
COPY --chown=1001:0 requirements.txt ./
RUN pip install --no-deps -r requirements.txt
COPY --chown=1001:0 . .
EXPOSE 8080
# TODO(DOC-2044): reportlab fonts. Works on the ubi image by accident; pin the font package.
CMD ["sh", "-c", "for f in /vault/secrets/*.env; do [ -f \"$f\" ] && . \"$f\"; done; exec uvicorn ${APP_MODULE:-statements_api.main:app} --host 0.0.0.0 --port 8080 --workers ${WEB_CONCURRENCY:-2}"]
