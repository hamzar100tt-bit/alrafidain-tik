import { useEffect, useState } from "react";
import { supabase } from "./lib/supabase.js";

const panelStyle = {
  position: "fixed", inset: 0, zIndex: 1000, background: "#f4f6fb",
  color: "#182033", overflowY: "auto", padding: "24px", direction: "rtl"
};
const cardStyle = { background: "#fff", border: "1px solid #e5e9f2", borderRadius: 16, padding: 18, marginBottom: 14 };
const buttonStyle = { border: 0, borderRadius: 10, padding: "10px 14px", cursor: "pointer", background: "#152d24", color: "#fff", fontWeight: 700 };

export default function AdminPanel({ user, onClose }) {
  const [tab, setTab] = useState("users");
  const [term, setTerm] = useState("");
  const [results, setResults] = useState([]);
  const [selected, setSelected] = useState(null);
  const [vipDate, setVipDate] = useState("");
  const [reason, setReason] = useState("");
  const [ad, setAd] = useState({ title: "", media_url: "", target_url: "", placement: "feed" });
  const [audit, setAudit] = useState([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [authorized, setAuthorized] = useState(null);

  useEffect(() => {
    let alive = true;
    supabase.from("admin_roles").select("role").eq("user_id", user.id).maybeSingle()
      .then(({ data, error }) => { if (alive) setAuthorized(!error && data?.role === "super_admin"); });
    return () => { alive = false; };
  }, [user.id]);

  const searchUsers = async (e) => {
    e?.preventDefault();
    setBusy(true); setNotice("");
    const { data, error } = await supabase.from("profiles")
      .select("id,username,display_name,avatar_url,vip_until,account_status,moderation_reason")
      .or(`username.ilike.%${term.trim()}%,display_name.ilike.%${term.trim()}%`)
      .limit(25);
    setBusy(false);
    if (error) setNotice(error.message); else setResults(data || []);
  };

  const setVip = async (enabled) => {
    if (!selected) return;
    setBusy(true); setNotice("");
    const until = enabled ? new Date(vipDate).toISOString() : null;
    const { error } = await supabase.rpc("admin_set_vip", { p_target_user_id: selected.id, p_vip_until: until });
    setBusy(false);
    if (error) setNotice(error.message);
    else { setNotice(enabled ? "تم تفعيل VIP" : "تم إلغاء VIP"); await searchUsers(); }
  };

  const setStatus = async (status) => {
    if (!selected) return;
    setBusy(true); setNotice("");
    const { error } = await supabase.rpc("admin_set_account_status", {
      p_target_user_id: selected.id, p_status: status, p_reason: reason || null
    });
    setBusy(false);
    if (error) setNotice(error.message);
    else { setNotice("تم تحديث حالة الحساب"); await searchUsers(); }
  };

  const saveAd = async (e) => {
    e.preventDefault(); setBusy(true); setNotice("");
    const { error } = await supabase.from("admin_ads").insert({ ...ad, created_by: user.id });
    setBusy(false);
    setNotice(error ? error.message : "تم حفظ الإعلان");
    if (!error) setAd({ title: "", media_url: "", target_url: "", placement: "feed" });
  };

  const loadAudit = async () => {
    setBusy(true);
    const { data, error } = await supabase.from("admin_audit_log")
      .select("id,action,target_user_id,details,created_at").order("created_at", { ascending: false }).limit(100);
    setBusy(false);
    if (error) setNotice(error.message); else setAudit(data || []);
  };

  if (authorized === null) return <div style={panelStyle}><p>جارٍ التحقق من صلاحيات الإدارة…</p></div>;
  if (!authorized) return <div style={panelStyle}><button style={buttonStyle} onClick={onClose}>رجوع</button><h2>غير مصرح</h2><p>هذه اللوحة مخصصة للمدير العام فقط.</p></div>;

  const tabs = [["users","الحسابات وVIP"],["ads","الإعلانات"],["audit","سجل الإدارة"]];
  return <div style={panelStyle}>
    <header style={{display:"flex",justifyContent:"space-between",alignItems:"center",gap:12,flexWrap:"wrap",maxWidth:1100,margin:"0 auto 20px"}}>
      <div><div style={{fontSize:13,color:"#788399"}}>ALRAFIDAIN TIK · CONTROL CENTER</div><h1 style={{margin:"6px 0"}}>لوحة الإدارة</h1><small>{user.email} · المدير العام</small></div>
      <button style={buttonStyle} onClick={onClose}>إغلاق اللوحة ✕</button>
    </header>
    <nav style={{display:"flex",gap:8,flexWrap:"wrap",maxWidth:1100,margin:"0 auto 18px"}}>
      {tabs.map(([id,label])=><button key={id} style={{...buttonStyle,background:tab===id?"#b58b35":"#e7ebf2",color:tab===id?"#fff":"#263247"}} onClick={()=>{setTab(id);if(id==="audit")loadAudit();}}>{label}</button>)}
    </nav>
    <main style={{maxWidth:1100,margin:"auto"}}>
      {notice && <p role="status" style={{...cardStyle,borderColor:"#d5b76b"}}>{notice}</p>}
      {tab==="users" && <>
        <section style={cardStyle}><h3>البحث عن مستخدم</h3><form onSubmit={searchUsers} style={{display:"flex",gap:8,flexWrap:"wrap"}}><input value={term} onChange={e=>setTerm(e.target.value)} placeholder="اسم المستخدم أو الاسم الظاهر" style={{flex:"1 1 240px",padding:12,border:"1px solid #dce1ea",borderRadius:10}}/><button style={buttonStyle} disabled={busy}>بحث</button></form></section>
        <section style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(260px,1fr))",gap:12}}>
          <div style={cardStyle}><h3>نتائج البحث</h3>{results.map(p=><button key={p.id} onClick={()=>{setSelected(p);setVipDate(p.vip_until?new Date(p.vip_until).toISOString().slice(0,16):"");}} style={{display:"block",width:"100%",textAlign:"right",padding:12,margin:"6px 0",border:"1px solid #e5e9f2",borderRadius:10,background:selected?.id===p.id?"#f7f0df":"#fff",cursor:"pointer"}}><b>{p.display_name||p.username}</b><br/><small>@{p.username} · {p.account_status||"active"}</small></button>)}</div>
          <div style={cardStyle}><h3>إجراءات الحساب</h3>{selected ? <><b>{selected.display_name||selected.username}</b><p>@{selected.username}</p><label>انتهاء VIP</label><input type="datetime-local" value={vipDate} onChange={e=>setVipDate(e.target.value)} style={{display:"block",width:"100%",padding:10,margin:"8px 0"}}/><button style={buttonStyle} disabled={busy||!vipDate} onClick={()=>setVip(true)}>منح / تجديد VIP</button> <button style={{...buttonStyle,background:"#697386"}} disabled={busy} onClick={()=>setVip(false)}>إلغاء VIP</button><hr/><label>سبب الإجراء</label><textarea value={reason} onChange={e=>setReason(e.target.value)} style={{display:"block",width:"100%",padding:10,margin:"8px 0"}}/><div style={{display:"flex",gap:8,flexWrap:"wrap"}}><button style={{...buttonStyle,background:"#b7791f"}} disabled={busy} onClick={()=>setStatus("suspended")}>إيقاف مؤقت</button><button style={{...buttonStyle,background:"#b42318"}} disabled={busy} onClick={()=>setStatus("banned")}>حظر الحساب</button><button style={{...buttonStyle,background:"#18794e"}} disabled={busy} onClick={()=>setStatus("active")}>رفع الحظر</button></div></> : <p>اختر حساباً من نتائج البحث.</p>}</div>
        </section>
      </>}
      {tab==="ads" && <section style={cardStyle}><h3>إضافة إعلان</h3><form onSubmit={saveAd} style={{display:"grid",gap:10}}><input required placeholder="عنوان الإعلان" value={ad.title} onChange={e=>setAd({...ad,title:e.target.value})} style={{padding:12,border:"1px solid #dce1ea",borderRadius:10}}/><input required type="url" placeholder="رابط الصورة أو الفيديو" value={ad.media_url} onChange={e=>setAd({...ad,media_url:e.target.value})} style={{padding:12,border:"1px solid #dce1ea",borderRadius:10}}/><input type="url" placeholder="رابط الوجهة (اختياري)" value={ad.target_url} onChange={e=>setAd({...ad,target_url:e.target.value})} style={{padding:12,border:"1px solid #dce1ea",borderRadius:10}}/><select value={ad.placement} onChange={e=>setAd({...ad,placement:e.target.value})} style={{padding:12,borderRadius:10}}><option value="feed">الرئيسية</option><option value="discover">اكتشف</option><option value="live_rooms">الغرف المباشرة</option></select><button style={buttonStyle} disabled={busy}>حفظ الإعلان</button></form></section>}
      {tab==="audit" && <section style={cardStyle}><h3>سجل إجراءات الإدارة</h3><button style={buttonStyle} onClick={loadAudit} disabled={busy}>تحديث</button>{audit.map(a=><article key={a.id} style={{padding:"12px 0",borderBottom:"1px solid #edf0f5"}}><b>{a.action}</b><p style={{margin:"4px 0"}}>{a.target_user_id||"—"} · {new Date(a.created_at).toLocaleString("ar-IQ")}</p><small>{JSON.stringify(a.details)}</small></article>)}</section>}
      <p style={{color:"#687386",fontSize:12}}>كل إجراء حساس يتحقق منه الخادم ويُسجل في سجل الإدارة. احتفظ ببيانات دخولك ولا تشارك حساب المدير العام.</p>
    </main>
  </div>;
}
