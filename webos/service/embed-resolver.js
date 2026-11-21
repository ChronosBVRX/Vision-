"use strict";

/**
 * Resuelve los enlaces `token_api` de la agenda deportiva.
 *
 * La fuente no publica la URL del canal: entrega una página/endpoint que arranca
 * su reproductor y recién ahí escribe la dirección del HLS/DASH. El resolver
 * pide esa página con las mismas cabeceras del reproductor y extrae la primera
 * URL de media. Es el equivalente en Node del WebView oculto de la app Android.
 */

const http = require("http");
const https = require("https");
const urlmod = require("url");

const TTL_MS = 5 * 60 * 1000;
const cache = new Map();

// La fuente entrega páginas pensadas para el reproductor móvil. Un UA de
// escritorio hace que algunos proveedores devuelvan una página vacía o bloqueada.
const UA =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
  "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

function parseEmbed(tokenApi) {
  try {
    const data = JSON.parse(String(tokenApi || ""));
    if (!data || typeof data !== "object") return null;
    const api = String(data.api || data.url || "").trim();
    if (!/^https?:\/\//i.test(api)) return null;
    return {
      api,
      type: String(data.type || "embed").trim().toLowerCase(),
      linkKey: String(data.link_key || "playback_url").trim(),
    };
  } catch (error) {
    if (/^https?:\/\//i.test(String(tokenApi || "").trim())) {
      return { api: String(tokenApi).trim(), type: "embed", linkKey: "playback_url" };
    }
    return null;
  }
}

function headersFor(api) {
  let origin = "";
  try {
    const parsed = urlmod.parse(api);
    origin = `${parsed.protocol}//${parsed.host}`;
  } catch (error) {
    origin = "";
  }
  return {
    "user-agent": UA,
    referer: api,
    origin,
  };
}

function cleanHeaders(input) {
  const out = {};
  Object.keys(input || {}).forEach((key) => {
    const lower = String(key || "").toLowerCase();
    if (!lower) return;
    if (["host", "content-length", "connection", "accept-encoding", "range", "if-range"].includes(lower)) return;
    const value = input[key];
    if (value === undefined || value === null) return;
    const text = String(value).trim();
    if (text) out[lower] = text;
  });
  return out;
}

function mergeHeaders(base, extra) {
  return Object.assign({}, base || {}, cleanHeaders(extra));
}

function requestText(target, headers, timeout, redirectsLeft) {
  return new Promise((resolve, reject) => {
    let parsed;
    try {
      parsed = urlmod.parse(target);
    } catch (error) {
      reject(error);
      return;
    }
    if (!/^https?:$/.test(parsed.protocol || "")) {
      reject(new Error("dirección embed no válida"));
      return;
    }
    const transport = parsed.protocol === "http:" ? http : https;
    const request = transport.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        method: "GET",
        headers,
        rejectUnauthorized: false,
      },
      (response) => {
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location && redirectsLeft > 0) {
          response.resume();
          const next = urlmod.resolve(target, response.headers.location);
          resolve(requestText(next, headers, timeout, redirectsLeft - 1));
          return;
        }
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          resolve({
            status: response.statusCode,
            url: target,
            body: Buffer.concat(chunks).toString("utf8"),
            contentType: String(response.headers["content-type"] || ""),
          });
        });
      },
    );
    request.setTimeout(timeout || 15000, () => request.destroy(new Error("embed timeout")));
    request.on("error", reject);
    request.end();
  });
}

function isMediaUrl(raw) {
  return /\.m3u8(\?|$)|(?:\?|&)[^=]+=m3u8|\/m3u8(?:\?|$)|(?:\?|&)format=m3u8|\.mpd(\?|$)|\/manifest\.mpd/i.test(String(raw || ""));
}

/**
 * Descarta manifiestos de anuncios, intros y pre-rolls. Muchos portales
 * publican primero el anuncio y después la señal; devolver el primero hace que
 * el reproductor reproduzca un audio corto que parece un bucle.
 */
function isAdLikeUrl(raw) {
  const value = String(raw || "").toLowerCase();
  if (!value) return false;
  if (/(?:^|[/._-])(?:ads?|advert|adserver|advert(?:ising)?|preroll|pre-?roll|midroll|bumper|intro|promo|trailer|preview|sponsor)(?:[/._-]|$)/.test(value)) {
    return true;
  }
  return /(?:selpath|d3\.php|ad_type|adtype|vast|vmap|doubleclick|googlesyndication)/.test(value);
}

function normalizeMediaUrl(raw, base) {
  let value = String(raw || "").trim();
  if (!value) return "";
  value = value
    .replace(/\\\//g, "/")
    .replace(/&amp;/g, "&")
    .replace(/\\u0026/g, "&")
    .replace(/^['"]|['"]$/g, "");
  if (!value) return "";
  if (/%3A|%2F|%3F|%26/i.test(value)) {
    try {
      const decoded = decodeURIComponent(value);
      if (decoded && decoded !== value) value = decoded;
    } catch (error) {
      /* se conserva el valor original */
    }
  }
  if (value.startsWith("//")) value = `https:${value}`;
  if (!/^https?:\/\//i.test(value)) {
    try {
      value = urlmod.resolve(base || "", value);
    } catch (error) {
      return "";
    }
  }
  // Solo manifiestos HLS/DASH. Un .ts/.mp4 suelto suele ser un aviso, un
  // intro o un segmento: reproducirlo parece "un bucle raro" de dos segundos.
  if (!isMediaUrl(value) || isAdLikeUrl(value)) return "";
  return value;
}

function findMedia(body, base) {
  const text = String(body || "");
  const patterns = [
    /["'](?:playbackURL|playback_url|playbackUrl|file|source|src|url)["']\s*[:=]\s*["']([^"']+)["']/gi,
    /(?:playbackURL|playback_url|playbackUrl|file|source|src)\s*[:=]\s*["']([^"']+)["']/gi,
    /https?:\\?\/\\?\/[^\s"'<>\\]+?\.(?:m3u8|mpd)(?:\?[^\s"'<>\\]*)?/gi,
  ];
  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(text))) {
      const candidate = normalizeMediaUrl(match[1] || match[0], base);
      if (candidate) return candidate;
    }
  }
  // La página puede traer la URL en JSON escapado o como parámetro de otra página.
  try {
    const json = JSON.parse(text);
    const queue = [json];
    while (queue.length) {
      const value = queue.shift();
      if (typeof value === "string") {
        const found = normalizeMediaUrl(value, base);
        if (found) return found;
      } else if (Array.isArray(value)) {
        queue.push(...value);
      } else if (value && typeof value === "object") {
        queue.push(...Object.values(value));
      }
    }
  } catch (error) {
    /* la página no es JSON */
  }
  return "";
}

function findIframe(body) {
  const match = /<iframe[^>]+src=["']([^"']+)["']/i.exec(String(body || ""));
  return match ? match[1] : "";
}

function resolveViaHttp(config, headers) {
  return requestText(config.api, headers, 8000, 3)
    .then((response) => {
      let media = findMedia(response.body, response.url);
      if (!media) {
        const iframe = findIframe(response.body);
        if (iframe) {
          const iframeUrl = /^https?:\/\//i.test(iframe) ? iframe : urlmod.resolve(response.url, iframe);
          const next = normalizeMediaUrl(iframeUrl, response.url);
          if (next) {
            media = next;
          } else {
            return requestText(iframeUrl, headers, 8000, 2).then((frameResponse) => {
              const found = findMedia(frameResponse.body, frameResponse.url);
              if (!found) return "";
              return found;
            });
          }
        }
      }
      return media || "";
    });
}

function resolve(tokenApi) {
  const config = parseEmbed(tokenApi);
  if (!config) return Promise.reject(new Error("El enlace embed no trae una dirección válida."));
  const cached = cache.get(config.api);
  if (cached && Date.now() - cached.at < TTL_MS) return Promise.resolve(cached.value);
  const headers = headersFor(config.api);
  // Un token puede apuntar directo al manifiesto. No hace falta abrir un
  // navegador (y con una URL .m3u8 visible Windows puede abrir un diálogo de
  // descarga que bloquea la app).
  if (isMediaUrl(config.api) && !isAdLikeUrl(config.api)) {
    const direct = { url: config.api, headers };
    cache.set(config.api, { at: Date.now(), value: direct });
    console.log("[embed] directo");
    return Promise.resolve(direct);
  }
  console.log("[embed] inicio");
  return resolveViaHttp(config, headers)
    .catch(function () { return ""; })
    .then((media) => {
      if (media) {
        console.log("[embed] http");
        return { url: media, headers };
      }
      throw new Error("Esta señal se prepara en el reproductor web de la fuente y no está disponible en PC. Prueba otra señal.");
    })
    .then((value) => {
      cache.set(config.api, { at: Date.now(), value });
      return value;
    });
}

module.exports = {
  resolve,
  parseEmbed,
  findMedia,
  headersFor,
};
