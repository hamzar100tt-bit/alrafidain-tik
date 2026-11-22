create or replace function public.admin_search_users(p_query text)
returns table (
  id uuid,
  username text,
  display_name text,
  account_status text,
  moderation_reason text,
  vip_until timestamptz,
  coin_balance bigint,
  account_level integer,
  admin_role text
)
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_super_admin() then
    raise exception 'غير مخول';
  end if;
  if length(trim(coalesce(p_query, ''))) < 2 then
    return;
  end if;
  return query
  select p.id, p.username, p.display_name,
         coalesce(p.account_status, 'active'),
         p.moderation_reason, p.vip_until,
         coalesce(p.coin_balance, 0),
         coalesce(p.account_level, 1),
         ar.role
  from public.profiles p
  left join public.admin_roles ar on ar.user_id = p.id
  where p.username ilike '%' || trim(p_query) || '%'
     or p.display_name ilike '%' || trim(p_query) || '%'
  order by p.created_at desc
  limit 30;
end;
$$;
revoke all on function public.admin_search_users(text) from public;
grant execute on function public.admin_search_users(text) to authenticated;
