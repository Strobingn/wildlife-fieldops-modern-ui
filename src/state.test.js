import { describe, it, expect, vi } from 'vitest';
import { createStore } from './state.js';

describe('createStore', () => {
  it('initializes with initial state', () => {
    const store = createStore({ count: 0, items: ['a', 'b'] });
    expect(store.getState()).toEqual({ count: 0, items: ['a', 'b'] });
  });

  it('updates state with object partial', () => {
    const store = createStore({ count: 0, title: 'hello' });
    store.setState({ count: 1 });
    expect(store.getState()).toEqual({ count: 1, title: 'hello' });
  });

  it('updates state with reducer function', () => {
    const store = createStore({ count: 10 });
    store.setState(prev => ({ count: prev.count + 5 }));
    expect(store.getState()).toEqual({ count: 15 });
  });

  it('notifies subscribers on state change', () => {
    const store = createStore({ count: 0 });
    const listener = vi.fn();
    store.subscribe(listener);

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenLastCalledWith({ count: 0 });

    store.setState({ count: 1 });
    expect(listener).toHaveBeenCalledTimes(2);
    expect(listener).toHaveBeenLastCalledWith({ count: 1 });
  });

  it('unsubscribes listener correctly', () => {
    const store = createStore({ count: 0 });
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    expect(listener).toHaveBeenCalledTimes(1);
    store.setState({ count: 1 });
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
    store.setState({ count: 2 });
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('reads derived values via select', () => {
    const store = createStore({ user: { name: 'Alice', age: 30 } });
    const name = store.select(s => s.user.name);
    expect(name).toBe('Alice');
  });

  it('throws on non-object initial state', () => {
    expect(() => createStore(null)).toThrow(TypeError);
    expect(() => createStore('string')).toThrow(TypeError);
  });
});
