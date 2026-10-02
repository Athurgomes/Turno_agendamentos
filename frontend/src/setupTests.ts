import "@testing-library/jest-dom/vitest";

// jsdom não implementa ResizeObserver; o `recharts` (F8-3) usa para medir o
// container responsivo. Stub mínimo, sem observar de verdade — suficiente
// para o gráfico renderizar em teste.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub;
