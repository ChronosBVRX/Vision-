/** Kleiner deterministischer PRNG (mulberry32), damit Karten reproduzierbar sind. */
export interface SeededRandom {
  next(): number;
  int(minInclusive: number, maxExclusive: number): number;
  pick<T>(values: readonly T[]): T;
  shuffle<T>(values: readonly T[]): T[];
}

export function hashSeed(input: string): number {
  let hash = 0x811c9dc5;

  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }

  return hash >>> 0;
}

export function createSeededRandom(seed: number | string): SeededRandom {
  let state = (typeof seed === "string" ? hashSeed(seed) : seed >>> 0) || 0x9e3779b9;

  const next = (): number => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };

  const int = (minInclusive: number, maxExclusive: number): number => {
    if (maxExclusive <= minInclusive) {
      return minInclusive;
    }

    return minInclusive + Math.floor(next() * (maxExclusive - minInclusive));
  };

  return {
    next,
    int,
    pick<T>(values: readonly T[]): T {
      return values[int(0, values.length)];
    },
    shuffle<T>(values: readonly T[]): T[] {
      const copy = [...values];

      for (let index = copy.length - 1; index > 0; index -= 1) {
        const swapIndex = int(0, index + 1);
        const temp = copy[index];
        copy[index] = copy[swapIndex];
        copy[swapIndex] = temp;
      }

      return copy;
    }
  };
}
