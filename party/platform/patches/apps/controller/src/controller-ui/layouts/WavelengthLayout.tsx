import { useEffect, useState } from "react";
import type { WavelengthLayoutModel } from "./models.js";

type Props = { model: WavelengthLayoutModel };

const TEAM_LABEL = { bass: "Bajos", treble: "Agudos" } as const;

function SpectrumBar({ model, showTarget }: { model: WavelengthLayoutModel; showTarget: boolean }) {
  const [angle, setAngle] = useState(model.pointerAngle);
  useEffect(() => setAngle(model.pointerAngle), [model.pointerAngle, model.resetKey]);
  const canMove = model.phase === "guess" && model.isActiveTeam && !model.isOracle && !model.disabled;
  const left = `${(model.pointerAngle / 180) * 100}%`;
  const targetLeft = model.targetAngle === null ? null : `${(model.targetAngle / 180) * 100}%`;

  return (
    <section style={{ display: "grid", gap: 10, width: "100%", maxWidth: 520 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, font: "700 12px system-ui", color: "var(--text-muted)", textTransform: "uppercase", letterSpacing: ".08em" }}>
        <span>{model.prompt.left}</span>
        <span>{model.prompt.right}</span>
      </div>
      <div
        style={{
          position: "relative",
          height: 68,
          borderRadius: 18,
          border: "1px solid var(--panel-border)",
          background: "linear-gradient(90deg,#13233d,#1d3f70 50%,#13233d)",
          boxShadow: "inset 0 8px 24px #0006",
          overflow: "hidden",
        }}
      >
        {showTarget && targetLeft !== null && (
          <div style={{ position: "absolute", left: targetLeft, top: 8, bottom: 8, width: 3, transform: "translateX(-50%)", background: "var(--gold, #F5C518)", boxShadow: "0 0 12px #f5c518aa", borderRadius: 3 }} />
        )}
        <div style={{ position: "absolute", left, top: 0, bottom: 0, width: 3, transform: "translateX(-50%)", background: "#fff", boxShadow: "0 0 14px #6ba6ff" }} />
        <div style={{ position: "absolute", left, top: "50%", width: 20, height: 20, transform: "translate(-50%,-50%)", borderRadius: "50%", background: "#fff", border: "3px solid #2D7DF6", boxShadow: "0 2px 8px #0008" }} />
      </div>
      {canMove ? (
        <input
          type="range"
          min={0}
          max={180}
          step={1}
          value={angle}
          aria-label="Dial del espectro"
          onChange={(event) => {
            const next = Number(event.currentTarget.value);
            setAngle(next);
            model.onMove(next);
          }}
          style={{ width: "100%", accentColor: "#2D7DF6", minHeight: 44 }}
        />
      ) : (
        <div style={{ minHeight: 44 }} />
      )}
      <div style={{ display: "flex", justifyContent: "space-between", font: "700 12px ui-monospace,monospace", color: "var(--text-muted)" }}>
        <span>0</span>
        <span>{model.pointerAngle}°</span>
        <span>180</span>
      </div>
    </section>
  );
}

export function WavelengthLayout({ model }: Props) {
  const [clue, setClue] = useState("");
  useEffect(() => setClue(""), [model.resetKey]);
  const canClue = model.phase === "clue" && model.isOracle && !model.disabled;
  const canLock = model.phase === "guess" && model.isActiveTeam && !model.isOracle && !model.disabled;
  const canSteal = model.phase === "steal" && model.isOpponent && !model.disabled;
  const showTarget = model.targetAngle !== null && (model.isOracle || model.phase === "reveal" || model.phase === "game_over");
  const title = model.phase === "clue"
    ? model.isOracle ? "Tu pista" : "El Oráculo está pensando"
    : model.phase === "guess"
      ? "Muevan el dial"
      : model.phase === "steal"
        ? "Undercut"
        : model.phase === "reveal"
          ? "Resultado"
          : "Fin del juego";

  return (
    <main style={{ display: "grid", alignContent: "start", gap: 18, padding: "18px 16px 28px", minHeight: "100dvh", boxSizing: "border-box", color: "var(--text-main)", background: "var(--bg)" }}>
      <header style={{ display: "grid", gap: 6 }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center" }}>
          <span style={{ font: "800 11px system-ui", letterSpacing: ".16em", textTransform: "uppercase", color: "var(--accent)" }}>VISION+ PLAY · ESPECTRO</span>
          <span style={{ font: "800 12px ui-monospace,monospace", color: "var(--text-muted)" }}>
            {TEAM_LABEL[model.activeTeam]} · {model.teamScores.bass} / {model.teamScores.treble}
          </span>
        </div>
        <h1 style={{ margin: 0, font: "800 clamp(28px,8vw,44px)/1.05 'Plus Jakarta Sans',system-ui", letterSpacing: "-.04em" }}>{title}</h1>
        <p style={{ margin: 0, color: "var(--text-muted)", font: "500 14px/1.4 system-ui" }}>
          {model.phase === "clue" && !model.isOracle ? "Mira la tele. El Oráculo ya tiene el objetivo secreto." : null}
          {model.phase === "clue" && model.isOracle ? `Objetivo visible solo para ti. Pista de máximo 60 caracteres.` : null}
          {model.phase === "guess" ? "El equipo activo mueve el dial; cuando estén de acuerdo, bloqueen." : null}
          {model.phase === "steal" ? "El equipo rival vota si el dial se pasó a la izquierda o a la derecha." : null}
          {model.phase === "reveal" ? "La verdad sale a la luz. Vean los puntos en la tele." : null}
          {model.phase === "game_over" ? "Partida terminada. Márquense listos para la revancha." : null}
        </p>
      </header>

      <section style={{ padding: 16, borderRadius: 20, border: "1px solid var(--panel-border)", background: "var(--surface)", display: "grid", gap: 10 }}>
        <strong style={{ font: "800 20px/1.25 'Plus Jakarta Sans',system-ui" }}>“{model.prompt.left} ↔ {model.prompt.right}”</strong>
        {model.clue ? <p style={{ margin: 0, color: "var(--gold, #F5C518)", font: "800 18px/1.3 system-ui" }}>Pista: “{model.clue}”</p> : null}
      </section>

      <SpectrumBar model={model} showTarget={showTarget} />

      {canClue && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            const text = clue.trim();
            if (text) model.onClue(text);
          }}
          style={{ display: "grid", gap: 10 }}
        >
          <input
            value={clue}
            maxLength={60}
            onChange={(event) => setClue(event.currentTarget.value)}
            placeholder="Ej. “cursi pero caro”"
            aria-label="Tu pista"
            style={{ minHeight: 52, borderRadius: 14, border: "1px solid var(--panel-border)", background: "var(--surface-2, #1B2538)", color: "var(--text-main)", padding: "0 14px", font: "600 16px system-ui" }}
          />
          <button type="submit" disabled={!clue.trim()} style={{ minHeight: 52, borderRadius: 14, border: 0, background: "linear-gradient(135deg,#2D7DF6,#6BA6FF)", color: "#fff", font: "800 16px system-ui" }}>
            Enviar pista
          </button>
        </form>
      )}

      {canLock && (
        <button type="button" onClick={model.onLock} style={{ minHeight: 54, borderRadius: 16, border: 0, background: "linear-gradient(135deg,#2D7DF6,#6BA6FF)", color: "#fff", font: "800 17px system-ui" }}>
          Bloquear dial ({model.readyCount}/{model.guesserCount})
        </button>
      )}

      {canSteal && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <button type="button" onClick={() => model.onSteal("left")} style={{ minHeight: 70, borderRadius: 16, border: "1px solid var(--panel-border)", background: model.ownSteal === "left" ? "#1d3f70" : "var(--surface)", color: "var(--text-main)", font: "800 16px system-ui" }}>
            ← Se pasó a la izquierda
          </button>
          <button type="button" onClick={() => model.onSteal("right")} style={{ minHeight: 70, borderRadius: 16, border: "1px solid var(--panel-border)", background: model.ownSteal === "right" ? "#1d3f70" : "var(--surface)", color: "var(--text-main)", font: "800 16px system-ui" }}>
            Se pasó a la derecha →
          </button>
        </div>
      )}

      {model.phase === "steal" && model.isActiveTeam && (
        <p style={{ color: "var(--text-muted)", font: "500 14px system-ui", textAlign: "center" }}>
          Votos del Undercut: {model.stealVotes.left} izquierda · {model.stealVotes.right} derecha
        </p>
      )}

      <footer style={{ marginTop: "auto", color: "var(--text-muted)", font: "500 12px system-ui", textAlign: "center" }}>
        Primero a {model.targetScore} puntos · {TEAM_LABEL[model.activeTeam]} juega ahora
      </footer>
    </main>
  );
}
