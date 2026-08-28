import { computed, onScopeDispose, ref, watch, type MaybeRefOrGetter, toValue } from 'vue';

export interface ExamTimingSnapshot {
  estimatedDurationSeconds: number;
  effectiveElapsedSeconds: number;
  serverTime: string;
}

/**
 * A display-only clock anchored to the last server acknowledgement. The client
 * never sends this derived value back to the API.
 */
export function useExamCountdown(
  timing: MaybeRefOrGetter<ExamTimingSnapshot | undefined>,
  shouldTick: MaybeRefOrGetter<boolean>
) {
  const confirmedElapsedSeconds = ref(0);
  const displayElapsedSeconds = ref(0);
  const durationSeconds = ref(0);
  let syncedAt = 0;
  let timerId: ReturnType<typeof setInterval> | undefined;

  const remainingSeconds = computed(() => Math.max(0, durationSeconds.value - displayElapsedSeconds.value));
  const overtimeSeconds = computed(() => Math.max(0, displayElapsedSeconds.value - durationSeconds.value));
  const isOvertime = computed(() => overtimeSeconds.value > 0);
  const label = computed(() => (isOvertime.value ? '超时' : '剩余'));
  const formatted = computed(() => formatSeconds(isOvertime.value ? overtimeSeconds.value : remainingSeconds.value));

  function sync(next = toValue(timing)) {
    if (!next) return;
    durationSeconds.value = Math.max(0, next.estimatedDurationSeconds);
    confirmedElapsedSeconds.value = Math.max(0, next.effectiveElapsedSeconds);
    displayElapsedSeconds.value = confirmedElapsedSeconds.value;
    syncedAt = performance.now();
  }

  function tick() {
    if (!toValue(shouldTick)) return;
    displayElapsedSeconds.value = confirmedElapsedSeconds.value + Math.max(0, Math.floor((performance.now() - syncedAt) / 1000));
  }

  function start() {
    if (timerId) return;
    sync();
    timerId = setInterval(tick, 250);
  }

  function stop() {
    if (timerId) clearInterval(timerId);
    timerId = undefined;
  }

  watch(() => toValue(timing), sync, { immediate: true, deep: false });
  watch(() => toValue(shouldTick), active => {
    if (active) {
      syncedAt = performance.now();
      start();
    }
  });
  onScopeDispose(stop);

  return {
    start,
    stop,
    sync,
    elapsedSeconds: displayElapsedSeconds,
    remainingSeconds,
    overtimeSeconds,
    isOvertime,
    label,
    formatted
  };
}

function formatSeconds(total: number) {
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
}
