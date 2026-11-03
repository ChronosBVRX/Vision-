"use strict";

/**
 * Servicio Luna de Vision+ para webOS.
 *
 * La app web llama `luna://com.chronosbvrx.visionplus.webos.service` con
 * `method` y `parameters`. El servicio habla con el API de Vision+ y con el
 * portal XTV, y publica el stream por un proxy HTTP local para que el
 * elemento <video> pueda reproducirlo con las cabeceras del CDN.
 */

const pkg = require("./package.json");
const core = require("./core");

let Service;
try {
  // El runtime de webOS trae este módulo; en Node puro (dev-server) no existe.
  Service = require("webos-service");
} catch (error) {
  console.error("[vision-service] webos-service no está disponible:", error.message);
  process.exit(1);
}

const service = new Service(pkg.name);

// El servicio debe sobrevivir al reproductor: si algo llama process.exit,
// se ignora mientras haya un stream activo.
try {
  process.exit = function blockedExit(code) {
    console.log(`[vision-service] process.exit(${code}) bloqueado para no cortar el stream`);
  };
} catch {
  /* noop */
}

if (service.activityManager) {
  try {
    service.activityManager.idleTimeout = 86400;
    service.activityManager.create("keepAlive", () => {
      console.log("[vision-service] actividad persistente creada");
    });
  } catch (error) {
    console.log("[vision-service] activityManager:", error.message);
  }
}

function respond(message, payload) {
  const body = payload || {};
  if (body.returnValue === false) {
    message.respond({
      returnValue: false,
      errorText: body.errorText || "error",
      errorCode: body.errorCode || 1,
    });
    return;
  }
  message.respond(Object.assign({ returnValue: true }, body));
}

function fail(message, error) {
  message.respond({
    returnValue: false,
    errorText: (error && error.message) || String(error || "error"),
    errorCode: (error && error.portalCode) || 1,
  });
}

function register(name, handler) {
  service.register(name, (message) => {
    Promise.resolve()
      .then(() => handler((message && message.payload) || {}, message))
      .then((payload) => respond(message, payload))
      .catch((error) => {
        console.error(`[vision-service] ${name}:`, error.message);
        fail(message, error);
      });
  });
}

register("ping", () => core.ping());
register("heartbeat", () => ({ alive: true, timestamp: Date.now() }));
register("keepAlive", (payload, message) => ({ subscribed: Boolean(message && message.isSubscription) }));

register("home", (payload) => core.home(payload));
register("banners", (payload) => core.banners(payload));
register("search", (payload) => core.search(payload));
register("category", (payload) => core.category(payload));
register("section", (payload) => core.section(payload));
register("live", (payload) => core.live(payload));
register("epgBulk", (payload) => core.epgBulk(payload));
register("events", (payload) => core.events(payload));
register("sports", (payload) => core.sports(payload));
register("matchStats", (payload) => core.matchStats(payload));
register("resolveEmbed", (payload) => core.resolveEmbed(payload));
register("markers", (payload) => core.markers(payload));
register("episodes", async (payload) => {
  const data = await core.episodes(payload);
  return data;
});
register("resolve", (payload) => core.resolve(payload));
register("resolveLive", (payload) => core.resolveLive(payload));
register("mediaInfo", (payload) => core.mediaInfo(payload));
register("prewarm", () => core.prewarm());
register("abortMedia", () => core.abortMedia());
register("resetSession", () => core.resetSession());
register("clearCache", () => core.clearCache());

console.log(`[vision-service] iniciado ${pkg.name} v${pkg.version}`);
