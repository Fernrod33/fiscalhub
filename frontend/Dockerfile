# syntax=docker/dockerfile:1
# Frontend: build do Vite servido por Caddy (estático, gzip/zstd, cache longo nos assets com hash).

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# A API está na mesma origem (o proxy encaminha /api). O limite de upload, em MB, é o mesmo do proxy e do Spring.
ARG UPLOAD_MAX_SIZE_MB=500
RUN VITE_API_URL="" VITE_UPLOAD_MAX_BYTES=$((UPLOAD_MAX_SIZE_MB * 1024 * 1024)) npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist /srv
USER nobody
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO /dev/null http://127.0.0.1:8080/ || exit 1
