import { describe, expect, it } from 'vitest';
import { listStudentResources } from '@/api/student';

describe('student resources API adapter', () => {
  it('keeps the existing local learning-material list available outside mock mode', async () => {
    const response = await listStudentResources({ pageNum: 1, pageSize: 10 });

    expect(response.code).toBe(200);
    expect(response.data?.rows).toHaveLength(4);
  });
});
