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
  const livekitApiKey = Deno.env.get("LIVEKIT_API_KEY");
  const livekitApiSecret = Deno.env.get("LIVEKIT_API_SECRET");
  if (!supabaseUrl || !supabaseAnonKey || !livekitApiKey || !livekitApiSecret) {
    return json({ error: "خدمة البث غير مهيأة على الخادم." }, 503);
  }

  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
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

  const displayName = String(authData.user.user_metadata?.username || authData.user.email || "مستخدم").slice(0, 80);
  const token = new AccessToken(livekitApiKey, livekitApiSecret, {
    identity: authData.user.id,
    name: displayName,
    ttl: "1h"
  });
  token.addGrant({
    roomJoin: true,
    room,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true
  });

  try {
    return json({ token: await token.toJwt() });
  } catch {
    return json({ error: "تعذر إنشاء تصريح البث." }, 500);
  }
});
