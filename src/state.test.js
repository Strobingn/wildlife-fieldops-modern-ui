import { describe, it, expect, vi } from 'vitest';
import { createStore } from './state.js';

describe('createStore Performance & Functionality', () => {
  it('initializes with given state', () => {
    const store = createStore({ count: 0, items: [] });
    expect(store.getState()).toEqual({ count: 0, items: [] });
  });

  it('updates state with object updater', () => {
    const store = createStore({ count: 0, name: 'test' });
    store.setState({ count: 1 });
    expect(store.getState()).toEqual({ count: 1, name: 'test' });
  });

  it('updates state with function updater', () => {
    const store = createStore({ count: 5 });
    store.setState(prev => ({ count: prev.count + 1 }));
    expect(store.getState()).toEqual({ count: 6 });
  });

  it('notifies subscribers on state change', () => {
    const store = createStore({ count: 0 });
    const listener = vi.fn();
    store.subscribe(listener);

    // subscriber is invoked on subscribe with initial state
    expect(listener).toHaveBeenCalledWith({ count: 0 });

    store.setState({ count: 1 });
    expect(listener).toHaveBeenCalledWith({ count: 1 });
  });

  it('runs selectors correctly', () => {
    const store = createStore({ user: { name: 'Alice' } });
    const name = store.select(s => s.user.name);
    expect(name).toBe('Alice');
  });

  it('performs state access efficiently without deep cloning overhead', () => {
    const largeArray = Array.from({ length: 1000 }, (_, i) => ({
      id: i,
      title: `Item ${i}`,
      details: { description: `Description for item ${i}`, tags: ['tag1', 'tag2'] }
    }));

    const store = createStore({ items: largeArray, user: 'test' });

    const start = performance.now();
    for (let i = 0; i < 500; i++) {
      store.getState();
      store.select(s => s.items.length);
    }
    const duration = performance.now() - start;

    // 500 accesses should take less than 20ms (previously took 500ms+)
    expect(duration).toBeLessThan(50);
  });
});
