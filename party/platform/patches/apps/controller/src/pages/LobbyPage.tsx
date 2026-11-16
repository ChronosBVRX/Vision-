import { useState } from "react";
import type { BotDifficulty, PlayerSetupValue, PlayerSnapshot, RoomSnapshot } from "@open-party-lab/protocol";
import { hasHostControl } from "@open-party-lab/protocol";
import { resolveGameMaxPlayers, resolveGameMinPlayers } from "@open-party-lab/game-core";
import { ControllerFrame } from "../controller-ui/layout/ControllerFrame.js";
import { HostControlPanel } from "../controller-ui/common/HostControlPanel.js";
import { getControllerText } from "../i18n/controllerText.js";
import { PlayerProfileEditor } from "../controller-ui/common/PlayerProfileEditor.js";

type AvailableRoomGame = RoomSnapshot["availableGames"][number];
type PlayerSetup = NonNullable<AvailableRoomGame["playerSetup"]>;
type PlayerSetupOption = PlayerSetup["options"][number];

interface LobbyPageProps {
  room: RoomSnapshot | null;
  player: PlayerSnapshot | null;
  error: string | null;
  onLeaveRoom: () => void;
  onSetReady: (isReady: boolean) => void;
  onSetProfile: (name: string, avatar: string | null) => Promise<string | null>;
  onSetPlayerSetup: (selectionKey: string, value: PlayerSetupValue) => void;
  onRequestHostControl: () => void;
  onReleaseHostControl: () => void;
  onResolveHostControl: (playerId: string, grant: boolean) => void;
  onSelectGame: (gameId: string) => void;
  onHostAction: (gameId: string, action: unknown) => void;
  onStartRound: () => void;
  onBackToMenu: () => void;
  onKickPlayer: (playerId: string) => void;
  onAddBot: (count: number, difficulty: BotDifficulty) => void;
  onRemoveBot: (playerId: string) => void;
}

function getPlayerSetupVisual(option: PlayerSetupOption): NonNullable<PlayerSetupOption["visual"]> {
  return option.visual ?? {
    primaryColor: "var(--accent)",
    secondaryColor: "var(--accent-soft)",
    accentColor: "var(--amber)"
  };
}

function resolvePlayerSetupOptionPortrait(
  room: RoomSnapshot,
  option: PlayerSetupOption
): PlayerSetupOption {
  const mapping = option.portraitPathBySetting;

  if (!mapping) {
    return option;
  }

  const settingValue = room.selectedGameSettings?.[mapping.settingKey];
  const themedPortraitPath =
    settingValue === undefined ? undefined : mapping.values[String(settingValue)];

  return themedPortraitPath ? { ...option, portraitPath: themedPortraitPath } : option;
}

function getPlayerSetupSelectionKey(setup: PlayerSetup): string {
  return setup.selectionKey ?? "character";
}

function getPlayerSetupValue(player: PlayerSnapshot | null, setup: PlayerSetup): PlayerSetupValue | undefined {
  const selectionKey = getPlayerSetupSelectionKey(setup);
  const storedValue = player?.setupSelections?.[selectionKey];

  if (storedValue !== undefined) {
    return storedValue;
  }

  if (setup.kind === "choice" && selectionKey === "character") {
    return player?.selectedCharacterId ?? undefined;
  }

  if (setup.kind === "multi-select" && setup.defaultValue) {
    return [...setup.defaultValue];
  }

  return undefined;
}

function isPlayerSetupSelectionMissing(player: PlayerSnapshot | null, selectedGame: AvailableRoomGame | undefined): boolean {
  const setup = selectedGame?.playerSetup;

  if (!setup || setup.required !== true) {
    return false;
  }

  const validOptionIds = new Set(setup.options.map((option) => option.id));
  const value = getPlayerSetupValue(player, setup);

  if (setup.kind === "choice") {
    return typeof value !== "string" || !validOptionIds.has(value);
  }

  if (!Array.isArray(value)) {
    return true;
  }

  const uniqueValidValues = [...new Set(value)].filter((entry) => validOptionIds.has(entry));
  return uniqueValidValues.length < setup.minSelections || uniqueValidValues.length > setup.maxSelections;
}

function renderPlayerSetupIcon(option: PlayerSetupOption | undefined, index: number, size = 27) {
  if (!option) {
    return (
      <span style={{ color: "var(--line-strong)", fontWeight: 900 }}>
        {index + 1}
      </span>
    );
  }

  const visual = getPlayerSetupVisual(option);

  if (option.iconPath) {
    return (
      <span
        style={{
          width: size,
          height: size,
          backgroundColor: visual.accentColor,
          WebkitMask: `url(${option.iconPath}) center / contain no-repeat`,
          mask: `url(${option.iconPath}) center / contain no-repeat`,
          filter: "drop-shadow(0 1px 4px rgba(60, 43, 26, 0.25))"
        }}
      />
    );
  }

  if (option.portraitPath) {
    return (
      <img
        src={option.portraitPath}
        alt=""
        width={size}
        height={size}
        style={{
          borderRadius: 8,
          objectFit: "cover",
          border: "1px solid var(--line)"
        }}
      />
    );
  }

  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: 8,
        display: "grid",
        placeItems: "center",
        background: visual.primaryColor,
        color: "var(--on-accent)",
        fontWeight: 900
      }}
    >
      {option.name.slice(0, 1)}
    </span>
  );
}

function renderChoicePlayerSetupChooser(
  room: RoomSnapshot,
  selectedGame: AvailableRoomGame,
  currentPlayer: PlayerSnapshot | null,
  text: ReturnType<typeof getControllerText>,
  onSetPlayerSetup: (selectionKey: string, value: PlayerSetupValue) => void
) {
  const setup = selectedGame.playerSetup;

  if (!setup || setup.kind !== "choice" || setup.options.length === 0) {
    return null;
  }

  const selectionKey = getPlayerSetupSelectionKey(setup);
  const playersBySelectionId = new Map<string, PlayerSnapshot[]>();

  for (const player of room.players) {
    const value = getPlayerSetupValue(player, setup);

    if (typeof value !== "string") {
      continue;
    }

    const players = playersBySelectionId.get(value) ?? [];
    players.push(player);
    playersBySelectionId.set(value, players);
  }

  const currentValue = getPlayerSetupValue(currentPlayer, setup);
  const selectedOption = typeof currentValue === "string"
    ? setup.options.find((option) => option.id === currentValue)
    : undefined;

  return (
    <section style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 8 }}>
        <strong style={{ color: "var(--text-main)" }}>{setup.title ?? text.characterChooseTitle}</strong>
        <span style={{ color: "var(--text-muted)", fontSize: ".76rem" }}>
          {setup.options.length} opciones
        </span>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 8 }}>
        {setup.options.map((setupOption) => {
          const option = resolvePlayerSetupOptionPortrait(room, setupOption);
          const selectedByCurrentPlayer = currentValue === option.id;
          const selectedPlayers = playersBySelectionId.get(option.id) ?? [];
          const visual = getPlayerSetupVisual(option);

          return (
            <button
              key={option.id}
              type="button"
              onClick={() => onSetPlayerSetup(selectionKey, option.id)}
              aria-pressed={selectedByCurrentPlayer}
              style={{
                position: "relative",
                display: "grid",
                justifyItems: "center",
                alignContent: "start",
                gap: 4,
                minHeight: 116,
                padding: "8px 4px 6px",
                border: selectedByCurrentPlayer
                  ? `2px solid ${visual.accentColor}`
                  : "1px solid var(--line)",
                borderRadius: 14,
                background: selectedByCurrentPlayer ? "var(--accent-soft)" : "var(--surface)",
                color: "var(--text-main)"
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: 14,
                  overflow: "hidden",
                  display: "grid",
                  placeItems: "center",
                  border: "1px solid var(--line)",
                  background: "var(--surface-muted)"
                }}
              >
                {renderPlayerSetupIcon(option, 0, option.portraitPath ? 56 : 28)}
              </div>
              <strong style={{ fontSize: ".82rem", lineHeight: 1.1, textAlign: "center" }}>
                {option.name}
              </strong>
              {option.archetype ? (
                <span style={{ fontSize: ".62rem", textTransform: "uppercase", letterSpacing: ".06em", color: "var(--text-muted)", textAlign: "center" }}>
                  {option.archetype}
                </span>
              ) : null}
              {selectedPlayers.length > 0 ? (
                <span
                  style={{
                    position: "absolute",
                    top: 4,
                    right: 7,
                    fontSize: ".62rem",
                    color: "var(--text-muted)"
                  }}
                  title={text.chosenBy(selectedPlayers.map((entry) => entry.name).join(", "))}
                >
                  {selectedPlayers.length}
                </span>
              ) : null}
            </button>
          );
        })}
      </div>

      <div
        style={{
          display: "grid",
          gap: 3,
          padding: "10px 12px",
          borderRadius: 12,
          background: "var(--surface-muted)",
          color: "var(--text-muted)"
        }}
      >
        {selectedOption ? (
          <>
            <strong style={{ color: "var(--text-main)" }}>
              {selectedOption.title ?? selectedOption.name}
            </strong>
            {selectedOption.description ? <span>{selectedOption.description}</span> : null}
            <span style={{ fontSize: ".78rem" }}>{text.yourCharacter}</span>
          </>
        ) : (
          <span>{setup.description ?? text.characterChooseDescription}</span>
        )}
      </div>
    </section>
  );
}

function renderMultiSelectPlayerSetupChooser(
  selectedGame: AvailableRoomGame,
  currentPlayer: PlayerSnapshot | null,
  text: ReturnType<typeof getControllerText>,
  onSetPlayerSetup: (selectionKey: string, value: PlayerSetupValue) => void
) {
  const setup = selectedGame.playerSetup;

  if (!setup || setup.kind !== "multi-select" || setup.options.length === 0) {
    return null;
  }

  const selectionKey = getPlayerSetupSelectionKey(setup);
  const value = getPlayerSetupValue(currentPlayer, setup);
  const selectedIds = Array.isArray(value) ? value : [];
  const selectedSet = new Set(selectedIds);
  const optionsById = new Map(setup.options.map((option) => [option.id, option]));
  const slots = Array.from({ length: setup.maxSelections }, (_, index) => optionsById.get(selectedIds[index]));
  const canReady = selectedIds.length >= setup.minSelections && selectedIds.length <= setup.maxSelections;
  const toggleOption = (optionId: string) => {
    if (selectedSet.has(optionId)) {
      if (selectedIds.length <= setup.minSelections) {
        return;
      }

      onSetPlayerSetup(selectionKey, selectedIds.filter((entry) => entry !== optionId));
      return;
    }

    if (selectedIds.length >= setup.maxSelections) {
      return;
    }

    onSetPlayerSetup(selectionKey, [...selectedIds, optionId]);
  };

  return (
    <section style={{ display: "grid", gap: 12 }}>
      <div
        style={{
          display: "grid",
          gap: 6,
          padding: "12px 14px",
          borderRadius: "var(--radius-md)",
          background: "var(--surface-muted)",
          color: "var(--text-muted)"
        }}
      >
        <strong style={{ color: "var(--text-main)" }}>{setup.title ?? text.setupChooseTitle}</strong>
        {setup.description ? <span>{setup.description}</span> : null}
        <span>{text.selectionCount(selectedIds.length, setup.minSelections, setup.maxSelections)}</span>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8 }}>
        {slots.map((option, index) => {
          const visual = option ? getPlayerSetupVisual(option) : null;

          return (
            <div
              key={`${option?.id ?? "empty"}:${index}`}
              title={option?.name}
              style={{
                minHeight: 54,
                borderRadius: 12,
                border: option ? `1px solid ${visual?.accentColor ?? "var(--accent)"}` : "1px dashed var(--line-strong)",
                background: option ? "var(--surface)" : "var(--surface-muted)",
                display: "grid",
                placeItems: "center",
                boxShadow: option ? "var(--shadow-card)" : "none"
              }}
            >
              {renderPlayerSetupIcon(option, index)}
            </div>
          );
        })}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8 }}>
        {setup.options.map((option) => {
          const selected = selectedSet.has(option.id);
          const visual = getPlayerSetupVisual(option);

          return (
            <button
              key={option.id}
              type="button"
              onClick={() => toggleOption(option.id)}
              style={{
                minHeight: 64,
                borderRadius: 12,
                border: selected ? `2px solid ${visual.accentColor}` : "1px solid var(--line)",
                background: selected ? "var(--surface)" : "var(--surface-muted)",
                color: "var(--text-main)",
                display: "grid",
                gap: 4,
                placeItems: "center",
                padding: "7px 5px"
              }}
            >
              {renderPlayerSetupIcon(option, 0)}
              <small style={{ color: "var(--text-muted)", fontSize: 10, lineHeight: 1.1 }}>
                {option.name}
              </small>
            </button>
          );
        })}
      </div>

      {!canReady ? (
        <small style={{ color: "var(--danger)" }}>
          {text.selectionCount(selectedIds.length, setup.minSelections, setup.maxSelections)}
        </small>
      ) : null}
    </section>
  );
}

function renderPlayerSetupSummary(
  setup: PlayerSetup,
  currentPlayer: PlayerSnapshot | null,
  text: ReturnType<typeof getControllerText>
) {
  const value = getPlayerSetupValue(currentPlayer, setup);

  if (setup.kind === "choice") {
    return `${setup.title ?? text.character}: ${currentPlayer?.selectedCharacterName ?? text.noCharacter}`;
  }

  const selectedCount = Array.isArray(value) ? value.length : 0;
  return `${setup.title ?? text.setup}: ${selectedCount}/${setup.maxSelections}`;
}

function renderPlayerSetupPlayerDetail(
  player: PlayerSnapshot,
  setup: PlayerSetup,
  text: ReturnType<typeof getControllerText>
) {
  const value = getPlayerSetupValue(player, setup);

  if (setup.kind === "choice") {
    return player.selectedCharacterName ?? text.noCharacter;
  }

  const selectedCount = Array.isArray(value) ? value.length : 0;
  return text.selectionCount(selectedCount, setup.minSelections, setup.maxSelections);
}

export function LobbyPage({
  room,
  player,
  error,
  onLeaveRoom,
  onSetReady,
  onSetProfile,
  onSetPlayerSetup,
  onRequestHostControl,
  onReleaseHostControl,
  onResolveHostControl,
  onSelectGame,
  onHostAction,
  onStartRound,
  onBackToMenu,
  onKickPlayer,
  onAddBot,
  onRemoveBot
}: LobbyPageProps) {
  const text = getControllerText(room?.language);
  const [lobbyTab, setLobbyTab] = useState<"play" | "room">("play");
  const currentPlayer = room?.players.find((entry) => entry.id === player?.id) ?? player;
  const selectedGame =
    room?.availableGames.find((game) => game.id === room.selectedGameId);
  const selectedGameName = selectedGame?.displayName ?? "Noch kein Spiel";
  const playerCount = room?.players.length ?? 0;
  const readyCount = (room?.players ?? []).filter((entry) => entry.isReady).length;
  const minimumPlayers = selectedGame
    ? resolveGameMinPlayers(selectedGame, room?.selectedGameSettings)
    : 0;
  const maximumPlayers = selectedGame
    ? resolveGameMaxPlayers(selectedGame, room?.selectedGameSettings)
    : 0;
  const enoughPlayers = selectedGame ? playerCount >= minimumPlayers : false;
  const playerSetup = selectedGame?.playerSetup;
  const hasPlayerSetup = Boolean(playerSetup && playerSetup.options.length > 0);
  const playerSetupSelectionMissing = isPlayerSetupSelectionMissing(currentPlayer, selectedGame);
  const setupBlockedLabel = playerSetup?.kind === "multi-select"
    ? playerSetup.readyBlockedLabel ?? text.chooseSetupFirst
    : text.chooseCharacterFirst;
  const inControl = Boolean(room && hasHostControl(room.hostControl, player?.id ?? null));

  return (
    <ControllerFrame
      title={`Lobby ${room?.code ?? "----"}`}
      subtitle={room?.selectedGameId ? text.lobbySelected(selectedGameName) : text.lobbyWaitingHost}
      footer={error ? <p style={{ color: "var(--danger)", margin: 0 }}>{error}</p> : null}
    >
      <div style={{ display: "grid", gap: 14 }}>
        {inControl && room ? (
          <HostControlPanel
            room={room}
            player={currentPlayer}
            onRequestControl={onRequestHostControl}
            onReleaseControl={onReleaseHostControl}
            onResolveControl={onResolveHostControl}
            onSelectGame={onSelectGame}
            onHostAction={onHostAction}
            onStartRound={onStartRound}
            onBackToMenu={onBackToMenu}
            onKickPlayer={onKickPlayer}
            onAddBot={onAddBot}
            onRemoveBot={onRemoveBot}
          />
        ) : (
          <>
            <LobbyTabBar active={lobbyTab} onChange={setLobbyTab} />

            {lobbyTab === "play" ? (
              <>
                {!room ? null : (
                  <button
                    type="button"
                    onClick={() => onSetReady(!(currentPlayer?.isReady ?? false))}
                    disabled={playerSetupSelectionMissing}
                    style={{
                      border: "1px solid color-mix(in srgb, var(--accent-strong) 24%, transparent)",
                      borderRadius: 18,
                      background: currentPlayer?.isReady
                        ? "linear-gradient(160deg, color-mix(in srgb, var(--success) 92%, white), var(--success))"
                        : "linear-gradient(160deg, var(--accent), var(--accent-strong))",
                      color: "var(--on-accent)",
                      padding: "18px 22px",
                      fontWeight: 900,
                      letterSpacing: "0.045em",
                      boxShadow: playerSetupSelectionMissing ? "none" : "0 16px 30px color-mix(in srgb, var(--accent-strong) 28%, transparent)",
                      opacity: playerSetupSelectionMissing ? 0.55 : 1
                    }}
                  >
                    {playerSetupSelectionMissing
                      ? setupBlockedLabel
                      : currentPlayer?.isReady
                        ? text.notReady
                        : text.ready}
                  </button>
                )}

                {selectedGame ? (
                  <div
                    className="lobby-game-summary"
                    style={{
                      display: "grid",
                      gridTemplateColumns: "64px minmax(0, 1fr)",
                      gap: 12,
                      alignItems: "center",
                      padding: "12px",
                      borderRadius: 16,
                      border: "1px solid color-mix(in srgb, var(--line-strong) 52%, transparent)",
                      background: "linear-gradient(145deg, color-mix(in srgb, var(--surface-raised) 76%, transparent), color-mix(in srgb, var(--surface-muted) 72%, transparent))",
                      color: "var(--text-muted)",
                      boxShadow: "inset 0 1px 0 color-mix(in srgb, white 58%, transparent), 0 12px 26px color-mix(in srgb, var(--ink) 7%, transparent)"
                    }}
                  >
                    <img
                      src={`/party-covers/${selectedGame.id}.webp`}
                      alt=""
                      width={64}
                      height={64}
                      style={{ width: 64, height: 64, objectFit: "cover", borderRadius: 12, boxShadow: "0 8px 18px color-mix(in srgb, var(--ink) 18%, transparent)" }}
                    />
                    <div style={{ display: "grid", gap: 4, minWidth: 0 }}>
                      <strong style={{ color: "var(--text-main)", fontSize: "1.02rem" }}>{selectedGame.displayName}</strong>
                      <span>
                        {text.players}: {playerCount}/{maximumPlayers} | {text.minRequired(minimumPlayers)}
                      </span>
                      <span>
                        {text.readyCount}: {readyCount}/{playerCount}
                        {enoughPlayers ? "" : ` | ${text.needsMorePlayers}`}
                      </span>
                      {playerSetup ? (
                        <span>
                          {renderPlayerSetupSummary(playerSetup, currentPlayer, text)}
                        </span>
                      ) : null}
                    </div>
                  </div>
                ) : null}

                {room && selectedGame && playerSetup?.kind === "choice"
                  ? renderChoicePlayerSetupChooser(room, selectedGame, currentPlayer, text, onSetPlayerSetup)
                  : null}

                {selectedGame && playerSetup?.kind === "multi-select"
                  ? renderMultiSelectPlayerSetupChooser(selectedGame, currentPlayer, text, onSetPlayerSetup)
                  : null}
              </>
            ) : (
              <>
                {currentPlayer && room ? (
                  <PlayerProfileEditor
                    key={currentPlayer.id}
                    player={currentPlayer}
                    language={room.language}
                    onSave={onSetProfile}
                  />
                ) : null}

                <button type="button" onClick={onLeaveRoom} style={leaveRoomButtonStyle}>
                  {text.leaveRoom}
                </button>

                <div style={{ display: "grid", gap: 10 }}>
                  <p style={{ margin: "2px 0", color: "var(--text-muted)", fontSize: ".78rem", letterSpacing: "1.4px", textTransform: "uppercase" }}>
                    {text.players} · {playerCount}/{maximumPlayers}
                  </p>
                  {(room?.players ?? []).map((entry) => (
                    <div
                      key={entry.id}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        padding: "12px 14px",
                        borderRadius: 14,
                        border: "1px solid color-mix(in srgb, var(--line) 74%, transparent)",
                        background: "linear-gradient(145deg, color-mix(in srgb, var(--surface-raised) 70%, transparent), color-mix(in srgb, var(--surface-muted) 64%, transparent))"
                      }}
                    >
                      <div style={{ display: "grid", gap: 4 }}>
                        <strong>
                          {entry.avatar && <img src={entry.avatar} alt="" width={30} height={30} style={{ borderRadius: "50%", objectFit: "cover", verticalAlign: "middle", marginRight: 8 }} />}
                          {entry.name}
                          {entry.isBot ? <span style={{ marginLeft: 8, padding: "2px 7px", borderRadius: 999, background: "var(--paper-deep)", color: "var(--text-muted)", fontSize: ".66rem", letterSpacing: "1px" }}>BOT</span> : null}
                        </strong>
                        {hasPlayerSetup && playerSetup ? (
                          <span style={{ color: "var(--text-muted)", fontSize: "0.86rem" }}>
                            {renderPlayerSetupPlayerDetail(entry, playerSetup, text)}
                          </span>
                        ) : null}
                      </div>
                      <span style={{ color: entry.connected ? "var(--success)" : "var(--text-muted)" }}>
                        {entry.connected ? (entry.isReady ? text.connectedReady : text.connectedWaiting) : entry.presence}
                      </span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </ControllerFrame>
  );
}

const leaveRoomButtonStyle = {
  justifySelf: "start",
  border: "1px solid color-mix(in srgb, var(--danger) 34%, transparent)",
  borderRadius: 999,
  background: "color-mix(in srgb, var(--danger-soft) 34%, transparent)",
  color: "var(--danger)",
  padding: "10px 16px",
  fontWeight: 700
} as const;

/** Two-page lobby: play and room. The host gets its own tabbed panel instead. */
function LobbyTabBar({
  active,
  onChange
}: {
  active: "play" | "room";
  onChange: (tab: "play" | "room") => void;
}) {
  const tabs: Array<{ id: "play" | "room"; label: string }> = [
    { id: "play", label: "Jugar" },
    { id: "room", label: "Sala" }
  ];

  return (
    <div
      role="tablist"
      aria-label="Lobby"
      style={{
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 4,
        padding: 4,
        borderRadius: 999,
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
              minHeight: 44,
              borderRadius: 999,
              border: selected ? "1px solid var(--accent)" : "1px solid transparent",
              background: selected ? "var(--accent-soft)" : "transparent",
              color: selected ? "var(--ink)" : "var(--text-muted)",
              fontWeight: selected ? 800 : 600,
              letterSpacing: "0.02em"
            }}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
