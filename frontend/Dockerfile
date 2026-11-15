# syntax=docker/dockerfile:1

# --- Build stage ---
FROM node:26-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# No API exists while `docker compose` builds images, so the local image is client-only by
# default. Production prerenders (part 5). To test a prerendered image locally, pass
# --build-arg PRERENDER=true --build-arg API_URL=http://host.docker.internal:8080
ARG PRERENDER=false
ARG API_URL=
ARG SITE_URL=
ENV PRERENDER=$PRERENDER API_URL=$API_URL SITE_URL=$SITE_URL
RUN npm run build

# --- Runtime stage ---
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
