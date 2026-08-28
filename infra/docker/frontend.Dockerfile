FROM node:22-alpine AS admin-build

WORKDIR /workspace
RUN npm install --global pnpm@10.34.5
COPY web/admin/package.json web/admin/pnpm-lock.yaml web/admin/pnpm-workspace.yaml ./
RUN pnpm fetch --frozen-lockfile
COPY web/admin/ ./
RUN pnpm install --offline --frozen-lockfile && pnpm exec vite build --mode demo

FROM node:22-alpine AS student-build

WORKDIR /workspace
RUN npm install --global pnpm@10.34.5
COPY web/student/package.json web/student/pnpm-lock.yaml web/student/pnpm-workspace.yaml ./
RUN pnpm fetch --frozen-lockfile
COPY web/student/ ./
RUN pnpm install --offline --frozen-lockfile && pnpm exec vite build --mode demo

FROM nginx:1.28-alpine

COPY --from=admin-build /workspace/dist /usr/share/nginx/html
COPY --from=student-build /workspace/dist /usr/share/nginx/html/student-assets
COPY --from=student-build /workspace/dist/index.html /usr/share/nginx/html/student-index.html
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
