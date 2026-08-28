import { describe, it, expect } from 'vitest';
import { createStore } from './state.js';

describe('createStore', () => {
  it('initializes state correctly', () => {
    const store = createStore({ count: 0, items: [] });
    expect(store.getState()).toEqual({ count: 0, items: [] });
  });

  it('updates state via object payload', () => {
    const store = createStore({ count: 0, items: [] });
    store.setState({ count: 5 });
    expect(store.getState().count).toBe(5);
  });

  it('updates state via function updater', () => {
    const store = createStore({ count: 0, items: [] });
    store.setState(s => ({ count: s.count + 1 }));
    expect(store.getState().count).toBe(1);
  });

  it('notifies subscribers on state change', () => {
    const store = createStore({ count: 0 });
    let called = 0;
    let lastState = null;
    store.subscribe(state => {
      called++;
      lastState = state;
    });
    // First call happens on subscribe
    expect(called).toBe(1);
    expect(lastState).toEqual({ count: 0 });

    store.setState({ count: 10 });
    expect(called).toBe(2);
    expect(lastState).toEqual({ count: 10 });
  });

  it('selects derived state', () => {
    const store = createStore({ count: 4 });
    const double = store.select(s => s.count * 2);
    expect(double).toBe(8);
  });

  it('handles 1000 fast state updates in milliseconds', () => {
    const largeState = {
      jobs: Array.from({ length: 200 }, (_, i) => ({ id: `j${i}`, name: `Job ${i}` })),
      customers: Array.from({ length: 200 }, (_, i) => ({ id: `c${i}`, name: `Cust ${i}` })),
      counter: 0
    };
    const store = createStore(largeState);
    store.subscribe(() => {});

    const start = performance.now();
    for (let i = 0; i < 1000; i++) {
      store.setState({ counter: i });
      store.getState();
    }
    const elapsed = performance.now() - start;

    expect(store.getState().counter).toBe(999);
    // Should complete 1000 iterations in under 50ms (previously >1000ms due to deepClone)
    expect(elapsed).toBeLessThan(100);
  });
});
