# 纯静态部署
FROM nginx:stable-alpine

# 直接复制本地 build 好的 dist 目录
COPY dist /usr/share/nginx/html

# 复制配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]