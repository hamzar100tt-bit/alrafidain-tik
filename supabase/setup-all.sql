-- الرافدين تيك: إعداد قاعدة البيانات والتخزين دفعة واحدة
-- شغّل هذا الملف مرة واحدة من Supabase Dashboard > SQL Editor > New query.
-- الملف قابل لإعادة التشغيل بأمان قدر الإمكان (IF NOT EXISTS / DROP POLICY IF EXISTS).

-- ===== المخطط الأساسي: الحسابات والفيديوهات والتعليقات والإعجابات والمتابعة =====
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null check (char_length(username) between 3 and 24),
  display_name text not null default '',
  avatar_url text,
  bio text not null default '',
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare chosen_username text;
begin
  chosen_username := coalesce(
    nullif(new.raw_user_meta_data ->> 'username', ''),
    'user_' || replace(substr(new.id::text, 1, 8), '-', '')
  );
  insert into public.profiles (id, username, display_name)
  values (new.id, chosen_username, chosen_username)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute procedure public.handle_new_user();

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

drop policy if exists "Profiles are publicly readable" on public.profiles;
create policy "Profiles are publicly readable" on public.profiles for select using (true);
drop policy if exists "Users update their own profile" on public.profiles;
create policy "Users update their own profile" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "Videos are publicly readable" on public.videos;
create policy "Videos are publicly readable" on public.videos for select using (true);
drop policy if exists "Authenticated users publish videos" on public.videos;
create policy "Authenticated users publish videos" on public.videos for insert with check (auth.uid() = user_id);
drop policy if exists "Owners update videos" on public.videos;
create policy "Owners update videos" on public.videos for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "Owners delete videos" on public.videos;
create policy "Owners delete videos" on public.videos for delete using (auth.uid() = user_id);

drop policy if exists "Comments are publicly readable" on public.comments;
create policy "Comments are publicly readable" on public.comments for select using (true);
drop policy if exists "Authenticated users comment as themselves" on public.comments;
create policy "Authenticated users comment as themselves" on public.comments for insert with check (auth.uid() = user_id);
drop policy if exists "Comment owners delete" on public.comments;
create policy "Comment owners delete" on public.comments for delete using (auth.uid() = user_id);

drop policy if exists "Likes are publicly readable" on public.likes;
create policy "Likes are publicly readable" on public.likes for select using (true);
drop policy if exists "Users manage own likes" on public.likes;
create policy "Users manage own likes" on public.likes for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Follows are publicly readable" on public.follows;
create policy "Follows are publicly readable" on public.follows for select using (true);
drop policy if exists "Users manage own follows" on public.follows;
create policy "Users manage own follows" on public.follows for all using (auth.uid() = follower_id) with check (auth.uid() = follower_id);

-- ===== تخزين الفيديوهات: 100 MB =====
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('videos', 'videos', true, 104857600, array['video/mp4','video/webm','video/quicktime'])
on conflict (id) do update set public = true, file_size_limit = 104857600,
  allowed_mime_types = array['video/mp4','video/webm','video/quicktime'];

drop policy if exists "Public can view video files" on storage.objects;
create policy "Public can view video files" on storage.objects for select using (bucket_id = 'videos');
drop policy if exists "Users upload into own folder" on storage.objects;
create policy "Users upload into own folder" on storage.objects for insert to authenticated
with check (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "Users delete own video files" on storage.objects;
create policy "Users delete own video files" on storage.objects for delete to authenticated
using (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);

-- ===== صور الحسابات: 5 MB =====
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 5242880, array['image/jpeg','image/png','image/webp','image/gif'])
on conflict (id) do update set public = true, file_size_limit = 5242880,
  allowed_mime_types = array['image/jpeg','image/png','image/webp','image/gif'];

drop policy if exists "Public can view profile avatars" on storage.objects;
create policy "Public can view profile avatars" on storage.objects for select using (bucket_id = 'avatars');
drop policy if exists "Users upload their own avatar" on storage.objects;
create policy "Users upload their own avatar" on storage.objects for insert to authenticated
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "Users delete their own avatar" on storage.objects;
create policy "Users delete their own avatar" on storage.objects for delete to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ===== الرسائل الخاصة =====
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


-- ===== الفيديوهات المحفوظة =====
create table if not exists public.saved_videos (
  user_id uuid not null references public.profiles(id) on delete cascade,
  video_id uuid not null references public.videos(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, video_id)
);
alter table public.saved_videos enable row level security;
drop policy if exists "Users manage own saved videos" on public.saved_videos;
create policy "Users manage own saved videos" on public.saved_videos
for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
