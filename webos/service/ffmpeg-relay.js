"use strict";
var http = require("http"),
  path = require("path"),
  fs = require("fs"),
  crypto = require("crypto"),
  urlmod = require("url"),
  spawn = require("child_process").spawn,
  spawnSync = require("child_process").spawnSync,
  PC = process.env.VISION_PC === "1",
  FFMPEG = process.env.VISION_FFMPEG || __dirname + "/ffmpeg",
  HOST = "127.0.0.1",
  PORT = Number(process.env.VISION_RELAY_PORT || 0) || 0,
  actualPort = 0,
  availableCache = null,
  sessions = {},
  current = null;
function log() {
  var e = Array.prototype.slice.call(arguments);
  try {
    var logDir = process.env.VISION_DATA_DIR || "/tmp";
    fs.appendFileSync(path.join(logDir, "relay.log"), e.join(" ") + "\n");
  } catch (e) {}
  console.log.apply(console, ["[relay]"].concat(e));
}
try {
  if (process.platform !== "win32" && path.isAbsolute(FFMPEG)) {
    var mode = fs.statSync(FFMPEG).mode;
    mode & parseInt("111", 8) || fs.chmodSync(FFMPEG, parseInt("755", 8));
  }
} catch (e) {
  log("no se pudo aplicar permiso de ejecucion a ffmpeg:", e.message);
}
function stopCurrent() {
  if (current) {
    try {
      current.ff.kill("SIGKILL");
    } catch (e) {}
    current = null;
  }
}
process.on("exit", function () {
  try {
    if (current && current.ff) current.ff.kill("SIGKILL");
  } catch (e) {}
});
function splitHeaders(e) {
  var r = null,
    t = [];
  return (
    Object.keys(e || {}).forEach(function (o) {
      "user-agent" === o.toLowerCase()
        ? (r = e[o])
        : t.push(o + ": " + e[o] + "\r\n");
    }),
    { ua: r, rest: t.join("") }
  );
}
function register(e, r) {
  r = r || {};
  var t = crypto.randomBytes(8).toString("hex");
  return (
    (sessions[t] = {
      url: e.url,
      headers: e.headers || {},
      audio: r.audio || 0,
      hevc: !!r.hevc,
      drmKey: e.drmKey || r.drmKey || "",
      isLive: false,
    }),
    "http://" + HOST + ":" + (actualPort || PORT || 8787) + "/s/" + t + ".mp4"
  );
}
function registerLive(e) {
  var t = crypto.randomBytes(8).toString("hex");
  sessions[t] = {
    url: e.url,
    headers: e.headers || {},
    audio: 0,
    hevc: false,
    drmKey: e.drmKey || "",
    isLive: true,
  };
  return "http://" + HOST + ":" + (actualPort || PORT || 8787) + "/live/" + t + ".mp4";
}
function probeDuration(e) {
  return new Promise(function (resolve, reject) {
    var args = ["-hide_banner", "-nostdin"];
    var parts = splitHeaders(e.headers || {});
    if (/^https?:\/\//i.test(e.url || "")) {
      parts.ua && args.push("-user_agent", parts.ua);
      parts.rest && args.push("-headers", parts.rest);
      if (e.drmKey) args.push("-cenc_decryption_keys", String(e.drmKey).trim().replace(/:/g, "="));
    }
    args.push("-i", e.url);
    var child;
    try {
      child = spawn(FFMPEG, args, { stdio: ["ignore", "ignore", "pipe"] });
    } catch (error) {
      reject(error);
      return;
    }
    var stderr = "";
    var done = false;
    var timer = setTimeout(function () { finish(new Error("tiempo de sonda agotado")); }, 9000);
    function finish(error, value) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      try { child.kill("SIGKILL"); } catch (ignored) { /* noop */ }
      if (error) reject(error);
      else resolve(value);
    }
    child.stderr.on("data", function (chunk) {
      stderr += String(chunk);
      var match = /Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)/i.exec(stderr);
      if (match) {
        var seconds = Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
        if (seconds > 0) finish(null, seconds);
      }
    });
    child.on("error", function (error) { finish(error); });
    child.on("close", function (code) {
      if (done) return;
      var match = /Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)/i.exec(stderr);
      if (match) {
        var seconds = Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
        if (seconds > 0) return finish(null, seconds);
      }
      finish(new Error("no se pudo leer la duración"));
    });
  });
}

function buildArgs(e, r, t) {
  var o = splitHeaders(e.headers),
    n = [
      "-hide_banner",
      "-loglevel",
      "warning",
      "-nostdin",
      "-probesize",
      "2M",
      "-analyzeduration",
      "2M",
    ];
  if (/^https?:\/\//i.test(e.url)) {
    n.push("-rw_timeout", e.isLive ? "10000000" : "15000000");
    o.ua && n.push("-user_agent", o.ua);
    o.rest && n.push("-headers", o.rest);
    if (e.drmKey) {
      // GeeSports entrega ClearKey como `KID:KEY`; FFmpeg espera `KID=KEY`.
      n.push("-cenc_decryption_keys", String(e.drmKey).trim().replace(/:/g, "="));
    }
  }
  if (e.isLive) {
    // Chrome/Edge no reproducen HLS nativo. El relay entrega un MP4 fragmentado
    // H.264/AAC en vivo, igual que el port de XuperTV para Windows.
    n.push(
      "-i",
      e.url,
      "-map",
      "0:v:0",
      "-map",
      "0:a:0?",
      "-c:v",
      "libx264",
      "-preset",
      "ultrafast",
      "-tune",
      "zerolatency",
      "-crf",
      "23",
      "-pix_fmt",
      "yuv420p",
      "-c:a",
      "aac",
      "-b:a",
      "128k",
      "-movflags",
      "frag_keyframe+empty_moov+default_base_moof",
      "-f",
      "mp4",
      "pipe:1",
    );
    return n;
  }
  r > 0 && n.push("-ss", String(r));
  n.push("-i", e.url, "-map", "0:v:0", "-map", "0:a:" + t + "?");
  if (PC && e.hevc) {
    // En PC no se puede asumir que Windows tenga el códec HEVC instalado:
    // se transcodifica a H.264 (mismo criterio que XuperTV PC).
    n.push(
      "-c:v",
      "libx264",
      "-preset",
      "ultrafast",
      "-tune",
      "zerolatency",
      "-crf",
      "22",
      "-pix_fmt",
      "yuv420p",
    );
  } else {
    n.push("-c:v", "copy");
    e.hevc && n.push("-tag:v", "hvc1");
  }
  n.push("-c:a", "copy");
  if (
    /mp2t/i.test(e.mime || "") ||
    /\.ts(\?|$)/i.test(e.url || "")
  ) {
    n.push("-bsf:a", "aac_adtstoasc");
  }
  n.push(
    "-avoid_negative_ts",
    "make_zero",
    "-movflags",
    "frag_keyframe+empty_moov+default_base_moof",
    "-f",
    "mp4",
    "pipe:1",
  );
  return n;
}
function resolveForFfmpeg(e, r) {
  var t,
    o = urlmod.parse(e.url || "");
  if (
    PC ||
    "http:" !== o.protocol ||
    !o.hostname ||
    /^\d+\.\d+\.\d+\.\d+$/.test(o.hostname)
  )
    return r(e);
  try {
    t = spawn("/usr/bin/getent", ["ahostsv4", o.hostname]);
  } catch (t) {
    return r(e);
  }
  var n = "";
  t.stdout.on("data", function (e) {
    n += String(e);
  }),
    t.on("error", function () {
      r(e);
    }),
    t.on("close", function () {
      var t = /\b(\d{1,3}(?:\.\d{1,3}){3})\b/.exec(n);
      if (!t) return log("DNS nativo no devolvio IPv4; se usa hostname"), r(e);
      var s = {};
      Object.keys(e.headers || {}).forEach(function (r) {
        "host" !== r.toLowerCase() && (s[r] = e.headers[r]);
      }),
        (s.Host = o.host),
        (o.hostname = t[1]),
        (o.host = t[1] + (o.port ? ":" + o.port : "")),
        (o.href = null),
        log("CDN resuelto por webOS para ffmpeg"),
        r({
          url: urlmod.format(o),
          headers: s,
          audio: e.audio,
          hevc: e.hevc,
          drmKey: e.drmKey,
          isLive: e.isLive,
        });
    });
}
var server = http.createServer(function (e, r) {
  var t = urlmod.parse(e.url, !0);
  if ("OPTIONS" === e.method)
    return (
      r.writeHead(200, {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
        "Access-Control-Allow-Headers": "*",
      }),
      r.end()
    );
  if ("/abort" === t.pathname || "/media/abort" === t.pathname)
    return (
      stopCurrent(),
      r.writeHead(200, {
        "Content-Type": "text/plain",
        "Access-Control-Allow-Origin": "*",
      }),
      r.end("aborted")
    );
  var o = /^\/(?:s|live)\/([0-9a-f]+)\.mp4$/.exec(t.pathname || ""),
    n = o && sessions[o[1]];
  if (!n)
    return r.writeHead(404, { "Access-Control-Allow-Origin": "*" }), r.end();
  if ("HEAD" === e.method)
    return (
      r.writeHead(200, {
        "Content-Type": "video/mp4",
        "Accept-Ranges": "none",
        "Access-Control-Allow-Origin": "*",
      }),
      r.end()
    );
  var s = Math.max(0, Number(t.query.start) || 0),
    a = Number(null != t.query.a ? t.query.a : n.audio) || 0;
  stopCurrent(),
    resolveForFfmpeg(n, function (e) {
      var t = spawn(FFMPEG, buildArgs(e, s, a), {
        stdio: ["ignore", "pipe", "pipe"],
      });
      current = { ff: t };
      var o = !1;
      t.on("error", function (e) {
        log("no se pudo lanzar ffmpeg:", e.message),
          o ||
            (r.writeHead(500, { "Access-Control-Allow-Origin": "*" }),
            (o = !0)),
          r.end();
      }),
        t.stderr.on("data", function (e) {
          log("ffmpeg:", String(e).trim());
        }),
        t.stdout.on("data", function (e) {
          o ||
            (r.writeHead(200, {
              "Content-Type": "video/mp4",
              "Accept-Ranges": "none",
              "Cache-Control": "no-cache",
              Connection: "close",
              "Access-Control-Allow-Origin": "*",
            }),
            (o = !0)),
            r.write(e) ||
              (t.stdout.pause(),
              r.once("drain", function () {
                t.stdout.resume();
              }));
        }),
        t.on("close", function (e) {
          current && current.ff === t && (current = null),
            o ||
              (r.writeHead(502, { "Access-Control-Allow-Origin": "*" }),
              (o = !0)),
            r.end(),
            e && log("ffmpeg termino con codigo", e);
        }),
        r.on("close", function () {
          try {
            t.kill("SIGKILL");
          } catch (e) {}
        });
    });
});
server.on("error", function (e) {
  log("error del servidor:", e.message);
}),
  server.listen(PORT, HOST, function () {
    actualPort = server.address().port;
    log("escuchando en", HOST + ":" + actualPort);
  }),
  (module.exports = {
    register: register,
    registerLive: registerLive,
    stop: stopCurrent,
    probeDuration: probeDuration,
    available: function () {
      if (availableCache !== null) return availableCache;
      try {
        if (path.isAbsolute(FFMPEG)) {
          var accessMode =
            process.platform === "win32"
              ? (fs.constants && fs.constants.F_OK) || 0
              : (fs.constants && fs.constants.X_OK) || fs.X_OK || 1;
          fs.accessSync(FFMPEG, accessMode);
        }
      } catch (e) {
        availableCache = !1;
        return availableCache;
      }
      try {
        // El binario incluido es ARM (webOS). En un PC x86 no debe intentarse:
        // se comprueba una vez y se cachea para no lanzar ffmpeg en cada play.
        var probe = spawnSync(FFMPEG, ["-version"], {
          stdio: "ignore",
          timeout: 2500,
        });
        availableCache = !probe.error && probe.status === 0;
        if (!availableCache) log("ffmpeg incluido no ejecutable en este equipo");
      } catch (e) {
        availableCache = !1;
      }
      return availableCache;
    },
    path: function () {
      return FFMPEG;
    },
    port: function () {
      return actualPort || PORT;
    },
  });
