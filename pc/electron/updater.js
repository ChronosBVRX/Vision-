"use strict";

/**
 * Actualizaciones OTA del ejecutable portable de Vision+ PC.
 *
 * El manifiesto vive en el canal fijo `pc-latest` del repositorio de releases.
 * El .exe descargado se verifica por SHA-256 y se instala con un script de
 * PowerShell que espera a que esta instancia termine, reemplaza el portable y
 * vuelve a abrirlo. Si el reemplazo falla, conserva/restaura el .exe anterior.
 */

const fs = require("fs");
const path = require("path");
const https = require("https");
const crypto = require("crypto");
const { spawn } = require("child_process");
const { app, ipcMain, shell } = require("electron");

const DEFAULT_MANIFEST =
  process.env.VISION_PC_UPDATE_MANIFEST ||
  "https://github.com/ChronosBVRX/Vision-Releases/releases/download/pc-latest/pc-version.json";
const DEFAULT_BASE =
  process.env.VISION_PC_UPDATE_BASE ||
  "https://github.com/ChronosBVRX/Vision-Releases/releases/download/pc-latest/";
const MANUAL_URL =
  process.env.VISION_PC_MANUAL_UPDATE ||
  "https://github.com/ChronosBVRX/Vision-Releases/releases/download/pc-latest/VisionPlusPC-Portable.exe";
const MAX_REDIRECTS = 5;

let ctx = null;
let latest = null;
let checking = null;
let downloading = null;
let installing = false;
let lastStatus = null;

function log() {
  const args = Array.prototype.slice.call(arguments);
  const line = args.map((value) => String(value)).join(" ");
  if (ctx && typeof ctx.log === "function") ctx.log("[update] " + line);
  else console.log("[update]", ...args);
}

function windowForEvents() {
  try {
    return ctx && typeof ctx.getWindow === "function" ? ctx.getWindow() : null;
  } catch (error) {
    return null;
  }
}

function send(status) {
  lastStatus = status;
  const win = windowForEvents();
  if (win && !win.isDestroyed()) {
    try {
      win.webContents.send("pc-update:status", status);
    } catch (error) {
      /* la ventana pudo cerrarse mientras se descargaba */
    }
  }
}

function versionParts(value) {
  return String(value || "")
    .replace(/^v/i, "")
    .split(/[^0-9]+/)
    .filter((part) => part !== "")
    .map((part) => Number(part) || 0);
}

function compareVersions(left, right) {
  const a = versionParts(left);
  const b = versionParts(right);
  const count = Math.max(a.length, b.length);
  for (let index = 0; index < count; index += 1) {
    const x = a[index] || 0;
    const y = b[index] || 0;
    if (x !== y) return x > y ? 1 : -1;
  }
  return 0;
}

function httpsGet(target, timeoutMs) {
  return new Promise((resolve, reject) => {
    function request(url, redirectsLeft) {
      let parsed;
      try {
        parsed = new URL(url);
      } catch (error) {
        reject(new Error("URL de actualización no válida"));
        return;
      }
      if (parsed.protocol !== "https:") {
        reject(new Error("La actualización debe usar HTTPS"));
        return;
      }
      const req = https.get(
        {
          protocol: parsed.protocol,
          hostname: parsed.hostname,
          port: parsed.port || 443,
          path: parsed.pathname + parsed.search,
          headers: {
            Accept: "*/*",
            "Accept-Encoding": "identity",
            "User-Agent": "VisionPlusPC/" + app.getVersion(),
          },
          rejectUnauthorized: false,
        },
        (response) => {
          if (
            response.statusCode >= 300 &&
            response.statusCode < 400 &&
            response.headers.location &&
            redirectsLeft > 0
          ) {
            response.resume();
            request(new URL(response.headers.location, url).toString(), redirectsLeft - 1);
            return;
          }
          if (response.statusCode !== 200) {
            response.resume();
            reject(new Error("HTTP " + response.statusCode + " al consultar la actualización"));
            return;
          }
          resolve(response);
        },
      );
      req.setTimeout(timeoutMs || 25000, () => req.destroy(new Error("tiempo de espera agotado")));
      req.on("error", reject);
    }
    request(String(target || ""), MAX_REDIRECTS);
  });
}

function readManifest() {
  return httpsGet(DEFAULT_MANIFEST, 25000).then(
    (response) =>
      new Promise((resolve, reject) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
          } catch (error) {
            reject(new Error("El manifiesto de actualización no es JSON"));
          }
        });
        response.on("error", reject);
      }),
  );
}

function normalizeManifest(raw) {
  const version = String((raw && raw.version) || "").trim();
  if (!version) throw new Error("El manifiesto no trae versión");
  const exe = String((raw && raw.exe) || "VisionPlusPC-Portable.exe").trim();
  const downloadUrl = String(
    (raw && (raw.downloadUrl || raw.download_url)) ||
      new URL(exe, DEFAULT_BASE).toString(),
  ).trim();
  if (!/^https:\/\//i.test(downloadUrl)) {
    throw new Error("El manifiesto no trae una descarga HTTPS válida");
  }
  return {
    version,
    notes: String((raw && raw.notes) || ""),
    exe,
    downloadUrl,
    sha256: String((raw && (raw.sha256 || raw.sha)) || "").trim().toLowerCase(),
    size: Number((raw && raw.size) || 0) || 0,
  };
}

function check() {
  if (checking) return checking;
  send({ state: "checking" });
  checking = readManifest()
    .then((raw) => {
      const info = normalizeManifest(raw);
      latest = info;
      const current = app.getVersion();
      const newer = compareVersions(info.version, current) > 0;
      if (newer) {
        const payload = {
          state: "available",
          version: info.version,
          current,
          notes: info.notes,
          size: info.size,
        };
        send(payload);
        return payload;
      }
      const payload = { state: "latest", version: current, remoteVersion: info.version };
      send(payload);
      return payload;
    })
    .catch((error) => {
      const payload = { state: "error", message: error.message, manualUrl: MANUAL_URL };
      send(payload);
      return payload;
    })
    .finally(() => {
      checking = null;
    });
  return checking;
}

function downloadTarget(info) {
  const safe = info.version.replace(/[^0-9A-Za-z._-]/g, "_");
  return path.join(ctx.dataDir, "update", safe, "VisionPlusPC-Portable.exe");
}

function verifyFile(file, expectedHash) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash("sha256");
    const stream = fs.createReadStream(file);
    stream.on("data", (chunk) => hash.update(chunk));
    stream.on("end", () => {
      const actual = hash.digest("hex").toLowerCase();
      if (!expectedHash || actual !== expectedHash.toLowerCase()) {
        reject(new Error("La verificación SHA-256 de la actualización falló"));
        return;
      }
      resolve(true);
    });
    stream.on("error", reject);
  });
}

function downloadFile(url, dest, expectedSize, onProgress) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    const part = dest + ".part";
    try {
      if (fs.existsSync(part)) fs.unlinkSync(part);
    } catch (error) {
      /* noop */
    }
    const output = fs.createWriteStream(part);
    const hash = crypto.createHash("sha256");
    let received = 0;
    let total = expectedSize;
    let settled = false;
    function fail(error) {
      if (settled) return;
      settled = true;
      try { output.destroy(); } catch (ignored) { /* noop */ }
      try { if (fs.existsSync(part)) fs.unlinkSync(part); } catch (ignored) { /* noop */ }
      reject(error);
    }
    httpsGet(url, 30000)
      .then((response) => {
        total = Number(response.headers["content-length"] || expectedSize) || expectedSize;
        response.on("data", (chunk) => {
          received += chunk.length;
          hash.update(chunk);
          if (typeof onProgress === "function") onProgress(received, total);
          if (!output.write(chunk)) response.pause();
        });
        output.on("drain", () => response.resume());
        response.on("end", () => {
          if (settled) return;
          output.end(() => {
            if (settled) return;
            const actual = hash.digest("hex");
            if (expectedSize > 0 && received !== expectedSize) {
              fail(new Error("La actualización llegó incompleta"));
              return;
            }
            try {
              if (fs.existsSync(dest)) fs.unlinkSync(dest);
              fs.renameSync(part, dest);
            } catch (error) {
              try { if (fs.existsSync(part)) fs.unlinkSync(part); } catch (ignored) { /* noop */ }
              settled = true;
              reject(error);
              return;
            }
            settled = true;
            resolve({ size: received, sha256: actual });
          });
        });
        response.on("error", fail);
      })
      .catch(fail);
  });
}

function download() {
  if (downloading) return downloading;
  if (!latest) {
    return check().then(() => {
      if (!latest) throw new Error("No hay una actualización disponible");
      return download();
    });
  }
  if (!latest.sha256) {
    return Promise.reject(new Error("El manifiesto no trae SHA-256; se cancela la actualización"));
  }
  const dest = downloadTarget(latest);
  const existing = fs.existsSync(dest);
  send({ state: "downloading", received: 0, total: latest.size || 0, percent: 0 });
  downloading = (existing ? verifyFile(dest, latest.sha256).then(() => null) : downloadFile(
    latest.downloadUrl,
    dest,
    latest.size,
    (received, total) => {
      const percent = total > 0 ? Math.min(100, Math.round((received / total) * 100)) : 0;
      send({ state: "downloading", received, total, percent });
    },
  ))
    .then(() => verifyFile(dest, latest.sha256))
    .then(() => {
      latest.readyPath = dest;
      const payload = {
        state: "ready",
        version: latest.version,
        notes: latest.notes,
        size: latest.size,
      };
      send(payload);
      return payload;
    })
    .catch((error) => {
      try { if (fs.existsSync(dest)) fs.unlinkSync(dest); } catch (ignored) { /* noop */ }
      const payload = { state: "error", message: error.message, manualUrl: MANUAL_URL };
      send(payload);
      throw error;
    })
    .finally(() => {
      downloading = null;
    });
  return downloading;
}

function psQuote(value) {
  return "'" + String(value).replace(/'/g, "''") + "'";
}

function executablePath() {
  const explicit = String(process.env.PORTABLE_EXECUTABLE_FILE || "").trim();
  if (explicit) return explicit;
  const dir = String(process.env.PORTABLE_EXECUTABLE_DIR || "").trim();
  if (dir) return path.join(dir, "VisionPlusPC-Portable.exe");
  return process.execPath;
}

function buildUpdaterScript(oldExe, newExe, logFile) {
  const backup = oldExe + ".bak";
  const oldDir = path.dirname(oldExe);
  return [
    "$ErrorActionPreference = 'Continue'",
    "$log = " + psQuote(logFile),
    "function Write-Log([string]$text) { try { Add-Content -LiteralPath $log -Value ((Get-Date).ToString('s') + ' ' + $text) -Encoding UTF8 } catch {} }",
    "Write-Log " + psQuote(
      "inicio pid=" + process.pid +
      " old=" + oldExe +
      " new=" + newExe +
      " portableFile=" + String(process.env.PORTABLE_EXECUTABLE_FILE || "") +
      " portableDir=" + String(process.env.PORTABLE_EXECUTABLE_DIR || "") +
      " exec=" + process.execPath,
    ),
    "for ($w = 0; $w -lt 60; $w++) { if (-not (Get-Process -Id " + process.pid + " -ErrorAction SilentlyContinue)) { break }; Start-Sleep -Milliseconds 500 }",
    "$copied = $false",
    "$lastError = ''",
    "for ($i = 0; $i -lt 120; $i++) {",
    "  try {",
    "    if (-not (Test-Path -LiteralPath " + psQuote(oldExe) + ")) { throw 'no existe el ejecutable antiguo' }",
    "    if (-not (Test-Path -LiteralPath " + psQuote(newExe) + ")) { throw 'no existe la actualizacion descargada' }",
    "    if (-not (Test-Path -LiteralPath " + psQuote(backup) + ")) {",
    "      Copy-Item -LiteralPath " + psQuote(oldExe) + " -Destination " + psQuote(backup) + " -Force -ErrorAction Stop",
    "      Write-Log 'respaldo creado'",
    "    }",
    "    $src = Get-Item -LiteralPath " + psQuote(newExe) + " -ErrorAction Stop",
    "    $dstBefore = Get-Item -LiteralPath " + psQuote(oldExe) + " -ErrorAction Stop",
    "    if ($dstBefore.IsReadOnly) { $dstBefore.IsReadOnly = $false }",
    "    Copy-Item -LiteralPath " + psQuote(newExe) + " -Destination " + psQuote(oldExe) + " -Force -ErrorAction Stop",
    "    $dst = Get-Item -LiteralPath " + psQuote(oldExe) + " -ErrorAction Stop",
    "    if ($dst.Length -ne $src.Length) { throw 'el tamano copiado no coincide' }",
    "    $copied = $true",
    "    Write-Log ('copia correcta en el intento ' + $i)",
    "    break",
    "  } catch {",
    "    $lastError = $_.Exception.Message",
    "    Write-Log ('intento ' + $i + ': ' + $lastError)",
    "    Start-Sleep -Milliseconds 500",
    "  }",
    "}",
    "if (-not $copied) {",
    "  Write-Log ('FALLO tras 120 intentos: ' + $lastError)",
    "  try { Start-Process -FilePath " + psQuote(oldExe) + " -WorkingDirectory " + psQuote(oldDir) + " -ErrorAction Stop | Out-Null } catch { Write-Log ('no se pudo reabrir: ' + $_.Exception.Message) }",
    "  exit 1",
    "}",
    "try {",
    "  $proc = Start-Process -FilePath " + psQuote(oldExe) + " -WorkingDirectory " + psQuote(oldDir) + " -PassThru -ErrorAction Stop",
    "  Write-Log ('reiniciado pid ' + $proc.Id)",
    "} catch {",
    "  Write-Log ('no se pudo iniciar: ' + $_.Exception.Message)",
    "  exit 1",
    "}",
    "Write-Log 'fin'",
  ].join("\r\n");
}

function failInstall(error) {
  installing = false;
  const message = error && error.message ? error.message : String(error || "error");
  send({ state: "error", message, manualUrl: MANUAL_URL });
  return Promise.reject(error);
}

function install() {
  if (installing) return Promise.resolve({ ok: true });
  if (!latest || !latest.readyPath || !fs.existsSync(latest.readyPath)) {
    return Promise.reject(new Error("Todavía no hay una actualización descargada"));
  }
  if (!process.env.PORTABLE_EXECUTABLE_FILE && !process.env.PORTABLE_EXECUTABLE_DIR) {
    return failInstall(new Error("La actualización automática solo está disponible en el .exe portable. Descarga manual: " + MANUAL_URL));
  }
  const oldExe = executablePath();
  const oldDir = path.dirname(oldExe);
  if (!/\.exe$/i.test(oldExe) || !fs.existsSync(oldExe)) {
    return failInstall(new Error("No se encontró el ejecutable de Vision+ PC (" + oldExe + "). Descarga manual: " + MANUAL_URL));
  }
  try {
    const probe = path.join(oldDir, ".visionpc-write-" + process.pid);
    fs.writeFileSync(probe, "ok", "utf8");
    fs.unlinkSync(probe);
  } catch (error) {
    return failInstall(new Error("No se puede escribir en la carpeta del ejecutable (" + oldDir + "). Descarga manual: " + MANUAL_URL));
  }
  installing = true;
  send({ state: "installing", version: latest.version });
  try {
    const logFile = path.join(path.dirname(latest.readyPath), "install.log");
    const script = path.join(path.dirname(latest.readyPath), "install-update.ps1");
    log("instalando", latest.version, "old=" + oldExe, "new=" + latest.readyPath);
    fs.writeFileSync(script, buildUpdaterScript(oldExe, latest.readyPath, logFile), "utf8");
    const child = spawn(
      "powershell.exe",
      ["-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script],
      { detached: true, stdio: "ignore", windowsHide: true },
    );
    child.unref();
    setTimeout(() => {
      try {
        if (ctx && typeof ctx.quit === "function") ctx.quit();
        else app.quit();
      } catch (error) {
        app.quit();
      }
    }, 900);
    return Promise.resolve({ ok: true, log: logFile });
  } catch (error) {
    return failInstall(error);
  }
}

function init(options) {
  ctx = options || {};
  ipcMain.handle("pc-update:version", () => app.getVersion());
  ipcMain.handle("pc-update:check", () => check());
  ipcMain.handle("pc-update:download", () => download());
  ipcMain.handle("pc-update:install", () => install());
  ipcMain.handle("pc-update:last", () => lastStatus);
  ipcMain.handle("pc-update:open-download", () => shell.openExternal(MANUAL_URL));
  log("canal", DEFAULT_MANIFEST);
}

module.exports = {
  init,
  check,
  download,
  install,
  buildUpdaterScript,
  compareVersions,
  normalizeManifest,
};
