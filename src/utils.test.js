import { describe, it, expect } from 'vitest';
import { mergeArrays, deepClone } from './utils.js';

describe('mergeArrays', () => {
  it('returns local array copy if server is invalid or empty', () => {
    const local = [{ id: '1', title: 'Job 1' }];
    expect(mergeArrays(local, null, 'id')).toEqual(local);
    expect(mergeArrays(local, [], 'id')).toEqual(local);
  });

  it('merges server items into local, replacing conflicts where server wins', () => {
    const local = [
      { id: '1', name: 'Alpha', status: 'pending' },
      { id: '2', name: 'Beta', status: 'pending' },
    ];
    const server = [
      { id: '2', name: 'Beta Updated', status: 'completed' },
      { id: '3', name: 'Gamma', status: 'pending' },
    ];

    const result = mergeArrays(local, server, 'id');

    expect(result).toHaveLength(3);
    expect(result[0]).toEqual({ id: '1', name: 'Alpha', status: 'pending' });
    expect(result[1]).toEqual({ id: '2', name: 'Beta Updated', status: 'completed' });
    expect(result[2]).toEqual({ id: '3', name: 'Gamma', status: 'pending' });
  });

  it('performs deep cloning so server objects are decoupled from output', () => {
    const local = [{ id: '1', data: { value: 10 } }];
    const serverItem = { id: '1', data: { value: 20 } };
    const result = mergeArrays(local, [serverItem], 'id');

    expect(result[0].data.value).toBe(20);
    serverItem.data.value = 99;
    expect(result[0].data.value).toBe(20);
  });

  it('handles elements without valid keys gracefully', () => {
    const local = [{ id: '1', name: 'A' }, { name: 'No ID Local' }];
    const server = [{ id: '1', name: 'A Updated' }, { name: 'No ID Server' }];

    const result = mergeArrays(local, server, 'id');

    expect(result).toHaveLength(3);
    expect(result[0]).toEqual({ id: '1', name: 'A Updated' });
    expect(result[1]).toEqual({ name: 'No ID Local' });
    expect(result[2]).toEqual({ name: 'No ID Server' });
  });

  it('efficiently handles large collections in O(N + M) time', () => {
    const N = 2000;
    const M = 2000;
    const local = Array.from({ length: N }, (_, i) => ({ id: `id-${i}`, val: i }));
    const server = Array.from({ length: M }, (_, i) => ({ id: `id-${i + N / 2}`, val: i + 10000 }));

    const start = performance.now();
    const result = mergeArrays(local, server, 'id');
    const elapsed = performance.now() - start;

    expect(result).toHaveLength(N + M / 2); // 2000 original + 1000 new
    // O(N + M) should execute well within 50ms (typically ~2-5ms)
    expect(elapsed).toBeLessThan(100);
  });
});
