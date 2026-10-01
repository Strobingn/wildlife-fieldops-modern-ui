import { describe, it, expect, vi } from 'vitest';
import { createStore } from './state.js';
import { buildJobIndexes, jobScore } from './utils.js';

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

describe('buildJobIndexes and jobScore performance helpers', () => {
  it('buildJobIndexes aggregates visits, repairs, photos, and signatures into Map/Set indexes', () => {
    const mockState = {
      visits: [{ jobId: 'j1' }, { job_id: 'j1' }, { jobId: 'j2' }],
      repairs: [{ jobId: 'j1' }],
      photos: [{ job_id: 'j1' }, { job_id: 'j1' }],
      signatures: [{ jobId: 'j1' }],
    };

    const indexes = buildJobIndexes(mockState);

    expect(indexes.visitCounts.get('j1')).toBe(2);
    expect(indexes.visitCounts.get('j2')).toBe(1);
    expect(indexes.repairCounts.get('j1')).toBe(1);
    expect(indexes.photoCounts.get('j1')).toBe(2);
    expect(indexes.signatureSet.has('j1')).toBe(true);
    expect(indexes.signatureSet.has('j2')).toBe(false);
  });

  it('jobScore computes score accurately with indexes', () => {
    const mockState = {
      visits: [{ jobId: 'j1' }],
      repairs: [{ jobId: 'j1' }],
      photos: [{ jobId: 'j1' }],
      signatures: [{ jobId: 'j1' }],
    };

    const indexes = buildJobIndexes(mockState);
    expect(jobScore('j1', mockState, indexes)).toBe(100);
    expect(jobScore('j2', mockState, indexes)).toBe(0);
  });

  it('jobScore computes score accurately without indexes (fallback)', () => {
    const mockState = {
      visits: [{ jobId: 'j1' }],
      repairs: [{ jobId: 'j1' }],
      photos: [],
      signatures: [],
    };

    expect(jobScore('j1', mockState)).toBe(50);
  });
});
