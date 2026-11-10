import { supabase, isSupabaseConfigured } from "../lib/supabase.js";

const MAX_VIDEO_SIZE = 100 * 1024 * 1024;
const VIDEO_TYPES_BY_EXTENSION = {
  mp4: "video/mp4",
  webm: "video/webm",
  mov: "video/quicktime"
};

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
  if (!file) throw new Error("اختار فيديو من جهازك أولاً.");
  if (!userId) throw new Error("سجّل الدخول قبل نشر الفيديو.");
  const extension = file.name.split(".").pop()?.toLowerCase() || "";
  const expectedType = VIDEO_TYPES_BY_EXTENSION[extension];
  if (!expectedType) throw new Error("الصيغ المدعومة حالياً: MP4 وWebM وMOV.");
  if (file.type && file.type !== expectedType && !(extension === "mov" && file.type === "video/mp4")) {
    throw new Error("نوع الملف لا يطابق امتداده. جرّب اختيار فيديو MP4 أو MOV أو WebM.");
  }
  if (!file.size || file.size > MAX_VIDEO_SIZE) throw new Error("حجم الفيديو يجب أن يكون أكبر من صفر وأقل من 100 ميغابايت.");
  const path = `${userId}/${crypto.randomUUID()}.${extension}`;
  const { error: uploadError } = await supabase.storage.from("videos").upload(path, file, {
    contentType: file.type || expectedType, upsert: false
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
    .select("id, user_id, caption, video_url, created_at, profiles(username, display_name, avatar_url)")
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
    .select("id, user_id, caption, video_url, created_at, profiles(username, display_name, avatar_url)")
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


export async function getLikedVideoIds(userId, videoIds) {
  requireSupabase();
  if (!videoIds.length) return [];
  const { data, error } = await supabase.from("likes")
    .select("video_id").eq("user_id", userId).in("video_id", videoIds);
  if (error) throw error;
  return (data || []).map(item => item.video_id);
}


export async function getProfileStats(userId) {
  requireSupabase();
  const [videos, followers, following] = await Promise.all([
    supabase.from("videos").select("id", { count: "exact", head: true }).eq("user_id", userId),
    supabase.from("follows").select("follower_id", { count: "exact", head: true }).eq("followed_id", userId),
    supabase.from("follows").select("followed_id", { count: "exact", head: true }).eq("follower_id", userId)
  ]);
  const failed = [videos, followers, following].find(result => result.error);
  if (failed) throw failed.error;
  return { videos: videos.count || 0, followers: followers.count || 0, following: following.count || 0 };
}


export async function findProfileByUsername(username) {
  requireSupabase();
  const clean = username.trim().replace(/^@/, "");
  if (!clean) throw new Error("اكتب اسم المستخدم.");
  const { data, error } = await supabase.from("profiles")
    .select("id, username, display_name, avatar_url").ilike("username", clean).maybeSingle();
  if (error) throw error;
  if (!data) throw new Error("ما لكيت هذا الحساب.");
  return data;
}

export async function getDirectMessages(userId, otherId) {
  requireSupabase();
  const { data, error } = await supabase.from("messages").select("id, sender_id, receiver_id, body, created_at")
    .or(`and(sender_id.eq.${userId},receiver_id.eq.${otherId}),and(sender_id.eq.${otherId},receiver_id.eq.${userId})`)
    .order("created_at", { ascending: true }).limit(200);
  if (error) throw error;
  return data || [];
}

export async function sendDirectMessage({ senderId, receiverId, body }) {
  requireSupabase();
  const clean = body.trim();
  if (!clean || clean.length > 2000) throw new Error("الرسالة لازم تكون بين حرف و2000 حرف.");
  const { data, error } = await supabase.from("messages")
    .insert({ sender_id: senderId, receiver_id: receiverId, body: clean }).select().single();
  if (error) throw error;
  return data;
}


export async function updateProfile({ userId, username, displayName, bio }) {
  requireSupabase();
  const cleanUsername = username.trim().replace(/^@/, "").toLowerCase();
  if (!/^[a-z0-9_.]{3,24}$/.test(cleanUsername)) {
    throw new Error("اسم المستخدم لازم يكون 3-24 حرفاً إنكليزياً أو أرقاماً أو _ أو نقطة.");
  }
  const { data, error } = await supabase.from("profiles").update({
    username: cleanUsername,
    display_name: displayName.trim().slice(0, 60),
    bio: bio.trim().slice(0, 160)
  }).eq("id", userId).select("id, username, display_name, bio, avatar_url").single();
  if (error) {
    if (error.code === "23505") throw new Error("اسم المستخدم مستخدم من شخص ثاني.");
    throw error;
  }
  return data;
}

export async function uploadProfileAvatar({ file, userId }) {
  requireSupabase();
  if (!file || !file.type.startsWith("image/")) throw new Error("اختار صورة صالحة.");
  if (file.size > 5 * 1024 * 1024) throw new Error("حجم الصورة لازم أقل من 5 ميغابايت.");
  const ext = file.name.split(".").pop()?.toLowerCase() || "jpg";
  const path = `${userId}/${crypto.randomUUID()}.${ext}`;
  const { error: uploadError } = await supabase.storage.from("avatars").upload(path, file, {
    contentType: file.type, upsert: true
  });
  if (uploadError) throw uploadError;
  const { data: urlData } = supabase.storage.from("avatars").getPublicUrl(path);
  const { data, error } = await supabase.from("profiles").update({ avatar_url: urlData.publicUrl })
    .eq("id", userId).select("id, username, display_name, bio, avatar_url").single();
  if (error) {
    await supabase.storage.from("avatars").remove([path]);
    throw error;
  }
  return data;
}
