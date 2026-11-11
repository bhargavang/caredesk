# Build the static site (with the prerendered booking page), then serve it with nginx,
# which also proxies /api to the backend so the browser only ever talks to one origin.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG SITE_URL=
ARG PRERENDER_API_URL=
RUN SITE_URL="$SITE_URL" PRERENDER_API_URL="$PRERENDER_API_URL" npm run build

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
