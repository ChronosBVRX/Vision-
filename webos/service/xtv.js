"use strict";

/**
 * Cliente del portal XTV (tenant `masnew`) para el port de Vision+ a webOS.
 *
 * Es la misma familia de portal que usa la app Android, pero aquí NO se carga
 * el motor nativo: se pide `startPlayVOD` / `startPlayLive`, se toma la licencia
 * y la CDN libre de `getSlbInfo` y se construye la URL `/vod/..._media.ts` o
 * `/live/....m3u8`. El servicio local (`proxy.js`) pone las cabeceras
 * `Content-Auth` / `Content-License` que el CDN exige.
 *
 * La sesión se acuña con SN propio (snToken -> MD5(snToken + sal) -> active)
 * y se guarda en /tmp. No usa seeds compartidos ni patea la sesión del móvil.
 */

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const https = require("https");
const path = require("path");
const urlmod = require("url");
const api = require("./api");

const KEY_HEX = "dfb6bbe7d737fdf7366ffe35dddfdbe36e3fd1b6f47b5e74";
const KEY = Buffer.from(KEY_HEX, "hex");
const EMPTY_IV = Buffer.alloc(0);
const SESSION_FILE = process.env.VISION_WEBOS_SESSION || "/tmp/visionplus-webos-session.json";
const SESSION_TTL_MS = 6 * 60 * 60 * 1000;
const SNTOKEN_SALT = "ntFT65w6itH!lHCPw7D=@qnsFC5adD28";
const PORTAL_CODE = "masnew";
const PORTAL_PLAY = "masmobile";
const APP_ID = "com.msandroid.mobile";
const APK_VERSION = "60507";
const APK_VER_HEADER = "60507";
const SPKG_VER = "13";
const USER_AGENT = "okhttp/3.12.12";
const CONTENT_TYPE = "application/json;charset=utf-8";
const UA_CDN = "Ranger/4.9.4-17294ac0";
const REQUEST_TIMEOUT = 20000;
const RATE_MS = 260;

/**
 * Columnas del catálogo (`getNextColumns`). La licencia v10 solo firma en su
 * calidad real cuando se pide en la columna donde vive el título; sin columna
 * (v9) el portal entrega 480p. El mapa se cachea 12 h.
 */
const COLUMN_CODES = ["masmobile_free", "masmobile_movies", "masmobile_series", "masmobile_kids"];
const COLUMN_TTL_MS = 12 * 60 * 60 * 1000;
const COLUMN_RETRY_MS = 10 * 60 * 1000;
const columnCache = { map: {}, free: [], at: 0, loading: false, waiters: [] };

/** CDN elegido por título: evita sondear en cada play (10 min). */
const cdnChoiceCache = new Map();
const CDN_CHOICE_TTL_MS = 10 * 60 * 1000;

/** Canales de vivo ya resueltos (`liveAddressList` trae la licencia). */
const liveChannelCache = new Map();


const HOSTS = (process.env.VISION_XTV_HOSTS || "rhokqi.ryh1gifvn.com,zgmnxa.ryh1gifvn.com")
  .split(",")
  .map((h) => h.trim())
  .filter(Boolean);

const DEVICE_FIXED = {
  loginType: "2",
  appLanguage: "es",
  hardwareInfo: "ranchu",
  model: "sdk_gphone64_arm64",
  product: "sdk_gphone64_arm64",
  cpu: "arm64-v8a",
  B29: "",
  reserve1: "",
  deviceToken: "",
  drmId: "",
  sdkVer: 36,
};

const FINGERPRINT_FIXED = {
  board: "goldfish_arm64",
  brand: "google",
  cpuAbi: "arm64-v8a",
  device: "emu64a",
  diskInfo: "8GB",
  display: "sdk_gphone64_arm64",
  fingerprint: "google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.036/11228894:user/release-keys",
  hardware: "ranchu",
  host: "abfarm",
  manufacturer: "Google",
  ramSize: "4GB",
  romSize: "8GB",
  tags: "release-keys",
  verId: "",
};

const session = {
  sn: "",
  userId: "",
  userToken: "",
  snToken: "",
  ready: false,
  at: 0,
  busy: false,
};

let queue = [];
let lastCallAt = 0;
let hostIndex = 0;

function log(...args) {
  console.log("[xtv]", ...args);
}

function debug(...args) {
  if (process.env.VISION_DEBUG_PORTAL === "1") log(...args);
}

function encryptBody(value) {
  const cipher = crypto.createCipheriv("des-ede3", KEY, EMPTY_IV);
  const encrypted = Buffer.concat([cipher.update(Buffer.from(value, "utf8")), cipher.final()]);
  return Buffer.from(encrypted.toString("base64"), "ascii").toString("hex");
}

function decryptBlob(value) {
  const encoded = Buffer.from(value, "hex").toString("ascii");
  const decipher = crypto.createDecipheriv("des-ede3", KEY, EMPTY_IV);
  return Buffer.concat([
    decipher.update(Buffer.from(encoded, "base64")),
    decipher.final(),
  ]).toString("utf8");
}

function randomHex(bytes) {
  return crypto.randomBytes(bytes).toString("hex");
}

function randomMac() {
  return randomHex(6).replace(/(..)(?=.)/g, "$1:");
}

function text(value) {
  return value === null || value === undefined ? "" : String(value);
}

function trim(value) {
  return text(value).trim();
}

function deviceDict() {
  return Object.assign({}, DEVICE_FIXED, {
    apkVersion: APK_VERSION,
    sysVersion: SPKG_VER,
    appId: APP_ID,
    sn: session.sn,
  });
}

function loadSession() {
  try {
    const raw = fs.readFileSync(SESSION_FILE, "utf8");
    const saved = JSON.parse(raw);
    if (!saved || !saved.userToken || !saved.userId || !saved.sn) return false;
    if (Date.now() - Number(saved.at || 0) > SESSION_TTL_MS) return false;
    session.sn = trim(saved.sn);
    session.userId = trim(saved.userId);
    session.userToken = trim(saved.userToken);
    session.snToken = trim(saved.snToken);
    session.ready = true;
    session.at = Number(saved.at || 0);
    log("sesión guardada reutilizada", session.userId);
    return true;
  } catch {
    return false;
  }
}

function saveSession() {
  try {
    fs.writeFileSync(
      SESSION_FILE,
      JSON.stringify({
        sn: session.sn,
        userId: session.userId,
        userToken: session.userToken,
        snToken: session.snToken,
        at: session.at,
      }),
      { mode: 0o600 },
    );
  } catch (e) {
    log("no se pudo guardar la sesión:", e.message);
  }
}

function clearSession() {
  session.sn = "";
  session.userId = "";
  session.userToken = "";
  session.snToken = "";
  session.ready = false;
  session.at = 0;
  try {
    fs.unlinkSync(SESSION_FILE);
  } catch {
    /* noop */
  }
}

function post(pathname, body, callback) {
  let wire;
  try {
    wire = encryptBody(JSON.stringify(body));
  } catch (e) {
    return callback(new Error(`${pathname}: no se pudo cifrar el cuerpo: ${e.message}`));
  }

  let attempt = 0;
  const tryHost = () => {
    const host = HOSTS[(hostIndex + attempt) % HOSTS.length];
    const req = https.request(
      {
        hostname: host,
        servername: host,
        rejectUnauthorized: false,
        path: `/api/portalCore/${pathname}`,
        method: "POST",
        headers: {
          apk: APP_ID,
          apkVer: APK_VER_HEADER,
          spkgVer: SPKG_VER,
          "User-Agent": USER_AGENT,
          "Content-Type": CONTENT_TYPE,
          "Content-Length": Buffer.byteLength(wire),
        },
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          let payload;
          try {
            payload = JSON.parse(Buffer.concat(chunks).toString("utf8"));
          } catch {
            return retry(new Error(`${pathname}: respuesta no JSON (${res.statusCode})`));
          }
          const code = payload.returnCode === undefined || payload.returnCode === null ? "" : String(payload.returnCode);
          if (code !== "" && code !== "0") {
            const error = new Error(`${pathname} -> ${code} ${text(payload.errorMessage)}`);
            error.portalCode = code;
            return callback(error);
          }
          if (typeof payload.data === "string" && payload.data !== "") {
            try {
              return callback(null, JSON.parse(decryptBlob(payload.data)));
            } catch (e) {
              return callback(new Error(`${pathname}: respuesta cifrada ilegible (${e.message})`));
            }
          }
          callback(null, payload.data !== undefined && payload.data !== null ? payload.data : payload);
        });
      },
    );

    const retry = (error) => {
      attempt += 1;
      if (attempt < HOSTS.length) {
        hostIndex = (hostIndex + 1) % HOSTS.length;
        return tryHost();
      }
      callback(error);
    };

    req.setTimeout(REQUEST_TIMEOUT, () => req.destroy(new Error(`${pathname}: timeout`)));
    req.on("error", retry);
    req.write(wire);
    req.end();
  };

  tryHost();
}

function enqueue(task) {
  queue.push(task);
  drain();
}

function drain() {
  if (session.busy || !queue.length) return;
  const wait = lastCallAt + RATE_MS - Date.now();
  if (wait > 0) {
    session.busy = true;
    return setTimeout(() => {
      session.busy = false;
      drain();
    }, wait);
  }
  const task = queue.shift();
  session.busy = true;
  lastCallAt = Date.now();
  task(() => {
    session.busy = false;
    drain();
  });
}

/**
 * Una llamada de portal con los campos de sesión y dispositivo.
 * `opts.baseFields === false` se usa para snToken/active.
 */
function call(pathname, spec, opts, callback) {
  if (typeof opts === "function") {
    callback = opts;
    opts = {};
  }
  opts = opts || {};
  enqueue((done) => {
    const body = {};
    if (opts.baseFields !== false) {
      body.portalCode = PORTAL_CODE;
      body.userId = session.userId;
      body.userToken = session.userToken;
    }
    Object.assign(body, spec || {}, deviceDict());

    post(pathname, body, (error, payload) => {
      done();
      if (
        error &&
        (error.portalCode === "aaa100028" ||
          error.portalCode === "aaa100027" ||
          error.portalCode === "aaa100083" ||
          /未登录|sesi[oó]n|login/i.test(error.message || "")) &&
        !opts._sessionRetried
      ) {
        log("sesión rechazada; se acuña una nueva");
        clearSession();
        ensureSession((renewError) => {
          if (renewError) return callback(error);
          opts._sessionRetried = true;
          call(pathname, spec, opts, callback);
        });
        return;
      }
      callback(error, payload);
    });
  });
}

function deriveSn(snToken) {
  return crypto.createHash("md5").update(snToken + SNTOKEN_SALT).digest("hex");
}

/** Acumula filas de `getNextColumns` en el mapa contentId -> columnId. */
function indexColumns(payload, freeOnly) {
  const rows = (payload && payload.recommendList) || [];
  for (const row of rows) {
    const column = parseInt(row && row.columnId, 10) || 0;
    if (column <= 0) continue;
    if (freeOnly && columnCache.free.indexOf(column) < 0) columnCache.free.push(column);
    for (const asset of (row && row.assetList) || []) {
      const contentId = trim(asset && asset.contentId);
      if (contentId) columnCache.map[contentId] = column;
    }
  }
}

/**
 * Carga (una vez por TTL) las columnas de los catálogos `masmobile_*`.
 * Se llama al arrancar en segundo plano y, si aún no está, el primer play espera.
 */
function loadColumns(callback) {
  const cb = typeof callback === "function" ? callback : function () {};
  const hasMap = Object.keys(columnCache.map).length > 0;
  const age = Date.now() - columnCache.at;
  if (hasMap && age < COLUMN_TTL_MS) return cb(null);
  if (!hasMap && age < COLUMN_RETRY_MS) return cb(null);
  if (columnCache.loading) {
    columnCache.waiters.push(cb);
    return;
  }
  columnCache.loading = true;
  columnCache.waiters.push(cb);
  const finish = () => {
    columnCache.loading = false;
    columnCache.at = Date.now();
    const waiters = columnCache.waiters.splice(0);
    waiters.forEach((waiter) => {
      try {
        waiter(null);
      } catch (error) {
        /* noop */
      }
    });
  };
  ensureSession((sessionError) => {
    if (sessionError) return finish();
    let index = 0;
    const next = () => {
      if (index >= COLUMN_CODES.length) return finish();
      const code = COLUMN_CODES[index++];
      call(
        "getNextColumns",
        {
          portalCode: PORTAL_PLAY,
          pageNum: 1,
          pageSize: 100,
          columnCode: code,
          version: "",
        },
        (error, payload) => {
          if (!error && payload) indexColumns(payload, code === "masmobile_free");
          debug(
            "columnas",
            code,
            error ? `error ${error.message}` : `ok map=${Object.keys(columnCache.map).length} free=${columnCache.free.length}`,
          );
          next();
        },
      );
    };
    next();
  });
}

function columnFor(contentId, callback) {
  const cid = trim(contentId);
  const cached = columnCache.map[cid] || 0;
  if (cached > 0) return callback(cached);
  loadColumns(() => callback(columnCache.map[cid] || 0));
}

function prewarmColumns() {
  loadColumns(function () {});
}

/**
 * Broker FHD del VPS. La cuenta de cortesía vive en el servidor: la app recibe
 * las pistas ya firmadas y pide el SLB con su propia IP (mismo diseño que
 * Android). Si falla, el play sigue con la licencia local v10/v9.
 */
const BROKER_TTL_MS = 5 * 60 * 1000;
const BROKER_CACHE_MAX = 80;
const brokerCache = new Map();

function brokerTrack(media) {
  const contentId = trim(media && media.n);
  const license = trim(media && media.l);
  if (!contentId || !license) return null;
  return {
    contentId,
    quality: trim(media.q),
    encodeFormat: trim(media.e).toUpperCase(),
    videoFormat: trim(media.f).toLowerCase(),
    audioInfo: trim(media.a),
    licenseList: [{ license }],
    duration: "",
  };
}

function playBroker(contentId, seriesContentId, isSeries, episodeNumber, callback) {
  if (process.env.VISION_DISABLE_BROKER === "1") {
    return callback(new Error("broker desactivado"));
  }
  const key = `${contentId}|${isSeries ? seriesContentId : ""}|${isSeries ? episodeNumber : 0}`;
  const hit = brokerCache.get(key);
  if (hit && hit.exp > Date.now()) return callback(null, hit.value);
  api
    .playVip({
      contentId,
      seriesContentId: isSeries ? seriesContentId : contentId,
      columnId: 0,
      type: "0",
      episodeNumberList: isSeries ? [episodeNumber] : [],
    })
    .then((payload) => {
      const tracks = ((payload && payload.medias) || []).map(brokerTrack).filter(Boolean);
      if (!tracks.length) throw new Error("el broker no devolvió pistas");
      const value = { play: payload, episode: null, tracks, vip: true };
      const now = Date.now();
      const expires = Number(payload && payload.expiresAt) * 1000;
      const exp = Math.max(now + 60_000, Math.min(expires || 0, now + BROKER_TTL_MS));
      brokerCache.set(key, { exp, value });
      if (brokerCache.size > BROKER_CACHE_MAX) {
        brokerCache.delete(brokerCache.keys().next().value);
      }
      callback(null, value);
    })
    .catch((error) => callback(error));
}


function mintSession(callback) {
  const fingerprint = Object.assign({}, FINGERPRINT_FIXED, {
    androidId: randomHex(8),
    cpuId: randomHex(8),
    serialNumber: randomHex(8),
    etheMac: randomMac(),
    gatewayMac: randomMac(),
    wifiMac: randomMac(),
  });

  log("acuñando sesión propia…");
  call(
    "v3/snToken",
    fingerprint,
    { baseFields: false },
    (tokenError, tokenPayload) => {
      if (tokenError) return callback(tokenError);
      const snToken = trim(tokenPayload && tokenPayload.snToken);
      if (!snToken) return callback(new Error("el portal no devolvió snToken"));
      const sn = deriveSn(snToken);
      session.sn = sn;
      session.snToken = snToken;

      call(
        "v8/active",
        {
          snToken,
          authVersion: "",
          authCode: "",
          preCode: "",
          macAddr: "02:00:00:00:00:00",
          reserve1: "",
          openNum: 4,
          channel: "default",
          matadata: "",
          signdata: "",
        },
        { baseFields: false },
        (activeError, activePayload) => {
          if (activeError) return callback(activeError);
          const userId = trim(activePayload && activePayload.userId);
          const userToken = trim(activePayload && activePayload.userToken);
          if (!userId || !userToken) return callback(new Error("el portal no activó el dispositivo"));
          session.userId = userId;
          session.userToken = userToken;
          session.ready = true;
          session.at = Date.now();
          saveSession();
          log("sesión lista", userId, `sn=${sn.slice(0, 12)}…`);
          callback(null, session);
        },
      );
    },
  );
}

function ensureSession(callback) {
  if (session.ready && session.userId && session.userToken) return callback(null, session);
  if (loadSession()) return callback(null, session);
  mintSession(callback);
}

function itemFromAsset(asset, category, kind) {
  if (!asset || !trim(asset.contentId)) return null;
  const programType = trim(asset.programType) || "movie";
  const isSeries =
    programType === "teleplay" ||
    programType === "series" ||
    (asset.volumnCount && parseInt(asset.volumnCount, 10) > 1);
  const posterList = asset.posterList || [];
  const imageOf = (type) => {
    for (const item of posterList) {
      if (item && item.fileType === type && trim(item.fileUrl)) return trim(item.fileUrl);
    }
    return "";
  };
  return {
    id: trim(asset.contentId),
    title: text(asset.name || asset.title),
    category: category || "",
    kind: kind || (isSeries ? "serie" : "película"),
    score: asset.score === undefined || asset.score === null || asset.score === "" ? "—" : text(asset.score),
    year: trim(asset.releaseTime || asset.releaseDate).slice(0, 4) || "—",
    duration: text(asset.duration),
    genres: trim(asset.tags) ? trim(asset.tags).split(",").map((x) => x.trim()).filter(Boolean) : [],
    director: text(asset.director),
    actors: text(asset.actorDisplay || asset.actors),
    synopsis: text(asset.description || asset.synopsis),
    poster: imageOf("icon") || imageOf("poster"),
    backdrop: imageOf("stage") || imageOf("poster"),
    type: isSeries ? "teleplay" : programType,
    alias: text(asset.alias),
    country: trim(asset.originalCountry || asset.country),
    releaseDate: trim(asset.releaseTime || asset.releaseDate),
    volumeCount: parseInt(asset.volumnCount || asset.volumeCount, 10) || 0,
    updateCount: parseInt(asset.updateCount, 10) || 0,
    moreAudio: asset.moreAudio || 0,
    moreSubtitle: asset.moreSubtitle || 0,
  };
}

function durationSeconds(value) {
  const raw = trim(value);
  if (!raw) return 0;
  if (/^\d{1,2}:\d{2}:\d{2}$/.test(raw)) {
    const [h, m, s] = raw.split(":").map((x) => parseInt(x, 10) || 0);
    return h * 3600 + m * 60 + s;
  }
  if (/^\d{1,2}:\d{2}$/.test(raw)) {
    const [m, s] = raw.split(":").map((x) => parseInt(x, 10) || 0);
    return m * 60 + s;
  }
  const number = parseFloat((raw.match(/(\d+(?:\.\d+)?)/) || [])[1] || "0");
  return number > 100000 ? number / 1000 : number;
}

function fieldNumber(value) {
  const match = text(value).match(/(\d+(?:\.\d+)?)/);
  return match ? parseFloat(match[1]) : 0;
}

function trackHeight(track) {
  const direct = fieldNumber(track && (track.videoHeight || track.height || track.pixelHeight));
  if (direct) return direct;
  const label = text(track && (track.resolution || track.videoResolution || track.quality)).toLowerCase();
  const pair = label.match(/\d{3,4}\s*[x×]\s*(\d{3,4})/);
  if (pair) return parseInt(pair[1], 10);
  if (/\b(4k|uhd|2160)\b/.test(label)) return 2160;
  if (/\b1440\b/.test(label)) return 1440;
  if (/\b1080\b|\bfhd\b/.test(label)) return 1080;
  if (/\b720\b|\bhd\b/.test(label)) return 720;
  if (/\b480\b|\bsd\b/.test(label)) return 480;
  return 0;
}

function trackSummary(track) {
  const codec = trim(track && track.encodeFormat).toUpperCase();
  const format = trim(track && track.videoFormat).toLowerCase();
  return {
    codec,
    format,
    quality: trim(track && track.quality),
    height: trackHeight(track),
    bitrateKbps: fieldNumber(track && (track.videoBitrate || track.bitrate || track.rate)),
    frameRate: fieldNumber(track && (track.frameRate || track.fps || track.videoFps)),
  };
}

function trackScore(track, preferredQuality) {
  const summary = trackSummary(track);
  const codec = summary.codec.replace(/[^a-z0-9]/g, "").toLowerCase();
  let score = 0;
  if (codec.includes("h264") || codec.includes("avc")) score += 0;
  else if (codec.includes("h265") || codec.includes("hevc")) score += 40;
  else score += 160;
  if (summary.format === "mp4") score += 0;
  else score += 8;
  if (/10bit|hdr|av1|vp9/.test(codec)) score += 400;
  if (summary.height > 1080) score += 500 + summary.height;
  else if (summary.height > 0) score -= summary.height;
  if (summary.frameRate > 31) score += 60;
  if (summary.bitrateKbps > 9000) score += 120;
  if (preferredQuality && preferredQuality !== "auto") {
    const wanted = parseInt(String(preferredQuality).replace(/\D/g, ""), 10) || 0;
    if (wanted && summary.height) score += Math.abs(summary.height - wanted) * 2;
  }
  return score;
}

function collectTracks(episode) {
  const tracks = [];
  for (const group of (episode && episode.totalMovieList) || []) {
    for (const track of (group && group.movieList) || []) {
      if (track && trim(track.contentId)) tracks.push(track);
    }
  }
  return tracks;
}

function subtitleList(episode) {
  const list = [];
  for (const item of (episode && episode.subtitleList) || []) {
    const file = (item && item.file && item.file[0]) || null;
    if (file && trim(file.url)) {
      list.push({
        lang: trim(item.language),
        url: trim(file.url),
        format: trim(file.fileType) || "srt",
      });
    }
    if (list.length >= 30) break;
  }
  return list;
}

/**
 * VOD: se piden en paralelo la licencia local v9 (metadatos, subtítulos y
 * respaldo 480p) y el broker FHD del VPS (1080p/720p ya firmados). Si el broker
 * responde, mandan sus pistas y del v9 local se toman capítulo/subtítulos.
 * En series el portal elige el capítulo por `episodeNumberList`.
 */
function loadPlay(contentId, seriesContentId, options, callback) {
  if (typeof options === "function") {
    callback = options;
    options = {};
  }
  options = options || {};
  if (!trim(contentId)) return callback(new Error("falta contentId"));
  ensureSession((sessionError) => {
    if (sessionError) return callback(sessionError);
    const cid = trim(contentId);
    const sid = trim(seriesContentId);
    const isSeries = sid !== "" && sid !== cid;
    const episodeNumber = Math.max(1, parseInt(options.episodeNumber, 10) || 1);
    const makeBody = (type, column) => {
      const body = {
        portalCode: PORTAL_PLAY,
        contentId: cid,
        seriesContentId: isSeries ? sid : cid,
        columnId: column || 0,
        type,
        startTime: 0,
        authType: isSeries ? "1" : "",
      };
      if (isSeries) body.episodeNumberList = [episodeNumber];
      return body;
    };

    let localDone = false;
    let brokerDone = false;
    let localLoaded = null;
    let brokerLoaded = null;
    let replied = false;
    let waitTimer = null;

    const stopWait = () => {
      if (waitTimer) {
        clearTimeout(waitTimer);
        waitTimer = null;
      }
    };
    const finish = (value) => {
      if (replied) return;
      replied = true;
      stopWait();
      callback(null, value);
    };
    const fail = (message) => {
      if (replied) return;
      replied = true;
      stopWait();
      callback(new Error(message));
    };
    const mergeBroker = () => {
      if (localLoaded) {
        brokerLoaded.episode = localLoaded.episode;
        brokerLoaded.play = Object.assign({}, brokerLoaded.play, {
          name: (localLoaded.play && localLoaded.play.name) || "",
        });
      }
      finish(brokerLoaded);
    };

    const fallbackV10 = () => {
      columnFor(cid, (mapped) => {
        const columns = [];
        if (mapped > 0) columns.push(mapped);
        columnCache.free.slice(0, 4).forEach((column) => {
          if (columns.indexOf(column) < 0) columns.push(column);
        });
        if (!columns.length) {
          return fail("el portal no devolvió pistas de vídeo");
        }
        const tryColumn = (index) => {
          if (index >= columns.length) {
            return fail("el portal no devolvió pistas de vídeo");
          }
          const column = columns[index];
          call("v10/startPlayVOD", makeBody("0", column), (error, payload) => {
            const episode = !error && payload && payload.episodeList && payload.episodeList[0];
            const tracks = collectTracks(episode);
            debug(
              "play v10",
              `columna=${column}`,
              error ? `error ${error.message}` : `pistas=${tracks.length}`,
            );
            if (!tracks.length) return tryColumn(index + 1);
            finish({ play: payload, episode, tracks });
          });
        };
        tryColumn(0);
      });
    };

    const reply = () => {
      if (replied) return;
      if (brokerLoaded) {
        if (localDone) mergeBroker();
        return;
      }
      if (localDone && brokerDone) {
        if (localLoaded) return finish(localLoaded);
        fallbackV10();
      }
    };

    call("v9/startPlayVOD", makeBody("2", 0), (error, payload) => {
      localDone = true;
      const episode = !error && payload && payload.episodeList && payload.episodeList[0];
      const tracks = collectTracks(episode);
      if (tracks.length) localLoaded = { play: payload, episode, tracks };
      debug("play v9", error ? `error ${error.message}` : `pistas=${tracks.length}`);
      if (localLoaded && !brokerDone && !replied) {
        // Si el broker tarda, no se deja al usuario esperando: a los 4 s se
        // reproduce el respaldo local (480p) con sus subtítulos.
        stopWait();
        waitTimer = setTimeout(() => {
          if (!replied) finish(localLoaded);
        }, 4000);
      }
      reply();
    });

    playBroker(cid, sid, isSeries, episodeNumber, (error, value) => {
      brokerDone = true;
      if (!error && value && value.tracks && value.tracks.length) brokerLoaded = value;
      debug(
        "broker",
        error ? `error ${error.message}` : `pistas=${(value && value.tracks.length) || 0}`,
      );
      if (brokerLoaded && !localDone && !replied) {
        // Metadatos/subtítulos del v9: se esperan 1.5 s como máximo.
        stopWait();
        waitTimer = setTimeout(() => {
          if (!replied) {
            localDone = true;
            mergeBroker();
          }
        }, 1500);
      }
      reply();
    });
  });
}

function slbBean() {
  return {
    hasPay: "0",
    userIdentity: "1",
    type: "merge",
    appVer: APK_VERSION,
    lang: "es",
    encMediaSupported: 1,
    liveCodeList: ["masnew_live"],
    appParams: "",
    // Valor fijo que usa la familia Hydra; con el MAC el portal devolvía
    // balanceadores que el CDN rechazaba (403).
    reserve1: "583442714b345572434a4d5769637461545a735936413d3d",
    pipFlag: "0",
  };
}

function withScheme(value) {
  const clean = trim(value).replace(/\/+$/, "");
  return /^https?:\/\//i.test(clean) ? clean : `https://${clean}`;
}

function hasCfl(item) {
  const raw = trim(item && item.url).toLowerCase();
  return trim(item && item.sign_type).toLowerCase() === "cfl" || raw.includes("sign_type=cfl");
}

function cdnSignRank(item) {
  const raw = trim(item && item.url).toLowerCase();
  const match = raw.match(/sign_type=([a-z0-9]+)/);
  const sign = (trim(item && item.sign_type) || (match && match[1]) || "").toLowerCase();
  if (sign === "cfl") return 0;
  // Los balanceadores `goog` responden bien al VOD; los `cs` (xauf/schh)
  // devuelven 403 desde la migración de CDN. Se dejan como último recurso.
  if (sign === "goog") return 1;
  if (sign === "cs") return 2;
  return 3;
}

function vodCdns(slb) {
  const result = [];
  for (const cdn of (slb && slb.cdn_list) || []) {
    if (!cdn || trim(cdn.tag) !== "vod") continue;
    for (const url of cdn.url_list || []) {
      // El portal ya no publica siempre `sign_type=cfl`: ahora también firma
      // con `cs` y `goog`. Antes el filtro CFL dejaba el VOD sin CDN.
      if (!url || trim(url.tag) !== "free" || !trim(url.url)) continue;
      const bases = [withScheme(cdn.main_addr)];
      const spare = trim(cdn.spared_addr);
      if (spare && !bases.includes(withScheme(spare))) bases.push(withScheme(spare));
      result.push({ bases, auth: trim(url.url), rank: cdnSignRank(url) });
      break;
    }
  }
  // CFL/IPTV primero, luego CS y GOOG; todas entregan el mismo archivo.
  result.sort((left, right) => left.rank - right.rank);
  return result;
}

/** Prueba un CDN con Range 0-0 antes de entregarlo al reproductor. */
function probeCdnUrl(candidate, callback) {
  let done = false;
  const finish = (ok) => {
    if (done) return;
    done = true;
    callback(ok);
  };
  let parsed;
  try {
    parsed = urlmod.parse(candidate.url);
  } catch (error) {
    return finish(false);
  }
  const transport = parsed.protocol === "http:" ? http : https;
  const req = transport.request(
    {
      hostname: parsed.hostname,
      port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
      path: parsed.path,
      method: "GET",
      headers: Object.assign({}, candidate.headers, { Range: "bytes=0-0" }),
      timeout: 4000,
    },
    (res) => {
      res.resume();
      finish(res.statusCode === 200 || res.statusCode === 206);
    },
  );
  req.on("timeout", () => {
    req.destroy();
    finish(false);
  });
  req.on("error", () => finish(false));
  req.end();
}

/** Devuelve el primer candidato que responda; si ninguno, el primero. */
function pickCdn(candidates, callback) {
  if (!candidates.length) return callback(null);
  const results = new Array(candidates.length).fill(false);
  let pending = candidates.length;
  candidates.forEach((candidate, index) => {
    probeCdnUrl(candidate, (ok) => {
      results[index] = ok;
      pending -= 1;
      if (pending > 0) return;
      const chosen = candidates[results.indexOf(true)] || candidates[0];
      callback(chosen);
    });
  });
}

function liveCdns(slb) {
  const result = [];
  for (const cdn of (slb && slb.cdn_list) || []) {
    if (!cdn || trim(cdn.tag) !== "live") continue;
    for (const url of cdn.url_list || []) {
      if (!url || !hasCfl(url)) continue;
      const host = bareHost(cdn.main_addr);
      if (host) result.push({ host, authBase: trim(url.url) });
      break;
    }
  }
  return result;
}

function bareHost(value) {
  let host = trim(value);
  if (host.startsWith("https://")) host = host.slice(8);
  if (host.startsWith("http://")) host = host.slice(7);
  const slash = host.indexOf("/");
  return slash >= 0 ? host.slice(0, slash) : host;
}

function signLive(token, startMoment) {
  const salt = Buffer.from([
    115, 97, 108, 116, 51, 51, 51, 51, 61, 52, 152, 13, 10, 21, 50, 201, 195, 130,
    23, 8, 192,
  ]);
  const message = `token=${token}&sign2_method=sign_o3&instance=0&start_moment=${startMoment}`;
  return crypto.createHash("md5").update(Buffer.concat([Buffer.from(message, "utf8"), salt])).digest("hex");
}

function resolve(contentId, seriesContentId, options, callback) {
  if (typeof options === "function") {
    callback = options;
    options = {};
  }
  options = options || {};
  loadPlay(contentId, seriesContentId, options, (error, loaded) => {
    if (error) return callback(error);
    const ranked = loaded.tracks
      .map((track) => ({ track, score: trackScore(track, options.quality) }))
      .sort((left, right) => {
        const leftHeight = trackHeight(left.track);
        const rightHeight = trackHeight(right.track);
        if (leftHeight !== rightHeight) return rightHeight - leftHeight;
        return left.score - right.score;
      })
      .map((entry) => entry.track);
    // El broker devuelve la misma calidad en H.264 y H.265: el reproductor
    // solo necesita una pista por resolución (la primera, ya ordenada).
    const tracks = [];
    const seenQuality = {};
    for (const candidate of ranked) {
      const key = trim(candidate.quality).toLowerCase() || trim(candidate.contentId);
      if (seenQuality[key]) continue;
      seenQuality[key] = true;
      tracks.push(candidate);
    }
    let index = parseInt(options.trackIndex, 10);
    if (!(index >= 0 && index < tracks.length)) index = 0;
    const track = tracks[index];
    const license = trim(
      (track.licenseList && track.licenseList[0] && track.licenseList[0].license) || "",
    );
    if (!license) return callback(new Error("el portal no devolvió licencia de reproducción"));

    const bean = slbBean();
    // La licencia del broker es de la cuenta de cortesía: el SLB debe pedirse
    // en el tenant de reproducción (`masmobile`), como hace Android.
    if (loaded.vip) bean.portalCode = PORTAL_PLAY;
    const useSlb = (slb, version) => {
      debug(
        `slb vod ${version}`,
        JSON.stringify(
          ((slb && slb.cdn_list) || [])
            .filter((cdn) => trim(cdn.tag) === "vod")
            .map((cdn) => ({
              host: cdn.main_addr,
              spare: cdn.spared_addr,
              urls: (cdn.url_list || [])
                .filter((u) => trim(u.tag) === "free")
                .map((u) => u.url),
            })),
        ),
      );
      const cdns = vodCdns(slb);
      if (!cdns.length) return false;
      const format = trim(track.videoFormat).toLowerCase() === "ts" ? "ts" : "mp4";
      const mediaId = trim(track.contentId);
      const cacheKey = `${mediaId}|${format}`;
      const headersFor = (auth) => ({
        "Content-Auth": auth,
        "Content-License": license,
        "User-Agent": UA_CDN,
        App: APP_ID,
        "App-Version": APK_VERSION,
        Accept: "*/*",
      });
      const finish = (chosen) => {
        callback(null, {
          url: chosen.url,
          mime: format === "ts" ? "video/mp2t" : "video/mp4",
          codec: trim(track.encodeFormat).toUpperCase(),
          quality: trim(track.quality),
          duration: durationSeconds(
            track.duration ||
              (loaded.episode && loaded.episode.duration) ||
              (loaded.play && loaded.play.duration),
          ),
          selectedTrack: trackSummary(track),
          availableTracks: tracks.map(trackSummary),
          trackIndex: index,
          audio: trim(track.audioInfo),
          headers: chosen.headers,
          subtitles: subtitleList(loaded.episode),
          name: text(loaded.play && loaded.play.name),
        });
      };
      const cachedChoice = cdnChoiceCache.get(cacheKey);
      if (cachedChoice && cachedChoice.exp > Date.now()) {
        finish(cachedChoice.value);
        return true;
      }
      const candidates = [];
      for (const cdn of cdns) {
        for (const base of cdn.bases) {
          candidates.push({
            url: `${base}/vod/${mediaId}_media.${format}`,
            headers: headersFor(cdn.auth),
          });
        }
      }
      // Algunos balanceadores `cs` devuelven 403 y otros cierran la conexión:
      // se prueba el primero que entregue bytes antes de abrir el reproductor.
      pickCdn(candidates.slice(0, 4), (chosen) => {
        const value = chosen || candidates[0];
        if (value) {
          cdnChoiceCache.set(cacheKey, { exp: Date.now() + CDN_CHOICE_TTL_MS, value });
        }
        finish(value);
      });
      return true;
    };
    const tryVersion = (versions, position) => {
      if (position >= versions.length) {
        return callback(new Error("el portal no expuso CDN de vod libre"));
      }
      const version = versions[position];
      call(`${version}/getSlbInfo`, bean, (slbError, slb) => {
        if (slbError || !useSlb(slb, version)) {
          tryVersion(versions, position + 1);
        }
      });
    };
    // v15 es el balanceador vigente; v14 queda como respaldo.
    tryVersion(["v15", "v14"], 0);
  });
}

function mediaInfo(contentId, seriesContentId, options, callback) {
  if (typeof options === "function") {
    callback = options;
    options = {};
  }
  loadPlay(contentId, seriesContentId, options || {}, (error, loaded) => {
    if (error) return callback(error);
    const track = loaded.tracks[0];
    callback(null, {
      name: text(loaded.play && loaded.play.name),
      audio: trim(track && track.audioInfo),
      subtitles: subtitleList(loaded.episode),
      tracks: loaded.tracks.map(trackSummary),
      selected: trackSummary(track),
      duration: durationSeconds(
        (track && track.duration) ||
          (loaded.episode && loaded.episode.duration) ||
          (loaded.play && loaded.play.duration),
      ),
    });
  });
}

function liveCatalog(callback) {
  ensureSession((sessionError) => {
    if (sessionError) return callback(sessionError);
    call(
      "v6/getLiveData",
      {
        columnId: 76182,
        dataVersion: "",
        pageNum: 1,
        pageSize: 3000,
        expireTimeStr: "",
      },
      (error, payload) => {
        if (error) return callback(error);
        const list = (payload && payload.channelList) || [];
        const channels = [];
        for (const channel of list) {
          const code = trim(channel && channel.channelCode);
          if (!code) continue;
          let logo = "";
          for (const poster of (channel.posterList || [])) {
            if (poster && trim(poster.fileType) === "icon" && trim(poster.fileUrl)) {
              logo = trim(poster.fileUrl);
              break;
            }
          }
          let address = null;
          for (const item of (channel.liveAddressList || [])) {
            if (!item || trim(item.tag) !== "free" || !trim(item.playCode)) continue;
            if (
              !address ||
              (parseInt(item.quality, 10) || 0) >= (parseInt(address.quality, 10) || 0)
            ) {
              address = item;
            }
          }
          const entry = {
            code,
            number: parseInt(channel.channelNumber, 10) || 0,
            name: text(channel.name),
            logo,
            group: text(channel.columnName || channel.groupName || channel.categoryName || ""),
            address: address
              ? {
                  playCode: trim(address.playCode),
                  license: trim(address.license),
                  quality: trim(address.quality),
                }
              : null,
          };
          liveChannelCache.set(code, entry);
          // La licencia se queda en el servicio; la UI solo recibe el canal.
          channels.push({
            code: entry.code,
            number: entry.number,
            name: entry.name,
            logo: entry.logo,
            group: entry.group,
          });
        }
        callback(null, { channels, total: channels.length });
      },
    );
  });
}

/** Construye el HLS de un canal con la licencia que viene en `getLiveData`. */
function liveStreamFor(code, address, name, callback) {
  const playCode = trim(address && address.playCode) || code;
  const license = trim(address && address.license);
  if (!license) return callback(new Error("el portal no devolvió licencia para el canal"));
  const versions = ["v15", "v14"];
  const lists = [[code], ["masnew_live"], ["masmobile_live"]];
  const tryPlan = (versionIndex, listIndex) => {
    if (versionIndex >= versions.length) {
      return callback(new Error("el portal no expuso CDN de vivo"));
    }
    if (listIndex >= lists.length) {
      return tryPlan(versionIndex + 1, 0);
    }
    const bean = slbBean();
    bean.liveCodeList = lists[listIndex];
    call(`${versions[versionIndex]}/getSlbInfo`, bean, (error, slb) => {
      if (error) return tryPlan(versionIndex, listIndex + 1);
      const cdns = liveCdns(slb);
      if (!cdns.length) return tryPlan(versionIndex, listIndex + 1);
      const cdn = cdns[0];
      const tokenMatch = cdn.authBase.match(/token=([0-9a-fA-F]{32})/);
      const token = tokenMatch ? tokenMatch[1] : "";
      const startMoment = Date.now();
      const sign = signLive(token, startMoment);
      callback(null, {
        url: `http://${cdn.host}/live/${playCode}.m3u8`,
        mime: "application/x-mpegurl",
        headers: {
          "Content-Auth": `${cdn.authBase}&sign2_method=sign_o3&instance=0&start_moment=${startMoment}&sign2=${sign}`,
          "Content-License": license,
          "User-Agent": UA_CDN,
          App: APP_ID,
          "App-Version": APK_VERSION,
          "X-Buffer": "0",
          Accept: "*/*",
        },
        name: text(name),
        isLive: true,
      });
    });
  };
  tryPlan(0, 0);
}

function resolveLive(channelCode, callback) {
  if (!trim(channelCode)) return callback(new Error("falta channelCode"));
  ensureSession((sessionError) => {
    if (sessionError) return callback(sessionError);
    const code = trim(channelCode);
    const cached = liveChannelCache.get(code);
    if (cached && cached.address && cached.address.license) {
      return liveStreamFor(code, cached.address, cached.name, callback);
    }
    // `getLiveData` ya entrega `liveAddressList` con licencia por canal;
    // `startPlayLive` fue retirado por el portal (`portal100001`).
    liveCatalog((catalogError) => {
      const found = !catalogError ? liveChannelCache.get(code) : null;
      if (found && found.address && found.address.license) {
        return liveStreamFor(code, found.address, found.name, callback);
      }
      call(
        "v4/startPlayLive",
        { channelCode: code, columnId: 0, type: "1" },
        (error, payload) => {
          if (error) return callback(error);
          const addresses = (payload && payload.liveAddressList) || [];
          let address = null;
          for (const item of addresses) {
            if (item && item.playCode && item.license) {
              address = item;
              break;
            }
          }
          if (!address) address = addresses[0];
          liveStreamFor(code, address, payload && payload.name, callback);
        },
      );
    });
  });
}

function detail(contentId, callback) {
  ensureSession((sessionError) => {
    if (sessionError) return callback(sessionError);
    call(
      "v4/getItemData",
      {
        contentId: trim(contentId),
        // type "0"/en es el que usa el port de XuperTV y el que responde para
        // IDs de serie; el espejo del VPS sigue siendo la primera opción.
        type: "0",
        sortType: "0",
        language: "en",
        macAddr: "02:00:00:00:00:00",
      },
      (error, payload) => {
        if (error) return callback(error);
        const asset = payload && payload.assetData;
        if (!asset) return callback(new Error("el portal no devolvió datos del título"));
        const item = itemFromAsset(asset, "", null);
        const episodes = [];
        for (let index = 0; index < (asset.simpleProgramList || []).length; index += 1) {
          const episode = asset.simpleProgramList[index];
          if (!episode) continue;
          episodes.push({
            id: trim(episode.contentId),
            title: text(episode.name),
            name: text(episode.name),
            number: episode.seriesNumber === undefined || episode.seriesNumber === null ? index + 1 : episode.seriesNumber,
            duration: text(episode.duration),
            quality: text(episode.quality),
            picture: text(episode.picture || episode.pic || episode.image || episode.cover || episode.posterUrl || episode.poster),
            description: text(episode.description || episode.desc || episode.synopsis || episode.shortDescription || episode.introduce),
          });
        }
        const seasons = [];
        for (const season of asset.sameSeasonSeriesList || []) {
          if (season && trim(season.contentId)) {
            seasons.push({ id: trim(season.contentId), number: season.seasonNumber });
          }
        }
        callback(null, { item, episodes, seasons });
      },
    );
  });
}

module.exports = {
  resolve,
  resolveLive,
  liveCatalog,
  mediaInfo,
  detail,
  ensureSession,
  clearSession,
  session,
  prewarmColumns,
  constants: {
    HOSTS,
    PORTAL_CODE,
    APP_ID,
    APK_VERSION,
    UA_CDN,
  },
};
