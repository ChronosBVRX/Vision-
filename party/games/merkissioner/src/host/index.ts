import type { HostGame, HostGameStateSource } from "@open-party-lab/game-core";
import { merkissionerManifest } from "../manifest.js";
import type { MerkissionerPublicView } from "../protocol.js";

interface HostState {
  game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  room?: { language?: "de" | "en" | "es"; pausedBy?: unknown } | null;
}

const el = (tag: string, className?: string, value?: string): HTMLElement => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value !== undefined) node.textContent = value;
  return node;
};

const styles = `
.mk-mount{position:absolute;inset:0;overflow:hidden;container-type:size}
.mk-host{--bg:#070B13;--surface:#0F1626;--surface2:#1B2538;--line:#26334A;--text:#F7FAFF;--muted:#A9B4C6;--blue:#2D7DF6;--blue2:#6BA6FF;--gold:#F5C518;--red:#C0392B;--green:#2E8B57;box-sizing:border-box;width:100%;height:100%;padding:clamp(20px,3.4vw,52px);display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:clamp(12px,2vh,22px);background:linear-gradient(180deg,#070b13b8,#070b13ea),radial-gradient(circle at 15% 0%,#14305a 0,transparent 38%),radial-gradient(circle at 90% 100%,#2a1648 0,transparent 40%),url('/merkissioner/ambient.webp') center/cover no-repeat,var(--bg);color:var(--text);font-family:"Plus Jakarta Sans",Inter,system-ui,sans-serif;overflow:hidden}
.mk-host *{box-sizing:border-box}
.mk-top{display:flex;align-items:center;justify-content:space-between;gap:18px;border-bottom:1px solid var(--line);padding-bottom:12px}
.mk-brand{font:800 clamp(22px,3vw,36px)/1 "Plus Jakarta Sans",system-ui;letter-spacing:-.04em}.mk-brand span{color:var(--blue2)}
.mk-pill{font:700 11px/1 system-ui;letter-spacing:.14em;text-transform:uppercase;color:var(--muted);padding:9px 13px;border:1px solid var(--line);border-radius:999px;background:#0b1220aa}
.mk-body{min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr);gap:clamp(10px,1.8vh,18px)}
.mk-heading{text-align:center;max-width:1100px;margin:auto;animation:mk-rise .35s both}
.mk-kicker{margin:0 0 6px;font:800 clamp(11px,1.05vw,14px)/1 system-ui;letter-spacing:.18em;text-transform:uppercase;color:var(--blue2)}
.mk-title{font:800 clamp(26px,4.2vw,58px)/1.05 "Plus Jakarta Sans",system-ui;letter-spacing:-.04em;margin:0;text-wrap:balance;overflow-wrap:anywhere}
.mk-helper{margin:8px auto 0;max-width:74ch;color:var(--muted);font-size:clamp(13px,1.4vw,19px);line-height:1.4;text-wrap:balance}
.mk-narrator{margin:10px auto 0;max-width:900px;display:flex;align-items:center;justify-content:center;gap:10px;padding:10px 16px;border:1px solid #2b3f61;border-radius:999px;background:#0d1a2ecc;color:#dce8ff;font-size:clamp(12px,1.25vw,17px);line-height:1.35;text-align:center}
.mk-narrator b{color:var(--blue2);font-weight:800;white-space:nowrap}
.mk-stage{min-height:0;display:grid;grid-template-columns:minmax(0,1.25fr) minmax(300px,.75fr);gap:clamp(12px,1.8vw,22px)}
.mk-board{display:grid;gap:14px;align-content:start}
.mk-track{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px}
.mk-track.merkite{grid-template-columns:repeat(6,minmax(0,1fr))}
.mk-slot{min-height:clamp(58px,8vh,92px);border:1px solid #2a3b57;border-radius:12px;background:#0d1728;display:grid;place-items:center;gap:4px;padding:8px;text-align:center;font-size:clamp(10px,.95vw,13px);color:var(--muted)}
.mk-slot.is-on{border-color:#2f8f5a;background:#0d2a1b;color:#c8ffd9}
.mk-slot.is-merkite.is-on{border-color:#a33b3b;background:#2a0d0d;color:#ffd6d6}
.mk-slot b{font-size:clamp(11px,1.1vw,15px);color:var(--text)}
.mk-slot.is-on b{color:inherit}
.mk-slot small{font-size:9px;line-height:1.1;text-transform:uppercase;letter-spacing:.05em}
.mk-players{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:8px}
.mk-player{position:relative;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:10px 12px;border:1px solid #2a3b57;border-radius:12px;background:#0d1728;font-size:clamp(12px,1.15vw,16px)}
.mk-player b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mk-player .badge{font-size:9px;text-transform:uppercase;letter-spacing:.06em;color:var(--gold);white-space:nowrap}
.mk-player.is-chair{border-color:var(--gold);background:#2a2208}
.mk-player.is-commissioner{border-color:var(--blue);background:#0d1a33}
.mk-player.is-banished{opacity:.45;text-decoration:line-through}
.mk-player .vote{font:800 13px ui-monospace,monospace;color:var(--green)}
.mk-player .vote.nah{color:var(--red)}
.mk-side{display:grid;gap:10px;align-content:start;min-height:0;overflow:auto;padding-right:2px}
.mk-card{border:1px solid #2a3b57;border-radius:14px;background:#0d1728;padding:12px 14px;display:grid;gap:4px}
.mk-card .label{font:800 10px/1 system-ui;letter-spacing:.12em;text-transform:uppercase;color:var(--muted)}
.mk-card b{font-size:clamp(14px,1.4vw,19px);color:var(--text)}
.mk-card span{font-size:clamp(12px,1.2vw,16px);color:var(--muted)}
.mk-over{display:flex;flex-direction:column;align-items:center;gap:10px;text-align:center;padding-top:6px}
.mk-over h2{margin:0;font:800 clamp(28px,4.2vw,58px)/1.05 "Plus Jakarta Sans",system-ui;color:var(--gold)}
.mk-over p{margin:0;color:var(--muted);font-size:clamp(13px,1.4vw,19px)}
.mk-roles{display:flex;flex-wrap:wrap;gap:8px;justify-content:center;max-width:900px}
.mk-role{padding:8px 11px;border:1px solid #2a3b57;border-radius:999px;background:#0d1728;font-size:clamp(11px,1.1vw,15px)}
.mk-role b{color:var(--blue2)}
.mk-footer{display:flex;align-items:center;justify-content:space-between;gap:14px;border-top:1px solid var(--line);padding-top:10px;color:var(--muted);font-size:clamp(10px,1vw,14px)}
@keyframes mk-rise{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
@container(max-height:650px){.mk-host{padding:14px 22px;gap:9px}.mk-title{font-size:clamp(22px,3.3vw,42px)}.mk-helper{font-size:12px;margin-top:5px}.mk-narrator{padding:7px 10px;font-size:11px}.mk-slot{min-height:48px}.mk-card{padding:8px 10px}.mk-footer{padding-top:6px;font-size:10px}}
@container(max-width:780px){.mk-host{padding:16px;gap:10px}.mk-stage{grid-template-columns:1fr;overflow:auto}.mk-title{font-size:clamp(22px,5.5vw,36px)}.mk-track,.mk-track.merkite{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(prefers-reduced-motion:reduce){.mk-heading{animation:none}}
`;

const boardPowers = {
  "5-6": ["", "", "Vistazo", "Expulsión", "Expulsión", "Veto"],
  "7-8": ["", "Auditoría", "Elección", "Expulsión", "Expulsión", "Veto"],
} as const;

export function mountMerkissionerHost(input: unknown, source: HostGameStateSource): () => void {
  const root = input as HTMLElement;
  const style = el("style");
  style.textContent = styles;
  root.classList.add("mk-mount");
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
    const game = app.game?.state as MerkissionerPublicView | undefined;
    const phase = game?.phase ?? "huddle";
    const audioMoment = phase === "huddle" ? "huddle"
      : phase === "nominate" ? "nominate"
        : phase === "vote" ? "vote"
          : phase.startsWith("legislative") || phase === "veto_pending" ? "legislature"
            : phase.startsWith("power_") ? "power"
              : phase === "game_over" ? "game_over"
                : "huddle";
    const roundNumber = game?.roundNumber ?? 0;
    const audioKey = `${audioMoment}:${roundNumber}`;
    if (game && audioKey !== lastAudioKey) {
      lastAudioKey = audioKey;
      const audioCount = narratorCounts.get(audioMoment) ?? 0;
      narratorCounts.set(audioMoment, audioCount + 1);
      const narratorVariant = (narratorOffset + audioCount) % 10;
      narratorAudio.src = `/merkissioner/narrator/narrator_${audioMoment}_${narratorVariant}.m4a`;
      narratorAudio.currentTime = 0;
      void narratorAudio.play().catch(() => {});
    }
    const key = JSON.stringify([game, app.game?.phase]);
    if (key === lastKey) return;
    lastKey = key;

    const shell = el("main", "mk-host");
    const top = el("header", "mk-top");
    const brand = el("div", "mk-brand");
    brand.append(document.createTextNode("El Comisionado Merk"));
    top.append(brand, el("div", "mk-pill", `Ronda ${game?.roundNumber ?? 0} · ${game?.playerCount ?? 0} jugadores`));

    const body = el("section", "mk-body");
    const heading = el("div", "mk-heading");
    const stage = el("section", "mk-stage");
    const board = el("div", "mk-board");
    const side = el("aside", "mk-side");
    const p = game;

    let kicker = "CONSEJO SECRETO";
    let title = "El consejo se reúne";
    let helper = "Revisen su rol en el celular. Nadie enseñe la pantalla.";
    if (phase === "huddle") {
      title = "Reunión secreta";
      helper = "Cada quien ve su rol y sus aliados en el celular. Cuando todos estén listos, empieza la nominación.";
    } else if (phase === "nominate") {
      kicker = "NOMINACIÓN";
      title = `${nameOf(p, p?.chairSeat)} nomina a su comisionado`;
      helper = "La silla elige a alguien del consejo. Después todos votan.";
    } else if (phase === "vote") {
      kicker = "VOTACIÓN";
      title = "¿Aprueban el gobierno?";
      helper = `Silla: ${nameOf(p, p?.chairSeat)} · Comisionado: ${p?.nomineeSeat !== null && p?.nomineeSeat !== undefined ? nameOf(p, p.nomineeSeat) : "—"}`;
    } else if (phase === "legislative_chair") {
      kicker = "SESIÓN LEGISLATIVA";
      title = "La silla recibe tres decretos";
      helper = "Descarta uno en el celular; los otros dos pasan al comisionado.";
    } else if (phase === "legislative_commissioner") {
      kicker = "SESIÓN LEGISLATIVA";
      title = "El comisionado decide";
      helper = "Aprueba uno de los dos decretos en el celular.";
    } else if (phase === "veto_pending") {
      kicker = "VETO";
      title = "Veto en la mesa";
      helper = "La silla decide si acepta el veto del comisionado.";
    } else if (phase.startsWith("power_")) {
      kicker = "PODER";
      title = phase === "power_audit" ? "Auditoría" : phase === "power_snap" ? "Elección anticipada" : phase === "power_peek" ? "Vistazo al mazo" : "Expulsión";
      helper = "La silla usa el poder en el celular. El consejo observa.";
    } else if (phase === "game_over") {
      kicker = "FIN DEL JUEGO";
      title = p?.winnerTeam === "merkite" ? "Ganaron los Merkites" : "Ganaron los Merkizens";
      helper = p?.winReason === "boss_elected" ? "El Comisionado Merk fue elegido comisionado." : p?.winReason === "boss_banished" ? "El Comisionado Merk fue expulsado." : "Los decretos decidieron la partida.";
    }

    heading.append(el("p", "mk-kicker", kicker), el("h1", "mk-title", title), el("p", "mk-helper", helper));
    if (p?.narrator) {
      const bubble = el("div", "mk-narrator");
      bubble.append(el("b", undefined, "Narradora:"), document.createTextNode(p.narrator));
      heading.append(bubble);
    }

    if (phase === "game_over" && p?.revealedRoles) {
      const over = el("div", "mk-over");
      over.append(el("h2", undefined, title), el("p", undefined, helper));
      const roles = el("div", "mk-roles");
      for (const player of p.seatPlayers) {
        roles.append(el("span", "mk-role", `${player.name}: ${roleLabel(p.revealedRoles[player.seat] ?? "merkizen")}`));
      }
      over.append(roles);
      stage.append(over);
    } else {
      board.append(renderTrack(p, "merkizen", 5, "Merkizen"));
      board.append(renderTrack(p, "merkite", 6, "Merkite"));
      const players = el("div", "mk-players");
      for (const player of p?.seatPlayers ?? []) {
        const card = el("div", `mk-player${player.seat === p?.chairSeat ? " is-chair" : ""}${player.seat === p?.commissionerSeat ? " is-commissioner" : ""}${p?.banishedSeats.includes(player.seat) ? " is-banished" : ""}`);
        card.append(el("b", undefined, player.name));
        const badges = [];
        if (player.seat === p?.chairSeat) badges.push("Silla");
        if (player.seat === p?.commissionerSeat) badges.push("Comisionado");
        if (p?.banishedSeats.includes(player.seat)) badges.push("Expulsado");
        if (p?.votedSeats.includes(player.seat) && phase === "vote") badges.push("Votó");
        if (badges.length) card.append(el("span", "badge", badges.join(" · ")));
        players.append(card);
      }
      board.append(players);
      stage.append(board);
    }

    if (p) {
      const votes = p.lastVote;
      side.append(card("Silla", nameOf(p, p.chairSeat)));
      side.append(card("Comisionado", p.commissionerSeat === null ? "—" : nameOf(p, p.commissionerSeat)));
      side.append(card("Mazo", `${p.drawCount} cartas · descarte ${p.discardCount}`));
      side.append(card("Gobiernos fallidos", `${p.gridlock} / 3`));
      if (votes) side.append(card("Última votación", `${votes.tally.yeah} SÍ · ${votes.tally.nah} NO · ${votes.passed ? "aprobado" : "rechazado"}`));
      if (p.lastEnacted) side.append(card("Último decreto", `${p.lastEnacted.type === "merkite" ? "Merkite" : "Merkizen"}${p.lastEnacted.viaAnarchy ? " · anarquía" : ""}`));
      if (p.activePower) side.append(card("Poder activo", p.activePower));
      if (p.lastAudit) side.append(card("Auditoría", `${nameOf(p, p.lastAudit.by)} → ${nameOf(p, p.lastAudit.target)}`));
      if (p.lastBanish) side.append(card("Expulsión", `${nameOf(p, p.lastBanish.by)} → ${nameOf(p, p.lastBanish.target)}`));
      stage.append(side);
    }

    body.append(heading, stage);
    const footer = el("footer", "mk-footer");
    footer.append(el("span", undefined, "Vision+ Party · El Comisionado Merk"), el("span", undefined, "Lean su rol. Nadie confía en nadie."));
    shell.append(top, body, footer);
    root.replaceChildren(style, shell, narratorAudio);
  };

  function card(label: string, value: string): HTMLElement {
    const box = el("div", "mk-card");
    box.append(el("span", "label", label), el("b", undefined, value));
    return box;
  }

  function nameOf(game: MerkissionerPublicView | undefined, seat: number | null | undefined): string {
    if (seat === null || seat === undefined) return "—";
    return game?.seatPlayers.find((player) => player.seat === seat)?.name ?? "—";
  }

  function roleLabel(role: string): string {
    return role === "merkissioner" ? "El Comisionado Merk" : role === "merkite" ? "Merkite" : "Merkizen";
  }

  function renderTrack(game: MerkissionerPublicView | undefined, kind: "merkizen" | "merkite", count: number, label: string): HTMLElement {
    const track = el("div", `mk-track${kind === "merkite" ? " merkite" : ""}`);
    const enacted = kind === "merkizen" ? game?.merkizenEnacted ?? 0 : game?.merkiteEnacted ?? 0;
    for (let index = 0; index < count; index += 1) {
      const on = index < enacted;
      const slot = el("div", `mk-slot${on ? " is-on" : ""}${kind === "merkite" ? " is-merkite" : ""}`);
      slot.append(el("b", undefined, on ? label : `${index + 1}`), el("small", undefined, kind === "merkite" ? boardPowers[game?.board ?? "5-6"][index + 1] ?? "" : ""));
      track.append(slot);
    }
    return track;
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
    root.classList.remove("mk-mount");
  };
}

export const hostGame = {
  id: merkissionerManifest.id,
  displayName: merkissionerManifest.displayName,
  mountDom: mountMerkissionerHost,
} satisfies HostGame;
