import { useEffect, useMemo, useRef, useState } from "react";
import { Home, Compass, Users, PlusSquare, UserRound, Search, Heart, MessageCircle, Bookmark, Share2, Radio, Volume2, VolumeX, Play, Menu, X, LogOut, Send, Video, LoaderCircle } from "lucide-react";
import { supabase } from "./lib/supabase.js";
import LiveStudio from "./LiveStudio.jsx";
import { signIn, signUp, signOut, publishVideo, getFeed, addComment, getComments, toggleLike, toggleFollow, getFollowingFeed, getMyVideos, getProfile, getFollowState, getLikedVideoIds, getProfileStats, findProfileByUsername, getDirectMessages, sendDirectMessage, updateProfile, uploadProfileAvatar } from "./services/videoService.js";

const demoVideos = [
  { id: "demo-1", user: "iraq_daily", name: "يوميات العراق 🇮🇶", caption: "جمال بلادنا ما ينتهي ❤️ #العراق #الرافدين", image: "https://images.unsplash.com/photo-1565008576549-57569a49371d?auto=format&fit=crop&w=900&q=85", avatar: "🇮🇶", demo: true },
  { id: "demo-2", user: "food.with.ali", name: "علي | أكلات", caption: "منو يحب الأكل العراقي؟ 😍 #اكل_عراقي", image: "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=85", avatar: "🍽️", demo: true },
  { id: "demo-3", user: "travel.iraq", name: "سياحة العراق", caption: "أماكن لازم تزورها بالعراق ✨", image: "https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=900&q=85", avatar: "🏞️", demo: true }
];

function VideoThumbnail({ src, alt = "معاينة الفيديو" }) {
  const [thumbnail, setThumbnail] = useState("");
  useEffect(() => {
    let cancelled = false;
    const video = document.createElement("video");
    video.crossOrigin = "anonymous";
    video.muted = true;
    video.playsInline = true;
    video.preload = "auto";
    const capture = () => {
      if (cancelled || !video.videoWidth || !video.videoHeight) return;
      try {
        const canvas = document.createElement("canvas");
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
        const image = canvas.toDataURL("image/jpeg", 0.78);
        if (!cancelled) setThumbnail(image);
      } catch { /* keep the neutral placeholder if the browser blocks canvas capture */ }
    };
    video.addEventListener("loadeddata", () => {
      if (video.duration && Number.isFinite(video.duration)) {
        try { video.currentTime = Math.min(0.25, video.duration / 3); } catch { capture(); }
      } else capture();
    });
    video.addEventListener("seeked", capture, { once: true });
    video.addEventListener("error", () => { if (!cancelled) setThumbnail(""); });
    video.src = src;
    video.load();
    return () => { cancelled = true; video.removeAttribute("src"); video.load(); };
  }, [src]);
  return thumbnail
    ? <img className="profile-video-thumb" src={thumbnail} alt={alt}/>
    : <div className="profile-video-placeholder" aria-label="تحميل معاينة الفيديو"><Play fill="currentColor"/></div>;
}

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
  const [previewUrl, setPreviewUrl] = useState("");
  const [caption, setCaption] = useState("");
  const [messagesOpen, setMessagesOpen] = useState(false);
  const [messageUsername, setMessageUsername] = useState("");
  const [messagePeer, setMessagePeer] = useState(null);
  const [directMessages, setDirectMessages] = useState([]);
  const [directText, setDirectText] = useState("");
  const [commentsFor, setCommentsFor] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState("");
  const [profile, setProfile] = useState(null);
  const [profileStats, setProfileStats] = useState({ videos: 0, followers: 0, following: 0 });
  const [profileEditOpen, setProfileEditOpen] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  const [profileUsername, setProfileUsername] = useState("");
  const [liveOpen, setLiveOpen] = useState(false);
  const profileAvatarRef = useRef(null);
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, [previewUrl]);
  const videoRef = useRef(null);
  const fileRef = useRef(null);
  const cameraFileRef = useRef(null);
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
          const stats = await getProfileStats(currentUser.id);
          setProfileStats(stats);
          setDisplayName(ownProfile.display_name || "");
          setBio(ownProfile.bio || "");
          setProfileUsername(ownProfile.username || "");
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
    e.preventDefault(); if (!user) return; setBusy(true); setError("");
    try {
      const updated = await updateProfile({ userId: user.id, username: profileUsername, displayName, bio });
      setProfile(updated); setProfileEditOpen(false);
    } catch (e) { setError(e.message || "تعذر حفظ الملف الشخصي."); }
    finally { setBusy(false); }
  };
  const handleAvatarChange = async (e) => {
    const selected = e.target.files?.[0];
    if (!selected || !user) return;
    setBusy(true); setError("");
    try {
      const updated = await uploadProfileAvatar({ file: selected, userId: user.id });
      setProfile(updated);
    } catch (e) { setError(e.message || "تعذر تغيير الصورة."); }
    finally { setBusy(false); e.target.value = ""; }
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
  const openMessages = () => { if (!user) { openAuth(); return; } setMessagesOpen(true); };
  const findMessagePeer = async (e) => {
    e.preventDefault(); setBusy(true); setError("");
    try {
      const peer = await findProfileByUsername(messageUsername);
      if (peer.id === user.id) throw new Error("ما تكدر تراسل نفسك.");
      setMessagePeer(peer);
      setDirectMessages(await getDirectMessages(user.id, peer.id));
    } catch (e) { setError(e.message || "تعذر فتح المحادثة."); }
    finally { setBusy(false); }
  };
  const submitDirectMessage = async (e) => {
    e.preventDefault(); if (!user || !messagePeer || !directText.trim()) return;
    setBusy(true);
    try {
      const sent = await sendDirectMessage({ senderId: user.id, receiverId: messagePeer.id, body: directText });
      setDirectMessages(items => [...items, sent]); setDirectText("");
    } catch (e) { setError(e.message || "تعذر إرسال الرسالة."); }
    finally { setBusy(false); }
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
      <button className="upload-btn" onClick={() => { if (user) { setError(""); setUploadOpen(true); } else openAuth(); }}><PlusSquare size={18}/> رفع فيديو</button><button className="login-btn" onClick={() => user ? setLiveOpen(true) : openAuth()}><Radio size={16}/> بث مباشر</button><button className="login-btn" onClick={openMessages}><MessageCircle size={16}/> رسائل</button>
      {user ? <button className="login-btn" onClick={async () => { await signOut(); setUser(null); }}> <LogOut size={16}/> خروج</button> : <button className="login-btn" onClick={openAuth}>تسجيل الدخول</button>}
    </header>
    <div className="layout">
      <aside className={`sidebar ${mobileMenu ? "open" : ""}`}>
        <div className="side-links">{nav.map(({label, icon: Icon}) => <button key={label} className={`nav-item ${active === label ? "selected" : ""}`} onClick={() => {setActive(label); setMobileMenu(false); if(label === "رفع فيديو") user ? setUploadOpen(true) : openAuth();}}><Icon size={21}/><span>{label}</span></button>)}</div>
        <div className="side-note"><b>مجتمع الرافدين</b><p>اكتشف صناع المحتوى العراقيين وشارك لحظاتك مع الجميع.</p><small>© 2026 الرافدين تيك</small></div>
      </aside>
      <main className={`main-content ${active === "الملف الشخصي" ? "profile-view" : ""}`}>
        {active !== "الملف الشخصي" && <><div className="feed-heading"><div><span className="eyebrow">اكتشف الجديد</span><h1>{active === "الرئيسية" ? "لك" : active}</h1></div><div className="feed-tabs"><button className={`tab ${active !== "المتابَعون" ? "active" : ""}`} onClick={() => setActive("الرئيسية")}>لك</button><button className={`tab ${active === "المتابَعون" ? "active" : ""}`} onClick={() => setActive("المتابَعون")}>أتابع</button></div></div>
        {query && <p className="search-count">نتائج البحث: {filtered.length}</p>}
        {error && <p className="search-count" role="status">{error}</p>}
        {loading ? <p className="search-count">جاري تحميل الفيديوهات…</p> : shownVideos.length === 0 ? <section className="empty-state"><h2>{active === "الملف الشخصي" ? "ملفك الشخصي" : active === "المتابَعون" ? "ماكو فيديوهات من الحسابات اللي تتابعها بعد" : "ماكو نتائج"}</h2><p>{profile ? `@${profile.username} · ${profile.bio || "شارك أول فيديو إلك"}` : "سجّل الدخول أو تابع حسابات حتى تظهر الفيديوهات هنا."}</p>{active === "الملف الشخصي" && <button className="upload-btn" onClick={() => setUploadOpen(true)}>انشر أول فيديو</button>}</section> : <section className="video-stage">
          <button className="arrow prev" onClick={() => go(-1)} aria-label="الفيديو السابق">‹</button>
          <article className="video-card" onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
            {video.videoUrl ? <video ref={videoRef} className="video-image" src={video.videoUrl} playsInline loop autoPlay muted={muted} preload="auto" onLoadedData={() => videoRef.current?.play().catch(() => {})}/> : <img className="video-image" src={video.image || demoVideos[0].image} alt="معاينة الفيديو"/>}
            <div className="video-shade"/>
            <button className="sound-toggle" onClick={() => setMuted(!muted)} aria-label="الصوت">{muted ? <VolumeX/> : <Volume2/>}</button>
            <div className="video-info">
              <div className="creator"><span className="avatar">{typeof video.avatar === "string" && video.avatar.startsWith("http") ? <img src={video.avatar} alt="" /> : video.avatar}</span><div><b>@{video.user}</b><small>{video.name}</small></div><button className="follow-btn" onClick={() => handleFollow(video)}>{following[video.userId] ? "تتابعه" : "متابعة"}</button></div>
              <p>{video.caption}</p><small className="sound-line">♫ الرافدين تيك</small>
            </div>
            <div className="video-actions">
              <button onClick={() => handleLike(video)} className={liked[video.id] ? "is-liked" : ""}><Heart fill={liked[video.id] ? "currentColor" : "none"}/><b>إعجاب</b></button>
              <button onClick={() => openComments(video)}><MessageCircle/><b>تعليقات</b></button>
              <button onClick={() => setSaved(v => ({...v, [video.id]: !v[video.id]}))} className={saved[video.id] ? "is-saved" : ""}><Bookmark fill={saved[video.id] ? "currentColor" : "none"}/><b>حفظ</b></button>
              <button onClick={() => navigator.clipboard?.writeText(video.videoUrl || window.location.href)}><Share2/><b>مشاركة</b></button>
            </div>
          </article>
          <button className="arrow next" onClick={() => go(1)} aria-label="الفيديو التالي">›</button>
        </section>}
        <div className="feed-footer"><span><i/> {videos.length ? `فيديو ${Math.min(index + 1, videos.length)} من ${videos.length}` : "معاينات تجريبية"}</span><div className="dots">{shownVideos.map((v,i) => <button key={v.id} onClick={() => setIndex(i)} className={i === index ? "dot active-dot" : "dot"} aria-label={`فيديو ${i+1}`}/>)}</div></div>
        <div className="mobile-actions"><button onClick={() => go(-1)}>الفيديو السابق</button><button onClick={() => go(1)}>الفيديو التالي</button></div></>}
        {active === "الملف الشخصي" && <section className="profile-page"><div className="profile-cover"><span>الرافدين تيك</span></div><div className="profile-main"><div className="profile-avatar">{profile?.avatar_url ? <img src={profile.avatar_url} alt="صورة الحساب"/> : "🇮🇶"}</div><div className="profile-heading"><div><h2>{profile?.display_name || profile?.username || "حسابي"}</h2><p>@{profile?.username || user?.email?.split("@")[0] || "user"}</p></div><button className="profile-edit-btn" onClick={() => setProfileEditOpen(true)}>تعديل الملف الشخصي</button></div><p className="profile-bio">{profile?.bio || "أهلاً بيك بحسابي على الرافدين تيك 🇮🇶"}</p><div className="profile-stats"><span><b>{profileStats.videos}</b> فيديو</span><span><b>{profileStats.followers}</b> متابع</span><span><b>{profileStats.following}</b> يتابع</span></div><div className="profile-content-tabs"><b>▦ فيديوهاتي</b><span>المفضلة</span></div>{videos.length ? <div className="profile-video-grid">{videos.map((v,i)=><button key={v.id} onClick={()=>{setActive("الرئيسية");setIndex(i)}}><VideoThumbnail src={v.videoUrl} alt={`معاينة فيديو ${i + 1}`}/><span>▶</span></button>)}</div> : <div className="profile-empty"><Video size={34}/><b>ما نشرت أي فيديو بعد</b><span>شارك أول فيديو وخلي الناس تشوف محتواك</span><button className="upload-btn" onClick={()=>setUploadOpen(true)}>＋ نشر أول فيديو</button></div>}</div></section>}
      </main>
      <aside className="right-panel"><h3>حسابات مقترحة</h3><div className="community-card"><span>🇮🇶</span><b>صوتك يوصل</b><p>انشر إبداعك وخلي العالم يشوف جمال العراق.</p></div></aside>
    </div>

    <nav className="app-bottom-nav"><button className={active === "الرئيسية" ? "active" : ""} onClick={() => setActive("الرئيسية")}><Home/><span>الرئيسية</span></button><button className={active === "اكتشف" ? "active" : ""} onClick={() => setActive("اكتشف")}><Compass/><span>اكتشف</span></button><button className="nav-create" onClick={() => user ? setUploadOpen(true) : openAuth()}><PlusSquare/><span>نشر</span></button><button onClick={() => user ? setLiveOpen(true) : openAuth()}><Radio/><span>بث</span></button><button className={active === "المتابَعون" ? "active" : ""} onClick={() => setActive("المتابَعون")}><Users/><span>أتابع</span></button><button className={active === "الملف الشخصي" ? "active" : ""} onClick={() => user ? setActive("الملف الشخصي") : openAuth()}><UserRound/><span>حسابي</span></button></nav>

    {liveOpen && <LiveStudio user={user} onClose={() => setLiveOpen(false)}/>}
    {authOpen && <div className="modal-backdrop" onClick={() => setAuthOpen(false)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setAuthOpen(false)}><X/></button><h2>{authMode === "signup" ? "إنشاء حساب جديد" : "تسجيل الدخول"}</h2><form onSubmit={handleAuth}>
      {authMode === "signup" && <input required minLength="3" maxLength="24" value={username} onChange={e => setUsername(e.target.value)} placeholder="اسم المستخدم"/>}
      <input required type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="البريد الإلكتروني"/>
      <input required minLength="6" type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="كلمة المرور"/>
      <button className="login-btn modal-submit" disabled={busy}>{busy ? "انتظر..." : authMode === "signup" ? "إنشاء الحساب" : "دخول"}</button>
    </form>{error && <p className="auth-error" role="alert">{error}</p>}<button className="switch-auth" onClick={() => { setError(""); setAuthMode(authMode === "signup" ? "signin" : "signup"); }}>{authMode === "signup" ? "عندك حساب؟ سجل دخول" : "ما عندك حساب؟ أنشئ حساب"}</button></section></div>}

    {messagesOpen && <div className="modal-backdrop" onClick={() => setMessagesOpen(false)}><section className="modal messages-modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setMessagesOpen(false)}><X/></button><h2>الرسائل الخاصة</h2><form onSubmit={findMessagePeer}><input value={messageUsername} onChange={e => setMessageUsername(e.target.value)} placeholder="اكتب اسم المستخدم" required/><button className="login-btn modal-submit" disabled={busy}>{busy ? "انتظر..." : "فتح المحادثة"}</button></form>{messagePeer && <><h3>محادثة مع @{messagePeer.username}</h3><div className="comment-list direct-message-list">{directMessages.map(m => <p key={m.id} className={m.sender_id === user.id ? "my-message" : "their-message"}>{m.body}</p>)}{!directMessages.length && <p>ابدأ المحادثة برسالة ترحيب.</p>}</div><form onSubmit={submitDirectMessage}><textarea value={directText} onChange={e => setDirectText(e.target.value)} maxLength="2000" placeholder="اكتب رسالتك…"/><button className="login-btn modal-submit" disabled={busy || !directText.trim()}><Send size={16}/> إرسال</button></form></>}</section></div>}

    {uploadOpen && <div className="modal-backdrop" onClick={() => setUploadOpen(false)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setUploadOpen(false)}><X/></button><h2><Video size={20}/> نشر فيديو</h2><form onSubmit={handleUpload}>
      <div className="upload-choice"><button type="button" className="upload-dropzone" onClick={() => fileRef.current?.click()}><Video size={32}/><b>اختيار فيديو من الجهاز</b><span>MP4 أو MOV أو WebM · حتى 100 ميغابايت</span></button><button type="button" className="upload-dropzone camera-choice" onClick={() => cameraFileRef.current?.click()}><Video size={32}/><b>تصوير فيديو بالكاميرا</b><span>افتح كاميرا الهاتف لتسجيل فيديو جديد</span></button></div><input ref={fileRef} className="hidden-file-input" type="file" accept="video/mp4,video/webm,video/quicktime" onChange={e => { const selected=e.target.files?.[0] || null; setFile(selected); setPreviewUrl(selected ? URL.createObjectURL(selected) : ""); }}/><input ref={cameraFileRef} className="hidden-file-input" type="file" accept="video/*" capture="environment" onChange={e => { const selected=e.target.files?.[0] || null; setFile(selected); setPreviewUrl(selected ? URL.createObjectURL(selected) : ""); }}/>{file && <p className="selected-video-name">الفيديو المختار: {file.name}</p>}{previewUrl && <div className="upload-preview"><video src={previewUrl} controls playsInline/><span>معاينة قبل النشر</span></div>}{error && <p className="auth-error" role="alert">{error}</p>}
      <textarea required maxLength="2200" value={caption} onChange={e => setCaption(e.target.value)} placeholder="اكتب وصف الفيديو…"/>
      <button className="login-btn modal-submit" disabled={busy || !file}>{busy ? "جاري الرفع..." : "نشر الفيديو"}</button>
    </form></section></div>}

    {profileEditOpen && <div className="modal-backdrop" onClick={() => setProfileEditOpen(false)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setProfileEditOpen(false)}><X/></button><h2>تعديل الملف الشخصي</h2><form onSubmit={saveProfile}><label className="avatar-upload-control">صورة الحساب<input ref={profileAvatarRef} type="file" accept="image/*" onChange={handleAvatarChange}/></label><input required minLength="3" maxLength="24" value={profileUsername} onChange={e => setProfileUsername(e.target.value)} placeholder="اسم المستخدم (إنكليزي)"/><input maxLength="60" value={displayName} onChange={e => setDisplayName(e.target.value)} placeholder="الاسم الظاهر"/><textarea maxLength="160" value={bio} onChange={e => setBio(e.target.value)} placeholder="نبذة عنك"/><button className="login-btn modal-submit" disabled={busy}>{busy ? "حفظ..." : "حفظ التغييرات"}</button></form></section></div>}
    
    {commentsFor && <div className="modal-backdrop" onClick={() => setCommentsFor(null)}><section className="modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setCommentsFor(null)}><X/></button><h2>التعليقات</h2><div className="comment-list">{comments.map(c => <article className="comment-item" key={c.id}><span className="comment-avatar">{c.profiles?.avatar_url ? <img src={c.profiles.avatar_url} alt=""/> : "🇮🇶"}</span><div className="comment-content"><div className="comment-heading"><b>{c.profiles?.display_name || c.profiles?.username || "مستخدم"}</b><small>@{c.profiles?.username || "user"}</small></div><p>{c.body}</p><time>{c.created_at ? new Date(c.created_at).toLocaleString("ar-IQ") : ""}</time></div></article>)}{!comments.length && <p>لا توجد تعليقات بعد.</p>}</div><form onSubmit={sendComment}><input value={commentText} onChange={e => setCommentText(e.target.value)} placeholder="اكتب تعليقاً…"/><button className="login-btn modal-submit" disabled={!commentText.trim()}><Send size={16}/> إرسال</button></form></section></div>}
  </div>;
}

export default App;
