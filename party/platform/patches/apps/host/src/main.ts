document.documentElement.lang = "es";
import { bootstrapHostApp } from "./app/appBootstrap.js";
import { mountHostedLanding } from "./app/hostedLanding.js";
import { installDpadNavigation } from "./app/dpadNavigation.js";

installDpadNavigation();

const platform = new URLSearchParams(window.location.search).get("platform")?.trim();
if (platform) {
  document.body.dataset.platform = platform;
}

const hostedBuild = import.meta.env.VITE_OPEN_PARTY_LAB_HOSTED === "1";
const isHostRoute = window.location.pathname === "/host" || window.location.pathname.startsWith("/host/");

if (hostedBuild && !isHostRoute) {
  mountHostedLanding();
} else {
  const query = new URLSearchParams(window.location.search);
  const roomCode = query.get("room")?.trim().toUpperCase() || null;
  const gameId = query.get("game")?.trim() || null;

  if (hostedBuild && !roomCode && !gameId) {
    window.location.replace("/");
  } else {
    bootstrapHostApp(roomCode, gameId);
  }
}
