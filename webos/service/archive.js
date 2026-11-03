"use strict";

/**
 * Resolver mínimo para las fichas del Internet Archive (`ia:<identifier>|título`).
 * El API de Vision+ ya trae el identificador; aquí se busca el MP4 de mayor
 * calidad en `archive.org/metadata/<id>` y se devuelve una URL directa.
 */

const http = require("http");
const https = require("https");
const urlmod = require("url");

const TIMEOUT_MS = 15000;

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    const parsed = urlmod.parse(url);
    const transport = parsed.protocol === "http:" ? http : https;
    const req = transport.get(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        headers: { Accept: "application/json", "User-Agent": "VisionPlusWebOS/0.1" },
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          if (res.statusCode < 200 || res.statusCode >= 300) {
            return reject(new Error(`Archive HTTP ${res.statusCode}`));
          }
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
          } catch (e) {
            reject(new Error(`Archive JSON inválido: ${e.message}`));
          }
        });
      },
    );
    req.setTimeout(TIMEOUT_MS, () => req.destroy(new Error("Archive timeout")));
    req.on("error", reject);
  });
}

function parseRef(ref) {
  const raw = String(ref || "").replace(/^ia:/, "");
  return raw.split("|")[0].trim();
}

async function resolve(ref) {
  const identifier = parseRef(ref);
  if (!identifier) throw new Error("falta el identificador de Internet Archive");
  const meta = await fetchJson(`https://archive.org/metadata/${encodeURIComponent(identifier)}`);
  const files = (meta && meta.files) || [];
  const candidates = files
    .filter((file) => /\.(mp4|m4v)$/i.test(file.name || ""))
    .sort((a, b) => {
      const height = (file) => parseInt((String(file.height || "").match(/\d+/) || [0])[0], 10) || 0;
      return height(b) - height(a) || Number(b.size || 0) - Number(a.size || 0);
    });
  if (!candidates.length) throw new Error("Internet Archive no tiene un MP4 para este título");
  const file = candidates[0];
  const server = (meta && meta.server) || "archive.org";
  const dir = (meta && meta.dir) || `/download/${identifier}`;
  const url = `https://${server}${dir}/${encodeURIComponent(file.name)}`;
  return {
    url,
    mime: "video/mp4",
    codec: "H264",
    quality: file.height ? `${file.height}p` : "",
    duration: 0,
    headers: { "User-Agent": "VisionPlusWebOS/0.1", Accept: "*/*" },
    subtitles: [],
    name: (meta && meta.metadata && meta.metadata.title) || identifier,
  };
}

module.exports = { resolve, parseRef };
