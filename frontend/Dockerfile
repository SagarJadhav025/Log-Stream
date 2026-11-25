# ════════════════════════════════════════════════════════════════════════
# Stage 1 — Build the React + Vite app
# ════════════════════════════════════════════════════════════════════════
FROM node:20-alpine AS build

WORKDIR /app

# Cache node_modules layer
COPY package*.json ./
RUN npm ci --silent

# Copy source and build production bundle
COPY . .
RUN npm run build

# ════════════════════════════════════════════════════════════════════════
# Stage 2 — Serve via Nginx
# ════════════════════════════════════════════════════════════════════════
FROM nginx:1.27-alpine

LABEL org.opencontainers.image.title="LogStream Frontend"

# Copy Vite build output
COPY --from=build /app/dist /usr/share/nginx/html

# Custom Nginx config with API + WebSocket proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
