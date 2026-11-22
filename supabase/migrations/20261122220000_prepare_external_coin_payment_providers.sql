-- External Coins payment provider preparation.
-- Live provider credentials are intentionally NOT stored in this repository.
alter table public.coin_topup_requests
  add column if not exists provider_transaction_id text,
  add column if not exists provider_status text,
  add column if not exists verified_at timestamptz;

create unique index if not exists coin_topup_provider_transaction_unique
  on public.coin_topup_requests(payment_method, provider_transaction_id)
  where provider_transaction_id is not null;

create or replace function public.create_coin_topup_request(
  p_payment_method text,
  p_coins bigint,
  p_amount_iqd bigint,
  p_receipt_url text default null,
  p_provider_reference text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $function$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'يجب تسجيل الدخول'; end if;
  if p_payment_method not in ('manual','online','fastpay','zaincash','google_play') then
    raise exception 'طريقة الدفع غير مدعومة';
  end if;
  if p_coins < 1 or p_coins > 10000000 or p_amount_iqd < 1 then
    raise exception 'بيانات الباقة غير صحيحة';
  end if;
  if p_payment_method = 'manual' and coalesce(trim(p_receipt_url),'') = '' then
    raise exception 'أرفق صورة وصل التحويل أولاً';
  end if;
  if p_payment_method in ('fastpay','zaincash','google_play','online')
     and coalesce(trim(p_provider_reference),'') = '' then
    raise exception 'مرجع عملية الدفع مطلوب';
  end if;
  insert into public.coin_topup_requests
    (user_id,payment_method,coins,amount_iqd,receipt_url,provider_reference,provider_status)
  values
    (auth.uid(),p_payment_method,p_coins,p_amount_iqd,
     nullif(trim(p_receipt_url),''),nullif(trim(p_provider_reference),''),'pending')
  returning id into v_id;
  return v_id;
end;
$function$;

revoke all on function public.create_coin_topup_request(text,bigint,bigint,text,text) from public;
grant execute on function public.create_coin_topup_request(text,bigint,bigint,text,text) to authenticated;
