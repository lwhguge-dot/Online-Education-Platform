ARG DOCKER_MIRROR=docker.io
FROM ${DOCKER_MIRROR}/library/node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --prefer-offline

COPY . .
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN npm run build

FROM ${DOCKER_MIRROR}/library/nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
USER nginx
CMD ["nginx", "-g", "daemon off;"]