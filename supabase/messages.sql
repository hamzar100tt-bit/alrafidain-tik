-- Direct messages for Alrafidain Tik
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.profiles(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now(),
  check (sender_id <> receiver_id)
);
create index if not exists messages_sender_receiver_created_idx on public.messages(sender_id, receiver_id, created_at);
create index if not exists messages_receiver_sender_created_idx on public.messages(receiver_id, sender_id, created_at);
alter table public.messages enable row level security;
drop policy if exists "Participants can read messages" on public.messages;
create policy "Participants can read messages" on public.messages for select to authenticated
using (auth.uid() = sender_id or auth.uid() = receiver_id);
drop policy if exists "Users send messages as themselves" on public.messages;
create policy "Users send messages as themselves" on public.messages for insert to authenticated
with check (auth.uid() = sender_id);
