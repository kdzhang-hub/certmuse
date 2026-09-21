import { describe, expect, it, vi } from 'vitest';
import { emitImportProgress, emitImportProgressFromPush, onImportProgress } from '@/utils/import-progress-events';

describe('import progress event channel', () => {
  it('delivers only CertMuse import progress payloads to matching subscribers', () => {
    const listener = vi.fn();
    const stop = onImportProgress('90001', listener);

    emitImportProgress({ eventType: 'other', batchId: '90001' });
    emitImportProgress({ eventType: 'certmuse.import.progress', batchId: '2' });
    emitImportProgress({ eventType: 'certmuse.import.progress', batchId: '90001', status: 'validating' });

    expect(listener).toHaveBeenCalledOnce();
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ batchId: '90001', status: 'validating' }));
    stop();
  });

  it('routes terminal message payloads to the matching import subscriber', () => {
    const listener = vi.fn();
    const stop = onImportProgress('90001', listener);

    const routed = emitImportProgressFromPush({
      type: 'message',
      data: {
        eventType: 'certmuse.import.progress',
        batchId: '90001',
        status: 'completed',
        progressPercent: 100
      }
    });

    expect(routed).toBe(true);
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ status: 'completed' }));
    stop();
  });

  it('ignores non-progress messages and stops delivery after unsubscribe', () => {
    const listener = vi.fn();
    const stop = onImportProgress(90001, listener);
    stop();

    expect(emitImportProgressFromPush({ type: 'message', data: null })).toBe(false);
    emitImportProgress({ eventType: 'certmuse.import.progress', batchId: 90001, status: 'completed' });

    expect(listener).not.toHaveBeenCalled();
  });
});
