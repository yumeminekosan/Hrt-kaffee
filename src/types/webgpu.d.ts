// WebGPU 类型声明
declare global {
  interface Navigator {
    gpu?: GPU;
  }

  interface GPU {
    requestAdapter(): Promise<GPUAdapter | null>;
  }

  interface GPUAdapter {
    requestDevice(): Promise<GPUDevice>;
  }
}

export {};
