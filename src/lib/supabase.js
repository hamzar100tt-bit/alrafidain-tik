import { createClient } from "@supabase/supabase-js";

// These are public browser credentials (publishable key), not secret/service-role keys.
const url = import.meta.env.VITE_SUPABASE_URL || "https://gxzbgwgovteijsmkouuv.supabase.co";
const publishableKey = import.meta.env.VITE_SUPABASE_ANON_KEY || "sb_publishable_My_DP4QQei1tdT32rYLeGg_9XYinUWp";

export const isSupabaseConfigured = Boolean(url && publishableKey);
export const supabase = isSupabaseConfigured
  ? createClient(url, publishableKey, {
      auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true }
    })
  : null;
