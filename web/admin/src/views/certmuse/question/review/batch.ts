export interface BatchResult<T> { succeeded: T[]; failed: Array<{ item: T; reason: unknown }>; }

export async function runSequentialBatch<T>(items: T[], operation: (item: T) => Promise<unknown>): Promise<BatchResult<T>> {
  const result: BatchResult<T> = { succeeded: [], failed: [] };
  for (const item of items) {
    try {
      await operation(item);
      result.succeeded.push(item);
    } catch (reason) {
      result.failed.push({ item, reason });
    }
  }
  return result;
}
