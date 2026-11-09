FROM node:22-alpine

WORKDIR /app
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    OPEN_PARTY_LAB_WEB_ROOT=/app/web

COPY party-server.mjs ./party-server.mjs
COPY web ./web

EXPOSE 3000
CMD ["node", "party-server.mjs"]
