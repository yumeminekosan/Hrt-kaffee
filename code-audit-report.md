# FJÖRÐUR 代码冗余项检查报告

## 检查范围
- 文件: `index.html` (主模拟器)
- 新增文件: `advanced-sde-methods.js` (Master + Fokker-Planck + Langevin + Path Integral)
- 新增文件: `integration-patch.js` (整合补丁)

---

## 🔴 发现的问题

### 1. **drawChart 函数严重冗余** (第661-775行)

**问题**: `drawChart` 函数内部定义了一个嵌套的 `draw()` 函数,而 `draw()` 又重复实现了 `drawChart` 的大部分逻辑.

**具体表现**:
- 第661-710行: 第一次绘制(初始化canvas、计算范围、画网格)
- 第713-740行: `drawSpline` 函数定义
- 第742-775行: `draw()` 函数又重复了所有绘制逻辑

**影响**: 代码量增加约60%,维护困难,任何图表修改需要在两处同步.

**修复建议**:
```javascript
// 将 drawChart 简化为只设置数据和调用一次绘制
function drawChart(datasets, xLabel, yLabel) {
  window._chartData = { datasets, xLabel, yLabel };
  redrawChart();
}

function redrawChart() {
  const { datasets, xLabel, yLabel } = window._chartData;
  // 单一的绘制逻辑...
}
```

---

### 2. **simOral 中的重复代码** (第553-580行)

**问题**: `simOral`、`simEV`、`simDDI` 三个函数有共同的模式:
- 初始化数组
- 时间步进循环
- Euler积分

**冗余度**: ~30%的代码可以提取为通用函数.

**修复建议**:
```javascript
function eulerStep(state, derivatives, dt) {
  const newState = {};
  for (const key in state) {
    newState[key] = state[key] + (derivatives[key] || 0) * dt;
  }
  return newState;
}
```

---

### 3. **CSS 变量重复定义** (第28-30行, 第1312-1338行)

**问题**: 校准报告HTML字符串中内联了完整的CSS,与主文件头部CSS重复.

**影响**: 增加文件大小 ~15KB.

**修复建议**: 将报告CSS提取到外部样式表或使用 `<link>` 引入.

---

### 4. **i18n 重复代码** (第424-467行)

**问题**: `setLang` 函数中对每个元素类型单独处理,可以统一.

---

### 5. **数值积分方法单一**

**问题**: 所有模拟都用一阶Euler方法,没有更高阶的方法(如RK4).

**影响**: 精度受限,需要很小的时间步长.

---

### 6. **advanced-sde-methods.js 中的问题**

#### 6a. **Fokker-Planck 求解器精度问题**
- 使用中心差分计算导数,在边界处精度下降
- 建议: 使用谱方法(Chebyshev微分矩阵)替代有限差分

#### 6b. **Langevin 求解器时间步长限制**
- `dt=1e-7s` 对于长时间模拟不现实
- 建议: 使用过阻尼近似(忽略惯性项)或隐式方法

#### 6c. **Path Integral 的 instantonPath 简化过度**
- 当前实现使用梯度下降,不是真正的瞬子解
- 建议: 使用射击法(Shooting Method)求解两点边值问题

---

## 🟡 建议的优化

### 1. **提取通用数值积分框架**

```javascript
class ODESolver {
  constructor(derivatives, method = 'euler') {
    this.derivatives = derivatives;
    this.method = method;
  }
  
  step(state, t, dt) {
    switch(this.method) {
      case 'euler': return this.eulerStep(state, t, dt);
      case 'rk4': return this.rk4Step(state, t, dt);
      case 'rkf45': return this.rkf45Step(state, t, dt);
    }
  }
  
  eulerStep(state, t, dt) {
    const k1 = this.derivatives(state, t);
    const newState = {};
    for (const key in state) {
      newState[key] = state[key] + k1[key] * dt;
    }
    return newState;
  }
  
  rk4Step(state, t, dt) {
    const k1 = this.derivatives(state, t);
    const s2 = {}; for (const k in state) s2[k] = state[k] + k1[k] * dt/2;
    const k2 = this.derivatives(s2, t + dt/2);
    const s3 = {}; for (const k in state) s3[k] = state[k] + k2[k] * dt/2;
    const k3 = this.derivatives(s3, t + dt/2);
    const s4 = {}; for (const k in state) s4[k] = state[k] + k3[k] * dt;
    const k4 = this.derivatives(s4, t + dt);
    
    const newState = {};
    for (const key in state) {
      newState[key] = state[key] + (k1[key] + 2*k2[key] + 2*k3[key] + k4[key]) * dt / 6;
    }
    return newState;
  }
}
```

### 2. **统一图表绘制**

```javascript
const ChartEngine = {
  data: null,
  
  setData(datasets, xLabel, yLabel) {
    this.data = { datasets, xLabel, yLabel };
    this.draw();
  },
  
  draw() {
    if (!this.data) return;
    // 单一绘制逻辑...
  },
  
  toggleSmooth() {
    window._smooth = !window._smooth;
    this.draw();
  }
};
```

### 3. **使用 Web Worker 进行 heavy computation**

Master方程和Path Integral的模拟可以移到Web Worker,避免阻塞UI.

---

## 📊 代码统计

| 组件 | 行数 | 冗余度估计 |
|------|------|-----------|
| index.html (主文件) | ~1470 | ~25% |
| advanced-sde-methods.js | ~450 | ~15% |
| integration-patch.js | ~320 | ~10% |

**总冗余代码**: 约 400-500 行可以简化或提取.

---

## 🎯 优先级建议

1. **高**: 修复 `drawChart` 重复绘制问题
2. **高**: 提取通用ODE求解器框架
3. **中**: 优化Fokker-Planck求解器精度
4. **中**: 将报告CSS提取到外部文件
5. **低**: 添加Web Worker支持

---

*检查完成. Mon3.*
