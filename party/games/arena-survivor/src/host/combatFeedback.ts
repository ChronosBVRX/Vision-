import Phaser from "phaser";
import type { ArenaSurvivorState } from "../protocol.js";

/** Host-only effects, driven by authoritative hits and weapon fire timestamps. */
export function createCombatFeedback(scene: Phaser.Scene) {
  let previous: ArenaSurvivorState | null = null;
  const seen = new Set<string>();
  const labels = new Set<Phaser.GameObjects.Text>();
  const voices = new Set<OscillatorNode>();
  const lastSound = new Map<string, number>();

  function sound(kind: "shot" | "swing" | "hit") {
    const manager = scene.sound;
    if (!(manager instanceof Phaser.Sound.WebAudioSoundManager) || manager.context.state !== "running") return;
    const now = manager.context.currentTime;
    if (now - (lastSound.get(kind) ?? -1) < 0.065 || voices.size >= 12) return;
    lastSound.set(kind, now);
    const oscillator = manager.context.createOscillator();
    const gain = manager.context.createGain();
    const [start, end, duration, volume] = kind === "shot" ? [620, 100, 0.085, 0.035]
      : kind === "swing" ? [240, 65, 0.14, 0.045] : [165, 45, 0.065, 0.055];
    oscillator.type = kind === "shot" ? "square" : "triangle";
    oscillator.frequency.setValueAtTime(start, now);
    oscillator.frequency.exponentialRampToValueAtTime(end, now + duration);
    gain.gain.setValueAtTime(0.001, now);
    gain.gain.linearRampToValueAtTime(volume, now + 0.004);
    gain.gain.exponentialRampToValueAtTime(0.001, now + duration);
    oscillator.connect(gain);
    gain.connect(manager.destination);
    voices.add(oscillator);
    oscillator.onended = () => { voices.delete(oscillator); oscillator.disconnect(); gain.disconnect(); };
    oscillator.start(now);
    oscillator.stop(now + duration);
  }

  function clear() {
    previous = null;
    seen.clear();
    for (const label of labels) { scene.tweens.killTweensOf(label); label.destroy(); }
    labels.clear();
    for (const voice of voices) voice.stop();
    voices.clear();
    lastSound.clear();
  }

  return {
    clear,
    accept(state: ArenaSurvivorState, reset: boolean, live: boolean) {
      if (reset || (previous && state.elapsedMs < previous.elapsedMs)) clear();
      const events = state.damageEvents ?? [];
      if (previous && (live || state.elapsedMs > previous.elapsedMs)) {
        for (const player of state.players) {
          const old = previous.players.find(p => p.playerId === player.playerId);
          player.weaponRuntimeStates.forEach((weapon, slot) => {
            if (weapon.lastFiredAt !== null && weapon.lastFiredAt !== undefined && old &&
                weapon.lastFiredAt !== old.weaponRuntimeStates[slot]?.lastFiredAt) {
              sound(player.loadout.weapons[slot]?.category === "melee" ? "swing" : "shot");
            }
          });
        }
        for (const event of events) {
          if (seen.has(event.id) || state.elapsedMs - event.atMs > 400) continue;
          sound("hit");
          if (labels.size >= 96) continue;
          const offset = (Number(event.id.split(":").at(-1)) % 5 - 2) * 9;
          const label = scene.add.text(event.x + offset, event.y - 12, String(Math.max(1, Math.round(event.damage))), {
            fontFamily: "Arial, sans-serif", fontSize: "24px", fontStyle: "bold",
            color: "#fff0a8", stroke: "#24120d", strokeThickness: 4
          }).setOrigin(0.5, 1).setDepth(30);
          labels.add(label);
          scene.tweens.add({ targets: label, y: label.y - 48, alpha: 0, duration: 650, ease: "Cubic.Out",
            onComplete: () => { labels.delete(label); label.destroy(); } });
        }
      }
      seen.clear();
      events.forEach(event => seen.add(event.id));
      previous = state;
    }
  };
}
