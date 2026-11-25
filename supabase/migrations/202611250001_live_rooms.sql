-- Live rooms used by the LiveKit token edge function.
create extension if not exists pgcrypto;

create table if not exists public.live_rooms (
  id uuid primary key default gen_random_uuid(),
  room_key text not null unique check (char_length(room_key) between 3 and 60),
  owner_id uuid not null references auth.users(id) on delete cascade,
  is_live boolean not null default false,
  created_at timestamptz not null default now(),
  ended_at timestamptz
);

create table if not exists public.live_room_speakers (
  room_id uuid not null references public.live_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (room_id, user_id)
);

create index if not exists live_rooms_live_created_idx
  on public.live_rooms(is_live, created_at desc);

alter table public.live_rooms enable row level security;
alter table public.live_room_speakers enable row level security;

drop policy if exists "Live rooms are publicly readable" on public.live_rooms;
create policy "Live rooms are publicly readable"
  on public.live_rooms for select using (is_live = true or auth.uid() = owner_id);

drop policy if exists "Users create their own live rooms" on public.live_rooms;
create policy "Users create their own live rooms"
  on public.live_rooms for insert to authenticated
  with check (auth.uid() = owner_id);

drop policy if exists "Owners update their live rooms" on public.live_rooms;
create policy "Owners update their live rooms"
  on public.live_rooms for update to authenticated
  using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

drop policy if exists "Live speakers readable by room owner or self" on public.live_room_speakers;
create policy "Live speakers readable by room owner or self"
  on public.live_room_speakers for select to authenticated
  using (
    auth.uid() = user_id or exists (
      select 1 from public.live_rooms r
      where r.id = room_id and r.owner_id = auth.uid()
    )
  );

drop policy if exists "Room owners manage speakers" on public.live_room_speakers;
create policy "Room owners manage speakers"
  on public.live_room_speakers for all to authenticated
  using (exists (
    select 1 from public.live_rooms r
    where r.id = room_id and r.owner_id = auth.uid()
  ))
  with check (exists (
    select 1 from public.live_rooms r
    where r.id = room_id and r.owner_id = auth.uid()
  ));
