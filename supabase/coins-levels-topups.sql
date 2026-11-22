-- Alrafidain Tik: coins, account levels, and secure top-up requests
-- Apply once in Supabase Dashboard > SQL Editor.
-- Payment provider confirmation must be performed server-side; never trust the Android client.

alter table public.profiles
  add column if not exists coin_balance bigint not null default 0 check (coin_balance >= 0),
  add column if not exists recharge_points bigint not null default 0 check (recharge_points >= 0),
  add column if not exists support_points bigint not null default 0 check (support_points >= 0),
  add column if not exists level_points bigint not null default 0 check (level_points >= 0),
  add column if not exists account_level integer not null default 1 check (account_level >= 1);

create table if not exists public.coin_topup_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  payment_method text not null check (payment_method in ('manual','online')),
  coins bigint not null check (coins > 0),
  amount_iqd bigint not null check (amount_iqd > 0),
  receipt_url text,
  provider_reference text,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id)
);
create index if not exists coin_topup_user_created_idx
  on public.coin_topup_requests(user_id, created_at desc);

create table if not exists public.coin_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  amount bigint not null,
  transaction_type text not null check (transaction_type in ('topup','gift_spend','gift_receive','admin_adjustment')),
  reference_id uuid,
  description text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists coin_ledger_user_created_idx
  on public.coin_ledger(user_id, created_at desc);

alter table public.coin_topup_requests enable row level security;
alter table public.coin_ledger enable row level security;

drop policy if exists "Users read own topup requests" on public.coin_topup_requests;
create policy "Users read own topup requests" on public.coin_topup_requests
  for select to authenticated using (auth.uid() = user_id);
drop policy if exists "Users create own topup requests" on public.coin_topup_requests;
create policy "Users create own topup requests" on public.coin_topup_requests
  for insert to authenticated with check (auth.uid() = user_id and status = 'pending');
drop policy if exists "Users read own coin ledger" on public.coin_ledger;
create policy "Users read own coin ledger" on public.coin_ledger
  for select to authenticated using (auth.uid() = user_id);

-- A fixed, transparent level curve: every 1,000 earned points advances one level.
create or replace function public.refresh_account_level(p_user_id uuid)
returns void language plpgsql security definer set search_path = public
as $$
begin
  update public.profiles
     set level_points = recharge_points + support_points,
         account_level = least(100, 1 + ((recharge_points + support_points) / 1000)::integer)
   where id = p_user_id;
end;
$$;

-- Users can create a pending request, but cannot approve it or credit coins themselves.
create or replace function public.create_coin_topup_request(
  p_payment_method text,
  p_coins bigint,
  p_amount_iqd bigint,
  p_receipt_url text default null,
  p_provider_reference text default null
) returns uuid
language plpgsql security definer set search_path = public
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'يجب تسجيل الدخول'; end if;
  if p_payment_method not in ('manual','online') then raise exception 'طريقة الدفع غير مدعومة'; end if;
  if p_coins < 1 or p_coins > 10000000 or p_amount_iqd < 1 then raise exception 'بيانات الباقة غير صحيحة'; end if;
  if p_payment_method = 'manual' and coalesce(trim(p_receipt_url),'') = '' then
    raise exception 'أرفق صورة وصل التحويل أولاً';
  end if;
  if p_payment_method = 'online' and coalesce(trim(p_provider_reference),'') = '' then
    raise exception 'مرجع الدفع الإلكتروني مطلوب';
  end if;
  insert into public.coin_topup_requests(user_id,payment_method,coins,amount_iqd,receipt_url,provider_reference)
  values(auth.uid(),p_payment_method,p_coins,p_amount_iqd,nullif(trim(p_receipt_url),''),nullif(trim(p_provider_reference),''))
  returning id into v_id;
  return v_id;
end;
$$;

-- Admin approval is restricted to trusted app_metadata role=admin.
create or replace function public.review_coin_topup(p_request_id uuid, p_approve boolean)
returns void
language plpgsql security definer set search_path = public
as $$
declare r public.coin_topup_requests%rowtype;
begin
  if coalesce(auth.jwt()->'app_metadata'->>'role','') <> 'admin' then
    raise exception 'غير مخول لمراجعة طلبات الشحن';
  end if;
  select * into r from public.coin_topup_requests where id = p_request_id for update;
  if not found or r.status <> 'pending' then raise exception 'طلب الشحن غير موجود أو تمت مراجعته'; end if;
  update public.coin_topup_requests
     set status = case when p_approve then 'approved' else 'rejected' end,
         reviewed_at = now(), reviewed_by = auth.uid()
   where id = p_request_id;
  if p_approve then
    update public.profiles
       set coin_balance = coin_balance + r.coins,
           recharge_points = recharge_points + r.coins
     where id = r.user_id;
    insert into public.coin_ledger(user_id,amount,transaction_type,reference_id,description)
    values(r.user_id,r.coins,'topup',r.id,'شحن عملات بعد تأكيد الدفع');
    perform public.refresh_account_level(r.user_id);
  end if;
end;
$$;

revoke all on function public.refresh_account_level(uuid) from public, anon, authenticated;
grant execute on function public.create_coin_topup_request(text,bigint,bigint,text,text) to authenticated;
grant execute on function public.review_coin_topup(uuid,boolean) to authenticated;

-- Do not expose direct table writes for balances or ledger entries.
revoke insert, update, delete on public.coin_ledger from anon, authenticated;
revoke update, delete on public.coin_topup_requests from anon, authenticated;
