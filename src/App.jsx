import { useEffect, useMemo, useRef, useState } from "react";
import { Home, Compass, Users, PlusSquare, UserRound, Search, Heart, MessageCircle, Bookmark, Share2, Volume2, VolumeX, Play, Menu, X, LogOut, Send, Video, LoaderCircle } from "lucide-react";
import { supabase } from "./lib/supabase.js";
import { signIn, signUp, signOut, publishVideo, getFeed, addComment, getComments, toggleLike, toggleFollow, getFollowingFeed, getMyVideos, getProfile, getFollowState, getLikedVideoIds } from "./services/videoService.js";

const demoVideos = [
  { id: "demo-1", user: "iraq_daily", name: "يوميات العراق 🇮🇶", caption: "جمال بلادنا ما ينتهي ❤️ #العراق #الرافدين", image: "https://images.unsplash.com/photo-1565008576549-57569a49371d?auto=format&fit=crop&w=900&q=85", avatar: "🇮🇶", demo: true },
  { id: "demo-2", user: "food.with.ali", name: "علي | أكلات", caption: "منو يحب الأكل العراقي؟ 😍 #اكل_عراقي", image: "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=85", avatar: "🍽️", demo: true },
  { id: "demo-3", user: "travel.iraq", name: "سياحة العراق", caption: "أماكن لازم تزورها بالعراق ✨", image: "https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=900&q=85", avatar: "🏞️", demo: true }
];

function App() {
  const [active, setActive] = useState("الرئيسية");
  const [liked, setLiked] = useState({});
  const [saved, setSaved] = useState({});
  const [following, setFollowing] = useState({});
  const [muted, setMuted] = useState(true);
  const [query, setQuery] = useState("");
  const [mobileMenu, setMobileMenu] = useState(false);
  const [user, setUser] = useState(null);
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [username, setUsername] = useState("");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [file, setFile] = useState(null);
  const [caption, setCaption] = useState("");
  const [commentsFor, setCommentsFor] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState("");
  const [profile, setProfile] = useState(null);
  const [profileEditOpen, setProfileEditOpen] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  const videoRef = useRef(null);
  const fileRef = useRef(null);
  const touchStartY = useRef(0);
  const handleTouchStart = (e) => { touchStartY.current = e.touches[0]?.clientY || 0; };
  const handleTouchEnd = (e) => { const delta = touchStartY.current - (e.changedTouches[0]?.clientY || touchStartY.current); if (Math.abs(delta) > 45) go(delta > 0 ? 1 : -1); };

  const loadFeed = async (target = "الرئيسية", currentUser = user) => {
    setLoading(true);
    try {
      let data = [];
      if (target === "المتابَعون") data = currentUser ? await getFollowingFeed(currentUser.id) : [];
      else if (target === "الملف الشخصي") data = currentUser ? await getMyVideos(currentUser.id) : [];
      else data = await getFeed();
      setVideos(data.map(v => ({
        id: v.id, userId: v.user_id, user: v.profiles?.username || "مستخدم",
        name: v.profiles?.display_name || v.profiles?.username || "مستخدم الرافدين",
        caption: v.caption, videoUrl: v.video_url, avatar: v.profiles?.avatar_url || "🇮🇶", demo: false
      })));
      if (currentUser) {
        try {
          const ownProfile = await getProfile(currentUser.id);
          setProfile(ownProfile);
          setDisplayName(ownProfile.display_name || "");
          setBio(ownProfile.bio || "");
        } catch { setProfile(null); }
      }
      setError("");
    } catch (e) {
      setVideos([]);
      setError(e.message || "تعذر تحميل الفيديوهات.");
    } finally { setLoading(false); }
  };

  useEffect(() => {
    let alive = true;
    supabase.auth.getSession().then(({ data }) => { if (alive) setUser(data.session?.user || null); });
    const { data: listener } = supabase.auth.onAuthStateChange((_event, session) => setUser(session?.user || null));
    return () => { alive = false; listener.subscription.unsubscribe(); };
  }, []);

  useEffect(() => { loadFeed(active, user); setIndex(0); }, [active, user]);

  useEffect(() => {
    let cancelled = false;
    const loadSocialState = async () => {
      if (!user || !videos.length) { setLiked({}); setFollowing({}); return; }
      const ids = videos.filter(v => !v.demo).map(v => v.id);
      const userIds = [...new Set(videos.map(v => v.userId).filter(id => id && id !== user.id))];
      try {
        const [likedIds, followedIds] = await Promise.all([
          getLikedVideoIds(user.id, ids), getFollowState(user.id, userIds)
        ]);
        if (!cancelled) {
          setLiked(Object.fromEntries(likedIds.map(id => [id, true])));
          setFollowing(Object.fromEntries(followedIds.map(id => [id, true])));
        }
      } catch (e) { if (!cancelled) setError(e.message || "تعذر تحميل حالة الإعجاب والمتابعة."); }
    };
    loadSocialState();
    return () => { cancelled = true; };
  }, [user, videos]);

  const shownVideos = videos.length ? videos : (active === "الرئيسية" || active === "اكتشف" ? demoVideos : []);
  const [index, setIndex] = useState(0);
  useEffect(() => { if (index >= shownVideos.length) setIndex(0); }, [shownVideos.length, index]);
  const video = shownVideos[index] || demoVideos[0];
  const filtered = query.trim() ? shownVideos.filter(v => (v.name + v.caption + v.user).toLowerCase().includes(query.toLowerCase())) : shownVideos;
  const nav = [{ label: "الرئيسية", icon: Home }, { label: "اكتشف", icon: Compass }, { label: "المتابَعون", icon: Users }, { label: "رفع فيديو", icon: PlusSquare }, { label: "الملف الشخصي", icon: UserRound }];

  const go = (step) => { if (shownVideos.length) setIndex(i => (i + step + shownVideos.length) % shownVideos.length); };
  const openAuth = () => { setError(""); setAuthOpen(true); };
  const handleAuth = async (e) => {
    e.preventDefault(); setBusy(true); setError("");
    try {
      if (authMode === "signup") {
        const result = await signUp({ email, password, username });
        if (!result.session) setError("تم إنشاء الحساب. افحص بريدك الإلكتروني لتأكيد التسجيل، ثم سجّل الدخول.");
        else setAuthOpen(false);
      } else { await signIn({ email, password }); setAuthOpen(false); }
    } catch (e) { setError(e.message || "تعذر تسجيل الدخول."); }
    finally { setBusy(false); }
  };
  const handleUpload = async (e) => {
    e.preventDefault(); if (!user) { setUploadOpen(false); openAuth(); return; }
    setBusy(true); setError("");
    try {
      await publishVideo({ file, caption, userId: user.id });
      setUploadOpen(false); setFile(null); setCaption(""); setActive("الرئيسية"); setIndex(0); await loadFeed("الرئيسية", user);
    } catch (e) { setError(e.message || "تعذر رفع الفيديو."); }
    finally { setBusy(false); }
  };
  const saveProfile = async (e) => {
    e.preventDefault(); if (!user) return; setBusy(true);
    try {
      const { error: updateError } = await supabase.from("profiles").update({ display_name: displayName.trim(), bio: bio.trim() }).eq("id", user.id);
      if (updateError) throw updateError;
      setProfile(p => ({ ...p, display_name: displayName.trim(), bio: bio.trim() })); setProfileEditOpen(false);
    } catch (e) { setError(e.message || "تعذر حفظ الملف الشخصي."); }
    finally { setBusy(false); }
  };

  const handleFollow = async (v) => {
    if (!user) { openAuth(); return; }
    if (!v.userId || v.userId === user.id) return;
    const wasFollowing = Boolean(following[v.userId]);
    setFollowing(s => ({ ...s, [v.userId]: !wasFollowing }));
    try { await toggleFollow({ followerId: user.id, followedId: v.userId, following: wasFollowing }); }
    catch (e) { setFollowing(s => ({ ...s, [v.userId]: wasFollowing })); setError(e.message || "تعذر تحديث المتابعة."); }
  };

  useEffect(() => {
    if (videoRef.current && video?.videoUrl) {
      videoRef.current.currentTime = 0;
      videoRef.current.play().catch(() => {});
    }
  }, [video?.id, video?.videoUrl]);

  const handleLike = async (v) => {
    if (v.demo || !user) { if (!user) openAuth(); return; }
    const wasLiked = Boolean(liked[v.id]);
    setLiked(s => ({ ...s, [v.id]: !wasLiked }));
    try { await toggleLike({ videoId: v.id, userId: user.id, liked: wasLiked }); }
    catch (e) { setLiked(s => ({ ...s, [v.id]: wasLiked })); setError(e.message); }
  };
  const openComments = async (v) => {
    if (v.demo) { setError("التعليقات متاحة على الفيديوهات المنشورة بعد تسجيل الدخول."); return; }
    setCommentsFor(v); setComments([]);
    try { setComments(await getComments(v.id)); } catch (e) { setError(e.message); }
  };
  const sendComment = async (e) => {
    e.preventDefault(); if (!user) { openAuth(); return; }
    if (!commentText.trim() || !commentsFor) return;
    try { await addComment({ videoId: commentsFor.id, userId: user.id, body: commentText.trim() }); setCommentText(""); setComments(await getComments(commentsFor.id)); }
    catch (e) { setError(e.message); }
  };

  return <div className="app-shell">
    <header className="topbar">
      <button className="icon-btn mobile-only" onClick={() => setMobileMenu(!mobileMenu)} aria-label="القائمة">{mobileMenu ? <X/> : <Menu/>}</button>
      <a className="brand" href="#"><span className="brand-mark">ر</span><span>الرافدين <b>تيك</b></span></a>
      <label className="search"><Search size={18}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="ابحث عن فيديوهات أو حسابات"/></label>
      <button className="upload-btn" onClick={() => user ? setUploadOpen(true) : openAuth()}><PlusSquare size={18}/> رفع فيديو</button>
      {user ? <button className="login-btn" onClick={async () => { await signOut(); setUser(null); }}> <LogOut size={16}/> خروج</button> : <button className="login-btn" onClick={openAuth}>تسجيل الدخول</button>}
    </header>
    <div className="layout">
      <aside className={`sidebar ${mobileMenu ? "open" : ""}`}>
        <div className="side-links">{nav.map(({label, icon: Icon}) => <button key={label} className={`nav-item ${active === label ? "selected" : ""}`} onClick={() => {setActive(label); setMobileMenu(false); if(label === "رفع فيديو") user ? setUploadOpen(true) : openAuth();}}><Icon size={21}/><span>{label}</span></button>)}</div>
        <div className="side-note"><b>مجتمع الرافدين</b><p>اكتشف صناع المحتوى العراقيين وشارك لحظاتك مع الجميع.</p><small>© 2026 الرافدين تيك</small></div>
      </aside>
      <main className="main-content">
        <div className="feed-heading"><div><span className="eyebrow">اكتشف الجديد</span><h1>{active === "الرئيسية" ? "لك" : active}</h1></div><div className="feed-tabs"><button className={`tab ${active !== "المتابَعون" ? "active" : ""}`} onClick={() => setActive("الرئيسية")}>لك</button><button className={`tab ${active === "المتابَعون" ? "active" : ""}`} onClick={() => setActive("المتابَعون")}>أتابع</button></div></div>
        {query && <p className="search-count">نتائج البحث: {filtered.length}</p>}
        {error && <p className="search-count" role="status">{error}</p>}
        {loading ? <p className="search-count">جاري تحميل الفيديوهات…</p> : shownVideos.length === 0 ? <section className="empty-state"><h2>{active === "الملف الشخصي" ? "ملفك الشخصي" : active === "المتابَعون" ? "ماكو فيديوهات من الحسابات اللي تتابعها بعد" : "ماكو نتائج"}</h2><p>{profile ? `@${profile.username} · ${profile.bio || "شارك أول فيديو إلك"}` : "سجّل الدخول أو تابع حسابات حتى تظهر الفيديوهات هنا."}</p>{active === "الملف الشخصي" && <button className="upload-btn" onClick={() => setUploadOpen(true)}>انشر أول فيديو</button>}</section> : <section className="video-stage">
          <button className="arrow prev" onClick={() => go(-1)} aria-label="الفيديو السابق">‹</button>
          <article className="video-card" onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
            {video.videoUrl ? <video ref={videoRef} className="video-image" src={video.videoUrl} playsInline loop autoPlay muted={muted} controls/> : <img className="video-image" src={video.image || demoVideos[0].image} alt="معاينة الفيديو"/>}
            <div className="video-shade"/>
            <button className="sound-toggle" onClick={() => setMuted(!muted)} aria-label="الصوت">{muted ? <VolumeX/> : <Volume2/>}</button>
            <div className="video-info">
              <div className="creator"><span className="avatar">{video.avatar}</span><div><b>@{video.user}</b><small>{video.name}</small></div><button className="follow-btn" onClick={() => handleFollow(video)}>{following[video.userId] ? "تتابعه" : "متابعة"}</button></div>
              <p>{video.caption}</p><small className="sound-line">♫ الرافدين تيك</small>
            </div>
            <div className="video-actions">
              <button onClick={() => handleLike(video)} className={liked[video.id] ? "is-liked" : ""}><Heart fill={liked[video.id] ? "currentColor" : "none"}/><b>إعجاب</b></button>
              <button onClick={() => openComments(video)}><MessageCircle/><b>تعليقات</b></button>
              <button onClick={() => setSaved(v => ({...v, [video.id]: !v[video.id]}))} className={saved[video.id] ? "is-saved" : ""}><Bookmark fill={saved[video.id] ? "currentColor" : "none"}/><b>حفظ</b></button>
              <button onClick={() => navigator.clipboard?.writeText(video.videoUrl || window.location.href)}><Share2/><b>مشاركة</b></button>
            </div>
            {!video.videoUrl && <button className="play-indicator" aria-label="معاينة"><Play fill="white"/></button>}
          </article>
          <button className="arrow next" onClick={() => go(1)} aria-label="الفيديو التالي">›</button>
        </section>}
        <div className="feed-footer"><span><i/> {videos.length ? `فيديو ${Math.min(index + 1, videos.length)} من ${videos.length}` : "معاينات تجريبية"}</span><div className="dots">{shownVideos.map((v,i) => <button key={v.id} onClick={() => setIndex(i)} className={i === index ? "dot active-dot" : "dot"} aria-label={`فيديو ${i+1}`}/>)}</div></div>
        <div className="mobile-actions"><button onClick={() => go(-1)}>الفيديو السابق</button><button onClick={() => go(1)}>الفيديو التالي</button></div>
      </main>
      <aside className="right-panel"><h3>حسابات مقترحة</h3><div className="community-card"><span>🇮🇶</span><b>صوتك يوصل</b><p>انشر إبداعك وخلي العالم يشوف جمال العراق.</p></div></aside>
    </div>

    <nav className="app-bottom-nav"><button className={active === "الرئيسية" ? "active" : ""} onClick={() => setActive("الرئيسية")}><Home/><span>الرئيسية</span></button><button className={active === "اكتشف" ? "active" : ""} onClick={() => setActive("اكتشف")}><Compass/><span>اكتشف</span></button><button className="nav-create" onClick={() => user ? setUploadOpen(true) : openAuth()}><PlusSquare/><span>نشر</span></button><button className={active === "المتابَعون" ? "active" : ""} onClick={() => setActive("المتابَعون")}><Users/><span>أتابع</span></button><button className={active === "الملف الشخصي" ? "active" : ""} onClick={() => user ? setActive("الملف الشخصي") : openAuth()}><UserRound/><span>حسابي</span></button></nav>

    {authOpen && <div className="modal-backdrop" onClick={() => setAuthOpen(false)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setAuthOpen(false)}><X/></button><h2>{authMode === "signup" ? "إنشاء حساب جديد" : "تسجيل الدخول"}</h2><form onSubmit={handleAuth}>
      {authMode === "signup" && <input required minLength="3" maxLength="24" value={username} onChange={e => setUsername(e.target.value)} placeholder="اسم المستخدم"/>}
      <input required type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="البريد الإلكتروني"/>
      <input required minLength="6" type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="كلمة المرور"/>
      <button className="login-btn modal-submit" disabled={busy}>{busy ? "انتظر..." : authMode === "signup" ? "إنشاء الحساب" : "دخول"}</button>
    </form><button className="switch-auth" onClick={() => setAuthMode(authMode === "signup" ? "signin" : "signup")}>{authMode === "signup" ? "عندك حساب؟ سجل دخول" : "ما عندك حساب؟ أنشئ حساب"}</button></section></div>}

    {uploadOpen && <div className="modal-backdrop" onClick={() => setUploadOpen(false)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setUploadOpen(false)}><X/></button><h2><Video size={20}/> نشر فيديو</h2><form onSubmit={handleUpload}>
      <input ref={fileRef} required type="file" accept="video/mp4,video/webm,video/quicktime" onChange={e => setFile(e.target.files?.[0] || null)}/>
      <textarea required maxLength="2200" value={caption} onChange={e => setCaption(e.target.value)} placeholder="اكتب وصف الفيديو…"/>
      <button className="login-btn modal-submit" disabled={busy || !file}>{busy ? "جاري الرفع..." : "نشر الفيديو"}</button>
    </form></section></div>}

    {profileEditOpen && <div className="modal-backdrop" onClick={() => setProfileEditOpen(false)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setProfileEditOpen(false)}><X/></button><h2>تعديل الملف الشخصي</h2><form onSubmit={saveProfile}><input maxLength="60" value={displayName} onChange={e => setDisplayName(e.target.value)} placeholder="الاسم الظاهر"/><textarea maxLength="160" value={bio} onChange={e => setBio(e.target.value)} placeholder="نبذة عنك"/><button className="login-btn modal-submit" disabled={busy}>{busy ? "حفظ..." : "حفظ التغييرات"}</button></form></section></div>}
    {active === "الملف الشخصي" && user && <div className="profile-tools"><h2>@{profile?.username || "حسابي"}</h2><p>{profile?.bio || "أضف نبذة تعريفية عنك"}</p><button className="login-btn" onClick={() => setProfileEditOpen(true)}>تعديل الملف الشخصي</button></div>}
    {commentsFor && <div className="modal-backdrop" onClick={() => setCommentsFor(null)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setCommentsFor(null)}><X/></button><h2>التعليقات</h2><div className="comment-list">{comments.map(c => <p key={c.id}><b>@{c.profiles?.username || "مستخدم"}</b> {c.body}</p>)}{!comments.length && <p>لا توجد تعليقات بعد.</p>}</div><form onSubmit={sendComment}><input value={commentText} onChange={e => setCommentText(e.target.value)} placeholder="اكتب تعليقاً…"/><button className="login-btn modal-submit" disabled={!commentText.trim()}><Send size={16}/> إرسال</button></form></section></div>}
  </div>;
}

export default App;
