import { supabase, isSupabaseConfigured } from "../lib/supabase.js";

const MAX_VIDEO_SIZE = 100 * 1024 * 1024;
const ALLOWED_TYPES = ["video/mp4", "video/webm", "video/quicktime"];

function requireSupabase() {
  if (!isSupabaseConfigured || !supabase) throw new Error("أضف مفاتيح Supabase أولاً.");
}

export async function signUp({ email, password, username }) {
  requireSupabase();
  const { data, error } = await supabase.auth.signUp({
    email, password, options: { data: { username } }
  });
  if (error) throw error;
  return data;
}

export async function signIn({ email, password }) {
  requireSupabase();
  const { data, error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) throw error;
  return data;
}

export async function signOut() {
  requireSupabase();
  const { error } = await supabase.auth.signOut();
  if (error) throw error;
}

export async function publishVideo({ file, caption, userId }) {
  requireSupabase();
  if (!file || !ALLOWED_TYPES.includes(file.type)) throw new Error("اختر فيديو MP4 أو WebM أو MOV.");
  if (file.size > MAX_VIDEO_SIZE) throw new Error("حجم الفيديو الأقصى 100 ميغابايت حالياً.");
  if (!userId) throw new Error("سجّل الدخول قبل نشر الفيديو.");
  const extension = file.name.split(".").pop()?.toLowerCase() || "mp4";
  const path = `${userId}/${crypto.randomUUID()}.${extension}`;
  const { error: uploadError } = await supabase.storage.from("videos").upload(path, file, {
    contentType: file.type, upsert: false
  });
  if (uploadError) throw uploadError;
  const { data: urlData } = supabase.storage.from("videos").getPublicUrl(path);
  const { data, error } = await supabase.from("videos").insert({
    user_id: userId, caption, storage_path: path, video_url: urlData.publicUrl
  }).select().single();
  if (error) {
    await supabase.storage.from("videos").remove([path]);
    throw error;
  }
  return data;
}

export async function getFeed() {
  requireSupabase();
  const { data, error } = await supabase.from("videos")
    .select("id, caption, video_url, created_at, profiles(username, display_name, avatar_url)")
    .order("created_at", { ascending: false }).limit(30);
  if (error) throw error;
  return data ?? [];
}

export async function addComment({ videoId, userId, body }) {
  requireSupabase();
  const { data, error } = await supabase.from("comments")
    .insert({ video_id: videoId, user_id: userId, body }).select().single();
  if (error) throw error;
  return data;
}

export async function getComments(videoId) {
  requireSupabase();
  const { data, error } = await supabase.from("comments")
    .select("id, body, created_at, profiles(username, display_name)")
    .eq("video_id", videoId).order("created_at", { ascending: true }).limit(100);
  if (error) throw error;
  return data ?? [];
}

export async function toggleLike({ videoId, userId, liked }) {
  requireSupabase();
  const request = liked
    ? supabase.from("likes").delete().match({ video_id: videoId, user_id: userId })
    : supabase.from("likes").insert({ video_id: videoId, user_id: userId });
  const { error } = await request;
  if (error) throw error;
}


export async function toggleFollow({ followerId, followedId, following }) {
  requireSupabase();
  if (followerId === followedId) throw new Error("ما تكدر تتابع حسابك.");
  const request = following
    ? supabase.from("follows").delete().match({ follower_id: followerId, followed_id: followedId })
    : supabase.from("follows").insert({ follower_id: followerId, followed_id: followedId });
  const { error } = await request;
  if (error) throw error;
}

export async function getFollowingFeed(userId) {
  requireSupabase();
  const { data: relations, error: relationError } = await supabase
    .from("follows").select("followed_id").eq("follower_id", userId);
  if (relationError) throw relationError;
  const ids = (relations || []).map(item => item.followed_id);
  if (!ids.length) return [];
  const { data, error } = await supabase.from("videos")
    .select("id, caption, video_url, created_at, user_id, profiles(username, display_name, avatar_url)")
    .in("user_id", ids).order("created_at", { ascending: false }).limit(50);
  if (error) throw error;
  return data ?? [];
}

export async function getMyVideos(userId) {
  requireSupabase();
  const { data, error } = await supabase.from("videos")
    .select("id, caption, video_url, created_at, profiles(username, display_name, avatar_url)")
    .eq("user_id", userId).order("created_at", { ascending: false }).limit(50);
  if (error) throw error;
  return data ?? [];
}

export async function getProfile(userId) {
  requireSupabase();
  const { data, error } = await supabase.from("profiles")
    .select("id, username, display_name, bio, avatar_url")
    .eq("id", userId).single();
  if (error) throw error;
  return data;
}

export async function getFollowState(followerId, followedIds) {
  requireSupabase();
  if (!followedIds.length) return [];
  const { data, error } = await supabase.from("follows")
    .select("followed_id").eq("follower_id", followerId).in("followed_id", followedIds);
  if (error) throw error;
  return (data || []).map(item => item.followed_id);
}
