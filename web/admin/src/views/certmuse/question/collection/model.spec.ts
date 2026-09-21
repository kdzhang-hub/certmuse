import { describe, expect, it } from 'vitest';
import {
  collectionStatusMeta,
  compareRevisionStatus,
  hasInProgressRevision,
  nextRevisionNo,
  revisionDisplayStatus,
  toCollectionItems
} from './model';

describe('collection model', () => {
  it('recognizes that only an in-review revision blocks another publish flow', () => {
    expect(hasInProgressRevision([{ status: 'published' }, { status: 'draft' }])).toBe(false);
    expect(hasInProgressRevision([{ status: 'published' }, { status: 'pending_review' }])).toBe(true);
    expect(hasInProgressRevision([{ status: 'published' }, { status: 'published' }])).toBe(false);
    expect(collectionStatusMeta.published.label).toBe('已发布');
  });

  it('always allocates max revisionNo plus one', () => {
    expect(nextRevisionNo([{ revisionNo: 1 }, { revisionNo: 3 }, { revisionNo: 2 }])).toBe(4);
    expect(nextRevisionNo([])).toBe(1);
  });

  it('keeps rejected as a real revision status and orders it first', () => {
    expect(revisionDisplayStatus({ status: 'published', currentPublished: true })).toBe('published');
    expect(revisionDisplayStatus({ status: 'published', currentPublished: false })).toBe('published');
    expect(revisionDisplayStatus({ status: 'rejected', currentPublished: false, hasReviewOpinion: true })).toBe('rejected');
    const revisions = [
      { revisionNo: 1, status: 'draft' as const, currentPublished: false },
      { revisionNo: 2, status: 'draft' as const, currentPublished: false },
      { revisionNo: 3, status: 'pending_review' as const, currentPublished: false },
      { revisionNo: 4, status: 'published' as const, currentPublished: true },
      { revisionNo: 5, status: 'rejected' as const, currentPublished: false, hasReviewOpinion: true }
    ];
    expect(revisions.toSorted(compareRevisionStatus).map(revision => revisionDisplayStatus(revision))).toEqual([
      'rejected',
      'published',
      'pending_review',
      'draft',
      'draft'
    ]);
  });

  it('maps revisionId to questionRevisionId', () => {
    expect(
      toCollectionItems([
        { revisionId: '90001', reportScore: 2 },
        { revisionId: '90002', reportScore: 10 }
      ])
    ).toEqual([
      { itemOrder: 1, questionRevisionId: '90001', reportScore: 2 },
      { itemOrder: 2, questionRevisionId: '90002', reportScore: 10 }
    ]);
  });
});
