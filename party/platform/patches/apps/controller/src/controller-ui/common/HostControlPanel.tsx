import {
  canManagePlayerRoster,
  hasActiveRound,
  hasHostControl,
  hasPendingHostControlRequest,
  isAskedToHandOverHostControl,
  type BotDifficulty,
  type PlayerSnapshot,
  type RoomSnapshot
} from "@open-party-lab/protocol";
import { useEffect, useState } from "react";
import { getControllerText } from "../../i18n/controllerText.js";
import { LobbySetupControls } from "./LobbySetupControls.js";

/**
 * Seconds left on a pending handover, ticking once per second.
 *
 * The deadline is a timestamp in the room snapshot, and the server completes
 * the handover on its own — this only keeps the number on screen honest while
 * the phone waits, and stops ticking as soon as there is nothing pending.
 */
function useHostControlCountdown(room: RoomSnapshot): number {
  const expiresAt = room.hostControl.pendingRequest?.expiresAt ?? null;
  const [remaining, setRemaining] = useState(() =>
    expiresAt === null ? 0 : Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000))
  );

  useEffect(() => {
    if (expiresAt === null) {
      setRemaining(0);
      return;
    }

    const update = () => setRemaining(Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)));

    update();
    const handle = setInterval(update, 1000);

    return () => clearInterval(handle);
  }, [expiresAt]);

  return remaining;
}

export interface HostControlPanelProps {
  room: RoomSnapshot;
  player: PlayerSnapshot | null;
  onRequestControl: () => void;
  onReleaseControl: () => void;
  /** Answers a handover aimed at this player. */
  onResolveControl: (playerId: string, grant: boolean) => void;
  onSelectGame: (gameId: string) => void;
  onHostAction: (gameId: string, action: unknown) => void;
  onStartRound: () => void;
  onBackToMenu: () => void;
  onKickPlayer: (playerId: string) => void;
  onAddBot: (count: number, difficulty: BotDifficulty) => void;
  onRemoveBot: (playerId: string) => void;
}

const panelStyle = {
  marginTop: 14,
  padding: 14,
  border: "1px solid var(--line)",
  borderRadius: "var(--radius-lg)",
  background: "var(--surface)",
  boxShadow: "var(--shadow-card)",
  display: "grid",
  gap: 10
} as const;

const headingStyle = {
  margin: 0,
  fontFamily: "var(--font-display)",
  fontWeight: 500,
  fontSize: "1.15rem"
} as const;

const noteStyle = {
  margin: 0,
  color: "var(--muted)",
  fontSize: ".85rem",
  lineHeight: 1.45
} as const;

const actionStyle = {
  minHeight: 48,
  padding: "0 16px",
  borderRadius: "var(--radius-md)",
  border: "1px solid var(--accent)",
  background: "var(--accent)",
  color: "var(--on-accent)",
  fontWeight: 700,
  width: "100%"
} as const;

const secondaryActionStyle = {
  ...actionStyle,
  border: "1px solid var(--line)",
  background: "transparent",
  color: "var(--ink)"
} as const;

/**
 * Phone-side host controls.
 *
 * Renders one of three states: an invitation to take over, a pending request,
 * or the full control surface. Every action here is also gated server-side —
 * this component only decides what is worth showing.
 */
export function HostControlPanel({
  room,
  player,
  onRequestControl,
  onReleaseControl,
  onResolveControl,
  onSelectGame,
  onHostAction,
  onStartRound,
  onBackToMenu,
  onKickPlayer,
  onAddBot,
  onRemoveBot
}: HostControlPanelProps) {
  const text = getControllerText(room.language);
  const playerId = player?.id ?? null;
  const gameLocked = Boolean(room.lockedGameId);
  const inControl = hasHostControl(room.hostControl, playerId);
  const requestPending = hasPendingHostControlRequest(room.hostControl, playerId);
  const otherHolder =
    room.hostControl.holderPlayerId && !inControl ? room.hostControl.holderName : null;
  const handoverRequest = isAskedToHandOverHostControl(room.hostControl, playerId)
    ? room.hostControl.pendingRequest
    : null;
  const remainingSeconds = useHostControlCountdown(room);
  // Hooks must run before the early return for players without control: the
  // same component flips between the two states when control is taken.
  const [section, setSection] = useState<"game" | "settings" | "players">("game");
  const [botDifficulty, setBotDifficulty] = useState<BotDifficulty>("hard");

  if (!inControl) {
    return (
      <section style={panelStyle}>
        <h2 style={headingStyle}>{text.hostControlTitle}</h2>
        {requestPending ? (
          <>
            <p style={noteStyle}>{text.hostControlPending}</p>
            <p style={noteStyle}>{text.hostControlPendingCountdown(remainingSeconds)}</p>
          </>
        ) : (
          <>
            {otherHolder ? (
              <p style={noteStyle}>{text.hostControlHeldByOther(otherHolder)}</p>
            ) : null}
            {/* Free controls need no permission, so the same button both asks
                and takes — which of the two it was is visible a moment later. */}
            <button type="button" style={secondaryActionStyle} onClick={onRequestControl}>
              {text.hostControlTake}
            </button>
          </>
        )}
      </section>
    );
  }

  const roundRunning = hasActiveRound(room);
  const canManageRoster = canManagePlayerRoster(room);
  const selectedGame = room.availableGames.find((game) => game.id === room.selectedGameId);
  const bots = room.players.filter((entry) => entry.isBot);
  const botSupport = selectedGame?.supportsBots === true;

  const startButton = selectedGame ? (
    <button
      type="button"
      style={{ ...actionStyle, opacity: roundRunning ? 0.55 : 1 }}
      disabled={roundRunning}
      onClick={onStartRound}
    >
      {text.hostControlStartRound}
    </button>
  ) : null;

  const backButton = !gameLocked ? (
    <button type="button" style={secondaryActionStyle} onClick={onBackToMenu}>
      {text.hostControlBackToMenu}
    </button>
  ) : null;

  return (
    <section style={panelStyle}>
      <h2 style={headingStyle}>{text.hostControlTitle}</h2>
      <p style={noteStyle}>{text.hostControlActive}</p>

      {/* The handover prompt belongs here, to the player who would lose the
          controls. Ignoring it is an answer too: the deadline grants them. */}
      {handoverRequest ? (
        <div
          style={{
            display: "grid",
            gap: 8,
            padding: 12,
            borderRadius: "var(--radius-md)",
            border: "1px solid var(--accent)",
            background: "var(--accent-soft)"
          }}
        >
          <strong>{text.hostControlHandoverTitle}</strong>
          <span style={noteStyle}>
            {text.hostControlHandoverBody(handoverRequest.playerName)}
          </span>
          <span style={noteStyle}>
            {text.hostControlHandoverCountdown(remainingSeconds)}
          </span>
          <div style={{ display: "flex", gap: 8 }}>
            <button
              type="button"
              style={{ ...secondaryActionStyle, flex: 1 }}
              onClick={() => onResolveControl(handoverRequest.playerId, false)}
            >
              {text.hostControlHandoverKeep}
            </button>
            <button
              type="button"
              style={{ ...actionStyle, flex: 1 }}
              onClick={() => onResolveControl(handoverRequest.playerId, true)}
            >
              {text.hostControlHandoverGive}
            </button>
          </div>
        </div>
      ) : null}

      <HostTabBar active={section} onChange={setSection} />

      {section === "game" ? (
        <>
          {roundRunning ? (
            <p style={noteStyle}>{text.hostControlRoundRunning}</p>
          ) : null}
          {gameLocked ? null : (
            <>
              <p style={{ ...noteStyle, marginTop: 4 }}>{text.hostControlChooseGame}</p>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
                {room.availableGames.map((game) => {
                  const selected = room.selectedGameId === game.id;

                  return (
                    <button
                      key={game.id}
                      type="button"
                      onClick={() => onSelectGame(game.id)}
                      style={{
                        display: "grid",
                        gridTemplateColumns: "42px minmax(0, 1fr)",
                        alignItems: "center",
                        gap: 8,
                        minHeight: 58,
                        padding: "7px 8px",
                        borderRadius: "var(--radius-md)",
                        border: selected ? "2px solid var(--accent)" : "1px solid var(--line)",
                        background: selected ? "var(--accent-soft)" : "var(--surface-muted)",
                        color: "var(--ink)",
                        textAlign: "left"
                      }}
                    >
                      <img
                        src={`/party-covers/${game.id}.webp`}
                        alt=""
                        width={42}
                        height={42}
                        style={{ width: 42, height: 42, borderRadius: 10, objectFit: "cover" }}
                      />
                      <span style={{ display: "grid", gap: 1, minWidth: 0 }}>
                        <strong style={{ fontSize: ".78rem", lineHeight: 1.15, overflow: "hidden", textOverflow: "ellipsis" }}>
                          {game.displayName}
                        </strong>
                        {game.visual?.eyebrow ? (
                          <small style={{ color: "var(--text-muted)", fontSize: ".62rem", textTransform: "uppercase", letterSpacing: ".06em" }}>
                            {game.visual.eyebrow}
                          </small>
                        ) : null}
                      </span>
                    </button>
                  );
                })}
              </div>
            </>
          )}
          {roundRunning ? null : startButton}
          {roundRunning ? backButton : null}
        </>
      ) : null}

      {section === "settings" ? (
        <>
          {selectedGame ? (
            <LobbySetupControls
              game={selectedGame}
              settings={room.selectedGameSettings ?? {}}
              disabled={roundRunning}
              onHostAction={onHostAction}
            />
          ) : (
            <p style={noteStyle}>Elige un juego para ver sus ajustes.</p>
          )}
          {roundRunning ? null : startButton}
          {backButton}
        </>
      ) : null}

      {section === "players" ? (
        <>
          <p style={{ ...noteStyle, marginTop: 4 }}>{text.hostControlRoster}</p>
          {canManageRoster ? null : <p style={noteStyle}>{text.hostControlRoundRunning}</p>}
          <div style={{ display: "grid", gap: 6 }}>
            {room.players.map((entry) => (
              <div
                key={entry.id}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  minHeight: 44,
                  padding: "0 10px",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--line)",
                  background: "var(--surface-muted)"
                }}
              >
                <span
                  aria-hidden="true"
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    background: entry.color,
                    flex: "0 0 auto"
                  }}
                />
                <span style={{ flex: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis" }}>
                  {entry.name}
                  {entry.isBot ? (
                    <span style={{ marginLeft: 8, padding: "2px 7px", borderRadius: 999, background: "var(--paper-deep)", color: "var(--text-muted)", fontSize: ".62rem", letterSpacing: "1px" }}>
                      BOT
                    </span>
                  ) : null}
                </span>
                {canManageRoster && entry.id !== playerId ? (
                  <button
                    type="button"
                    aria-label={entry.isBot ? `Quitar ${entry.name}` : text.hostControlKick(entry.name)}
                    onClick={() => entry.isBot ? onRemoveBot(entry.id) : onKickPlayer(entry.id)}
                    style={{
                      width: 34,
                      height: 34,
                      borderRadius: "var(--radius-sm)",
                      border: "1px solid var(--danger)",
                      background: "var(--danger-soft)",
                      color: "var(--danger)",
                      fontSize: "1.1rem",
                      lineHeight: 1
                    }}
                  >
                    ×
                  </button>
                ) : null}
              </div>
            ))}
          </div>

          <div
            style={{
              display: "grid",
              gap: 8,
              padding: 10,
              borderRadius: "var(--radius-md)",
              border: "1px dashed var(--line-strong)",
              background: "color-mix(in srgb, var(--surface-muted) 55%, transparent)"
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 8 }}>
              <strong style={{ fontSize: ".86rem" }}>Bots</strong>
              <span style={{ color: "var(--text-muted)", fontSize: ".72rem" }}>{bots.length} en la sala</span>
            </div>

            {botSupport && selectedGame ? (
              <>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 4 }}>
                  {([
                    ["normal", "Normal"],
                    ["hard", "Difícil"],
                    ["expert", "Experto"]
                  ] as Array<[BotDifficulty, string]>).map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      aria-pressed={botDifficulty === value}
                      onClick={() => setBotDifficulty(value)}
                      style={{
                        minHeight: 38,
                        borderRadius: 10,
                        border: botDifficulty === value ? "1px solid var(--accent)" : "1px solid var(--line)",
                        background: botDifficulty === value ? "var(--accent-soft)" : "var(--surface)",
                        color: "var(--ink)",
                        fontWeight: botDifficulty === value ? 800 : 500,
                        fontSize: ".75rem"
                      }}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
                  <button
                    type="button"
                    disabled={roundRunning || room.players.length >= selectedGame.maxPlayers}
                    onClick={() => onAddBot(1, botDifficulty)}
                    style={{ ...actionStyle, minHeight: 42 }}
                  >
                    +1 bot
                  </button>
                  <button
                    type="button"
                    disabled={roundRunning || room.players.length + 1 >= selectedGame.maxPlayers}
                    onClick={() => onAddBot(2, botDifficulty)}
                    style={{ ...secondaryActionStyle, minHeight: 42 }}
                  >
                    +2 bots
                  </button>
                </div>
                {bots.length > 0 ? (
                  <div style={{ display: "grid", gap: 4 }}>
                    {bots.map((bot) => (
                      <div key={bot.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, fontSize: ".78rem" }}>
                        <span style={{ minWidth: 0, overflow: "hidden", textOverflow: "ellipsis" }}>{bot.name}</span>
                        <button
                          type="button"
                          disabled={roundRunning}
                          onClick={() => onRemoveBot(bot.id)}
                          style={{ border: "1px solid var(--line)", background: "transparent", borderRadius: 8, padding: "3px 8px", color: "var(--text-muted)", fontSize: ".72rem" }}
                        >
                          Quitar
                        </button>
                      </div>
                    ))}
                  </div>
                ) : null}
              </>
            ) : (
              <p style={{ ...noteStyle, margin: 0 }}>
                Este juego todavía no tiene bots. Ya compiten en Light Trails, Arena Survivor, Comando Caos y Flatterfluff.
              </p>
            )}
          </div>

          {roundRunning ? null : startButton}
          {backButton}
          <button type="button" style={secondaryActionStyle} onClick={onReleaseControl}>
            {text.hostControlRelease}
          </button>
        </>
      ) : null}
    </section>
  );
}

function HostTabBar({
  active,
  onChange
}: {
  active: "game" | "settings" | "players";
  onChange: (section: "game" | "settings" | "players") => void;
}) {
  const tabs: Array<{ id: "game" | "settings" | "players"; label: string }> = [
    { id: "game", label: "Juego" },
    { id: "settings", label: "Ajustes" },
    { id: "players", label: "Jugadores" }
  ];

  return (
    <div
      role="tablist"
      aria-label="Control del anfitrión"
      style={{
        display: "grid",
        gridTemplateColumns: "1fr 1fr 1fr",
        gap: 4,
        padding: 4,
        borderRadius: 12,
        background: "var(--surface-muted)",
        border: "1px solid var(--line)"
      }}
    >
      {tabs.map((tab) => {
        const selected = active === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(tab.id)}
            style={{
              minHeight: 40,
              borderRadius: 9,
              border: selected ? "1px solid var(--accent)" : "1px solid transparent",
              background: selected ? "var(--accent-soft)" : "transparent",
              color: selected ? "var(--ink)" : "var(--text-muted)",
              fontWeight: selected ? 800 : 600,
              fontSize: ".76rem"
            }}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
