import { io } from "socket.io-client";

/**
 * End-to-end bot smoke test.
 *
 * Creates a room, seats two expert bots in Light Trails, starts the round and
 * checks that the bots actually move and survive. Then removes one and makes
 * sure a game without an adapter rejects the request.
 *
 * Usage: SMOKE_SERVER=http://127.0.0.1:3000 npm run smoke:bots
 */
const server = process.env.SMOKE_SERVER ?? "http://127.0.0.1:3000";
let roomCode = "";
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function connect() {
  return io(server, { transports: ["websocket"], forceNew: true, reconnection: false });
}

function emitAck(socket, event, payload) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`timeout ${event}`)), 8000);
    socket.emit(event, payload, (result) => {
      clearTimeout(timer);
      resolve(result);
    });
  });
}

function waitConnected(socket) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("connect timeout")), 8000);
    socket.once("connect", () => {
      clearTimeout(timer);
      resolve();
    });
    socket.once("connect_error", (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exitCode = 1;
}

const host = connect();
const human = connect();
let lastRoom = null;

try {
  await Promise.all([waitConnected(host), waitConnected(human)]);
  host.on("room:state", (payload) => { lastRoom = payload.room; });
  human.on("room:state", (payload) => { lastRoom = payload.room; });

  const created = await emitAck(host, "room:create", {
    hostName: "Bot smoke",
    language: "es"
  });
  if (!created?.ok) throw new Error(`room:create: ${created?.error}`);
  roomCode = created.data.room.code;

  const joined = await emitAck(human, "room:join", {
    roomCode,
    playerName: "Humano",
    deviceId: `bot-smoke-${Date.now()}`
  });
  if (!joined?.ok) throw new Error(`room:join: ${joined?.error}`);
  const humanId = joined.data.player.id;

  host.emit("game:select", { roomCode, gameId: "light-trails" });
  await wait(250);

  const bots = await emitAck(host, "bot:add", { roomCode, count: 2, difficulty: "expert" });
  if (!bots?.ok) throw new Error(`bot:add: ${bots?.error}`);
  const botPlayers = bots.data.room.players.filter((player) => player.isBot);
  if (botPlayers.length !== 2) fail(`esperaba 2 bots, llegaron ${botPlayers.length}`);
  if (botPlayers.some((player) => !player.isReady)) fail("un bot no nació listo");
  if (botPlayers.some((player) => player.connected !== true)) fail("un bot no está conectado");

  const frames = [];
  const onState = (payload) => frames.push(payload);
  human.on("game:state", onState);
  host.on("game:patch", onState);

  human.emit("player:ready", { roomCode, playerId: humanId, isReady: true });
  await wait(1000);
  if (!lastRoom?.currentRound) {
    host.emit("round:start", { roomCode });
  }
  await wait(9000);
  human.off("game:state", onState);
  host.off("game:patch", onState);

  if (!frames.length) fail("no llegó ningún estado de juego");
  const states = frames
    .map((frame) => frame?.game?.state ?? frame?.patch ?? frame)
    .filter((state) => state && state.players);
  if (!states.length) fail("los estados no traen jugadores");

  const first = states[0].players;
  const last = states[states.length - 1].players;
  for (const bot of botPlayers) {
    const initial = first?.[bot.id];
    const final = last?.[bot.id];
    if (!initial || !final) {
      fail(`el bot ${bot.name} no aparece en el estado`);
      continue;
    }
    if (initial.x === final.x && initial.y === final.y) {
      fail(`el bot ${bot.name} no se movió`);
    }
  }

  const aliveBots = botPlayers.filter((bot) => last?.[bot.id]?.alive);
  if (!aliveBots.length) fail("todos los bots murieron de inmediato");

  const aborted = await emitAck(host, "round:abort", { roomCode });
  if (!aborted?.ok) fail(`round:abort: ${aborted?.error}`);
  await wait(250);

  const removed = await emitAck(host, "bot:remove", {
    roomCode,
    playerId: botPlayers[0].id
  });
  if (!removed?.ok) fail(`bot:remove: ${removed?.error}`);
  if (removed.ok && removed.data.room.players.some((player) => player.id === botPlayers[0].id)) {
    fail("el bot expulsado sigue en la sala");
  }

  host.emit("game:select", { roomCode, gameId: "pantomime" });
  await wait(250);
  const rejected = await emitAck(host, "bot:add", { roomCode, count: 1 });
  if (rejected?.ok) fail("pantomime aceptó bots sin adaptador");

  if (!process.exitCode) {
    console.log(JSON.stringify({
      ok: true,
      bots: botPlayers.map((bot) => bot.name),
      frames: frames.length,
      aliveBots: aliveBots.map((bot) => bot.name)
    }, null, 2));
  }
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
} finally {
  human.disconnect();
  host.disconnect();
}
