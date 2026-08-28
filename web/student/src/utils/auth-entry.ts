import type { EntryType } from '@/api/types';

export const isEntryType = (value: unknown): value is EntryType => value === 'ADMIN' || value === 'LEARNING';

export const isLearningEntry = (value: unknown): value is 'LEARNING' => value === 'LEARNING';
