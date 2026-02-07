# Build stage
FROM node:20-slim as build-stage
WORKDIR /app

# 使用淘宝镜像源加速
RUN npm config set registry https://registry.npmmirror.com

# 复制配置文件
COPY package.json ./

# 删除可能存在的 package-lock.json 并重新安装
RUN rm -f package-lock.json && npm install --no-audit --progress=false

COPY . .
# 增加内存限制，防止OOM
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN npm run build

# Production stage
FROM nginx:stable-alpine as production-stage
COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
