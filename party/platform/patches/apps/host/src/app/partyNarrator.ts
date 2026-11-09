import type { HostAppState, HostSocketClient } from "./hostSocketClient.js";

const NARRATED_GAMES = new Set([
  "pantomime",
  "zeichnen-und-erraten",
  "schaetzorama",
  "imposter",
  "buzzwort",
  "schattenjagd",
  "flatterfluff",
  "magic-arena",
  "card-table",
  "dungeon-party"
]);

const momentForPhase = (phase: string | null | undefined): "intro" | "playing" | "result" | "scoreboard" | null => {
  if (!phase) return null;
  if (phase === "round_intro" || phase === "countdown") return "intro";
  if (phase === "playing" || phase === "locked") return "playing";
  if (phase === "result" || phase === "reveal" || phase === "finished") return "result";
  if (phase === "scoreboard") return "scoreboard";
  return null;
};

export function mountPartyNarrator(client: HostSocketClient): () => void {
  const bags = new Map<string, number[]>();
  let lastKey = "";
  let currentAudio: HTMLAudioElement | null = null;
  // One narration line owns the room. New moments keep the current line
  // alive, remember only the newest follow-up, and play it after the line
  // ends. This prevents overlapping takes and preserves the full phase cue.
  let pendingSource: string | null = null;

  const chooseSource = (gameId: string, moment: string): string => {
    const bucket = `${gameId}:${moment}`;
    let bag = bags.get(bucket);
    if (!bag || bag.length === 0) {
      bag = Array.from({ length: 10 }, (_, index) => index);
      for (let index = bag.length - 1; index > 0; index -= 1) {
        const swap = Math.floor(Math.random() * (index + 1));
        [bag[index], bag[swap]] = [bag[swap], bag[index]];
      }
      bags.set(bucket, bag);
    }
    const variant = bag.pop() ?? 0;
    return `/party-audio/${gameId}/narrator_${moment}_${variant}.m4a`;
  };

  const playNext = (): void => {
    if (currentAudio || !pendingSource) return;
    const source = pendingSource;
    pendingSource = null;
    const audio = new Audio(source);
    audio.volume = 0.82;
    audio.preload = "auto";
    currentAudio = audio;
    const finish = () => {
      if (currentAudio === audio) currentAudio = null;
      playNext();
    };
    audio.addEventListener("ended", finish, { once: true });
    audio.addEventListener("error", finish, { once: true });
    void audio.play().catch(finish);
  };

  const unsubscribe = client.subscribe((state: HostAppState) => {
    const gameId = state.room?.selectedGameId;
    if (!gameId || !NARRATED_GAMES.has(gameId)) return;
    const phase = state.room?.currentRound?.phase ?? state.game?.phase ?? null;
    const moment = momentForPhase(phase);
    if (!moment) return;
    const roundKey = state.room?.currentRound?.roundNumber ?? "0";
    const key = `${gameId}:${moment}:${roundKey}`;
    if (key === lastKey || key === pendingSource) return;
    lastKey = key;
    pendingSource = chooseSource(gameId, moment);
    playNext();
  });

  return () => {
    unsubscribe();
    pendingSource = null;
    currentAudio?.removeAttribute("src");
    currentAudio?.pause();
    currentAudio?.load();
    currentAudio = null;
  };
}
