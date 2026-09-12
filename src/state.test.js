import { describe, it, expect, vi } from 'vitest';
import { createStore } from './state.js';

describe('createStore', () => {
  it('throws TypeError if initialState is missing or non-object', () => {
    expect(() => createStore(null)).toThrow(TypeError);
    expect(() => createStore(123)).toThrow(TypeError);
  });

  it('initializes state and returns frozen snapshot from getState()', () => {
    const store = createStore({ count: 0, user: { name: 'Alice' } });
    const state = store.getState();
    expect(state.count).toBe(0);
    expect(state.user.name).toBe('Alice');
    expect(Object.isFrozen(state)).toBe(true);
  });

  it('updates state with partial objects using setState()', () => {
    const store = createStore({ count: 0, name: 'Store' });
    store.setState({ count: 5 });
    expect(store.getState()).toEqual({ count: 5, name: 'Store' });
  });

  it('updates state with function updater using setState()', () => {
    const store = createStore({ count: 10 });
    store.setState((s) => ({ count: s.count + 1 }));
    expect(store.getState().count).toBe(11);
  });

  it('notifies subscribers on state change', () => {
    const store = createStore({ value: 'a' });
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    // Initial call on subscribe
    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenLastCalledWith({ value: 'a' });

    store.setState({ value: 'b' });
    expect(listener).toHaveBeenCalledTimes(2);
    expect(listener).toHaveBeenLastCalledWith({ value: 'b' });

    unsubscribe();
    store.setState({ value: 'c' });
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('selects derived state using select()', () => {
    const store = createStore({ items: [1, 2, 3], filter: 'even' });
    const evens = store.select((s) => s.items.filter((x) => x % 2 === 0));
    expect(evens).toEqual([2]);
  });
});
