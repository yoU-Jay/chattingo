# Stage 1: Dependencies
FROM node:18-alpine AS deps
WORKDIR /app
COPY package*.json package-lock.json ./
RUN npm ci 

# Stage 2: Build
FROM deps AS build
COPY . .
RUN npm run build

# Stage 3: Runtime
FROM nginx:alpine
WORKDIR /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build .

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"] 