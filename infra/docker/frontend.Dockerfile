# syntax=docker/dockerfile:1.7
FROM node:22.22.0-alpine@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34 AS admin-build

WORKDIR /workspace
ARG VITE_APP_IMPORT_MOCK=false
ENV VITE_APP_IMPORT_MOCK=${VITE_APP_IMPORT_MOCK}

RUN npm install --global pnpm@10.34.5

# Keep dependency resolution in its own layer. Source-only edits then reuse the
# fetched pnpm store, while the lockfile remains the sole dependency authority.
COPY web/admin/package.json web/admin/pnpm-lock.yaml web/admin/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/pnpm/store \
    pnpm fetch --frozen-lockfile --store-dir /pnpm/store

COPY web/admin/ ./
RUN --mount=type=cache,target=/pnpm/store \
    pnpm install --offline --frozen-lockfile --store-dir /pnpm/store \
    && pnpm build

FROM node:22.22.0-alpine@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34 AS student-build

WORKDIR /workspace

RUN npm install --global pnpm@10.34.5

COPY web/student/package.json web/student/pnpm-lock.yaml web/student/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/pnpm/store \
    pnpm fetch --frozen-lockfile --store-dir /pnpm/store

COPY web/student/ ./
RUN --mount=type=cache,target=/pnpm/store \
    pnpm install --offline --frozen-lockfile --store-dir /pnpm/store \
    && pnpm build

FROM nginx:1.28.0-alpine@sha256:30f1c0d78e0ad60901648be663a710bdadf19e4c10ac6782c235200619158284

ARG CERTMUSE_SOURCE_REVISION
LABEL org.opencontainers.image.revision=$CERTMUSE_SOURCE_REVISION \
      org.opencontainers.image.title="CertMuse unified frontend"

ENV MINIO_UPSTREAM=minio:9000

COPY --from=admin-build /workspace/dist /usr/share/nginx/html
COPY --from=student-build /workspace/dist /usr/share/nginx/html/student-assets
COPY --from=student-build /workspace/dist/index.html /usr/share/nginx/html/student-index.html
COPY infra/docker/stale-chunk-reload.js /usr/share/nginx/html/stale-chunk-reload.js
COPY infra/docker/nginx.conf /etc/nginx/templates/default.conf.template

EXPOSE 80
