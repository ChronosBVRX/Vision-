/** Short, original synthesis cues; no downloaded samples or pending autoplay. */
export function createSchaetzoramaSounds() {
  let context: AudioContext | null = null;
  let muted = false;
  const voices = new Set<OscillatorNode>();
  try { muted = localStorage.getItem("schaetzorama-effects-muted") === "true"; } catch { /* Storage is optional. */ }
  const unlock = () => {
    if (!context && typeof AudioContext !== "undefined") context = new AudioContext();
    if (context?.state === "suspended") void context.resume().catch(() => undefined);
  };
  window.addEventListener("pointerdown", unlock, { passive: true });
  window.addEventListener("keydown", unlock);
  if (navigator.userActivation?.hasBeenActive) unlock();

  const stop = () => { for (const voice of voices) { voice.stop(); voice.disconnect(); } voices.clear(); };
  return {
    get muted() { return muted; },
    toggle() {
      muted = !muted;
      if (muted) stop(); else unlock();
      try { localStorage.setItem("schaetzorama-effects-muted", String(muted)); } catch { /* Storage is optional. */ }
    },
    play(kind: "reveal" | "land" | "points" | "final" | "answer" | "sweep", index = 0) {
      if (muted || document.hidden || !context || context.state !== "running") return;
      const notes = kind === "final" ? [0, 4, 7, 12] : kind === "reveal" ? [0, 7] : kind === "answer" ? [index % 5 * 2, index % 5 * 2 + 7] : [index % 5 * 2];
      for (const [position, note] of notes.entries()) {
        const start = context.currentTime + position * .12;
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.type = kind === "answer" ? "triangle" : "sine";
        oscillator.frequency.setValueAtTime(330 * 2 ** (note / 12), start);
        if (kind === "sweep") oscillator.frequency.exponentialRampToValueAtTime(660, start + 1.3);
        if (kind === "land") oscillator.frequency.exponentialRampToValueAtTime(440 * 2 ** (note / 12), start + .08);
        gain.gain.setValueAtTime(0, start);
        gain.gain.linearRampToValueAtTime(kind === "points" ? .075 : .1, start + .012);
        gain.gain.exponentialRampToValueAtTime(.0001, start + (kind === "sweep" ? 1.4 : .24));
        oscillator.connect(gain); gain.connect(context.destination);
        voices.add(oscillator);
        oscillator.onended = () => { voices.delete(oscillator); oscillator.disconnect(); gain.disconnect(); };
        oscillator.start(start); oscillator.stop(start + (kind === "sweep" ? 1.43 : .27));
      }
    },
    dispose() {
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
      stop();
      if (context) void context.close().catch(() => undefined);
    }
  };
}
