"use strict";

/**
 * Estadísticas de un partido para el Match Center (PC y TV).
 *
 * Es el mismo contrato que usa la app Android: se busca el partido en la agenda
 * de la casa y ESPN publica el resumen (posesión, tiros, tarjetas, alineaciones
 * y cronología). Si la fuente no publica el partido, se devuelve `null`; nunca
 * se inventan cifras.
 */

const http = require("http");
const https = require("https");
const urlmod = require("url");
const api = require("./api");

const TTL_MS = 60 * 1000;
const cache = new Map();

const STAT_LABELS = {
  possessionPct: "Posesión",
  totalShots: "Tiros",
  shotsOnGoal: "Tiros a puerta",
  shotsOnTarget: "Tiros a puerta",
  wonCorners: "Córners",
  cornerKicks: "Córners",
  foulsCommitted: "Faltas",
  fouls: "Faltas",
  offsides: "Fueras de juego",
  yellowCards: "Amarillas",
  redCards: "Rojas",
  saves: "Atajadas",
  totalPasses: "Pases",
  passes: "Pases",
  passPct: "Precisión de pase",
  totalCrosses: "Centros",
  interceptions: "Intercepciones",
  blockedShots: "Tiros bloqueados",
  fieldGoalPct: "Tiros de campo",
  threePointFieldGoalPct: "Triples",
  freeThrowPct: "Tiros libres",
  totalRebounds: "Rebotes",
  assists: "Asistencias",
  steals: "Robos",
  blocks: "Bloqueos",
  turnovers: "Pérdidas",
  totalYards: "Yardas",
  passingYards: "Yardas por pase",
  rushingYards: "Yardas por carrera",
  hits: "Hits",
  runs: "Carreras",
  errors: "Errores",
  homeRuns: "Jonrones",
};

const FRACTION_PERCENT = new Set([
  "passPct", "shotPct", "crossPct", "longballPct", "tacklePct",
]);

const SOCCER_LEAGUES = new Set([
  "mex.1", "mex.2", "esp.1", "eng.1", "ita.1", "ger.1", "fra.1", "por.1",
  "uefa.champions", "uefa.europa", "uefa.nations", "conmebol.libertadores",
  "conmebol.sudamericana", "arg.1", "bra.1", "col.1", "fifa.friendly",
  "concacaf.nations.league", "concacaf.gold", "fifa.world",
]);

function fold(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function sameTeam(a, b) {
  const left = fold(a);
  const right = fold(b);
  if (!left || !right) return false;
  return left === right || left.includes(right) || right.includes(left);
}

function espnPath(leagueId) {
  const clean = String(leagueId || "").trim();
  if (!clean) return null;
  if (clean.includes("/")) return clean;
  if (SOCCER_LEAGUES.has(clean)) return `soccer/${clean}`;
  return {
    nba: "basketball/nba",
    euroleague: "basketball/euroleague",
    nfl: "football/nfl",
    mlb: "baseball/mlb",
    nhl: "hockey/nhl",
  }[clean] || null;
}

function requestJson(target, timeout) {
  return new Promise((resolve, reject) => {
    let parsed;
    try {
      parsed = urlmod.parse(target);
    } catch (error) {
      reject(error);
      return;
    }
    if (!/^https?:$/.test(parsed.protocol || "")) {
      reject(new Error("URL de estadísticas no válida"));
      return;
    }
    const transport = parsed.protocol === "http:" ? http : https;
    const request = transport.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        method: "GET",
        headers: {
          Accept: "application/json",
          "Accept-Encoding": "identity",
          "User-Agent": "VisionPlus/1.0 (sports stats)",
        },
        rejectUnauthorized: false,
      },
      (response) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          const body = Buffer.concat(chunks).toString("utf8");
          if (response.statusCode < 200 || response.statusCode >= 300) {
            reject(new Error(`ESPN ${response.statusCode}`));
            return;
          }
          try {
            resolve(JSON.parse(body));
          } catch (error) {
            reject(new Error(`ESPN no devolvió JSON: ${error.message}`));
          }
        });
      },
    );
    request.setTimeout(timeout || 14000, () => request.destroy(new Error("ESPN timeout")));
    request.on("error", reject);
    request.end();
  });
}

async function findEvent(params) {
  const knownId = String(params.eventId || "").trim();
  const knownLeague = String(params.leagueId || "").trim();
  if (knownId && knownLeague && /^\d+$/.test(knownId)) {
    return { id: knownId, leagueId: knownLeague };
  }
  const date = String(params.dateIso || "").trim();
  const query = date ? { date, days: 1 } : { days: 2 };
  const payload = await api.fetchJson("/v1/sports", query);
  const matches = (payload && payload.matches) || [];
  const home = params.homeName;
  const away = params.awayName;
  const found = matches.find((match) => {
    const h = (match.home && match.home.name) || "";
    const a = (match.away && match.away.name) || "";
    const direct = sameTeam(home, h) && sameTeam(away, a);
    const reverse = sameTeam(home, a) && sameTeam(away, h);
    if (!direct && !reverse) return false;
    if (knownLeague && match.league && match.league.id && match.league.id !== knownLeague) return false;
    return true;
  });
  if (!found) return null;
  return {
    id: String(found.id || ""),
    leagueId: String((found.league && found.league.id) || knownLeague || ""),
  };
}

function statValue(stat) {
  const name = String(stat.name || "");
  const raw = stat.displayValue !== undefined && stat.displayValue !== null && String(stat.displayValue).trim()
    ? String(stat.displayValue).trim()
    : String(stat.value === undefined || stat.value === null ? "" : stat.value);
  if (FRACTION_PERCENT.has(name)) {
    const numeric = Number(raw.replace("%", ""));
    if (isFinite(numeric) && numeric >= 0 && numeric <= 1 && !String(raw).includes("%")) {
      return `${Math.round(numeric * 100)}%`;
    }
  }
  return raw;
}

function rosterFor(rosters, teamName) {
  if (!Array.isArray(rosters)) return { formation: "", players: [] };
  const target = fold(teamName);
  let roster = null;
  for (const entry of rosters) {
    const name = entry && entry.team && entry.team.displayName;
    if (target && fold(name) === target) {
      roster = entry;
      break;
    }
  }
  if (!roster && rosters.length) roster = rosters[0];
  if (!roster) return { formation: "", players: [] };
  const list = roster.roster || roster.athletes || [];
  const players = [];
  for (const entry of list) {
    if (!entry) continue;
    const athlete = entry.athlete || entry;
    const name = athlete.displayName || entry.displayName || athlete.fullName || "";
    if (!name) continue;
    const position = (entry.position && (entry.position.abbreviation || entry.position.displayName)) || "";
    players.push({
      number: String(entry.jersey || athlete.jersey || ""),
      name: String(name),
      position: String(position),
      starter: Boolean(entry.starter),
    });
  }
  const starters = players.filter((player) => player.starter);
  return {
    formation: String(roster.formation || "").trim(),
    players: starters.length ? starters : players.slice(0, 11),
  };
}

function parseGoals(root) {
  const events = Array.isArray(root.keyEvents) ? root.keyEvents : [];
  const goals = [];
  for (const event of events) {
    if (!event || !event.scoringPlay) continue;
    const type = String((event.type && event.type.text) || "");
    const text = String(event.text || "");
    const lower = `${type} ${text}`.toLowerCase();
    if (!lower.includes("goal") && !lower.includes("penalty")) continue;
    if (lower.includes("missed") || lower.includes("saved")) continue;
    const player = event.participants && event.participants[0] && event.participants[0].athlete;
    const name = (player && player.displayName) || "";
    if (!name) continue;
    goals.push({
      team: String((event.team && event.team.displayName) || ""),
      player: String(name),
      minute: String((event.clock && event.clock.displayValue) || ""),
      ownGoal: lower.includes("own goal"),
      penalty: lower.includes("penalty"),
      sort: Number((event.clock && event.clock.value) || 0),
    });
  }
  goals.sort((a, b) => a.sort - b.sort);
  return goals.map(({ sort, ...goal }) => goal);
}

function parseEvents(root) {
  const events = Array.isArray(root.keyEvents) ? root.keyEvents : [];
  const out = [];
  for (const event of events) {
    if (!event) continue;
    const typeText = String((event.type && event.type.text) || "");
    const lower = typeText.toLowerCase();
    let type = "";
    if (event.scoringPlay && (lower.includes("goal") || lower.includes("penalty")) && !lower.includes("missed")) {
      type = "GOAL";
    } else if (lower.includes("second yellow")) {
      type = "RED";
    } else if (lower.includes("yellow")) {
      type = "YELLOW";
    } else if (lower.includes("red")) {
      type = "RED";
    } else if (lower.includes("substitution")) {
      type = "SUBSTITUTION";
    }
    if (!type) continue;
    const player = event.participants && event.participants[0] && event.participants[0].athlete;
    const name = (player && player.displayName) || "";
    if (!name) continue;
    const second = event.participants && event.participants[1] && event.participants[1].athlete;
    const detail = type === "SUBSTITUTION"
      ? ((second && second.displayName) ? `por ${second.displayName}` : "")
      : (type === "GOAL" ? (lower.includes("own goal") ? "en propia puerta" : lower.includes("penalty") ? "penal" : "") : "");
    out.push({
      team: String((event.team && event.team.displayName) || ""),
      type,
      player: String(name),
      minute: String((event.clock && event.clock.displayValue) || ""),
      detail,
      sort: Number((event.clock && event.clock.value) || 0),
    });
  }
  out.sort((a, b) => a.sort - b.sort);
  return out.map(({ sort, ...event }) => event);
}

function parseSummary(root) {
  const box = root && root.boxscore;
  const teams = box && Array.isArray(box.teams) ? box.teams : [];
  if (teams.length < 2) return null;
  const rosters = root.rosters || [];
  const parsed = [];
  for (let index = 0; index < 2; index += 1) {
    const team = teams[index] || {};
    const info = team.team || {};
    const name = String(info.displayName || (index === 0 ? "Local" : "Visitante"));
    const values = {};
    for (const stat of team.statistics || []) {
      if (!stat) continue;
      const label = STAT_LABELS[stat.name];
      if (!label) continue;
      const value = statValue(stat);
      if (value) values[label] = value;
    }
    const roster = rosterFor(rosters, name);
    parsed.push({
      name,
      logo: String(info.logo || ""),
      values,
      formation: roster.formation,
      players: roster.players,
      lineup: roster.players.map((player) => player.name),
    });
  }
  return {
    home: parsed[0],
    away: parsed[1],
    goals: parseGoals(root),
    events: parseEvents(root),
    fetchedAt: Date.now(),
  };
}

function parseSummaryJson(body) {
  try {
    return parseSummary(JSON.parse(body));
  } catch (error) {
    return null;
  }
}

async function load(params) {
  const values = params || {};
  const home = String(values.homeName || "").trim();
  const away = String(values.awayName || "").trim();
  let event = {
    id: String(values.eventId || "").trim(),
    leagueId: String(values.leagueId || "").trim(),
  };
  if (!event.id || !event.leagueId || !/^\d+$/.test(event.id)) {
    const found = await findEvent(values);
    if (found) event = found;
  }
  if (!event.id || !/^\d+$/.test(event.id)) return null;
  const path = espnPath(event.leagueId);
  if (!path) return null;
  const key = `${path}|${event.id}`;
  const cached = cache.get(key);
  if (cached && Date.now() - cached.at < TTL_MS) return cached.stats;
  const root = await requestJson(
    `https://site.api.espn.com/apis/site/v2/sports/${path}/summary?event=${encodeURIComponent(event.id)}`,
  );
  const stats = parseSummary(root);
  if (stats) cache.set(key, { at: Date.now(), stats });
  return stats;
}

module.exports = {
  load,
  parseSummary,
  parseSummaryJson,
  espnPath,
  _fold: fold,
  _sameTeam: sameTeam,
  _statValue: statValue,
};
