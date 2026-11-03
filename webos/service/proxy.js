"use strict";
var http = require("http"),
  https = require("https"),
  crypto = require("crypto"),
  urlmod = require("url"),
  tsIndexer = require("./ts-indexer"),
  MAX_TOKENS = 6,
  tokens = {},
  order = [],
  subtitleTokens = {},
  subtitleOrder = [],
  MAX_SUBTITLES = 12,
  server = null,
  port = 0,
  httpAgent = new http.Agent({
    keepAlive: !0,
    maxSockets: 4,
    keepAliveMsecs: 15e3,
  }),
  httpsAgent = new https.Agent({
    keepAlive: !0,
    maxSockets: 4,
    keepAliveMsecs: 15e3,
    rejectUnauthorized: !1,
  }),
  activeToken = null,
  activeUpstreams = [],
  TS_PACKET_BYTES = 188,
  HLS_SEGMENT_SECONDS = 10;
function destroyConnection(e) {
  if (e) {
    if (e.up) {
      try {
        e.up.unpipe();
      } catch (e) {}
      try {
        "function" == typeof e.up.destroy && e.up.destroy();
      } catch (e) {}
      try {
        e.up.socket && e.up.socket.destroy();
      } catch (e) {}
      e.up = null;
    }
    try {
      e.upstream && e.upstream.abort();
    } catch (e) {}
    try {
      e.upstream && e.upstream.socket && e.upstream.socket.destroy();
    } catch (e) {}
    try {
      e.res && !e.res.finished && e.res.destroy();
    } catch (e) {}
    (e.upstream = null), (e.res = null), (e.req = null);
  }
}
function killActiveStreams() {
  for (; activeUpstreams.length > 0; ) {
    destroyConnection(activeUpstreams.shift());
  }
}
function remember(e, t) {
  for (tokens[e] = t, order.push(e); order.length > MAX_TOKENS; ) {
    var r = order.shift();
    r && delete tokens[r];
  }
}
function add(e, t, r) {
  var n = crypto.randomBytes(12).toString("hex");
  return (
    remember(n, {
      url: e,
      headers: t || {},
      duration: Number(r) || 0,
      contentLength: 0,
      lengthWaiters: null,
      at: Date.now(),
    }),
    n
  );
}
function addSubtitle(e) {
  var t = crypto.randomBytes(12).toString("hex");
  for (
    subtitleTokens[t] = { url: e, at: Date.now() }, subtitleOrder.push(t);
    subtitleOrder.length > MAX_SUBTITLES;

  ) {
    var r = subtitleOrder.shift();
    r && delete subtitleTokens[r];
  }
  return baseUrl() + "/sub/" + t + ".srt";
}
function sendSubtitle(e, t, r) {
  var n = urlmod.parse(r.url || ""),
    o = "https:" === n.protocol,
    s = {
      hostname: n.hostname,
      servername: n.hostname,
      rejectUnauthorized: !1,
      port: n.port || (o ? 443 : 80),
      path: n.path,
      method: "GET",
      headers: { "User-Agent": "Ranger/4.9.4-17294ac0", Accept: "*/*" },
      agent: o ? httpsAgent : httpAgent,
    },
    a = !1,
    i = (o ? https : http).request(s, function (e) {
      var r = [];
      e.on("data", function (e) {
        r.push(e);
      }),
        e.on("end", function () {
          if (!a) {
            var n = Buffer.concat(r),
              o = e.statusCode >= 200 && e.statusCode < 300;
            t.writeHead(o ? 200 : 502, {
              "Content-Type": "text/plain; charset=utf-8",
              "Content-Length": n.length,
              "Access-Control-Allow-Origin": "*",
              "Cache-Control": "private, max-age=300",
            }),
              t.end(n);
          }
        });
    });
  i.setTimeout(2e4, function () {
    try {
      i.abort();
    } catch (e) {}
  }),
    i.on("error", function () {
      if (!a) {
        a = !0;
        try {
          t.writeHead(502, {
            "Content-Type": "text/plain",
            "Access-Control-Allow-Origin": "*",
          }),
            t.end("subtitle upstream error");
        } catch (e) {}
      }
    }),
    i.end();
}
function mediaRequest(e, t, r) {
  var n,
    o = urlmod.parse(e.url),
    s = "https:" === o.protocol,
    a = {};
  for (n in e.headers)
    Object.prototype.hasOwnProperty.call(e.headers, n) && (a[n] = e.headers[n]);
  for (n in r || {})
    Object.prototype.hasOwnProperty.call(r, n) && (a[n] = r[n]);
  return {
    secure: s,
    options: {
      hostname: o.hostname,
      servername: o.hostname,
      rejectUnauthorized: !1,
      port: o.port || (s ? 443 : 80),
      path: o.path,
      method: t,
      headers: a,
      agent: s ? httpsAgent : httpAgent,
    },
  };
}
function sourceLength(e, t) {
  if (e.contentLength > 0) return t(null, e.contentLength);
  if (e.lengthWaiters) e.lengthWaiters.push(t);
  else {
    e.lengthWaiters = [t];
    var r = mediaRequest(e, "GET", { Range: "bytes=0-0" }),
      n = (r.secure ? https : http).request(r.options, function (t) {
        var r = /\/(\d+)$/.exec(String(t.headers["content-range"] || "")),
          n = r
            ? parseInt(r[1], 10)
            : parseInt(t.headers["content-length"], 10);
        t.resume();
        var o = e.lengthWaiters || [];
        (e.lengthWaiters = null), n > 0 && (e.contentLength = n);
        for (var s = 0; s < o.length; s++)
          o[s](n > 0 ? null : new Error("el CDN no informó el tamaño"), n);
      });
    n.setTimeout(15e3, function () {
      try {
        n.abort();
      } catch (e) {}
    }),
      n.on("error", function (t) {
        var r = e.lengthWaiters || [];
        e.lengthWaiters = null;
        for (var n = 0; n < r.length; n++) r[n](t);
      }),
      n.end();
  }
}
function readRange(e, t, r, n) {
  var o = mediaRequest(e, "GET", { Range: "bytes=" + t + "-" + r }),
    s = (o.secure ? https : http).request(o.options, function (e) {
      var t = [];
      e.on("data", function (e) {
        t.push(e);
      }),
        e.on("end", function () {
          n(
            e.statusCode >= 200 && e.statusCode < 300
              ? null
              : new Error("rango rechazado"),
            Buffer.concat(t),
          );
        });
    });
  s.setTimeout(25e3, function () {
    try {
      s.abort();
    } catch (e) {}
  }),
    s.on("error", function (e) {
      n(e);
    }),
    s.end();
}
function sendVirtualHls(e, t, r, n) {
  sourceLength(n, function (o, s) {
    if (o)
      return (
        t.writeHead(502, {
          "Content-Type": "text/plain",
          "Access-Control-Allow-Origin": "*",
        }),
        void t.end("no se pudo crear el manifiesto HLS")
      );
    n.indexer ||
      ((n.indexer = tsIndexer.create(s, function (e, t, r) {
        readRange(n, e, t, r);
      })),
      n.indexer.start());
    var a = n.indexer;
    if (a.segments.length || a.complete) {
      for (
        var i = [
            "#EXTM3U",
            "#EXT-X-VERSION:4",
            "#EXT-X-PLAYLIST-TYPE:VOD",
            "#EXT-X-TARGETDURATION:" + HLS_SEGMENT_SECONDS,
            "#EXT-X-MEDIA-SEQUENCE:0",
          ],
          c = 0;
        c < a.segments.length;
        c++
      ) {
        var l = a.segments[c];
        i.push("#EXTINF:" + l.duration.toFixed(3) + ","),
          i.push("#EXT-X-BYTERANGE:" + (l.end - l.start + 1) + "@" + l.start),
          i.push("stream.ts");
      }
      a.complete && i.push("#EXT-X-ENDLIST");
      var u = i.join("\n") + "\n";
      t.writeHead(200, {
        "Content-Type": "application/vnd.apple.mpegurl",
        "Content-Length": Buffer.byteLength(u),
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": "no-store",
      }),
        t.end(u);
    } else
      a.when(function () {
        sendVirtualHls(e, t, r, n);
      });
  });
}
function hlsLocalUrl(token, base, uri) {
  try {
    var absolute = urlmod.resolve(base || "", uri);
    if (!/^https?:\/\//i.test(absolute)) return uri;
    return baseUrl() + "/media/" + token + "/seg?u=" + encodeURIComponent(absolute);
  } catch (e) {
    return uri;
  }
}
function rewriteHls(text, token, base) {
  return String(text || "")
    .split(/\r?\n/)
    .map(function (line) {
      var trimmed = line.trim();
      if (!trimmed) return line;
      if (trimmed.charAt(0) !== "#") return hlsLocalUrl(token, base, trimmed);
      return line.replace(/URI="([^"]+)"/g, function (match, uri) {
        return 'URI="' + hlsLocalUrl(token, base, uri) + '"';
      });
    })
    .join("\n");
}
function forward(e, t, r, token) {
  activeToken !== token && (killActiveStreams(), (activeToken = token));
  var o = {};
  e.headers.range && (o.Range = e.headers.range),
    e.headers["if-range"] && (o["If-Range"] = e.headers["if-range"]);
  var s = mediaRequest(r, "HEAD" === e.method ? "HEAD" : "GET", o),
    a = s.secure,
    i = s.options;
  if (t.socket)
    try {
      t.socket.setNoDelay(!0);
    } catch (e) {}
  var c = { upstream: null, res: t, req: e, up: null };
  function l() {
    var e = activeUpstreams.indexOf(c);
    -1 !== e && activeUpstreams.splice(e, 1), destroyConnection(c);
  }
  activeUpstreams.push(c),
    e.on("close", l),
    e.on("aborted", l),
    t.on("close", l),
    t.on("finish", l),
    t.on("error", l);
  var u = (a ? https : http).request(i, function (n) {
    if (((c.up = n), n.socket))
      try {
        n.socket.setNoDelay(!0);
      } catch (e) {}
    if ((u.setTimeout(0), u.socket))
      try {
        u.socket.setTimeout(0);
      } catch (e) {}
    var o = (n.headers["content-type"] || "").toLowerCase(),
      s = "video/mp2t";
    -1 !== o.indexOf("mp4")
      ? (s = "video/mp4")
      : (-1 !== o.indexOf("mpegurl") ||
          (r.url && -1 !== r.url.indexOf(".m3u8"))) &&
        (s = "application/vnd.apple.mpegurl");
    var isHls = "application/vnd.apple.mpegurl" === s,
      headers = {
        "Content-Type": s,
        "Accept-Ranges": n.headers["accept-ranges"] || "bytes",
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": isHls ? "no-cache, no-store" : "private, max-age=120",
        Connection: "keep-alive",
      };
    if ("HEAD" === e.method) {
      if (n.headers["content-length"]) headers["Content-Length"] = n.headers["content-length"];
      t.writeHead(n.statusCode || 200, headers);
      t.end();
      n.resume();
      return;
    }
    if (isHls) {
      var chunks = [];
      n.on("data", function (chunk) {
        chunks.push(chunk);
      });
      n.on("error", function () {
        l();
      });
      n.on("close", function () {
        l();
      });
      n.on("end", function () {
        var body = rewriteHls(Buffer.concat(chunks).toString("utf8"), token, r.url);
        headers["Content-Length"] = Buffer.byteLength(body);
        try {
          t.writeHead(n.statusCode || 200, headers);
          t.end(body);
        } catch (error) {
          /* noop */
        }
        l();
      });
      return;
    }
    if (n.headers["content-length"]) headers["Content-Length"] = n.headers["content-length"];
    if (n.headers["content-range"]) headers["Content-Range"] = n.headers["content-range"];
    t.writeHead(n.statusCode || 200, headers);
    n.on("error", function () {
      l();
    }),
      n.on("close", function () {
        l();
      }),
      n.on("end", function () {
        l();
      }),
      n.pipe(t);
  });
  if (((c.upstream = u), u.socket))
    try {
      u.socket.setNoDelay(!0);
    } catch (e) {}
  u.setTimeout(25e3, function () {
    if (!t.headersSent) {
      try {
        u.abort();
      } catch (e) {}
      try {
        t.writeHead(504, {
          "Content-Type": "text/plain",
          "Access-Control-Allow-Origin": "*",
        }),
          t.end("gateway timeout");
      } catch (e) {}
    }
    l();
  }),
    u.on("error", function () {
      if (!t.headersSent)
        try {
          t.writeHead(502, {
            "Content-Type": "text/plain",
            "Access-Control-Allow-Origin": "*",
          }),
            t.end("upstream error");
        } catch (e) {}
      l();
    }),
    u.end();
}
function handler(e, t) {
  try {
    e.setTimeout(0);
  } catch (e) {}
  try {
    t.setTimeout(0);
  } catch (e) {}
  if ("OPTIONS" === e.method)
    return (
      t.writeHead(204, {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
        "Access-Control-Allow-Headers": "Range",
      }),
      void t.end()
    );
  if ("/media/abort" === e.url)
    return (
      killActiveStreams(),
      (activeToken = null),
      t.writeHead(200, {
        "Content-Type": "text/plain",
        "Access-Control-Allow-Origin": "*",
      }),
      void t.end("aborted")
    );
  var r = /^\/sub\/([a-f0-9]{24})\.srt$/.exec(e.url || "");
  if (r) {
    var n = subtitleTokens[r[1]];
    return n
      ? void sendSubtitle(e, t, n)
      : (t.writeHead(410, {
          "Content-Type": "text/plain",
          "Access-Control-Allow-Origin": "*",
        }),
        void t.end("expired"));
  }
  var o = /^\/media\/([a-f0-9]{24})(?:\/([^\?#]*))?(?:[?#].*)?$/.exec(
    e.url || "",
  );
  if (!o)
    return (
      t.writeHead(404, {
        "Content-Type": "text/plain",
        "Access-Control-Allow-Origin": "*",
      }),
      void t.end("not found")
    );
  var s = o[1],
    a = o[2] || "",
    i = tokens[s];
  if (!i)
    return (
      t.writeHead(410, {
        "Content-Type": "text/plain",
        "Access-Control-Allow-Origin": "*",
      }),
      void t.end("expired")
    );
  var target = null;
  try {
    var parsed = urlmod.parse(e.url, true);
    var requested = parsed.query && parsed.query.u;
    if (requested) target = String(requested);
  } catch (parseError) {
    target = null;
  }
  if (target && /^https?:\/\//i.test(target)) {
    return forward(e, t, { url: target, headers: i.headers || {}, duration: i.duration || 0 }, s);
  }
  -1 !== i.url.indexOf(".m3u8") || -1 === a.indexOf(".m3u8")
    ? forward(e, t, i, s)
    : sendVirtualHls(e, t, s, i);
}
function start(e) {
  if (server && port) return e(null, port);
  ((server = http.createServer(handler)).timeout = 0),
    "function" == typeof server.setTimeout && server.setTimeout(0),
    server.on("error", function (t) {
      e(t);
    }),
    server.listen(0, function () {
      (port = server.address().port), e(null, port);
    });
}
function baseUrl() {
  return port ? "http://127.0.0.1:" + port : "";
}
module.exports = {
  start: start,
  add: add,
  addSubtitle: addSubtitle,
  baseUrl: baseUrl,
  port: function () {
    return port;
  },
  killActiveStreams: killActiveStreams,
};
