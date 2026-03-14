import type { GPUSimulationConfig, GPUSimulationResult } from './types';

export class GPUODESolver {
  private device: GPUDevice | null = null;
  private supported: boolean = false;

  async init(): Promise<boolean> {
    if (!navigator.gpu) {
      return false;
    }

    try {
      const adapter = await navigator.gpu.requestAdapter();
      if (!adapter) return false;

      this.device = await adapter.requestDevice();
      this.supported = true;
      return true;
    } catch (e) {
      console.warn('WebGPU initialization failed:', e);
      return false;
    }
  }

  isSupported(): boolean {
    return this.supported && this.device !== null;
  }

  async solvePK(config: GPUSimulationConfig): Promise<GPUSimulationResult> {
    if (!this.device) {
      return { Cmax: new Float32Array(0), Cmin: new Float32Array(0), success: false };
    }

    const shader = `
      @group(0) @binding(0) var<storage, read> params: array<f32>;
      @group(0) @binding(1) var<storage, read_write> results: array<f32>;

      @compute @workgroup_size(64)
      fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
        let idx = global_id.x;
        let numSims = u32(params[9]);

        if (idx >= numSims) { return; }

        let CL = params[0];
        let Vd = params[1];
        let ka = params[2];
        let F = params[3];
        let dose = params[4];
        let interval = params[5];
        let duration = params[6];
        let dt = params[7];
        let sigma = params[8];

        let ke = CL / Vd;
        let n = u32(duration / dt);
        let nDoses = u32(duration / interval);

        var A_depot = 0.0;
        var A_central = 0.0;
        var Cmax = 0.0;
        var Cmin = 1e10;

        var seed = f32(idx) * 12345.0;

        for (var i = 0u; i < n; i++) {
          let t = f32(i) * dt;

          for (var d = 0u; d < nDoses; d++) {
            if (abs(t - f32(d) * interval) < dt / 2.0) {
              A_depot += dose * 1000.0 * F;
            }
          }

          seed = fract(sin(seed) * 43758.5453);
          let dW = (seed - 0.5) * 2.0 * sqrt(dt);

          let dA_dep = ka * A_depot * dt;
          A_depot -= dA_dep;
          A_central += dA_dep;

          let dA_elim = ke * A_central * dt;
          A_central -= dA_elim;

          if (sigma > 0.0) {
            let ito = -0.5 * sigma * sigma * dt;
            A_central *= exp(sigma * dW + ito);
          }

          A_depot = max(0.0, A_depot);
          A_central = max(0.0, A_central);

          let C = A_central / Vd * 1000.0;

          if (i > n / 2u) {
            Cmax = max(Cmax, C);
            if (C > 0.0001) {
              Cmin = min(Cmin, C);
            }
          }
        }

        results[idx * 2u] = Cmax;
        results[idx * 2u + 1u] = Cmin;
      }
    `;

    const shaderModule = this.device.createShaderModule({ code: shader });

    const paramsData = new Float32Array([
      config.CL, config.Vd, config.ka, config.F,
      config.dose, config.interval, config.duration, config.dt,
      config.sigma, config.numSims
    ]);

    const paramsBuffer = this.device.createBuffer({
      size: paramsData.byteLength,
      usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST,
    });
    this.device.queue.writeBuffer(paramsBuffer, 0, paramsData);

    const resultsBuffer = this.device.createBuffer({
      size: config.numSims * 2 * 4,
      usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_SRC,
    });

    const readBuffer = this.device.createBuffer({
      size: config.numSims * 2 * 4,
      usage: GPUBufferUsage.MAP_READ | GPUBufferUsage.COPY_DST,
    });

    const bindGroupLayout = this.device.createBindGroupLayout({
      entries: [
        { binding: 0, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'read-only-storage' } },
        { binding: 1, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'storage' } },
      ],
    });

    const bindGroup = this.device.createBindGroup({
      layout: bindGroupLayout,
      entries: [
        { binding: 0, resource: { buffer: paramsBuffer } },
        { binding: 1, resource: { buffer: resultsBuffer } },
      ],
    });

    const pipelineLayout = this.device.createPipelineLayout({
      bindGroupLayouts: [bindGroupLayout],
    });

    const pipeline = this.device.createComputePipeline({
      layout: pipelineLayout,
      compute: { module: shaderModule, entryPoint: 'main' },
    });

    const commandEncoder = this.device.createCommandEncoder();
    const passEncoder = commandEncoder.beginComputePass();
    passEncoder.setPipeline(pipeline);
    passEncoder.setBindGroup(0, bindGroup);
    passEncoder.dispatchWorkgroups(Math.ceil(config.numSims / 64));
    passEncoder.end();

    commandEncoder.copyBufferToBuffer(resultsBuffer, 0, readBuffer, 0, config.numSims * 2 * 4);
    this.device.queue.submit([commandEncoder.finish()]);

    await readBuffer.mapAsync(GPUMapMode.READ);
    const resultData = new Float32Array(readBuffer.getMappedRange());
    const Cmax = new Float32Array(config.numSims);
    const Cmin = new Float32Array(config.numSims);

    for (let i = 0; i < config.numSims; i++) {
      Cmax[i] = resultData[i * 2];
      Cmin[i] = resultData[i * 2 + 1];
    }

    readBuffer.unmap();

    return { Cmax, Cmin, success: true };
  }
}
