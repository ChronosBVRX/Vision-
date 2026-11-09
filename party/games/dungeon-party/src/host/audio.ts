type AudioContextCtor = new () => AudioContext;

export class DungeonAudioRig {
  private context: AudioContext | null = null;
  private enabled = true;
  private readonly unlockHandler = () => { void this.ensureContext()?.resume().catch(() => undefined); };

  constructor(private readonly onEnabledChange: (enabled: boolean) => void) {
    document.addEventListener("pointerdown", this.unlockHandler, { passive: true });
    document.addEventListener("keydown", this.unlockHandler);
  }

  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
    this.onEnabledChange(enabled);
    if (enabled) void this.ensureContext()?.resume().catch(() => undefined);
  }

  card(): void {
    this.play((ctx, now) => {
      this.tone(ctx, now, "triangle", 520, 880, 0.18, 0.09, 0);
      this.noise(ctx, now, 0.11, 0.045, 3_200, 1_000, 0.015);
    });
  }

  dice(): void {
    this.play((ctx, now) => {
      this.noise(ctx, now, 0.64, 0.12, 2_100, 380, 0);
      for (let index = 0; index < 7; index += 1) {
        const at = now + index * 0.075;
        this.tone(ctx, at, "triangle", 125 + (index % 3) * 35, 84, 0.075, 0.045, 0);
      }
      this.tone(ctx, now + 0.59, "sine", 180, 95, 0.16, 0.11, 0);
    });
  }

  verdict(success: boolean): void {
    this.play((ctx, now) => {
      if (success) {
        [392, 494, 587, 784].forEach((note, index) => this.tone(ctx, now + index * 0.075, "sine", note, note, 0.55, 0.055, 0));
      } else {
        this.tone(ctx, now, "sawtooth", 148, 62, 0.72, 0.12, 0);
        this.noise(ctx, now, 0.22, 0.045, 900, 180, 0.025);
      }
    });
  }

  impact(): void {
    this.play((ctx, now) => {
      this.noise(ctx, now, 0.12, 0.07, 1_100, 180, 0);
      this.tone(ctx, now, "sine", 118, 52, 0.2, 0.11, 0);
    });
  }

  destroy(): void {
    document.removeEventListener("pointerdown", this.unlockHandler);
    document.removeEventListener("keydown", this.unlockHandler);
    void this.context?.close().catch(() => undefined);
    this.context = null;
  }

  private play(build: (context: AudioContext, now: number) => void): void {
    if (!this.enabled) return;
    const context = this.ensureContext();
    if (!context || context.state !== "running") return;
    try { build(context, context.currentTime); } catch { /* Sound is optional; gameplay stays available. */ }
  }

  private ensureContext(): AudioContext | null {
    if (this.context) return this.context;
    const view = window as Window & { webkitAudioContext?: AudioContextCtor };
    const Ctor = window.AudioContext ?? view.webkitAudioContext;
    if (!Ctor) return null;
    try { this.context = new Ctor(); } catch { return null; }
    return this.context;
  }

  private tone(context: AudioContext, at: number, wave: OscillatorType, from: number, to: number, duration: number, volume: number, delay: number): void {
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    const start = at + delay;
    oscillator.type = wave;
    oscillator.frequency.setValueAtTime(Math.max(1, from), start);
    oscillator.frequency.exponentialRampToValueAtTime(Math.max(1, to), start + duration);
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(volume, start + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    oscillator.connect(gain).connect(context.destination);
    oscillator.start(start);
    oscillator.stop(start + duration + 0.02);
  }

  private noise(context: AudioContext, at: number, duration: number, volume: number, high: number, low: number, delay: number): void {
    const frames = Math.max(1, Math.floor(context.sampleRate * duration));
    const buffer = context.createBuffer(1, frames, context.sampleRate);
    const channel = buffer.getChannelData(0);
    for (let index = 0; index < frames; index += 1) channel[index] = Math.random() * 2 - 1;
    const source = context.createBufferSource();
    source.buffer = buffer;
    const filter = context.createBiquadFilter();
    filter.type = "bandpass";
    filter.frequency.setValueAtTime(high, at + delay);
    filter.frequency.exponentialRampToValueAtTime(Math.max(1, low), at + delay + duration);
    filter.Q.value = 0.8;
    const gain = context.createGain();
    gain.gain.setValueAtTime(0.0001, at + delay);
    gain.gain.exponentialRampToValueAtTime(volume, at + delay + 0.018);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + delay + duration);
    source.connect(filter).connect(gain).connect(context.destination);
    source.start(at + delay);
    source.stop(at + delay + duration + 0.02);
  }
}
