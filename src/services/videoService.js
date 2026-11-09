import { supabase, isSupabaseConfigured } from "../lib/supabase.js";

const MAX_VIDEO_SIZE = 100 * 1024 * 1024;
const ALLOWED_TYPES = ["video/mp4", "video/webm", "video/quicktime"];

export async function signUp({ email, password, username }) {
  if (!isSupabaseConfigured) throw new Error("أضف مفاتيح Supabase أولاً.");
  const { data, error } = await supabase.auth.signUp({ email, password, options: { data: { username } } });
  if (error) throw error;
  if (data.user) {
    const { error: profileError } = await supabase.from("profiles").upsert({
      id: data.user.id, username, display_name: username
    });
    if (profileError) throw profileError;
  }
  return data;
}

export async function signIn({ email, password }) {
  if (!isSupabaseConfigured) throw new Error("أضف مفاتيح Supabase أولاً.");
  const { data, error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) throw error;
  return data;
}

export async function signOut() {
  const { error } = await supabase.auth.signOut();
  if (error) throw error;
}

export async function publishVideo({ file, caption, userId }) {
  if (!isSupabaseConfigured) throw new Error("أضف مفاتيح Supabase أولاً.");
  if (!file || !ALLOWED_TYPES.includes(file.type)) throw new Error("اختر فيديو MP4 أو WebM أو MOV.");
  if (file.size > MAX_VIDEO_SIZE) throw new Error("حجم الفيديو الأقصى 100 ميغابايت حالياً.");
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
  if (!isSupabaseConfigured) return [];
  const { data, error } = await supabase.from("videos")
    .select("id, caption, video_url, created_at, profiles(username, display_name, avatar_url)")
    .order("created_at", { ascending: false }).limit(30);
  if (error) throw error;
  return data ?? [];
}

export async function addComment({ videoId, userId, body }) {
  const { data, error } = await supabase.from("comments")
    .insert({ video_id: videoId, user_id: userId, body }).select().single();
  if (error) throw error;
  return data;
}

export async function getComments(videoId) {
  const { data, error } = await supabase.from("comments")
    .select("id, body, created_at, profiles(username, display_name)")
    .eq("video_id", videoId).order("created_at", { ascending: true }).limit(100);
  if (error) throw error;
  return data ?? [];
}

export async function toggleLike({ videoId, userId, liked }) {
  const request = liked
    ? supabase.from("likes").delete().match({ video_id: videoId, user_id: userId })
    : supabase.from("likes").insert({ video_id: videoId, user_id: userId });
  const { error } = await request;
  if (error) throw error;
}
