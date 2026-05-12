/* tslint:disable */
/* eslint-disable */

export class KaffeeEngine {
    free(): void;
    [Symbol.dispose](): void;
    /**
     * Calculate statistics from concentration array
     */
    calculate_statistics(concentrations: Float64Array): Float64Array;
    constructor(cl: number, vd: number, ka: number, f: number, sigma: number);
    /**
     * Simulate with Euler-Maruyama solver
     */
    simulate_euler(dose: number, interval: number, num_doses: number, duration: number, dt: number): Float64Array;
    /**
     * Simulate with Stratonovich solver
     */
    simulate_stratonovich(dose: number, interval: number, num_doses: number, duration: number, dt: number): Float64Array;
    /**
     * Simulate with Symplectic solver
     */
    simulate_symplectic(dose: number, interval: number, num_doses: number, duration: number, dt: number): Float64Array;
}

export function monte_carlo_simulation(cl: number, vd: number, ka: number, f: number, sigma: number, dose: number, interval: number, num_doses: number, duration: number, dt: number, num_sims: number, solver_type: string): Float64Array;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly __wbg_kaffeeengine_free: (a: number, b: number) => void;
    readonly kaffeeengine_calculate_statistics: (a: number, b: number, c: number) => any;
    readonly kaffeeengine_new: (a: number, b: number, c: number, d: number, e: number) => number;
    readonly kaffeeengine_simulate_euler: (a: number, b: number, c: number, d: number, e: number, f: number) => any;
    readonly kaffeeengine_simulate_stratonovich: (a: number, b: number, c: number, d: number, e: number, f: number) => any;
    readonly kaffeeengine_simulate_symplectic: (a: number, b: number, c: number, d: number, e: number, f: number) => any;
    readonly monte_carlo_simulation: (a: number, b: number, c: number, d: number, e: number, f: number, g: number, h: number, i: number, j: number, k: number, l: number, m: number) => any;
    readonly __wbindgen_externrefs: WebAssembly.Table;
    readonly __wbindgen_malloc: (a: number, b: number) => number;
    readonly __wbindgen_realloc: (a: number, b: number, c: number, d: number) => number;
    readonly __wbindgen_start: () => void;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;

/**
 * Instantiates the given `module`, which can either be bytes or
 * a precompiled `WebAssembly.Module`.
 *
 * @param {{ module: SyncInitInput }} module - Passing `SyncInitInput` directly is deprecated.
 *
 * @returns {InitOutput}
 */
export function initSync(module: { module: SyncInitInput } | SyncInitInput): InitOutput;

/**
 * If `module_or_path` is {RequestInfo} or {URL}, makes a request and
 * for everything else, calls `WebAssembly.instantiate` directly.
 *
 * @param {{ module_or_path: InitInput | Promise<InitInput> }} module_or_path - Passing `InitInput` directly is deprecated.
 *
 * @returns {Promise<InitOutput>}
 */
export default function __wbg_init (module_or_path?: { module_or_path: InitInput | Promise<InitInput> } | InitInput | Promise<InitInput>): Promise<InitOutput>;
