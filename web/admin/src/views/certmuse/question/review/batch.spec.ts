import { describe, expect, it, vi } from 'vitest';
import { runSequentialBatch } from './batch';

describe('runSequentialBatch', () => {
  it('collects every item when all operations succeed', async () => {
    const operation = vi.fn().mockResolvedValue(undefined);
    const result = await runSequentialBatch([1, 2], operation);
    expect(result).toEqual({ succeeded: [1, 2], failed: [] });
  });

  it('collects successes and failures while preserving order', async () => {
    const operation = vi.fn(async (value: number) => { if (value === 2) throw new Error('failed'); });
    const result = await runSequentialBatch([1, 2, 3], operation);
    expect(result.succeeded).toEqual([1, 3]);
    expect(result.failed.map(entry => entry.item)).toEqual([2]);
    expect(operation.mock.calls.map(call => call[0])).toEqual([1, 2, 3]);
  });

  it('collects every failure without stopping later operations', async () => {
    const operation = vi.fn(async (value: number) => { throw new Error(`failed-${value}`); });
    const result = await runSequentialBatch([1, 2], operation);
    expect(result.succeeded).toEqual([]);
    expect(result.failed.map(entry => entry.item)).toEqual([1, 2]);
    expect(operation.mock.calls.map(call => call[0])).toEqual([1, 2]);
  });
});
