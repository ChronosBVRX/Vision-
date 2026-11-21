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
const http = require("http");
const https = require("https");
const urlmod = require("url");

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
  // Un HLS de VOD (4K externo) no debe caer en la ruta de vivo: necesita
  // duración, seek y el mismo relay de archivo que un MKV/TS.
  const isLive =
    Boolean(stream.isLive) ||
    (stream.vod !== true &&
      (/mpegurl/i.test(stream.mime || "") || /\.m3u8(\?|$)/i.test(stream.url || "")));
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
      // 4K externo: el video se copia cuando el equipo decodifica HEVC y el
      // audio se convierte a AAC (DTS/TrueHD/DD no los decodifica Chromium).
      copyVideo: Boolean(opts.copyVideo),
      audioAac: Boolean(opts.audioAac),
      preferAudioLang: Boolean(opts.preferAudioLang),
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

function audioLanguageMatches(value, want) {
  const source = String(value || "").toLowerCase();
  const target = String(want || "").toLowerCase();
  if (!target) return false;
  if (target === "spa" || target === "es") return /^(spa|es|esp)/.test(source);
  if (target === "eng" || target === "en") return /^(eng|en)/.test(source);
  return source.indexOf(target) === 0;
}

/** Español primero, luego inglés y, si no, la primera pista del archivo. */
function preferredAudioIndex(audio, want) {
  const tracks = Array.isArray(audio) ? audio : [];
  if (!tracks.length) return 0;
  const order = want ? [String(want)] : ["spa", "es", "eng", "en"];
  for (const code of order) {
    const found = tracks.find((track) => audioLanguageMatches(track.lang, code));
    if (found && Number.isInteger(Number(found.index))) return Number(found.index);
  }
  return Number(tracks[0].index) || 0;
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

/** Tipo de archivo de una URL externa (los 4K de HdHub llegan como MKV). */
function mimeForUrl(url) {
  var raw = String(url || "");
  try {
    raw = decodeURIComponent(raw);
  } catch (error) {
    /* la URL puede traer signos de porcentaje sueltos */
  }
  var clean = raw.split("?")[0].toLowerCase();
  if (clean.endsWith(".m3u8")) return "application/x-mpegurl";
  if (clean.endsWith(".mpd")) return "application/dash+xml";
  if (clean.endsWith(".mkv")) return "video/x-matroska";
  if (clean.endsWith(".webm")) return "video/webm";
  if (clean.endsWith(".m4v")) return "video/x-m4v";
  if (clean.endsWith(".ts")) return "video/mp2t";
  if (clean.endsWith(".mp4")) return "video/mp4";
  var lower = raw.toLowerCase();
  if (lower.indexOf(".mkv") >= 0) return "video/x-matroska";
  if (lower.indexOf(".m3u8") >= 0) return "application/x-mpegurl";
  return "video/mp4";
}

function fourKLanguage(blob) {
  if (/(?:\blatino\b|\blatina\b|\blatam\b|\bdual[ ._-]?lat\b|\bes[- ]?419\b|\bes[- ]?mx\b)/i.test(blob)) {
    return "Latino";
  }
  if (/(?:\bcastellano\b|\bespa[nñ]ol\b|\bspanish\b|\besp\b)/i.test(blob)) {
    return "Castellano";
  }
  return "Original";
}

function fourKProvider(name, blob) {
  const text = String(name || "").trim();
  if (/4khdhub/i.test(text) || /4khdhub/i.test(blob)) return "4KHDHub";
  const bracket = /^\[([^\]]+)]/.exec(text);
  const candidate = (bracket ? bracket[1] : text.split(/\r?\n/)[0] || "4K").trim();
  return candidate.replace(/[^\w .-]/g, "").trim().slice(0, 32) || "4K";
}

/**
 * Normaliza las fuentes 4K del VPS. Solo se aceptan pistas que declaren
 * 2160p; el nombre «4KHDHub» se descuenta antes de buscar la calidad real.
 */
function fourKSources(payload) {
  const streams = Array.isArray(payload && payload.streams) ? payload.streams : [];
  const seen = new Set();
  const out = [];
  const now = Date.now();
  for (const raw of streams) {
    if (!raw || typeof raw !== "object") continue;
    const url = String(raw.url || "").trim();
    if (!/^https?:\/\//i.test(url) || seen.has(url)) continue;
    const blob = `${raw.name || ""} ${raw.title || ""} ${raw.description || ""}`;
    const qualityBlob = blob.replace(/4khdhub/ig, "");
    if (!/(?:\b2160p?\b|\b4k\b|\buhd\b)/i.test(qualityBlob)) continue;
    seen.add(url);
    const isHls = /\.m3u8(?:\?|$)/i.test(url);
    const hints = raw.behaviorHints || {};
    const size = Number(hints.videoSize) || 0;
    const declared = String(raw.language || "").trim();
    const language =
      /latino/i.test(declared) ? "Latino"
        : /castellano/i.test(declared) ? "Castellano"
          : fourKLanguage(blob);
    const expires = (() => {
      const amz = /[?&]X-Amz-Expires=(\d+)/i.exec(url);
      if (amz) return now + Number(amz[1]) * 1000;
      const e = /[?&]e=(\d+)/i.exec(url);
      if (!e) return 0;
      const value = Number(e[1]);
      if (!value) return 0;
      return value >= 1_000_000_000_000 ? value : now + value * 1000;
    })();
    out.push({
      url,
      provider: fourKProvider(raw.name, blob),
      language,
      label: language === "Original" ? "4K · Original" : `4K · ${language}`,
      height: 2160,
      sizeBytes: size,
      isHls,
      mime: mimeForUrl(url),
      codec: /(?:hevc|h\.?265|x265)/i.test(blob) || /\.mkv(?:\?|$)/i.test(url) ? "HEVC" : "",
      expiresAt: expires,
    });
    if (out.length >= 6) break;
  }
  // Español primero; dentro de cada idioma, el archivo más ligero arranca antes.
  out.sort((left, right) => {
    const rank = (value) => (value.language === "Latino" ? 0 : value.language === "Castellano" ? 1 : 2);
    const byLanguage = rank(left) - rank(right);
    if (byLanguage) return byLanguage;
    return (left.sizeBytes || Number.MAX_SAFE_INTEGER) - (right.sizeBytes || Number.MAX_SAFE_INTEGER);
  });
  return out.slice(0, 4);
}

async function external4k(params) {
  const values = params || {};
  const title = String(values.title || "").trim();
  const original = String(values.original || values.originalTitle || "").trim();
  const imdb = String(values.imdb || "").trim();
  const tmdb = Number(values.tmdb || values.tmdbId || 0) || 0;
  const year = Number(values.year || 0) || 0;
  if (!title && !original && !imdb && !tmdb) return { sources: [] };
  try {
    const payload = await api.external4k({ imdb, tmdb, title, original, year });
    const candidates = fourKSources(payload);
    if (!candidates.length) return { sources: [] };
    // El addon publica espejos caídos (302 a un host que responde 400). Se
    // comprueba cada uno con un GET de 1 KB antes de ofrecerlo en el selector.
    const checks = await Promise.all(candidates.map((source) => probeUrl(source.url, 5500)));
    const sources = candidates.filter((source, index) => checks[index]).slice(0, 4);
    log("4K", title || imdb || tmdb, "·", sources.length, "de", candidates.length, "fuentes responden");
    return { sources, resolvedAt: Date.now() };
  } catch (error) {
    log("sin fuentes 4K para", title || imdb || tmdb, "·", error.message);
    return { sources: [] };
  }
}

/** GET de 1 KB con redirecciones para descartar espejos 4K caídos. */
function probeUrl(url, timeoutMs, redirectsLeft) {
  const left = redirectsLeft === undefined ? 3 : redirectsLeft;
  return new Promise((resolve) => {
    let parsed;
    try {
      parsed = urlmod.parse(url);
    } catch (error) {
      resolve(false);
      return;
    }
    if (!parsed.hostname || (parsed.protocol !== "http:" && parsed.protocol !== "https:")) {
      resolve(false);
      return;
    }
    const transport = parsed.protocol === "http:" ? http : https;
    let settled = false;
    const done = (value) => {
      if (settled) return;
      settled = true;
      resolve(value);
    };
    const req = transport.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        method: "GET",
        headers: {
          Range: "bytes=0-1023",
          Accept: "*/*",
          "User-Agent": "VisionPlusWebOS/0.2",
        },
      },
      (res) => {
        const status = Number(res.statusCode) || 0;
        const location = res.headers.location;
        res.on("error", () => done(false));
        if (status >= 300 && status < 400 && location && left > 0) {
          res.resume();
          probeUrl(urlmod.resolve(url, location), timeoutMs, left - 1).then(done);
          return;
        }
        res.resume();
        if (res.destroy) res.destroy();
        done(status >= 200 && status < 300);
      },
    );
    req.setTimeout(timeoutMs || 5500, () => {
      try { req.destroy(new Error("tiempo de sonda 4K agotado")); } catch (error) { /* noop */ }
      done(false);
    });
    req.on("error", () => done(false));
    req.end();
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
      mime: values.mime || mimeForUrl(ref),
      headers: values.headers || {},
      codec: String(values.codec || ""),
      quality: "",
      duration: 0,
      subtitles: [],
      isLive: values.isLive === true || values.isLive === "true",
      vod: values.vod === true || values.vod === "true",
      drmKey: String(values.drmKey || ""),
    };
  } else {
    if (!contentId && ref.startsWith("xtv:")) contentId = ref.slice(4);
    if (!contentId) throw new Error("falta el contentId para resolver la reproducción");
    stream = await resolveXtv(contentId, seriesContentId, {
      trackIndex: values.trackIndex,
      quality: values.quality,
      episodeNumber: values.episodeNumber,
    });
  }

  const wantsAudioProbe =
    values.probeAudio === true ||
    values.probeAudio === "true" ||
    values.preferAudioLang === true ||
    values.preferAudioLang === "true";
  const needsDurationProbe =
    !Number(stream.duration) &&
    !/\.(?:m3u8|mpd)(\?|$)/i.test(stream.url || "") &&
    !/video\/mp4/i.test(stream.mime || "");
  if (RELAY && !stream.isLive && relay.available() && (wantsAudioProbe || needsDurationProbe)) {
    try {
      if (wantsAudioProbe) {
        const probe = await relay.probeMedia(stream);
        if (Number(probe.duration) > 0 && !Number(stream.duration)) stream.duration = probe.duration;
        if (probe.audio && probe.audio.length) {
          stream.audioProbe = probe.audio;
          let chosen = -1;
          if (values.audioTrack !== undefined && values.audioTrack !== null && values.audioTrack !== "") {
            chosen = Number(values.audioTrack);
          }
          if (!(chosen >= 0) && (values.preferAudioLang === true || values.preferAudioLang === "true")) {
            chosen = preferredAudioIndex(probe.audio, values.audioLang);
          }
          if (!(chosen >= 0 && chosen < probe.audio.length)) chosen = 0;
          values.audioTrack = chosen;
          stream.audioIndex = chosen;
        }
      } else {
        const probed = await relay.probeDuration(stream);
        if (probed > 0) stream.duration = probed;
      }
    } catch (error) {
      log("no se pudo sondear la fuente:", error.message);
    }
  }

  const proxied = streamProxyPath(stream, {
    forceRelay:
      values.forceRelay === true ||
      values.forceRelay === "true" ||
      values.playbackMode === "smooth" ||
      values.playbackMode === "relay",
    audioTrack: values.audioTrack,
    copyVideo: values.copyVideo === true || values.copyVideo === "true",
    audioAac: values.audioAac === true || values.audioAac === "true",
    preferAudioLang: values.preferAudioLang === true || values.preferAudioLang === "true",
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

async function geeSports() {
  return api.geeSports();
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
      else {
        // Deja listo el mapa de columnas (FHD) antes del primer play.
        xtv.prewarmColumns();
        resolve({ userId: xtv.session.userId, ready: true });
      }
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
  geeSports,
  matchStats,
  resolveEmbed,
  markers,
  episodes,
  resolve,
  resolveLive,
  mediaInfo,
  external4k,
  prewarm,
  abortMedia,
  resetSession,
  clearCache,
  detail: detailXtv,
};
