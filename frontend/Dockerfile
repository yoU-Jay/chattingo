FROM node:18-alpine AS deps
WORKDIR /app
COPY package*.json package-lock.json ./
RUN npm ci 

# Stage 2: Build
FROM deps AS build
COPY --from=deps /app/node_modules /app/node_modules
COPY . .
ARG REACT_APP_API_URL
ENV REACT_APP_API_URL=${REACT_APP_API_URL}
RUN npm run build

# Stage 3: Runtime
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY public/nginx.conf /etc/nginx/conf.d/default.conf


EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]  