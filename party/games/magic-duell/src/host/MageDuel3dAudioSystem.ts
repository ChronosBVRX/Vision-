import type { MageDuelEffectState, MageDuelProjectileState, MageDuelSpellId } from "../protocol.js";

type MageDuelAudioCue =
  | "cast:start"
  | "cast:fizzle"
  | "effect:start"
  | "effect:loop"
  | "effect:end"
  | "projectile:launch"
  | "projectile:fly"
  | "projectile:impact";

type AudioCueDefinition =
  | {
      kind: "tone";
      frequency: number;
      durationMs: number;
      gain: number;
      type?: OscillatorType;
    }
  | {
      kind: "file";
      src: string;
      gain?: number;
      loop?: boolean;
    };

const spellAudioCues: Partial<Record<MageDuelSpellId, Partial<Record<MageDuelAudioCue, AudioCueDefinition>>>> = {
  fire_bolt: {
    "cast:start": { kind: "tone", frequency: 220, durationMs: 170, gain: 0.035, type: "sawtooth" },
    "projectile:launch": { kind: "tone", frequency: 520, durationMs: 120, gain: 0.045, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 180, durationMs: 360, gain: 0.02, type: "sawtooth" },
    "projectile:impact": { kind: "tone", frequency: 96, durationMs: 230, gain: 0.055, type: "square" }
  },
  flame_drop: {
    "cast:start": { kind: "tone", frequency: 185, durationMs: 190, gain: 0.036, type: "sawtooth" },
    "projectile:launch": { kind: "tone", frequency: 430, durationMs: 150, gain: 0.043, type: "sawtooth" },
    "projectile:fly": { kind: "tone", frequency: 140, durationMs: 420, gain: 0.018, type: "sawtooth" },
    "projectile:impact": { kind: "tone", frequency: 82, durationMs: 260, gain: 0.058, type: "square" }
  },
  ember_orb: {
    "cast:start": { kind: "tone", frequency: 205, durationMs: 210, gain: 0.034, type: "triangle" },
    "projectile:launch": { kind: "tone", frequency: 360, durationMs: 170, gain: 0.043, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 116, durationMs: 520, gain: 0.019, type: "sawtooth" },
    "projectile:impact": { kind: "tone", frequency: 74, durationMs: 290, gain: 0.06, type: "square" }
  },
  meteor_square: {
    "cast:start": { kind: "tone", frequency: 150, durationMs: 240, gain: 0.04, type: "sawtooth" },
    "projectile:launch": { kind: "tone", frequency: 290, durationMs: 210, gain: 0.046, type: "square" },
    "projectile:fly": { kind: "tone", frequency: 98, durationMs: 620, gain: 0.018, type: "sawtooth" },
    "projectile:impact": { kind: "tone", frequency: 62, durationMs: 340, gain: 0.064, type: "square" },
    "effect:start": { kind: "tone", frequency: 120, durationMs: 260, gain: 0.036, type: "sawtooth" }
  },
  burn_spiral: {
    "cast:start": { kind: "tone", frequency: 240, durationMs: 220, gain: 0.034, type: "sawtooth" },
    "projectile:launch": { kind: "tone", frequency: 410, durationMs: 180, gain: 0.042, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 155, durationMs: 560, gain: 0.017, type: "sawtooth" },
    "projectile:impact": { kind: "tone", frequency: 88, durationMs: 250, gain: 0.052, type: "square" },
    "effect:start": { kind: "tone", frequency: 140, durationMs: 310, gain: 0.035, type: "sawtooth" },
    "effect:loop": { kind: "tone", frequency: 96, durationMs: 720, gain: 0.014, type: "sawtooth" }
  },
  scarlet_lightning: {
    "cast:start": { kind: "tone", frequency: 520, durationMs: 110, gain: 0.037, type: "square" },
    "projectile:launch": { kind: "tone", frequency: 920, durationMs: 80, gain: 0.05, type: "square" },
    "projectile:fly": { kind: "tone", frequency: 430, durationMs: 220, gain: 0.018, type: "square" },
    "projectile:impact": { kind: "tone", frequency: 180, durationMs: 160, gain: 0.06, type: "square" }
  },
  rift_cleave: {
    "cast:start": { kind: "tone", frequency: 260, durationMs: 170, gain: 0.038, type: "triangle" },
    "projectile:launch": { kind: "tone", frequency: 620, durationMs: 120, gain: 0.044, type: "sawtooth" },
    "projectile:fly": { kind: "tone", frequency: 210, durationMs: 360, gain: 0.016, type: "triangle" },
    "projectile:impact": { kind: "tone", frequency: 118, durationMs: 230, gain: 0.054, type: "sawtooth" }
  },
  arcane_bolt: {
    "cast:start": { kind: "tone", frequency: 390, durationMs: 140, gain: 0.032, type: "sine" },
    "projectile:launch": { kind: "tone", frequency: 740, durationMs: 95, gain: 0.04, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 310, durationMs: 320, gain: 0.018, type: "sine" },
    "projectile:impact": { kind: "tone", frequency: 260, durationMs: 180, gain: 0.045, type: "triangle" }
  },
  shield: {
    "cast:start": { kind: "tone", frequency: 330, durationMs: 150, gain: 0.032, type: "sine" },
    "effect:start": { kind: "tone", frequency: 660, durationMs: 240, gain: 0.035, type: "sine" },
    "effect:end": { kind: "tone", frequency: 240, durationMs: 180, gain: 0.025, type: "triangle" }
  },
  frost_ward: {
    "cast:start": { kind: "tone", frequency: 300, durationMs: 160, gain: 0.03, type: "sine" },
    "effect:start": { kind: "tone", frequency: 560, durationMs: 230, gain: 0.034, type: "sine" },
    "effect:end": { kind: "tone", frequency: 210, durationMs: 190, gain: 0.023, type: "triangle" }
  },
  heal: {
    "cast:start": { kind: "tone", frequency: 420, durationMs: 150, gain: 0.025, type: "sine" },
    "effect:start": { kind: "tone", frequency: 580, durationMs: 260, gain: 0.03, type: "sine" }
  },
  thorn_lance: {
    "cast:start": { kind: "tone", frequency: 310, durationMs: 150, gain: 0.026, type: "triangle" },
    "projectile:launch": { kind: "tone", frequency: 610, durationMs: 120, gain: 0.038, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 240, durationMs: 340, gain: 0.015, type: "triangle" },
    "projectile:impact": { kind: "tone", frequency: 150, durationMs: 220, gain: 0.043, type: "square" }
  },
  vine_wall: {
    "cast:start": { kind: "tone", frequency: 270, durationMs: 170, gain: 0.026, type: "triangle" },
    "effect:start": { kind: "tone", frequency: 430, durationMs: 250, gain: 0.03, type: "sine" },
    "effect:end": { kind: "tone", frequency: 180, durationMs: 200, gain: 0.022, type: "triangle" }
  },
  regeneration: {
    "cast:start": { kind: "tone", frequency: 360, durationMs: 170, gain: 0.025, type: "sine" },
    "effect:start": { kind: "tone", frequency: 520, durationMs: 260, gain: 0.03, type: "sine" },
    "effect:loop": { kind: "tone", frequency: 210, durationMs: 640, gain: 0.012, type: "sine" }
  },
  fire_wall: {
    "cast:start": { kind: "tone", frequency: 180, durationMs: 190, gain: 0.04, type: "sawtooth" },
    "effect:start": { kind: "tone", frequency: 130, durationMs: 300, gain: 0.05, type: "sawtooth" },
    "effect:loop": { kind: "tone", frequency: 86, durationMs: 700, gain: 0.018, type: "sawtooth" },
    "effect:end": { kind: "tone", frequency: 110, durationMs: 210, gain: 0.025, type: "triangle" }
  },
  mana_surge: {
    "cast:start": { kind: "tone", frequency: 480, durationMs: 130, gain: 0.025, type: "sine" },
    "effect:start": { kind: "tone", frequency: 760, durationMs: 250, gain: 0.032, type: "triangle" }
  },
  mirror_square: {
    "cast:start": { kind: "tone", frequency: 280, durationMs: 180, gain: 0.03, type: "sine" },
    "effect:start": { kind: "tone", frequency: 520, durationMs: 290, gain: 0.036, type: "sine" },
    "effect:end": { kind: "tone", frequency: 190, durationMs: 210, gain: 0.022, type: "triangle" }
  },
  mana_triplet: {
    "cast:start": { kind: "tone", frequency: 510, durationMs: 150, gain: 0.026, type: "sine" },
    "effect:start": { kind: "tone", frequency: 820, durationMs: 300, gain: 0.034, type: "triangle" }
  },
  null_cross: {
    "cast:start": { kind: "tone", frequency: 210, durationMs: 170, gain: 0.032, type: "square" },
    "projectile:launch": { kind: "tone", frequency: 360, durationMs: 130, gain: 0.038, type: "square" },
    "projectile:fly": { kind: "tone", frequency: 140, durationMs: 410, gain: 0.014, type: "sine" },
    "projectile:impact": { kind: "tone", frequency: 120, durationMs: 260, gain: 0.046, type: "square" },
    "effect:start": { kind: "tone", frequency: 180, durationMs: 240, gain: 0.033, type: "triangle" }
  },
  tide_barrier: {
    "cast:start": { kind: "tone", frequency: 340, durationMs: 190, gain: 0.026, type: "sine" },
    "effect:start": { kind: "tone", frequency: 610, durationMs: 320, gain: 0.031, type: "sine" },
    "effect:end": { kind: "tone", frequency: 260, durationMs: 190, gain: 0.021, type: "triangle" }
  },
  clover_bloom: {
    "cast:start": { kind: "tone", frequency: 390, durationMs: 190, gain: 0.026, type: "sine" },
    "effect:start": { kind: "tone", frequency: 650, durationMs: 320, gain: 0.032, type: "sine" },
    "effect:loop": { kind: "tone", frequency: 245, durationMs: 650, gain: 0.012, type: "sine" }
  },
  poison_spiral: {
    "cast:start": { kind: "tone", frequency: 290, durationMs: 190, gain: 0.027, type: "triangle" },
    "projectile:launch": { kind: "tone", frequency: 470, durationMs: 140, gain: 0.036, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 170, durationMs: 510, gain: 0.014, type: "triangle" },
    "projectile:impact": { kind: "tone", frequency: 110, durationMs: 260, gain: 0.044, type: "square" },
    "effect:start": { kind: "tone", frequency: 150, durationMs: 280, gain: 0.03, type: "triangle" },
    "effect:loop": { kind: "tone", frequency: 82, durationMs: 760, gain: 0.012, type: "triangle" }
  },
  root_snare: {
    "cast:start": { kind: "tone", frequency: 250, durationMs: 160, gain: 0.026, type: "triangle" },
    "projectile:launch": { kind: "tone", frequency: 520, durationMs: 120, gain: 0.036, type: "triangle" },
    "projectile:fly": { kind: "tone", frequency: 190, durationMs: 360, gain: 0.014, type: "triangle" },
    "projectile:impact": { kind: "tone", frequency: 130, durationMs: 220, gain: 0.042, type: "square" },
    "effect:start": { kind: "tone", frequency: 210, durationMs: 210, gain: 0.028, type: "triangle" }
  },
  verdant_chevron: {
    "cast:start": { kind: "tone", frequency: 330, durationMs: 170, gain: 0.025, type: "sine" },
    "effect:start": { kind: "tone", frequency: 560, durationMs: 270, gain: 0.03, type: "sine" },
    "effect:loop": { kind: "tone", frequency: 220, durationMs: 680, gain: 0.011, type: "sine" },
    "effect:end": { kind: "tone", frequency: 190, durationMs: 180, gain: 0.02, type: "triangle" }
  }
};

const fizzleCue: Extract<AudioCueDefinition, { kind: "tone" }> = {
  kind: "tone",
  frequency: 92,
  durationMs: 220,
  gain: 0.034,
  type: "sawtooth"
};

const fallbackCue: AudioCueDefinition = {
  kind: "tone",
  frequency: 330,
  durationMs: 120,
  gain: 0.018,
  type: "sine"
};

type LoopHandle = {
  oscillator: OscillatorNode;
  gain: GainNode;
};

type AudioWindow = Window &
  typeof globalThis & {
    webkitAudioContext?: typeof AudioContext;
  };

export class MageDuel3dAudioSystem {
  private context: AudioContext | null = null;
  private buffers = new Map<string, AudioBuffer>();
  private loops = new Map<string, LoopHandle>();
  private unlockListenerAttached = false;

  attachUnlockListeners(): void {
    if (this.unlockListenerAttached) {
      return;
    }

    this.unlockListenerAttached = true;
    const unlock = () => {
      void this.resume();
    };

    window.addEventListener("pointerdown", unlock, { passive: true });
    window.addEventListener("keydown", unlock);
  }

  playCastStart(spellId: MageDuelSpellId): void {
    this.play(spellId, "cast:start");
  }

  playProjectileLaunch(projectile: MageDuelProjectileState): void {
    this.play(projectile.spellId, "projectile:launch");
    this.startLoop(`projectile:${projectile.id}`, projectile.spellId, "projectile:fly");
  }

  stopProjectile(projectile: MageDuelProjectileState): void {
    this.stopLoop(`projectile:${projectile.id}`);
  }

  playEffectStart(effect: MageDuelEffectState): void {
    if (effect.kind === "fizzle") {
      this.play(null, "cast:fizzle");
      return;
    }

    if (!effect.spellId) {
      this.play(null, "effect:start");
      return;
    }

    if (effect.kind === "impact") {
      this.play(effect.spellId, "projectile:impact");
      return;
    }

    this.play(effect.spellId, "effect:start");

    if (
      effect.kind === "fire_wall" ||
      effect.kind === "dot" ||
      effect.kind === "boost" ||
      effect.spellId === "regeneration" ||
      effect.spellId === "clover_bloom"
    ) {
      this.startLoop(`effect:${effect.id}`, effect.spellId, "effect:loop");
    }
  }

  stopEffect(effect: MageDuelEffectState): void {
    this.stopLoop(`effect:${effect.id}`);

    if (effect.spellId && (effect.kind === "fire_wall" || effect.kind === "shield" || effect.kind === "boost")) {
      this.play(effect.spellId, "effect:end");
    }
  }

  stopAll(): void {
    for (const key of this.loops.keys()) {
      this.stopLoop(key);
    }
  }

  private async resume(): Promise<void> {
    const context = this.ensureContext();

    if (!context || context.state !== "suspended") {
      return;
    }

    try {
      await context.resume();
    } catch {
      // Browsers may still block audio until a stronger user gesture. Gameplay should continue silently.
    }
  }

  private play(spellId: MageDuelSpellId | null, cue: MageDuelAudioCue): void {
    if (cue === "cast:fizzle") {
      this.playTone(fizzleCue);
      return;
    }

    const definition = (spellId ? spellAudioCues[spellId]?.[cue] : undefined) ?? fallbackCue;

    if (definition.kind === "file") {
      void this.playFile(definition);
      return;
    }

    this.playTone(definition);
  }

  private startLoop(loopKey: string, spellId: MageDuelSpellId, cue: MageDuelAudioCue): void {
    if (this.loops.has(loopKey)) {
      return;
    }

    const definition = spellAudioCues[spellId]?.[cue];

    if (!definition) {
      return;
    }

    if (definition.kind === "file") {
      void this.playFile({ ...definition, loop: true }, loopKey);
      return;
    }

    const context = this.ensureContext();

    if (!context) {
      return;
    }

    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = definition.type ?? "sine";
    oscillator.frequency.setValueAtTime(definition.frequency, context.currentTime);
    gain.gain.setValueAtTime(Math.min(0.04, definition.gain), context.currentTime);
    oscillator.connect(gain);
    gain.connect(context.destination);
    oscillator.start();
    this.loops.set(loopKey, { oscillator, gain });
  }

  private stopLoop(loopKey: string): void {
    const handle = this.loops.get(loopKey);

    if (!handle || !this.context) {
      return;
    }

    const now = this.context.currentTime;
    handle.gain.gain.cancelScheduledValues(now);
    handle.gain.gain.setTargetAtTime(0, now, 0.045);
    handle.oscillator.stop(now + 0.12);
    handle.oscillator.disconnect();
    handle.gain.disconnect();
    this.loops.delete(loopKey);
  }

  private playTone(definition: Extract<AudioCueDefinition, { kind: "tone" }>): void {
    const context = this.ensureContext();

    if (!context) {
      return;
    }

    const oscillator = context.createOscillator();
    const gain = context.createGain();
    const now = context.currentTime;
    oscillator.type = definition.type ?? "sine";
    oscillator.frequency.setValueAtTime(definition.frequency, now);
    gain.gain.setValueAtTime(0, now);
    gain.gain.linearRampToValueAtTime(definition.gain, now + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + definition.durationMs / 1000);
    oscillator.connect(gain);
    gain.connect(context.destination);
    oscillator.start(now);
    oscillator.stop(now + definition.durationMs / 1000 + 0.03);
  }

  private async playFile(
    definition: Extract<AudioCueDefinition, { kind: "file" }>,
    loopKey?: string
  ): Promise<void> {
    const context = this.ensureContext();

    if (!context) {
      return;
    }

    try {
      const buffer = await this.loadBuffer(definition.src);
      const source = context.createBufferSource();
      const gain = context.createGain();
      source.buffer = buffer;
      source.loop = Boolean(definition.loop);
      gain.gain.value = definition.gain ?? 0.7;
      source.connect(gain);
      gain.connect(context.destination);
      source.start();

      if (loopKey) {
        this.loops.set(loopKey, {
          oscillator: source as unknown as OscillatorNode,
          gain
        });
      }
    } catch {
      // Missing or blocked audio assets should degrade silently while the visual prototype remains usable.
    }
  }

  private async loadBuffer(src: string): Promise<AudioBuffer> {
    const cached = this.buffers.get(src);

    if (cached) {
      return cached;
    }

    const context = this.ensureContext();

    if (!context) {
      throw new Error("AudioContext unavailable.");
    }

    const response = await fetch(src);
    const arrayBuffer = await response.arrayBuffer();
    const buffer = await context.decodeAudioData(arrayBuffer);
    this.buffers.set(src, buffer);
    return buffer;
  }

  private ensureContext(): AudioContext | null {
    if (this.context) {
      return this.context;
    }

    const AudioContextCtor = window.AudioContext ?? (window as AudioWindow).webkitAudioContext;

    if (!AudioContextCtor) {
      return null;
    }

    try {
      this.context = new AudioContextCtor();
    } catch {
      return null;
    }

    return this.context;
  }
}
