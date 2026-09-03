/** Small, deterministic PRNG for reproducible browser and test simulations. */
export class SeededRandom {
  private state: number;
  private spareNormal: number | null = null;

  constructor(seed: number) {
    if (!Number.isFinite(seed)) throw new RangeError('seed must be finite');
    this.state = seed >>> 0;
  }

  nextUint32(): number {
    // Mulberry32: deterministic across JS runtimes because all operations are 32-bit.
    this.state = (this.state + 0x6D2B79F5) >>> 0;
    let value = this.state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return (value ^ (value >>> 14)) >>> 0;
  }

  uniform(): number {
    return this.nextUint32() / 0x100000000;
  }

  normal(): number {
    if (this.spareNormal !== null) {
      const value = this.spareNormal;
      this.spareNormal = null;
      return value;
    }

    const u1 = Math.max(Number.MIN_VALUE, this.uniform());
    const u2 = this.uniform();
    const radius = Math.sqrt(-2 * Math.log(u1));
    const angle = 2 * Math.PI * u2;
    this.spareNormal = radius * Math.sin(angle);
    return radius * Math.cos(angle);
  }
}
