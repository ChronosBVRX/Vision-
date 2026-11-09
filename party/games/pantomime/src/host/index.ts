import Phaser from "phaser";
import { pantomimeManifest } from "../manifest.js";
import type { PantomimeState } from "../protocol.js";
import { bindPlatformTheme, tokens } from "./platformTheme.js";
interface HostState { game?: { phase?: string; state?: unknown } | null; room?: { language?: "de" | "en" } | null; }
interface HostClient { subscribe(callback: (state: HostState) => void): () => void; getServerTime?(): number; }

export class PantomimeHostScene extends Phaser.Scene {
  private unsubscribe?: () => void;
  private latest: HostState = {};
  private root?: HTMLDivElement;
  private timer?: HTMLElement;
  private client?: HostClient;
  constructor() { super(pantomimeManifest.hostView); }
  create(): void {
    bindPlatformTheme(this.registry);
    const parent = this.game.canvas.parentElement;
    this.root = document.createElement("div");
    this.root.className = "pantomime-host";
    parent?.append(this.root);
    this.client = this.registry.get("hostClient") as HostClient;
    this.unsubscribe = this.client.subscribe((state) => { this.latest = state; this.render(); });
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => { this.unsubscribe?.(); this.root?.remove(); this.root = undefined; this.timer = undefined; });
  }
  private render(): void {
    if (!this.root) return;
    const scroll = this.root.querySelector(".pm-words")?.scrollTop ?? 0;
    this.root.replaceChildren(); this.timer = undefined;
    const state = (this.latest.game?.state ?? {}) as Partial<PantomimeState>;
    const en = this.latest.room?.language === "en";
    const theme = tokens();
    const style = document.createElement("style");
    style.textContent = `
      .pantomime-host{position:absolute;inset:0;z-index:5;box-sizing:border-box;display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:22px;padding:clamp(20px,4vw,56px);background:linear-gradient(180deg,#020617d9,#020617f2),url('/pantomime/ambient.webp') center/cover no-repeat,${theme.color.surface};color:${theme.color.text};font-family:system-ui,sans-serif}
      .pm-header{display:flex;align-items:center;justify-content:space-between;gap:24px}.pm-header h1{font:700 clamp(28px,4vw,54px)/1.1 Georgia,serif;margin:8px 0}.pm-kicker{color:${theme.color.textSoft};font-size:14px;text-transform:uppercase;letter-spacing:.12em}.pm-timer{font:700 clamp(38px,6vw,78px)/1 ui-monospace,monospace;font-variant-numeric:tabular-nums;white-space:nowrap}
      .pm-body{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(240px,1fr);gap:40px;min-height:0}.pm-body section{min-width:0;min-height:0;display:flex;flex-direction:column}.pm-body h2{font-size:20px;margin:0 0 14px}.pm-words{overflow:auto;min-height:0;flex:1}.pm-row{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(0,1fr);gap:20px;padding:14px 0;border-bottom:1px solid ${theme.color.line};font-size:clamp(16px,1.8vw,24px);overflow-wrap:anywhere}.pm-row span:last-child{color:${theme.color.textSoft}}.pm-scores{overflow:auto}.pm-scores p{display:flex;justify-content:space-between;gap:16px;font-size:20px;margin:0;padding:10px 0;border-bottom:1px solid ${theme.color.line}}.pm-members{font-size:14px!important;color:${theme.color.textSoft};border:0!important}.pm-footer{font-size:clamp(16px,1.7vw,22px);color:${theme.color.textSoft};line-height:1.4}.pm-empty{font-size:clamp(22px,3vw,38px);line-height:1.3;color:${theme.color.textSoft};padding:30px 0;max-width:30ch}
      @media(max-width:700px){.pm-body{grid-template-columns:1fr;overflow:auto;gap:24px}.pm-body section{min-height:180px}.pm-header{gap:12px}.pm-row{font-size:16px}.pantomime-host{gap:16px}}
    `;
    this.root.append(style);
    const add = (parent: HTMLElement, tag: string, text: string, cls?: string) => {
      const element = document.createElement(tag); element.textContent = text; if (cls) element.className = cls; parent.append(element); return element;
    };
    const header = add(this.root, "header", "", "pm-header");
    const title = add(header, "div", "");
    add(title, "div", `${pantomimeManifest.translations[en ? "en" : "de"].displayName} · ${state.mode === "team" ? (en ? "Teams" : "Equipos") : (en ? "Free-for-all" : "Todos contra todos")} · ${en ? "Turn" : "Turno"} ${state.turnNumber ?? 1}`, "pm-kicker");
    add(title, "h1", !state.actorPlayerId ? (en ? "Mímica en llamas starts shortly" : "Mímica en llamas comienza en breve") : state.stage === "review" ? (en ? `${state.actorName ?? "…"} checks the guesses` : `${state.actorName ?? "…"} revisa los aciertos`)
      : state.stage === "acting" ? (en ? `${state.actorName ?? "…"} is acting` : `${state.actorName ?? "…"} está actuando`)
      : (en ? `${state.actorName ?? "…"} is next` : `Es el turno de ${state.actorName ?? "…"}`));
    this.timer = add(header, "div", "", "pm-timer");
    const body = add(this.root, "div", "", "pm-body");
    const words = add(body, "section", "");
    add(words, "h2", en ? "Words & guessers" : "Términos y aciertos");
    const list = add(words, "div", "", "pm-words");
    if (!state.entries?.length) add(list, "p", state.stage === "ready"
      ? (en ? "The actor reveals their first word on the phone to start the clock." : "El actor revela el primer término en el celular. Sólo entonces comienza el tiempo.")
      : (en ? "Guess out loud. Tap Guessed on your phone when you get it right." : "Adivina en voz alta. Quien acierte presiona adivinar en su celular."), "pm-empty");
    for (const entry of state.entries ?? []) {
      const row = add(list, "div", "", "pm-row");
      add(row, "strong", entry.term);
      const guesser = state.players?.find((p) => p.id === entry.guesserPlayerId);
      add(row, "span", guesser ? `${guesser.name}${state.mode === "team" ? ` · ${guesser.teamId === "team1" ? "Team 1" : "Team 2"}` : ""}`
        : entry.status === "timeout" ? (en ? "Unfinished" : "Abierto cuando se acabe el tiempo") : (en ? "No point / skipped" : "Sin punto / omitido"));
    }
    list.scrollTop = state.stage === "review" ? scroll : list.scrollHeight;
    const scores = add(body, "section", "");
    add(scores, "h2", en ? "Standings" : "Punktestand");
    const standings = add(scores, "div", "", "pm-scores");
    if (state.mode === "team") {
      for (const team of ["team1", "team2"] as const) {
        const line = add(standings, "p", ""); add(line, "strong", team === "team1" ? "Team 1" : "Team 2"); add(line, "span", String(state.teamScores?.[team] ?? 0));
        add(standings, "p", (state.players ?? []).filter((p) => p.teamId === team).map((p) => p.name).join(" · "), "pm-members");
      }
    } else for (const player of [...(state.players ?? [])].sort((a,b) => (state.gameScores?.[b.id] ?? 0) - (state.gameScores?.[a.id] ?? 0))) {
      const line = add(standings, "p", ""); add(line, "span", player.name); add(line, "strong", String(state.gameScores?.[player.id] ?? 0));
    }
    add(this.root, "footer", state.stage === "review"
      ? (en ? "Claims may be corrected. The next actor starts only after the current actor confirms." : "Los aciertos se pueden corregir. Sólo después de la confirmación del actor será el turno de la siguiente persona.")
      : state.stage === "ready" ? (en ? "Waiting for the first reveal · no time is being used." : "Esperando la primera revelación · el tiempo aún no corre.")
      : `${en ? "Gestures only · skips remaining" : "Sólo gestos · Saltos restantes"}: ${(state.skipLimit ?? 3) - (state.skipsUsed ?? 0)}${state.mode === "team" ? ` · ${state.actorTeamId === "team1" ? "Team 1" : "Team 2"} ${en ? "is guessing" : "adivina"}` : ""}`, "pm-footer");
    this.update();
  }
  update(): void {
    if (!this.timer) return;
    const state = (this.latest.game?.state ?? {}) as Partial<PantomimeState>;
    const now = this.client?.getServerTime?.() ?? Date.now();
    const seconds = state.stage === "acting" ? Math.max(0, Math.ceil(((state.finishAt ?? now) - now) / 1000)) : 0;
    const label = state.stage === "ready" ? "—" : state.stage === "review" ? "✓" : `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2,"0")}`;
    if (this.timer.textContent !== label) this.timer.textContent = label;
  }
}
export const hostGame = { id: pantomimeManifest.id, displayName: pantomimeManifest.displayName, sceneKey: pantomimeManifest.hostView, scene: PantomimeHostScene } as const;
