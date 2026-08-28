export type CollectionStatus = 'draft' | 'pending_review' | 'rejected' | 'published';
export type CollectionDisplayStatus = CollectionStatus;

export interface CollectionQuestionSelection {
  revisionId: string;
  reportScore: number;
}

export const collectionStatusMeta: Record<
  CollectionDisplayStatus,
  { label: string; tagType: 'info' | 'warning' | 'success' | 'danger' }
> = {
  rejected: { label: '被驳回', tagType: 'danger' },
  published: { label: '已发布', tagType: 'success' },
  pending_review: { label: '审核中', tagType: 'warning' },
  draft: { label: '草稿', tagType: 'info' },
};

export const collectionStatusOrder: CollectionDisplayStatus[] = ['rejected', 'published', 'pending_review', 'draft'];

export const revisionDisplayStatus = (revision: {
  status: CollectionStatus;
  currentPublished: boolean;
  hasReviewOpinion?: boolean;
}): CollectionDisplayStatus => revision.status;

export const compareRevisionStatus = (
  left: { status: CollectionStatus; currentPublished: boolean; hasReviewOpinion?: boolean; revisionNo: number },
  right: { status: CollectionStatus; currentPublished: boolean; hasReviewOpinion?: boolean; revisionNo: number }
) => {
  const statusDifference =
    collectionStatusOrder.indexOf(revisionDisplayStatus(left)) -
    collectionStatusOrder.indexOf(revisionDisplayStatus(right));
  return statusDifference || right.revisionNo - left.revisionNo;
};

export const hasInProgressRevision = (revisions: Array<{ status: CollectionStatus }>) =>
  revisions.some(revision => revision.status === 'pending_review');

export const nextRevisionNo = (revisions: Array<{ revisionNo: number }>) =>
  revisions.reduce((max, revision) => Math.max(max, revision.revisionNo), 0) + 1;

export const toCollectionItems = (items: CollectionQuestionSelection[]) =>
  items.map((item, index) => ({
    itemOrder: index + 1,
    questionRevisionId: item.revisionId,
    reportScore: item.reportScore
  }));
