export interface ImportProgressEvent {
  eventType?: string;
  batchId?: string | number;
  importType?: string;
  status?: string;
  currentStage?: string;
  progressPercent?: number;
  [key: string]: unknown;
}

interface PushWithImportData {
  type?: string;
  data?: ImportProgressEvent | null;
}

type Listener = (event: ImportProgressEvent) => void;
const listeners = new Set<{ batchId: string; listener: Listener }>();

export function emitImportProgress(event: ImportProgressEvent) {
  if (event.eventType !== 'certmuse.import.progress' || event.batchId === undefined) return;
  const batchId = String(event.batchId);
  listeners.forEach(entry => {
    if (entry.batchId === batchId) entry.listener(event);
  });
}

export function emitImportProgressFromPush(payload: PushWithImportData) {
  const event = payload.data;
  if (!event || event.eventType !== 'certmuse.import.progress') return false;
  emitImportProgress(event);
  return true;
}

export function onImportProgress(batchId: string | number, listener: Listener) {
  const entry = { batchId: String(batchId), listener };
  listeners.add(entry);
  return () => listeners.delete(entry);
}
