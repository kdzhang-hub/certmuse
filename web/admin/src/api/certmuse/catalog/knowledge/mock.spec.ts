import { describe, expect, it, vi } from 'vitest';
import { createMockKnowledgeSyllabuses } from './mock';

describe('knowledge syllabus mock factory', () => {
  it('keeps each syllabus bound to its own lazy tree factory', () => {
    const architectTree = vi.fn(() => ({ id: 'architect' }));
    const engineerTree = vi.fn(() => ({ id: 'engineer' }));
    const syllabuses = createMockKnowledgeSyllabuses(architectTree, engineerTree);

    expect(syllabuses).toHaveLength(2);
    expect(syllabuses.map(item => item.certification)).toEqual(['SYSTEM_ARCHITECT', 'SOFTWARE_ENGINEER']);
    expect(syllabuses[0].createTree()).toEqual({ id: 'architect' });
    expect(syllabuses[1].createTree()).toEqual({ id: 'engineer' });
    expect(architectTree).toHaveBeenCalledOnce();
    expect(engineerTree).toHaveBeenCalledOnce();
  });
});
