import { useMemo, useState } from "react";
import { Home, Compass, Users, PlusSquare, Inbox, UserRound, Search, Heart, MessageCircle, Bookmark, Share2, Volume2, VolumeX, Play, Menu, X } from "lucide-react";

const videos = [
  { id: 1, user: "iraq_daily", name: "يوميات العراق 🇮🇶", caption: "جمال بلادنا ما ينتهي ❤️ #العراق #الرافدين", sound: "الصوت الأصلي - iraq_daily", likes: "24.8K", comments: "1,204", saves: "3,210", image: "https://images.unsplash.com/photo-1565008576549-57569a49371d?auto=format&fit=crop&w=900&q=85", avatar: "🇮🇶" },
  { id: 2, user: "food.with.ali", name: "علي | أكلات", caption: "منو يحب الأكل العراقي؟ 😍 #اكل_عراقي", sound: "صوت الطبخ - علي", likes: "12.2K", comments: "389", saves: "1,020", image: "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=85", avatar: "🍽️" },
  { id: 3, user: "travel.iraq", name: "سياحة العراق", caption: "أماكن لازم تزورها بالعراق ✨", sound: "الصوت الأصلي - سياحة العراق", likes: "8,540", comments: "210", saves: "780", image: "https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=900&q=85", avatar: "🏞️" }
];

function App() {
  const [active, setActive] = useState("الرئيسية");
  const [index, setIndex] = useState(0);
  const [liked, setLiked] = useState({});
  const [saved, setSaved] = useState({});
  const [following, setFollowing] = useState({});
  const [muted, setMuted] = useState(true);
  const [query, setQuery] = useState("");
  const [mobileMenu, setMobileMenu] = useState(false);
  const video = videos[index];
  const filtered = useMemo(() => videos.filter(v => (v.name + v.caption + v.user).toLowerCase().includes(query.toLowerCase())), [query]);

  const go = (step) => setIndex(i => (i + step + videos.length) % videos.length);
  const nav = [{ label: "الرئيسية", icon: Home }, { label: "اكتشف", icon: Compass }, { label: "المتابَعون", icon: Users }, { label: "رفع فيديو", icon: PlusSquare }, { label: "الرسائل", icon: Inbox }, { label: "الملف الشخصي", icon: UserRound }];

  return <div className="app-shell">
    <header className="topbar">
      <button className="icon-btn mobile-only" onClick={() => setMobileMenu(!mobileMenu)} aria-label="القائمة">{mobileMenu ? <X/> : <Menu/>}</button>
      <a className="brand" href="#"><span className="brand-mark">ر</span><span>الرافدين <b>تيك</b></span></a>
      <label className="search"><Search size={18}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="ابحث عن فيديوهات أو حسابات"/></label>
      <button className="upload-btn" onClick={() => alert("ميزة رفع الفيديو ستتوفر بعد ربط تسجيل الدخول والتخزين.")}><PlusSquare size={18}/> رفع فيديو</button>
      <button className="login-btn" onClick={() => alert("سيتم تفعيل تسجيل الدخول في المرحلة القادمة.")}>تسجيل الدخول</button>
    </header>
    <div className="layout">
      <aside className={`sidebar ${mobileMenu ? "open" : ""}`}>
        <div className="side-links">{nav.map(({label, icon: Icon}) => <button key={label} className={`nav-item ${active === label ? "selected" : ""}`} onClick={() => {setActive(label); setMobileMenu(false);}}><Icon size={21}/><span>{label}</span></button>)}</div>
        <div className="side-note"><b>مجتمع الرافدين</b><p>اكتشف صناع المحتوى العراقيين وشارك لحظاتك مع الجميع.</p><small>© 2026 الرافدين تيك</small></div>
      </aside>
      <main className="main-content">
        <div className="feed-heading"><div><span className="eyebrow">اكتشف الجديد</span><h1>{active === "الرئيسية" ? "لك" : active}</h1></div><div className="feed-tabs"><button className="tab active">لك</button><button className="tab" onClick={() => setActive("المتابَعون")}>أتابع</button></div></div>
        {query && <p className="search-count">نتائج البحث: {filtered.length}</p>}
        <section className="video-stage">
          <button className="arrow prev" onClick={() => go(-1)} aria-label="الفيديو السابق">‹</button>
          <article className="video-card">
            <img className="video-image" src={video.image} alt="معاينة الفيديو" />
            <div className="video-shade"/>
            <button className="sound-toggle" onClick={() => setMuted(!muted)} aria-label="الصوت">{muted ? <VolumeX/> : <Volume2/>}</button>
            <div className="video-info">
              <div className="creator"><span className="avatar">{video.avatar}</span><div><b>@{video.user}</b><small>{video.name}</small></div><button className="follow-btn" onClick={() => setFollowing(f => ({...f, [video.id]: !f[video.id]}))}>{following[video.id] ? "تتابعه" : "متابعة"}</button></div>
              <p>{video.caption}</p><small className="sound-line">♫ {video.sound}</small>
            </div>
            <div className="video-actions">
              <button onClick={() => setLiked(v => ({...v, [video.id]: !v[video.id]}))} className={liked[video.id] ? "is-liked" : ""}><Heart fill={liked[video.id] ? "currentColor" : "none"}/><b>{video.likes}</b></button>
              <button onClick={() => alert("التعليقات ستتوفر بعد ربط قاعدة البيانات.")}><MessageCircle/><b>{video.comments}</b></button>
              <button onClick={() => setSaved(v => ({...v, [video.id]: !v[video.id]}))} className={saved[video.id] ? "is-saved" : ""}><Bookmark fill={saved[video.id] ? "currentColor" : "none"}/><b>{video.saves}</b></button>
              <button onClick={() => navigator.clipboard?.writeText(window.location.href)}><Share2/><b>مشاركة</b></button>
            </div>
            <button className="play-indicator" aria-label="تشغيل"><Play fill="white"/></button>
          </article>
          <button className="arrow next" onClick={() => go(1)} aria-label="الفيديو التالي">›</button>
        </section>
        <div className="feed-footer"><span><i/> فيديو {index + 1} من {videos.length}</span><div className="dots">{videos.map((v,i) => <button key={v.id} onClick={() => setIndex(i)} className={i === index ? "dot active-dot" : "dot"} aria-label={`فيديو ${i+1}`}/>)}</div></div>
        <div className="mobile-actions"><button onClick={() => go(-1)}>الفيديو السابق</button><button onClick={() => go(1)}>الفيديو التالي</button></div>
      </main>
      <aside className="right-panel"><h3>حسابات مقترحة</h3>{videos.map(v => <div className="suggestion" key={v.id}><span className="avatar">{v.avatar}</span><div><b>@{v.user}</b><small>{v.name}</small></div><button onClick={() => setFollowing(f => ({...f,[v.id]:!f[v.id]}))}>{following[v.id] ? "متابَع" : "متابعة"}</button></div>)}<div className="community-card"><span>🇮🇶</span><b>صوتك يوصل</b><p>انشر إبداعك وخلي العالم يشوف جمال العراق.</p></div></aside>
    </div>
  </div>;
}

export default App;