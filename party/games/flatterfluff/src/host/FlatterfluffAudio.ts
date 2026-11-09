import type { FlatterfluffGameEvent, FlatterfluffState } from "../protocol.js";

const unlockEvents = ["pointerdown", "keydown", "touchstart"] as const;

function resolveAudioContextCtor(): typeof AudioContext | null {
  if (typeof window === "undefined") {
    return null;
  }
  const extendedWindow = window as Window & { webkitAudioContext?: typeof AudioContext };
  return window.AudioContext ?? extendedWindow.webkitAudioContext ?? null;
}

export class FlatterfluffAudioRig {
  private context: AudioContext | null = null;
  private master: GainNode | null = null;
  private noise: AudioBuffer | null = null;
  private knownEventIds = new Set<number>();
  private cleanup: Array<() => void> = [];

  constructor() {
    for (const eventName of unlockEvents) {
      const handler = () => {
        void this.ensureContext()?.resume().catch(() => undefined);
      };
      document.addEventListener(eventName, handler, { passive: true });
      this.cleanup.push(() => document.removeEventListener(eventName, handler));
    }
  }

  destroy(): void {
    this.cleanup.forEach((remove) => remove());
    this.cleanup = [];
    this.knownEventIds.clear();
    void this.context?.close();
    this.context = null;
    this.master = null;
    this.noise = null;
  }

  sync(state: FlatterfluffState): void {
    for (const event of state.events) {
      if (this.knownEventIds.has(event.id)) {
        continue;
      }
      this.knownEventIds.add(event.id);
      this.playEvent(event);
    }

    const currentIds = new Set(state.events.map((event) => event.id));
    for (const id of this.knownEventIds) {
      if (!currentIds.has(id)) {
        this.knownEventIds.delete(id);
      }
    }
  }

  private playEvent(event: FlatterfluffGameEvent): void {
    switch (event.kind) {
      case "shot":
        this.noiseBurst(0.075, 0.11, 3_900, 650);
        this.tone("triangle", 180, 72, 0.11, 0.055);
        break;
      case "hit":
        this.tone("sine", 520, 820, 0.13, 0.065);
        this.tone("triangle", 780, 1_180, 0.18, 0.045, 0.045);
        break;
      case "armor":
        this.noiseBurst(0.08, 0.05, 5_200, 2_100);
        this.tone("square", 360, 220, 0.12, 0.04);
        break;
      case "ammo":
        this.tone("triangle", 392, 392, 0.11, 0.05);
        this.tone("triangle", 523, 523, 0.13, 0.05, 0.08);
        this.tone("triangle", 659, 659, 0.18, 0.045, 0.16);
        break;
      case "empty":
        this.noiseBurst(0.025, 0.035, 7_000, 5_000);
        break;
      case "reload":
        this.noiseBurst(0.045, 0.045, 6_400, 1_800);
        this.tone("square", 210, 125, 0.09, 0.03);
        this.noiseBurst(0.045, 0.04, 5_600, 1_500, 0.34);
        this.tone("square", 245, 145, 0.09, 0.028, 0.34);
        this.noiseBurst(0.055, 0.045, 5_000, 1_200, 0.72);
        this.tone("triangle", 170, 285, 0.14, 0.03, 0.72);
        break;
      case "reload-complete":
        this.noiseBurst(0.035, 0.035, 7_200, 2_600);
        this.tone("triangle", 280, 560, 0.16, 0.045);
        break;
      case "miss":
        break;
    }
  }

  private tone(
    wave: OscillatorType,
    fromHz: number,
    toHz: number,
    duration: number,
    gainValue: number,
    delay = 0
  ): void {
    const context = this.ensureContext();
    if (!context || !this.master) {
      return;
    }

    const start = context.currentTime + delay;
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = wave;
    oscillator.frequency.setValueAtTime(fromHz, start);
    oscillator.frequency.exponentialRampToValueAtTime(Math.max(20, toHz), start + duration);
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(gainValue, start + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    oscillator.connect(gain);
    gain.connect(this.master);
    oscillator.start(start);
    oscillator.stop(start + duration + 0.03);
  }

  private noiseBurst(
    duration: number,
    gainValue: number,
    startHz: number,
    endHz: number,
    delay = 0
  ): void {
    const context = this.ensureContext();
    if (!context || !this.master) {
      return;
    }

    const source = context.createBufferSource();
    const filter = context.createBiquadFilter();
    const gain = context.createGain();
    const start = context.currentTime + delay;
    source.buffer = this.getNoise(context);
    filter.type = "bandpass";
    filter.frequency.setValueAtTime(startHz, start);
    filter.frequency.exponentialRampToValueAtTime(endHz, start + duration);
    filter.Q.value = 0.7;
    gain.gain.setValueAtTime(gainValue, start);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    source.connect(filter);
    filter.connect(gain);
    gain.connect(this.master);
    source.start(start);
    source.stop(start + duration + 0.02);
  }

  private getNoise(context: AudioContext): AudioBuffer {
    if (this.noise) {
      return this.noise;
    }
    const buffer = context.createBuffer(1, context.sampleRate, context.sampleRate);
    const channel = buffer.getChannelData(0);
    for (let index = 0; index < channel.length; index += 1) {
      channel[index] = Math.random() * 2 - 1;
    }
    this.noise = buffer;
    return buffer;
  }

  private ensureContext(): AudioContext | null {
    if (this.context && this.master) {
      return this.context;
    }
    const AudioContextCtor = resolveAudioContextCtor();
    if (!AudioContextCtor) {
      return null;
    }
    this.context = new AudioContextCtor();
    this.master = this.context.createGain();
    this.master.gain.value = 0.3;
    this.master.connect(this.context.destination);
    return this.context;
  }
}
