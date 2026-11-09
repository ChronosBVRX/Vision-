import type { HostGame, HostGameStateSource } from "@open-party-lab/game-core";
import { banterboltManifest } from "../manifest.js";
import type { BanterboltPublicState } from "../protocol.js";

interface HostState {
  game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  room?: {
    language?: "de" | "en" | "es";
    pausedBy?: unknown;
    players?: Array<{ id: string; name?: string; isReady?: boolean; connected?: boolean }>;
  } | null;
}

const el = (tag: string, className?: string, value?: string): HTMLElement => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value !== undefined) node.textContent = value;
  return node;
};

const styles = `
.bb-mount{position:absolute;inset:0;overflow:hidden;container-type:size}
.bb-host{--bg:#070B13;--bg2:#0B1220;--surface:#0F1626;--surface2:#1B2538;--line:#26334A;--text:#F7FAFF;--muted:#A9B4C6;--blue:#2D7DF6;--blue2:#6BA6FF;--gold:#F5C518;--success:#4ADE80;--danger:#FF5D5D;box-sizing:border-box;width:100%;height:100%;padding:clamp(22px,3.5vw,56px);display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:clamp(14px,2.4vh,28px);background:linear-gradient(180deg,#070b13b8,#070b13ea),radial-gradient(circle at 15% 0%,#14305a 0,transparent 38%),radial-gradient(circle at 90% 100%,#2a1648 0,transparent 40%),url('/banterbolt/ambient.webp') center/cover no-repeat,var(--bg);color:var(--text);font-family:"Plus Jakarta Sans",Inter,system-ui,sans-serif;overflow:hidden}
.bb-host *{box-sizing:border-box}
.bb-top{display:flex;align-items:center;justify-content:space-between;gap:18px;border-bottom:1px solid var(--line);padding-bottom:14px}
.bb-brand{font:800 clamp(24px,3vw,40px)/1 "Plus Jakarta Sans",system-ui,sans-serif;letter-spacing:-.04em}
.bb-brand span{color:var(--blue2)}
.bb-round{font:700 12px/1 system-ui,sans-serif;letter-spacing:.16em;text-transform:uppercase;color:var(--muted);padding:10px 14px;border:1px solid var(--line);border-radius:999px;background:#0b1220aa}
.bb-body{min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr);gap:clamp(12px,2vh,24px)}
.bb-heading{text-align:center;max-width:1100px;margin:auto;animation:bb-rise .35s both}
.bb-kicker{margin:0 0 8px;font:800 clamp(11px,1.1vw,15px)/1 system-ui,sans-serif;letter-spacing:.18em;text-transform:uppercase;color:var(--blue2)}
.bb-title{font:800 clamp(28px,4.6vw,68px)/1.05 "Plus Jakarta Sans",system-ui,sans-serif;letter-spacing:-.04em;margin:0;text-wrap:balance;overflow-wrap:anywhere}
.bb-helper{margin:12px auto 0;max-width:72ch;color:var(--muted);font-size:clamp(14px,1.5vw,21px);line-height:1.45;text-wrap:balance}
.bb-timer{font:800 clamp(18px,2.4vw,32px)/1 ui-monospace,monospace;color:var(--gold);font-variant-numeric:tabular-nums}
.bb-narrator{margin:14px auto 0;max-width:850px;display:flex;align-items:center;justify-content:center;gap:12px;padding:12px 18px;border:1px solid #2b3f61;border-radius:999px;background:#0d1a2ecc;color:#dce8ff;font-size:clamp(13px,1.35vw,19px);line-height:1.35;text-align:center}
.bb-narrator b{color:var(--blue2);font-weight:800;white-space:nowrap}
.bb-stage{min-height:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:clamp(12px,2vh,22px)}
.bb-prompt{max-width:1100px;width:100%;padding:clamp(14px,2vw,26px);border:1px solid #2b4a78;border-radius:22px;background:linear-gradient(180deg,#101d33,#0c1524);box-shadow:0 20px 50px #0007;text-align:center;font:800 clamp(20px,3vw,44px)/1.15 "Plus Jakarta Sans",system-ui,sans-serif;letter-spacing:-.03em}
.bb-answers{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:clamp(12px,1.8vw,24px);width:100%;max-width:1100px}
.bb-answer{position:relative;min-height:clamp(110px,20vh,230px);display:flex;flex-direction:column;justify-content:space-between;gap:12px;padding:clamp(14px,1.8vw,24px);border:1px solid #2c4468;border-radius:20px;background:linear-gradient(160deg,#15243c,#0d1728);box-shadow:0 16px 40px #0006;animation:bb-rise .35s both}
.bb-answer.is-winner{border-color:#2f8f5a;background:linear-gradient(160deg,#123b2a,#0d2118)}
.bb-answer.is-zap{border-color:var(--gold);background:linear-gradient(160deg,#3b2f0d,#241d08)}
.bb-answer .bb-letter{position:absolute;top:14px;right:14px;display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:var(--blue);color:white;font:800 18px/1 system-ui,sans-serif}
.bb-answer p{margin:0;padding-right:36px;font:800 clamp(16px,2.1vw,30px)/1.22 "Plus Jakarta Sans",system-ui,sans-serif;overflow-wrap:anywhere}
.bb-answer footer{display:flex;align-items:center;justify-content:space-between;gap:10px;color:var(--muted);font-size:clamp(12px,1.2vw,17px)}
.bb-answer footer b{color:var(--text)}
.bb-votes{display:flex;flex-wrap:wrap;gap:8px;justify-content:center;max-width:1000px}
.bb-chip{display:flex;align-items:center;gap:7px;padding:7px 11px;border:1px solid #2a3b57;border-radius:999px;background:#0d1728;color:var(--muted);font-size:clamp(11px,1.1vw,15px)}
.bb-chip.is-done{border-color:#2f8f5a;color:#c8ffd9;background:#0d2a1b}
.bb-chip .dot{width:7px;height:7px;border-radius:50%;background:#4b5b74}
.bb-chip.is-done .dot{background:var(--success)}
.bb-players{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px;width:100%;max-width:1100px}
.bb-player{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:11px 13px;border:1px solid #2a3b57;border-radius:14px;background:#0d1728;font-size:clamp(12px,1.2vw,16px)}
.bb-player b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.bb-player span{color:var(--muted);font-size:.9em;white-space:nowrap}
.bb-player.is-done{border-color:#2f8f5a;background:#0d2a1b}
.bb-player.is-done span{color:var(--success)}
.bb-scoreboard{width:100%;max-width:900px;display:grid;gap:8px}
.bb-score{display:grid;grid-template-columns:38px minmax(0,1fr) auto;align-items:center;gap:12px;padding:12px 16px;border:1px solid #2a3b57;border-radius:14px;background:#0d1728;font-size:clamp(14px,1.5vw,20px)}
.bb-score .rank{display:grid;place-items:center;width:30px;height:30px;border-radius:9px;background:#1a2a44;color:var(--blue2);font:800 14px/1 system-ui,sans-serif}
.bb-score:first-child .rank{background:var(--gold);color:#201500}
.bb-score b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.bb-score strong{color:var(--gold);font-variant-numeric:tabular-nums}
.bb-footer{display:flex;align-items:center;justify-content:space-between;gap:16px;border-top:1px solid var(--line);padding-top:12px;color:var(--muted);font-size:clamp(11px,1.1vw,15px)}
.bb-podium{display:flex;flex-direction:column;align-items:center;gap:12px;text-align:center}
.bb-podium .trophy{font-size:clamp(44px,8vw,90px);line-height:1}
.bb-podium h2{font:800 clamp(28px,4vw,58px)/1.05 "Plus Jakarta Sans",system-ui,sans-serif;margin:0;color:var(--gold)}
.bb-podium p{color:var(--muted);font-size:clamp(14px,1.5vw,20px);margin:0}
.bb-empty{color:var(--muted);font-size:clamp(18px,2.4vw,30px);text-align:center;max-width:30ch;line-height:1.35}
@keyframes bb-rise{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
@container(max-height:620px){.bb-host{padding:16px 24px;gap:10px}.bb-title{font-size:clamp(24px,3.6vw,46px)}.bb-helper{font-size:13px;margin-top:6px}.bb-answer{min-height:100px;padding:12px}.bb-answer p{font-size:clamp(14px,1.8vw,23px)}.bb-narrator{padding:8px 12px;font-size:12px}.bb-footer{padding-top:7px;font-size:11px}}
@container(max-width:760px){.bb-host{padding:18px;gap:12px}.bb-answers{grid-template-columns:1fr}.bb-answer{min-height:88px}.bb-title{font-size:clamp(24px,6vw,40px)}.bb-footer{flex-direction:column;align-items:flex-start;gap:4px}}
@media(prefers-reduced-motion:reduce){.bb-heading,.bb-answer{animation:none}}
`;

export function mountBanterboltHost(input: unknown, source: HostGameStateSource): () => void {
  const root = input as HTMLElement;
  const style = el("style");
  style.textContent = styles;
  root.classList.add("bb-mount");

  let lastKey = "";
  let latest: HostState | null = null;
  let timerMs: number | null = null;
  let lastAudioKey = "";
  const narratorCounts = new Map<string, number>();
  const narratorOffset = Math.floor(Math.random() * 10);
  const narratorAudio = document.createElement("audio");
  narratorAudio.preload = "auto";
  narratorAudio.style.display = "none";
  let timerAt = performance.now();
  let paused = false;
  let previousGame: HostState["game"];

  const updateTimer = () => {
    const clock = root.querySelector<HTMLElement>(".bb-timer");
    if (!clock || timerMs === null) return;
    const left = Math.max(0, timerMs - (paused ? 0 : performance.now() - timerAt));
    clock.textContent = `${Math.ceil(left / 1000)} s`;
  };

  const draw = (raw: unknown) => {
    const app = raw as HostState;
    latest = app;
    const game = app.game?.state as BanterboltPublicState | undefined;
    const nextPaused = Boolean(app.room?.pausedBy);
    if (app.game !== previousGame) {
      timerMs = game?.remainingMs ?? null;
      timerAt = performance.now();
      previousGame = app.game;
    } else if (paused !== nextPaused) {
      if (!paused && timerMs !== null) timerMs = Math.max(0, timerMs - (performance.now() - timerAt));
      timerAt = performance.now();
    }
    paused = nextPaused;

    const stage = game?.stage ?? "write";
    const phase = app.game?.phase;
    const audioMoment = stage === "reveal"
      ? (game?.currentMatchup?.zapWriterId ? "zap" : "reveal")
      : stage === "finale_reveal" || stage === "finished"
        ? "podium"
        : stage === "finale_write"
          ? "finale"
          : stage;
    const audioKey = `${audioMoment}:${game?.round ?? 0}:${game?.matchupIndex ?? 0}`;
    if (game && audioKey !== lastAudioKey) {
      lastAudioKey = audioKey;
      const audioCount = narratorCounts.get(audioMoment) ?? 0;
      narratorCounts.set(audioMoment, audioCount + 1);
      const narratorVariant = (narratorOffset + audioCount) % 10;
      narratorAudio.src = `/banterbolt/narrator/narrator_${audioMoment}_${narratorVariant}.m4a`;
      narratorAudio.currentTime = 0;
      void narratorAudio.play().catch(() => {});
    }
    const key = JSON.stringify([game, phase, paused]);
    if (key === lastKey) {
      updateTimer();
      return;
    }
    lastKey = key;

    const shell = el("main", "bb-host");
    const top = el("header", "bb-top");
    const brand = el("div", "bb-brand");
    brand.append(document.createTextNode("Banter"), el("span", undefined, "bolt"));
    top.append(brand, el("div", "bb-round", `Ronda ${game?.round ?? 1} / ${game?.totalRounds ?? 2}`));

    const body = el("section", "bb-body");
    const heading = el("div", "bb-heading");
    const stageBox = el("section", "bb-stage");
    const players = game?.players ?? [];
    const submittedCount = game?.submittedCount ?? 0;
    const playerCount = game?.playerCount ?? players.length;
    const narrator = game?.narrator ?? "";
    let kicker = "VISION+ PARTY";
    let title = "Banterbolt";
    let helper = "Escriban, voten y ríanse. Así de simple.";
    let showTimer = false;

    if (stage === "write") {
      kicker = "RONDA DE ESCRITURA";
      title = "Saquen la ocurrencia";
      helper = "Cada persona escribe dos respuestas en el celular. La tele solo muestra quién ya terminó.";
    } else if (stage === "vote") {
      kicker = "VOTACIÓN A CIEGAS";
      title = "¿Cuál estuvo mejor?";
      helper = "Voten A o B en el celular. No se vale votar por la propia.";
      showTimer = true;
    } else if (stage === "reveal") {
      kicker = game?.currentMatchup?.zapWriterId ? "¡ZAP! BARRIDA TOTAL" : "REVELACIÓN";
      title = game?.currentMatchup?.zapWriterId ? "Se pasaron de buenos" : "La verdad sale a la luz";
      helper = "Aquí están los votos, los autores y los puntos.";
    } else if (stage === "scoreboard") {
      kicker = "PUNTOS PARCIALES";
      title = "Así va la tabla";
      helper = "Todavía hay tiempo de remontar.";
    } else if (stage === "finale_write") {
      kicker = "RONDA RELÁMPAGO";
      title = "Todos al mismo prompt";
      helper = "La última oportunidad de escribir algo memorable.";
    } else if (stage === "finale_vote") {
      kicker = "RONDA RELÁMPAGO";
      title = "Vota por la mejor";
      helper = "Una sola respuesta se lleva la gloria.";
      showTimer = true;
    } else if (stage === "finale_reveal" || stage === "finished") {
      kicker = "FIN DEL JUEGO";
      title = "¡Tenemos campeón!";
      helper = "Que vaya por los tacos.";
    }

    heading.append(el("p", "bb-kicker", kicker), el("h1", "bb-title", title), el("p", "bb-helper", helper));
    if (showTimer) heading.append(el("p", "bb-timer", ""));
    if (narrator) {
      const bubble = el("div", "bb-narrator");
      bubble.append(el("b", undefined, "Narradora:"), document.createTextNode(narrator));
      heading.append(bubble);
    }

    if (stage === "write" || stage === "finale_write") {
      const grid = el("div", "bb-players");
      const submittedIds = new Set(game?.submittedPlayerIds ?? []);
      players.forEach((player) => {
        const done = submittedIds.has(player.id);
        const card = el("div", `bb-player${done ? " is-done" : ""}`);
        card.append(el("b", undefined, player.name), el("span", undefined, done ? "Listo" : "Escribiendo…"));
        grid.append(card);
      });
      stageBox.append(el("p", "bb-empty", `${submittedCount} / ${playerCount} ya escribieron`), grid);
    } else if ((stage === "vote" || stage === "reveal") && game?.currentMatchup) {
      const matchup = game.currentMatchup;
      stageBox.append(el("div", "bb-prompt", `“${matchup.promptText}”`));
      const answers = el("div", "bb-answers");
      matchup.answers.forEach((answer, index) => {
        const votes = matchup.votes?.[index] ?? 0;
        const points = matchup.points?.[index] ?? 0;
        const zap = Boolean(matchup.zapWriterId && answer.writerId === matchup.zapWriterId);
        const winner = Boolean(matchup.votes && votes > (matchup.votes[index === 0 ? 1 : 0] ?? 0));
        const card = el("article", `bb-answer${winner ? " is-winner" : ""}${zap ? " is-zap" : ""}`);
        card.append(el("span", "bb-letter", index === 0 ? "A" : "B"), el("p", undefined, answer.text));
        const footer = el("footer");
        footer.append(
          el("span", undefined, stage === "reveal" && answer.writerName ? `Autor: ${answer.writerName}` : "Respuesta anónima"),
          el("span", undefined, stage === "reveal" ? `${votes} voto${votes === 1 ? "" : "s"} · +${points}` : "Votación abierta"),
        );
        card.append(footer);
        answers.append(card);
      });
      stageBox.append(answers);
      const chips = el("div", "bb-votes");
      (matchup.voterIds ?? []).forEach((id) => {
        const name = players.find((player) => player.id === id)?.name ?? "Jugador";
        const voted = (matchup.votedPlayerIds ?? []).includes(id);
        const chip = el("span", `bb-chip${voted ? " is-done" : ""}`);
        chip.append(el("span", "dot"), document.createTextNode(`${name}${voted ? " · votó" : ""}`));
        chips.append(chip);
      });
      stageBox.append(chips);
    } else if (stage === "scoreboard") {
      stageBox.append(buildScoreboard(players));
    } else if (stage === "finale_vote" && game?.finale) {
      stageBox.append(el("div", "bb-prompt", `“${game.finale.promptText}”`));
      const grid = el("div", "bb-players");
      game.finale.options?.forEach((option) => {
        const card = el("div", "bb-player");
        card.append(el("b", undefined, option.text));
        grid.append(card);
      });
      if (grid.childElementCount === 0) grid.append(el("p", "bb-empty", "Esperando respuestas…"));
      stageBox.append(grid);
    } else if (stage === "finale_reveal" && game?.finale?.answers) {
      const list = el("div", "bb-scoreboard");
      [...game.finale.answers].sort((a, b) => a.rank - b.rank).forEach((answer, index) => {
        const row = el("div", "bb-score");
        row.append(
          el("span", "rank", String(index + 1)),
          el("b", undefined, answer.text),
          el("strong", undefined, `+${answer.points}`),
        );
        list.append(row);
      });
      stageBox.append(list);
    } else if (stage === "finished") {
      const podium = el("div", "bb-podium");
      podium.append(el("div", "trophy", "🏆"));
      const winners = (game?.winnerIds ?? []).map((id) => players.find((player) => player.id === id)?.name).filter(Boolean);
      podium.append(el("h2", undefined, winners.length ? winners.join(" y ") : "Empate"));
      podium.append(el("p", undefined, "Gracias por jugar Banterbolt. La revancha es en la misma sala."));
      podium.append(buildScoreboard(players));
      stageBox.append(podium);
    } else {
      stageBox.append(el("p", "bb-empty", "Preparando la siguiente ronda…"));
    }

    body.append(heading, stageBox);
    const footer = el("footer", "bb-footer");
    footer.append(
      el("span", undefined, "Vision+ Party · Banterbolt"),
      el("span", undefined, phase === "finished" ? "Partida terminada · márquense listos en el celular" : "La narradora y la tele llevan el ritmo"),
    );
    shell.append(top, body, footer);
    root.replaceChildren(style, shell, narratorAudio);
    updateTimer();
  };

  function buildScoreboard(players: BanterboltPublicState["players"]): HTMLElement {
    const list = el("div", "bb-scoreboard");
    [...players]
      .sort((a, b) => b.points - a.points)
      .forEach((player, index) => {
        const row = el("div", "bb-score");
        row.append(el("span", "rank", String(index + 1)), el("b", undefined, player.name), el("strong", undefined, `${player.points} pts`));
        list.append(row);
      });
    return list;
  }

  const unsubscribe = source.subscribe(draw);
  const initial = source.getState();
  if (initial) draw(initial);
  const resize = new ResizeObserver(() => {
    if (latest) {
      lastKey = "";
      draw(latest);
    }
  });
  resize.observe(root);
  const ticker = window.setInterval(updateTimer, 200);

  return () => {
    unsubscribe();
    resize.disconnect();
    window.clearInterval(ticker);
    root.replaceChildren();
    root.classList.remove("bb-mount");
  };
}

export const hostGame = {
  id: banterboltManifest.id,
  displayName: banterboltManifest.displayName,
  mountDom: mountBanterboltHost,
} satisfies HostGame;
