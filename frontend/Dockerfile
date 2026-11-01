# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# nginx serves the static app and proxies /api to the backend (BACKEND_URL).
FROM nginx:1.31-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
EXPOSE 80
