"use strict";

/**
 * Núcleo del servicio Vision+ webOS.
 *
 * No depende de `webos-service`: `index.js` lo registra en Luna y
 * `dev-server.js` lo expone por HTTP para probar en un navegador.
 */

const api = require("./api");
const archive = require("./archive");
const proxy = require("./proxy");
const relay = require("./ffmpeg-relay");
const xtv = require("./xtv");
const sportsStats = require("./sports-stats");
const embedResolver = require("./embed-resolver");

const PC = process.env.VISION_PC === "1";
// El relay se puede probar en un navegador sin activar la interfaz de PC.
const RELAY = PC || process.env.VISION_RELAY === "1";
const VERSION = require("./package.json").version;

let proxyReady = null;

function ensureProxy() {
  if (!proxyReady) {
    proxyReady = new Promise((resolve, reject) => {
      proxy.start((error, port) => (error ? reject(error) : resolve(port)));
    });
  }
  return proxyReady;
}

function log(...args) {
  console.log("[core]", ...args);
}

// Arranca el proxy local en cuanto se carga el servicio.
ensureProxy().catch((error) => log("no se pudo arrancar el proxy:", error.message));

function streamProxyPath(stream, options) {
  const opts = options || {};
  const isLive =
    Boolean(stream.isLive) ||
    /mpegurl/i.test(stream.mime || "") ||
    /\.m3u8(\?|$)/i.test(stream.url || "");
  const isHls = isLive || Boolean(stream.isHls);
  const isTs =
    /mp2t/i.test(stream.mime || "") || /\.ts(\?|$)/i.test(stream.url || "");
  const isDash =
    /dash|mpd/i.test(stream.mime || "") || /\.mpd(\?|$)/i.test(stream.url || "");
  const hevc = /hevc|h\.?265/i.test(`${stream.codec || ""} ${stream.mime || ""}`);
  // Chrome/Edge no reproducen HLS/TS/DASH de forma nativa y Windows no siempre
  // tiene el códec HEVC. En PC todo eso pasa por el relay FFmpeg -> MP4 H.264.
  const pcNeedsRelay = RELAY && (isLive || isHls || isTs || isDash || hevc || Boolean(stream.drmKey));
  if ((opts.forceRelay || pcNeedsRelay) && relay.available()) {
    if (isLive && RELAY) {
      const livePath = relay.registerLive(stream);
      return {
        proxyPath: livePath,
        isRelay: true,
        isHls: false,
        isLive: true,
        mime: "video/mp4",
      };
    }
    const relayPath = relay.register(stream, {
      hevc,
      audio: Number(opts.audioTrack) || 0,
    });
    return {
      proxyPath: relayPath,
      isRelay: true,
      isHls: false,
      mime: "video/mp4",
    };
  }
  const token = proxy.add(stream.url, stream.headers || {}, stream.duration || 0);
  const suffix = isHls ? "stream.m3u8" : /\.mp4(\?|$)/i.test(stream.url || "") ? "stream.mp4" : "stream.ts";
  return {
    proxyPath: `${proxy.baseUrl()}/media/${token}/${suffix}`,
    isRelay: false,
    isHls,
    mime: stream.mime,
  };
}

function resolveXtv(contentId, seriesContentId, options) {
  return new Promise((resolve, reject) => {
    xtv.resolve(contentId, seriesContentId, options || {}, (error, stream) => {
      if (error) reject(error);
      else resolve(stream);
    });
  });
}

function resolveLiveXtv(channelCode) {
  return new Promise((resolve, reject) => {
    xtv.resolveLive(channelCode, (error, stream) => {
      if (error) reject(error);
      else resolve(stream);
    });
  });
}

function detailXtv(contentId) {
  return new Promise((resolve, reject) => {
    xtv.detail(contentId, (error, payload) => {
      if (error) reject(error);
      else resolve(payload);
    });
  });
}

async function resolve(params) {
  await ensureProxy();
  const values = params || {};
  const ref = String(values.ref || values.videoUrl || "").trim();
  let contentId = String(values.contentId || "").trim();
  const seriesContentId = String(values.seriesContentId || "").trim();
  let stream;

  if (ref.startsWith("ia:")) {
    stream = await archive.resolve(ref);
  } else if (ref.startsWith("xtvlive:")) {
    stream = await resolveLiveXtv(ref.slice("xtvlive:".length));
  } else if (/^https?:\/\//i.test(ref)) {
    stream = {
      url: ref,
      mime: values.mime || (/\.m3u8(\?|$)/i.test(ref)
        ? "application/x-mpegurl"
        : /\.mpd(\?|$)/i.test(ref)
          ? "application/dash+xml"
          : "video/mp4"),
      headers: values.headers || {},
      codec: "",
      quality: "",
      duration: 0,
      subtitles: [],
      isLive: values.isLive === true || values.isLive === "true",
      drmKey: String(values.drmKey || ""),
    };
  } else {
    if (!contentId && ref.startsWith("xtv:")) contentId = ref.slice(4);
    if (!contentId) throw new Error("falta el contentId para resolver la reproducción");
    stream = await resolveXtv(contentId, seriesContentId, {
      trackIndex: values.trackIndex,
      quality: values.quality,
    });
  }

  if (
    !stream.isLive &&
    !Number(stream.duration) &&
    !/\.(?:m3u8|mpd)(\?|$)/i.test(stream.url || "") &&
    relay.available()
  ) {
    try {
      const probed = await relay.probeDuration(stream);
      if (probed > 0) stream.duration = probed;
    } catch (error) {
      log("no se pudo sondear la duración:", error.message);
    }
  }

  const proxied = streamProxyPath(stream, {
    forceRelay:
      values.forceRelay === true ||
      values.forceRelay === "true" ||
      values.playbackMode === "smooth" ||
      values.playbackMode === "relay",
    audioTrack: values.audioTrack,
  });
  const subtitles = (stream.subtitles || []).map((subtitle) =>
    Object.assign({}, subtitle, { proxyUrl: proxy.addSubtitle(subtitle.url) }),
  );
  const audioTracks = String(stream.audio || "")
    .split(/[,/]/)
    .map((lang) => lang.trim().toLowerCase())
    .filter(Boolean);
  return Object.assign({}, stream, proxied, {
    subtitles,
    audioTracks,
    resolvedAt: Date.now(),
  });
}

async function resolveLive(params) {
  await ensureProxy();
  const values = params || {};
  const stream = values.streamUrl
    ? {
        url: String(values.streamUrl),
        mime: "application/x-mpegurl",
        headers: values.headers || {},
        isLive: true,
        drmKey: String(values.drmKey || ""),
        subtitles: [],
      }
    : await resolveLiveXtv(values.channelCode);
  const proxied = streamProxyPath(
    Object.assign({}, stream, { isHls: true, mime: "application/x-mpegurl" }),
    { forceRelay: values.forceRelay === true || values.forceRelay === "true" },
  );
  return Object.assign({}, stream, proxied, { isLive: true });
}

async function episodes(params) {
  const ref = String((params && params.ref) || "").trim();
  if (!ref) throw new Error("falta la referencia de la serie");
  try {
    const payload = await api.episodes(ref);
    if (payload && Array.isArray(payload.episodes) && payload.episodes.length) {
      return Object.assign({}, payload, { source: "vps" });
    }
  } catch (error) {
    log("espejo de episodios falló:", error.message);
  }
  const contentId = ref.replace(/^xtv:/, "");
  const portal = await detailXtv(contentId);
  return {
    ref,
    status: "ok",
    source: "portal",
    episodes: (portal.episodes || []).map((episode) => ({
      name: episode.name,
      contentId: episode.id,
      seriesNumber: episode.number,
      episodeNumber: episode.number,
      title: episode.title,
      overview: episode.description,
      still: episode.picture,
      duration: episode.duration,
      quality: episode.quality,
    })),
    seasons: portal.seasons || [],
  };
}

async function mediaInfo(params) {
  const values = params || {};
  return new Promise((resolve, reject) => {
    xtv.mediaInfo(values.contentId, values.seriesContentId, (error, payload) => {
      if (error) reject(error);
      else resolve(payload);
    });
  });
}

async function home(params) {
  const payload = await api.home((params && params.limit) || 24);
  return Object.assign({}, payload, {
    categories: (payload.categories || []).filter((category) => category && (category.films || []).length),
  });
}

async function search(params) {
  const query = String((params && params.q) || "").trim();
  if (!query) return { query: "", films: [] };
  return api.search(query, (params && params.limit) || 30);
}

async function banners() {
  return api.banners();
}

async function epgBulk(params) {
  return api.epgBulk((params && params.channels) || []);
}

async function events(params) {
  const values = params || {};
  return api.geeEvents(values.day, values.days || 1);
}

async function sports(params) {
  return api.sportsAgenda((params && params.days) || 2);
}

async function matchStats(params) {
  const stats = await sportsStats.load(params || {});
  return { stats: stats || null };
}

async function resolveEmbed(params) {
  const token = String((params && params.tokenApi) || "").trim();
  if (!token) throw new Error("Falta el token de la señal.");
  return embedResolver.resolve(token);
}

async function markers(params) {
  const values = params || {};
  return api.markers(values.ref, values.ep, values.len);
}

async function category(params) {
  const values = params || {};
  return api.category(values.id, values.page || 1, values.pageSize || 60);
}

async function section(params) {
  const values = params || {};
  return api.section(values.name, values.limit || 40);
}

async function live(params) {
  const values = params || {};
  return api.live(values.page || 1, values.pageSize || 100);
}

function liveCatalog() {
  return new Promise((resolve, reject) => {
    xtv.liveCatalog((error, payload) => (error ? reject(error) : resolve(payload)));
  });
}

async function ping() {
  const port = proxy.port();
  return {
    app: PC ? "Vision+ PC" : "Vision+ webOS",
    platform: PC ? "windows" : "webos",
    pc: PC,
    version: VERSION,
    api: api.BASE_URL,
    proxyPort: port,
    relayAvailable: relay.availableFast(),
    relayPort: relay.availableFast() ? relay.port() : 0,
    sessionReady: Boolean(xtv.session.ready),
    userId: xtv.session.userId || "",
    portalHosts: xtv.constants.HOSTS,
  };
}

function prewarm() {
  return new Promise((resolve, reject) => {
    xtv.ensureSession((error) => {
      if (error) reject(error);
      else resolve({ userId: xtv.session.userId, ready: true });
    });
  });
}

function abortMedia() {
  try {
    relay.stop();
  } catch {
    /* noop */
  }
  try {
    proxy.killActiveStreams();
  } catch {
    /* noop */
  }
  return { ok: true };
}

function resetSession() {
  xtv.clearSession();
  return { ok: true };
}

function clearCache() {
  api.clearCache();
  return { ok: true };
}

module.exports = {
  ensureProxy,
  ping,
  home,
  banners,
  search,
  category,
  section,
  live,
  liveCatalog,
  epgBulk,
  events,
  sports,
  matchStats,
  resolveEmbed,
  markers,
  episodes,
  resolve,
  resolveLive,
  mediaInfo,
  prewarm,
  abortMedia,
  resetSession,
  clearCache,
  detail: detailXtv,
};
