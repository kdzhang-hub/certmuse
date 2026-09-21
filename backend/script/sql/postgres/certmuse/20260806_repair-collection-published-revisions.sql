-- 受控维护：每个题集只保留更新时间最新的发布修订；并重建当前发布指针。
-- 在维护窗口内执行，执行前应先备份 cm_collection_revision 与 cm_collection_current。
\set ON_ERROR_STOP on
begin;

with ranked as (
    select id, collection_id,
           row_number() over (
               partition by collection_id
               order by update_time desc, revision_no desc, id desc
           ) as published_rank
      from cm_collection_revision
     where status = 'published'
)
update cm_collection_revision revision
   set status = 'draft', row_version = row_version + 1, update_time = now()
  from ranked
 where revision.id = ranked.id and ranked.published_rank > 1;

delete from cm_collection_current current_row
 where not exists (
     select 1 from cm_collection_revision revision
      where revision.id = current_row.collection_revision_id
        and revision.collection_id = current_row.collection_id
        and revision.status = 'published'
 );

insert into cm_collection_current(collection_id, collection_revision_id, purpose_code, effective_time, update_time)
select revision.collection_id, revision.id, 'LEARNING', now(), now()
  from (
      select id, collection_id,
             row_number() over (
                 partition by collection_id
                 order by update_time desc, revision_no desc, id desc
             ) as published_rank
        from cm_collection_revision
       where status = 'published'
  ) revision
 where revision.published_rank = 1
on conflict (collection_id) do update
set collection_revision_id = excluded.collection_revision_id,
    effective_time = excluded.effective_time,
    update_time = now();

commit;
