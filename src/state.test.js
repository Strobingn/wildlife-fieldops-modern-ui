import { describe, it, expect, vi } from 'vitest';
import { createStore } from './state.js';

describe('createStore', () => {
  it('initializes with state', () => {
    const store = createStore({ a: 1, b: [1, 2, 3] });
    expect(store.getState()).toEqual({ a: 1, b: [1, 2, 3] });
  });

  it('updates state with object partial', () => {
    const store = createStore({ a: 1, b: 2 });
    store.setState({ b: 3 });
    expect(store.getState()).toEqual({ a: 1, b: 3 });
  });

  it('updates state with function updater', () => {
    const store = createStore({ count: 0, title: 'test' });
    store.setState(s => ({ count: s.count + 1 }));
    expect(store.getState()).toEqual({ count: 1, title: 'test' });
  });

  it('subscribes to changes and unsubscribe works', () => {
    const store = createStore({ count: 0 });
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    expect(listener).toHaveBeenCalledWith({ count: 0 });
    listener.mockClear();

    store.setState({ count: 5 });
    expect(listener).toHaveBeenCalledWith({ count: 5 });

    unsubscribe();
    store.setState({ count: 10 });
    expect(listener).not.toHaveBeenCalledWith({ count: 10 });
  });

  it('selects state slice via pure selector', () => {
    const store = createStore({ user: { name: 'Alice' } });
    const name = store.select(s => s.user.name);
    expect(name).toBe('Alice');
  });

  it('freezes state to prevent direct top-level mutation', () => {
    const store = createStore({ count: 0 });
    const state = store.getState();
    expect(Object.isFrozen(state)).toBe(true);
  });

  it('throws TypeError if invalid initialState or selector or subscribe fn is provided', () => {
    // @ts-ignore
    expect(() => createStore(null)).toThrow(TypeError);
    const store = createStore({ a: 1 });
    // @ts-ignore
    expect(() => store.subscribe(null)).toThrow(TypeError);
    // @ts-ignore
    expect(() => store.select(null)).toThrow(TypeError);
  });
});
