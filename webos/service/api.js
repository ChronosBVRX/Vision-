"use strict";

/**
 * Cliente del API de Vision+ (VPS): índice, búsqueda, fichas, episodios,
 * TV en vivo, banners, deportes y EPG.
 *
 * La app Android usa este mismo API con `X-Api-Key`. El servicio webOS lo
 * consume desde Node (sin CORS) y le entrega a la UI sólo lo que necesita.
 */

const http = require("http");
const https = require("https");
const urlmod = require("url");

const BASE_URL = (process.env.VISION_API_BASE || "https://visionplusv2.duckdns.org").replace(/\/+$/, "");
const API_KEY = process.env.VISION_API_KEY || "fb2244441f690185f861470423afb43278c3b19d31b14115";
const TIMEOUT_MS = 20000;
const TTL_MS = 5 * 60 * 1000;

const cache = new Map();
const inFlight = new Map();

function log(...args) {
  console.log("[api]", ...args);
}

function queryString(params) {
  return Object.entries(params || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join("&");
}

function fetchJson(pathname, params) {
  const query = queryString(params);
  const suffix = query ? `?${query}` : "";
  const target = `${BASE_URL}${pathname}${suffix}`;
  const key = target;

  const cached = cache.get(key);
  if (cached && Date.now() - cached.at < TTL_MS) {
    return Promise.resolve(cached.data);
  }
  if (inFlight.has(key)) return inFlight.get(key);

  const request = new Promise((resolve, reject) => {
    const parsed = urlmod.parse(target);
    const transport = parsed.protocol === "http:" ? http : https;
    const req = transport.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        method: "GET",
        headers: {
          "X-Api-Key": API_KEY,
          Accept: "application/json",
          "Accept-Encoding": "identity",
          "User-Agent": "VisionPlusWebOS/0.1",
        },
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const body = Buffer.concat(chunks).toString("utf8");
          if (res.statusCode < 200 || res.statusCode >= 300) {
            return reject(new Error(`API ${res.statusCode} en ${pathname}: ${body.slice(0, 180)}`));
          }
          try {
            const data = JSON.parse(body);
            cache.set(key, { at: Date.now(), data });
            resolve(data);
          } catch (e) {
            reject(new Error(`API no devolvió JSON en ${pathname}: ${e.message}`));
          }
        });
      },
    );
    req.setTimeout(TIMEOUT_MS, () => req.destroy(new Error(`API timeout en ${pathname}`)));
    req.on("error", (error) => {
      const stale = cache.get(key);
      if (stale) {
        log("se usa caché vieja por", error.message);
        return resolve(stale.data);
      }
      reject(error);
    });
    req.end();
  }).then(
    (data) => {
      inFlight.delete(key);
      return data;
    },
    (error) => {
      inFlight.delete(key);
      throw error;
    },
  );

  inFlight.set(key, request);
  return request;
}

function postJson(pathname, body, params) {
  const query = queryString(params);
  const suffix = query ? `?${query}` : "";
  const target = `${BASE_URL}${pathname}${suffix}`;
  return new Promise((resolve, reject) => {
    const parsed = urlmod.parse(target);
    const payload = JSON.stringify(body || {});
    const transport = parsed.protocol === "http:" ? http : https;
    const req = transport.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        method: "POST",
        headers: {
          "X-Api-Key": API_KEY,
          "Content-Type": "application/json",
          Accept: "application/json",
          "Accept-Encoding": "identity",
          "Content-Length": Buffer.byteLength(payload),
          "User-Agent": "VisionPlusWebOS/0.1",
        },
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf8");
          if (res.statusCode < 200 || res.statusCode >= 300) {
            return reject(new Error(`API ${res.statusCode} en ${pathname}: ${raw.slice(0, 180)}`));
          }
          try {
            resolve(JSON.parse(raw));
          } catch (e) {
            reject(new Error(`API no devolvió JSON en ${pathname}: ${e.message}`));
          }
        });
      },
    );
    req.setTimeout(TIMEOUT_MS, () => req.destroy(new Error(`API timeout en ${pathname}`)));
    req.on("error", reject);
    req.write(payload);
    req.end();
  });
}

function clearCache() {
  cache.clear();
}

function home(limit) {
  return fetchJson("/v1/index", { limitFilms: limit || 24, since: 0 });
}

function section(name, limit) {
  return fetchJson(`/v1/section/${encodeURIComponent(name)}`, { limit: limit || 40 });
}

function category(id, page, pageSize) {
  return fetchJson(`/v1/category/${encodeURIComponent(id)}`, {
    page: page || 1,
    pageSize: pageSize || 60,
  });
}

function search(query, limit) {
  return fetchJson("/v1/search", { q: query, limit: limit || 30 });
}

function episodes(ref) {
  return fetchJson("/v1/episodes", { ref });
}

function live(page, pageSize) {
  return category("tv-en-vivo", page || 1, pageSize || 100);
}

function banners() {
  return fetchJson("/v1/banners");
}

function trending(limit) {
  return fetchJson("/v1/trending", { limit: limit || 20 });
}

function schedule(days) {
  return fetchJson("/v1/schedule", { days: days || 7 });
}

function sports() {
  return fetchJson("/v1/sports");
}

function epg(channels) {
  return fetchJson("/v1/epg", { channels: (channels || []).join(",") });
}

function config() {
  return fetchJson("/v1/config");
}

function epgBulk(channels) {
  return postJson("/v1/epg/bulk", { channels: channels || [] });
}

function markers(ref, ep, length) {
  return fetchJson("/v1/markers", { ref, ep, len: length || 0 });
}

function geeEvents(day, days) {
  return fetchJson("/v1/gee38/events", { day: day || "", days: days || 1 });
}

function geeSports() {
  return fetchJson("/v1/gee38/sports");
}

function sportsAgenda(days) {
  return fetchJson("/v1/sports", { days: days || 2 });
}

module.exports = {
  BASE_URL,
  API_KEY,
  fetchJson,
  clearCache,
  home,
  section,
  category,
  search,
  episodes,
  live,
  banners,
  trending,
  schedule,
  sports,
  epg,
  config,
  postJson,
  epgBulk,
  markers,
  geeEvents,
  geeSports,
  sportsAgenda,
};
