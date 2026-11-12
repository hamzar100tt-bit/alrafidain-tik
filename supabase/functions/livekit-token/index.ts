import { createClient } from "npm:@supabase/supabase-js@2";
import { AccessToken } from "npm:livekit-server-sdk@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json"
};

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authorization = request.headers.get("Authorization") || "";
  const jwt = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!jwt) return json({ error: "سجّل الدخول أولاً." }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const livekitUrl = Deno.env.get("LIVEKIT_URL");
  const livekitApiKey = Deno.env.get("LIVEKIT_API_KEY");
  const livekitApiSecret = Deno.env.get("LIVEKIT_API_SECRET");
  if (!supabaseUrl || !supabaseAnonKey || !livekitUrl || !livekitApiKey || !livekitApiSecret) {
    return json({ error: "خدمة البث غير مهيأة على الخادم." }, 503);
  }

  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
    auth: { persistSession: false, autoRefreshToken: false }
  });
  const { data: authData, error: authError } = await supabase.auth.getUser(jwt);
  if (authError || !authData.user) return json({ error: "جلسة الدخول غير صالحة." }, 401);

  let body: { room?: unknown };
  try {
    body = await request.json();
  } catch {
    return json({ error: "طلب غير صالح." }, 400);
  }
  const room = typeof body.room === "string" ? body.room.trim() : "";
  if (room.length < 3 || room.length > 60 || !/^[\p{L}\p{N} _.-]+$/u.test(room)) {
    return json({ error: "اسم الغرفة لازم يكون 3 إلى 60 حرفاً أو رقماً." }, 400);
  }

  const { data: roomRow, error: roomError } = await supabase
    .from("live_rooms")
    .select("id, owner_id, is_live")
    .eq("room_key", room)
    .eq("is_live", true)
    .maybeSingle();
  if (roomError) return json({ error: "تعذر التحقق من الغرفة." }, 500);
  if (!roomRow) return json({ error: "الغرفة غير موجودة أو انتهى البث." }, 404);

  let role = "viewer";
  if (roomRow.owner_id === authData.user.id) {
    role = "host";
  } else {
    const { data: speaker } = await supabase
      .from("live_room_speakers")
      .select("user_id")
      .eq("room_id", roomRow.id)
      .eq("user_id", authData.user.id)
      .maybeSingle();
    if (speaker) role = "speaker";
  }

  const displayName = String(
    authData.user.user_metadata?.display_name ||
    authData.user.user_metadata?.username ||
    authData.user.email ||
    "مستخدم"
  ).slice(0, 80);
  const token = new AccessToken(livekitApiKey, livekitApiSecret, {
    identity: authData.user.id,
    name: displayName,
    ttl: "1h"
  });
  token.addGrant({
    roomJoin: true,
    room,
    canPublish: role === "host" || role === "speaker",
    canSubscribe: true,
    canPublishData: true
  });

  try {
    const serverUrl = livekitUrl.trim();
    return json({ serverUrl, participantToken: await token.toJwt(), room, role });
  } catch {
    return json({ error: "تعذر إنشاء تصريح البث." }, 500);
  }
});
