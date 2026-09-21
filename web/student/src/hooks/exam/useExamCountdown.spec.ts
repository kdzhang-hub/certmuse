import { effectScope, nextTick, ref } from 'vue';
import { describe, expect, it } from 'vitest';
import { useExamCountdown } from './useExamCountdown';

describe('useExamCountdown', () => {
  it('uses server-confirmed elapsed time and remains frozen without an active lease', async () => {
    const active = ref(true);
    const timing = ref({ estimatedDurationSeconds: 60, effectiveElapsedSeconds: 58, serverTime: '2026-08-13T00:00:00Z' });
    const scope = effectScope();
    const countdown = scope.run(() => useExamCountdown(timing, active))!;

    countdown.start();
    await nextTick();
    expect(countdown.formatted.value).toBe('00:00:02');

    active.value = false;
    await nextTick();
    timing.value = { ...timing.value, effectiveElapsedSeconds: 59 };
    await nextTick();
    expect(countdown.formatted.value).toBe('00:00:01');
    expect(countdown.elapsedSeconds.value).toBe(59);
    scope.stop();
  });

  it('switches to a non-negative overtime display after the duration is exhausted', async () => {
    const active = ref(false);
    const timing = ref({ estimatedDurationSeconds: 60, effectiveElapsedSeconds: 60, serverTime: '2026-08-13T00:00:00Z' });
    const scope = effectScope();
    const countdown = scope.run(() => useExamCountdown(timing, active))!;

    expect(countdown.isOvertime.value).toBe(false);
    expect(countdown.label.value).toBe('剩余');
    expect(countdown.formatted.value).toBe('00:00:00');

    timing.value = { ...timing.value, effectiveElapsedSeconds: 61 };
    await nextTick();
    expect(countdown.isOvertime.value).toBe(true);
    expect(countdown.label.value).toBe('超时');
    expect(countdown.formatted.value).toBe('00:00:01');
    scope.stop();
  });
});
