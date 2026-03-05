import { SimulatorContainer } from '@/components/simulator';

export default function Home() {
  return (
    <main className="min-h-screen bg-background">
      <div className="container mx-auto py-8">
        <div className="mb-8 text-center">
          <h1 className="text-4xl font-bold mb-2">HRT PK/PD 模拟器</h1>
          <p className="text-muted-foreground">跨性别女性激素替代疗法药代动力学模拟工具</p>
        </div>
        <SimulatorContainer />
      </div>
    </main>
  );
}
