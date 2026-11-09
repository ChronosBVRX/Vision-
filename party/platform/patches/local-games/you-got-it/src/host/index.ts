import type { HostGame, HostGameStateSource } from "@open-party-lab/game-core";
import { youGotItManifest } from "../manifest.js";
import type { YouGotItPublicState } from "../protocol.js";
import type { SeatIndex } from "../merky-sdk.js";

interface HostState {
  game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  room?: { language?: "de" | "en" | "es"; pausedBy?: unknown; players?: Array<{ id: string; name?: string; connected?: boolean; isReady?: boolean }> } | null;
}

const el = (tag: string, className?: string, value?: string): HTMLElement => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value !== undefined) node.textContent = value;
  return node;
};

const styles = `
.yg-mount{position:absolute;inset:0;overflow:hidden;container-type:size}
.yg-host{--bg:#070B13;--surface:#0F1626;--surface2:#1B2538;--line:#26334A;--text:#F7FAFF;--muted:#A9B4C6;--blue:#2D7DF6;--blue2:#6BA6FF;--gold:#F5C518;--bass:#6BA6FF;--treble:#F5C518;box-sizing:border-box;width:100%;height:100%;padding:clamp(22px,3.5vw,56px);display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:clamp(14px,2.4vh,26px);background:radial-gradient(circle at 15% 0%,#14305a 0,transparent 38%),radial-gradient(circle at 90% 100%,#2a1648 0,transparent 40%),var(--bg);color:var(--text);font-family:"Plus Jakarta Sans",Inter,system-ui,sans-serif;overflow:hidden}
.yg-host *{box-sizing:border-box}
.yg-top{display:flex;align-items:center;justify-content:space-between;gap:18px;border-bottom:1px solid var(--line);padding-bottom:14px}
.yg-brand{font:800 clamp(24px,3vw,38px)/1 "Plus Jakarta Sans",system-ui;letter-spacing:-.04em}.yg-brand span{color:var(--blue2)}
.yg-round{font:700 12px/1 system-ui;letter-spacing:.16em;text-transform:uppercase;color:var(--muted);padding:10px 14px;border:1px solid var(--line);border-radius:999px;background:#0b1220aa}
.yg-body{min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr);gap:clamp(12px,2vh,22px)}
.yg-heading{text-align:center;max-width:1100px;margin:auto;animation:yg-rise .35s both}
.yg-kicker{margin:0 0 8px;font:800 clamp(11px,1.1vw,15px)/1 system-ui;letter-spacing:.18em;text-transform:uppercase;color:var(--blue2)}
.yg-title{font:800 clamp(28px,4.6vw,64px)/1.05 "Plus Jakarta Sans",system-ui;letter-spacing:-.04em;margin:0;text-wrap:balance;overflow-wrap:anywhere}
.yg-helper{margin:12px auto 0;max-width:70ch;color:var(--muted);font-size:clamp(14px,1.5vw,21px);line-height:1.45;text-wrap:balance}
.yg-narrator{margin:14px auto 0;max-width:900px;display:flex;align-items:center;justify-content:center;gap:12px;padding:12px 18px;border:1px solid #2b3f61;border-radius:999px;background:#0d1a2ecc;color:#dce8ff;font-size:clamp(13px,1.35vw,19px);line-height:1.35;text-align:center}
.yg-narrator b{color:var(--blue2);font-weight:800;white-space:nowrap}
.yg-stage{min-height:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:clamp(12px,2vh,20px)}
.yg-prompt{max-width:1100px;width:100%;padding:clamp(14px,2vw,24px);border:1px solid #2b4a78;border-radius:22px;background:linear-gradient(180deg,#101d33,#0c1524);box-shadow:0 20px 50px #0007;text-align:center;font:800 clamp(20px,3vw,40px)/1.15 "Plus Jakarta Sans",system-ui;letter-spacing:-.03em}
.yg-clue{color:var(--gold);font:800 clamp(20px,2.8vw,38px)/1.2 "Plus Jakarta Sans",system-ui;text-align:center;margin:0}
.yg-spectrum{position:relative;width:min(1100px,92vw);height:clamp(70px,12vh,120px);border-radius:22px;border:1px solid #2c4468;background:linear-gradient(90deg,#10223d,#1d4a86 50%,#10223d);box-shadow:inset 0 10px 32px #0007}
.yg-marker{position:absolute;top:8px;bottom:8px;width:4px;transform:translateX(-50%);border-radius:4px}
.yg-pointer{position:absolute;top:0;bottom:0;width:4px;transform:translateX(-50%);background:#fff;box-shadow:0 0 16px #6ba6ff}
.yg-knob{position:absolute;top:50%;width:26px;height:26px;transform:translate(-50%,-50%);border-radius:50%;background:#fff;border:4px solid var(--blue);box-shadow:0 3px 12px #0008}
.yg-target{background:var(--gold);box-shadow:0 0 16px #f5c518aa}
.yg-labels{display:flex;justify-content:space-between;width:min(1100px,92vw);gap:18px;color:var(--muted);font:700 clamp(12px,1.2vw,18px)/1.2 system-ui;text-transform:uppercase;letter-spacing:.05em}
.yg-teams{display:grid;grid-template-columns:1fr 1fr;gap:clamp(10px,1.5vw,20px);width:min(1100px,92vw)}
.yg-team{padding:14px 18px;border:1px solid #2a3b57;border-radius:16px;background:#0d1728;display:flex;align-items:center;justify-content:space-between;gap:10px}
.yg-team.is-active{border-color:var(--blue);background:#102344;box-shadow:0 0 0 1px #2D7DF6 inset}
.yg-team b{font-size:clamp(13px,1.3vw,18px)}.yg-team strong{font:800 clamp(20px,2.4vw,34px)/1 ui-monospace,monospace;color:var(--gold)}
.yg-people{display:flex;flex-wrap:wrap;gap:8px;justify-content:center;max-width:1000px}
.yg-chip{display:flex;align-items:center;gap:6px;padding:6px 10px;border:1px solid #2a3b57;border-radius:999px;background:#0d1728;color:var(--muted);font-size:clamp(11px,1.05vw,15px)}
.yg-chip.is-done{border-color:#2f8f5a;color:#c8ffd9;background:#0d2a1b}
.yg-footer{display:flex;align-items:center;justify-content:space-between;gap:16px;border-top:1px solid var(--line);padding-top:12px;color:var(--muted);font-size:clamp(11px,1.1vw,15px)}
.yg-podium{display:flex;flex-direction:column;align-items:center;gap:12px;text-align:center}
.yg-podium h2{margin:0;color:var(--gold);font:800 clamp(30px,4.4vw,64px)/1.05 "Plus Jakarta Sans",system-ui}
.yg-podium p{margin:0;color:var(--muted);font-size:clamp(14px,1.5vw,20px)}
.yg-scoreboard{display:grid;gap:8px;width:min(900px,90vw)}
.yg-score{display:grid;grid-template-columns:42px minmax(0,1fr) auto;align-items:center;gap:12px;padding:12px 16px;border:1px solid #2a3b57;border-radius:14px;background:#0d1728;font-size:clamp(14px,1.5vw,20px)}
.yg-score .rank{display:grid;place-items:center;width:30px;height:30px;border-radius:9px;background:#1a2a44;color:var(--blue2);font:800 14px/1 system-ui}
.yg-score:first-child .rank{background:var(--gold);color:#201500}
.yg-score strong{color:var(--gold);font-variant-numeric:tabular-nums}
@keyframes yg-rise{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
@container(max-height:620px){.yg-host{padding:16px 24px;gap:10px}.yg-title{font-size:clamp(24px,3.6vw,44px)}.yg-helper{font-size:13px;margin-top:6px}.yg-narrator{padding:8px 12px;font-size:12px}.yg-spectrum{height:70px}.yg-footer{padding-top:7px;font-size:11px}}
@container(max-width:760px){.yg-host{padding:18px;gap:12px}.yg-title{font-size:clamp(24px,6vw,38px)}.yg-teams{grid-template-columns:1fr}.yg-footer{flex-direction:column;align-items:flex-start;gap:4px}}
@media(prefers-reduced-motion:reduce){.yg-heading{animation:none}}
`;

export function mountYouGotItHost(input: unknown, source: HostGameStateSource): () => void {
  const root = input as HTMLElement;
  const style = el("style");
  style.textContent = styles;
  root.classList.add("yg-mount");
  let lastKey = "";
  let latest: HostState | null = null;
  let lastAudioKey = "";
  const narratorCounts = new Map<string, number>();
  const narratorOffset = Math.floor(Math.random() * 10);
  const narratorAudio = document.createElement("audio");
  narratorAudio.preload = "auto";
  narratorAudio.style.display = "none";

  const draw = (raw: unknown) => {
    const app = raw as HostState;
    latest = app;
    const game = app.game?.state as YouGotItPublicState | undefined;
    const phase = game?.phase ?? "clue";
    const audioMoment = phase === "game_over" ? "game_over" : phase;
    const roundNumber = app.game?.roundNumber ?? 0;
    const audioKey = `${audioMoment}:${roundNumber}`;
    if (game && audioKey !== lastAudioKey) {
      lastAudioKey = audioKey;
      const audioCount = narratorCounts.get(audioMoment) ?? 0;
      narratorCounts.set(audioMoment, audioCount + 1);
      const narratorVariant = (narratorOffset + audioCount) % 10;
      narratorAudio.src = `/you-got-it/narrator/narrator_${audioMoment}_${narratorVariant}.m4a`;
      narratorAudio.currentTime = 0;
      void narratorAudio.play().catch(() => {});
    }
    const key = JSON.stringify([game, app.game?.phase]);
    if (key === lastKey) return;
    lastKey = key;

    const shell = el("main", "yg-host");
    const top = el("header", "yg-top");
    const brand = el("div", "yg-brand");
    brand.append(document.createTextNode("¿Le atinaste"), el("span", undefined, "?"));
    top.append(brand, el("div", "yg-round", `ESPECTRO · ${phase === "game_over" ? "FINAL" : "TURNO"}`));

    const body = el("section", "yg-body");
    const heading = el("div", "yg-heading");
    const stage = el("section", "yg-stage");
    const active = game?.activeTeam === "treble" ? "Agudos" : "Bajos";
    const scores = game?.teamScores ?? { bass: 0, treble: 0 };
    let kicker = "ESPECTRO";
    let title = "¿Dónde está el objetivo?";
    let helper = "El Oráculo da una pista; su equipo mueve el dial.";

    if (phase === "clue") {
      kicker = `TURNO DE ${active.toUpperCase()}`;
      title = game?.clue ? `“${game.clue}”` : "El Oráculo está afinando…";
      helper = game?.clue ? "El equipo activo ya puede mover el dial." : "Mira la tele. El Oráculo tiene el objetivo secreto en su celular.";
    } else if (phase === "guess") {
      kicker = `TURNO DE ${active.toUpperCase()}`;
      title = game?.clue ? `“${game.clue}”` : "Muevan el dial";
      helper = "El equipo activo mueve el dial; cuando estén de acuerdo, lo bloquean.";
    } else if (phase === "steal") {
      kicker = "UNDERCUT";
      title = "El rival puede robar";
      helper = "El equipo contrario vota si el dial se pasó a la izquierda o a la derecha.";
    } else if (phase === "reveal") {
      kicker = "RESULTADO";
      const points = (game as unknown as { lastTurn?: { points?: number } })?.lastTurn?.points ?? 0;
      title = points > 0 ? `+${points} para ${active}` : "Ni un punto";
      helper = "La línea dorada es el objetivo; la blanca era el dial.";
    } else if (phase === "game_over") {
      kicker = "FIN DEL JUEGO";
      title = game?.winnerTeam === "treble" ? "¡Ganaron los Agudos!" : "¡Ganaron los Bajos!";
      helper = "Que el equipo perdedor invite los tacos.";
    }
    heading.append(el("p", "yg-kicker", kicker), el("h1", "yg-title", title), el("p", "yg-helper", helper));
    if (game?.narrator) {
      const bubble = el("div", "yg-narrator");
      bubble.append(el("b", undefined, "Narradora:"), document.createTextNode(game.narrator));
      heading.append(bubble);
    }

    if (phase !== "game_over") {
      const prompt = el("div", "yg-prompt", game?.prompt ? `“${game.prompt.left}” ↔ “${game.prompt.right}”` : "");
      stage.append(prompt);
      if (game?.clue) stage.append(el("p", "yg-clue", `Pista: “${game.clue}”`));
      const spectrum = el("div", "yg-spectrum");
      const pointer = Math.max(0, Math.min(100, ((game?.pointerAngle ?? 90) / 180) * 100));
      const marker = el("div", "yg-pointer");
      marker.style.left = `${pointer}%`;
      spectrum.append(marker);
      const knob = el("div", "yg-knob");
      knob.style.left = `${pointer}%`;
      spectrum.append(knob);
      if ((phase === "reveal" || phase === "game_over") && typeof game?.targetAngle === "number") {
        const target = el("div", "yg-marker yg-target");
        target.style.left = `${Math.max(0, Math.min(100, (game.targetAngle / 180) * 100))}%`;
        spectrum.append(target);
      }
      stage.append(spectrum);
      const labels = el("div", "yg-labels", "");
      labels.append(el("span", undefined, game?.prompt?.left ?? "Menos"), el("span", undefined, game?.prompt?.right ?? "Más"));
      stage.append(labels);
    }

    const teams = el("div", "yg-teams");
    for (const team of ["bass", "treble"] as const) {
      const card = el("div", `yg-team${game?.activeTeam === team ? " is-active" : ""}`);
      card.append(el("b", undefined, team === "bass" ? "Bajos" : "Agudos"), el("strong", undefined, String(scores[team] ?? 0)));
      teams.append(card);
    }
    stage.append(teams);

    if (phase === "game_over" && game?.seatPlayers) {
      const board = el("div", "yg-scoreboard");
      [...game.seatPlayers].sort((a, b) => (scores[bTeam(game, b.seat)] ?? 0) - (scores[bTeam(game, a.seat)] ?? 0)).forEach((player, index) => {
        const row = el("div", "yg-score");
        row.append(el("span", "rank", String(index + 1)), el("b", undefined, player.name), el("strong", undefined, `${scores[bTeam(game, player.seat)] ?? 0} pts`));
        board.append(row);
      });
      stage.append(board);
    }

    body.append(heading, stage);
    const footer = el("footer", "yg-footer");
    footer.append(el("span", undefined, "Vision+ Party · ¿Le atinaste?"), el("span", undefined, `Primero a ${game?.targetScore ?? 10} puntos · ${active} juega ahora`));
    shell.append(top, body, footer);
    root.replaceChildren(style, shell, narratorAudio);
  };

  function bTeam(game: YouGotItPublicState, seat: SeatIndex): "bass" | "treble" {
    return game.teams.bass.includes(seat) ? "bass" : "treble";
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
  return () => {
    unsubscribe();
    resize.disconnect();
    root.replaceChildren();
    root.classList.remove("yg-mount");
  };
}

export const hostGame = {
  id: youGotItManifest.id,
  displayName: youGotItManifest.displayName,
  mountDom: mountYouGotItHost,
} satisfies HostGame;
