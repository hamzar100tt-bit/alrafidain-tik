-- الرافدين تيك: Supabase schema
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null check (char_length(username) between 3 and 24),
  display_name text not null default '',
  avatar_url text,
  bio text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  caption text not null default '' check (char_length(caption) <= 2200),
  storage_path text not null,
  video_url text,
  created_at timestamptz not null default now()
);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  created_at timestamptz not null default now()
);

create table if not exists public.likes (
  user_id uuid not null references public.profiles(id) on delete cascade,
  video_id uuid not null references public.videos(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, video_id)
);

create table if not exists public.follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  followed_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, followed_id),
  check (follower_id <> followed_id)
);

alter table public.profiles enable row level security;
alter table public.videos enable row level security;
alter table public.comments enable row level security;
alter table public.likes enable row level security;
alter table public.follows enable row level security;

create policy "Profiles are publicly readable" on public.profiles for select using (true);
create policy "Users create their own profile" on public.profiles for insert with check (auth.uid() = id);
create policy "Users update their own profile" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

create policy "Videos are publicly readable" on public.videos for select using (true);
create policy "Authenticated users publish videos" on public.videos for insert with check (auth.uid() = user_id);
create policy "Owners update videos" on public.videos for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "Owners delete videos" on public.videos for delete using (auth.uid() = user_id);

create policy "Comments are publicly readable" on public.comments for select using (true);
create policy "Authenticated users comment as themselves" on public.comments for insert with check (auth.uid() = user_id);
create policy "Comment owners delete" on public.comments for delete using (auth.uid() = user_id);

create policy "Likes are publicly readable" on public.likes for select using (true);
create policy "Users manage own likes" on public.likes for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Follows are publicly readable" on public.follows for select using (true);
create policy "Users manage own follows" on public.follows for all using (auth.uid() = follower_id) with check (auth.uid() = follower_id);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('videos', 'videos', true, 104857600, array['video/mp4','video/webm','video/quicktime'])
on conflict (id) do nothing;

create policy "Public can view video files" on storage.objects for select using (bucket_id = 'videos');
create policy "Users upload into own folder" on storage.objects for insert to authenticated
with check (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "Users delete own video files" on storage.objects for delete to authenticated
using (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);
