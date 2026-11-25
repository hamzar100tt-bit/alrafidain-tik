import { useEffect, useRef, useState } from "react";
import { Room, RoomEvent, createLocalTracks } from "livekit-client";
import { Radio, Video, VideoOff, Mic, MicOff, PhoneOff, Eye } from "lucide-react";
import { supabase } from "./lib/supabase.js";

const liveBackgrounds = [
  { id: "midnight", label: "نيون أزرق", value: "radial-gradient(circle at 50% 20%, #153a73 0%, #090d20 58%, #05060c 100%)" },
  { id: "purple", label: "بنفسجي", value: "radial-gradient(circle at 50% 15%, #592080 0%, #1c1036 55%, #090711 100%)" },
  { id: "gold", label: "ذهبي", value: "radial-gradient(circle at 50% 15%, #76501b 0%, #21170c 55%, #080706 100%)" },
  { id: "rose", label: "وردي", value: "radial-gradient(circle at 50% 15%, #782b50 0%, #26101f 55%, #09070b 100%)" },
  { id: "teal", label: "فيروزي", value: "radial-gradient(circle at 50% 15%, #145b63 0%, #0d252d 55%, #05090d 100%)" },
  { id: "plain", label: "أسود", value: "linear-gradient(145deg, #17171d, #050506)" }
];

export default function LiveStudio({ user, onClose }) {
  const [roomName, setRoomName] = useState("");
  const [mode, setMode] = useState("host");
  const [status, setStatus] = useState("idle");
  const [message, setMessage] = useState("");
  const [cameraOn, setCameraOn] = useState(true);
  const [micOn, setMicOn] = useState(true);
  const [selectedBackground, setSelectedBackground] = useState("midnight");
  const roomRef = useRef(null);
  const videoRef = useRef(null);
  const audioElementsRef = useRef([]);

  const clearMedia = () => {
    audioElementsRef.current.forEach(element => {
      element.pause();
      element.srcObject = null;
      element.remove();
    });
    audioElementsRef.current = [];
  };

  const connectRoom = async (event) => {
    event.preventDefault();
    const configuredServerUrl = import.meta.env.VITE_LIVEKIT_URL;
    const tokenEndpoint = import.meta.env.VITE_LIVEKIT_TOKEN_ENDPOINT;
    if (!tokenEndpoint) {
      setMessage("البث يحتاج إعداد رابط خدمة التصاريح VITE_LIVEKIT_TOKEN_ENDPOINT.");
      return;
    }
    const cleanRoom = roomName.trim();
    if (cleanRoom.length < 3) return setMessage("اسم الغرفة لازم يكون 3 أحرف على الأقل.");
    setStatus("connecting"); setMessage("");
    try {
      const { data: sessionData, error: sessionError } = await supabase.auth.getSession();
      if (sessionError || !sessionData.session?.access_token) throw new Error("انتهت جلسة الدخول. سجّل الدخول مرة ثانية.");
      const response = await fetch(tokenEndpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + sessionData.session.access_token
        },
        body: JSON.stringify({ room: cleanRoom, role: mode })
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.error || "تعذر الحصول على تصريح الدخول.");
      const liveToken = payload.participantToken || payload.token;
      const serverUrl = payload.serverUrl || configuredServerUrl;
      if (!serverUrl) throw new Error("خادم البث لم يرجع رابط LiveKit.");
      if (!liveToken) throw new Error("خادم التصاريح لم يرجع رمز دخول صالحاً.");

      const room = new Room();
      roomRef.current = room;
      room.on(RoomEvent.TrackSubscribed, (track) => {
        if (track.kind === "video" && videoRef.current) {
          track.attach(videoRef.current);
        } else if (track.kind === "audio") {
          const element = track.attach();
          element.autoplay = true;
          document.body.appendChild(element);
          audioElementsRef.current.push(element);
        }
      });
      room.on(RoomEvent.Disconnected, () => {
        clearMedia();
        setStatus("idle");
      });
      await room.connect(serverUrl, liveToken);

      if (mode === "host") {
        const tracks = await createLocalTracks({ audio: true, video: true });
        for (const track of tracks) await room.localParticipant.publishTrack(track);
        const cameraTrack = tracks.find(track => track.kind === "video");
        if (cameraTrack && videoRef.current) cameraTrack.attach(videoRef.current);
      }
      setStatus("live");
    } catch (error) {
      setMessage(error.message || "تعذر الاتصال بالبث.");
      setStatus("idle");
      await roomRef.current?.disconnect();
      roomRef.current = null;
      clearMedia();
    }
  };

  const stopLive = async () => {
    if (mode === "host") {
      try {
        const tokenEndpoint = import.meta.env.VITE_LIVEKIT_TOKEN_ENDPOINT;
        const { data } = await supabase.auth.getSession();
        if (tokenEndpoint && data.session?.access_token) {
          const response = await fetch(tokenEndpoint, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "Authorization": "Bearer " + data.session.access_token
            },
            body: JSON.stringify({ room: roomName.trim(), action: "end" })
          });
          const payload = await response.json().catch(() => ({}));
          if (!response.ok) setMessage(payload.error || "انقطع الاتصال، لكن تعذر إنهاء الغرفة على الخادم.");
        }
      } catch {
        setMessage("انقطع الاتصال، لكن تعذر تأكيد إنهاء الغرفة على الخادم.");
      }
    }
    await roomRef.current?.disconnect();
    roomRef.current = null;
    clearMedia();
    setStatus("idle");
  };

  useEffect(() => () => {
    roomRef.current?.disconnect();
    clearMedia();
  }, []);

  useEffect(() => {
    if (status !== "live" || mode !== "viewer" || !videoRef.current || !roomRef.current) return;
    for (const participant of roomRef.current.remoteParticipants.values()) {
      for (const publication of participant.trackPublications.values()) {
        if (publication.track?.kind === "video") publication.track.attach(videoRef.current);
      }
    }
  }, [status, mode]);

  const toggleCamera = async () => {
    const next = !cameraOn;
    await roomRef.current?.localParticipant.setCameraEnabled(next);
    setCameraOn(next);
  };
  const toggleMic = async () => {
    const next = !micOn;
    await roomRef.current?.localParticipant.setMicrophoneEnabled(next);
    setMicOn(next);
  };

  return <div className="modal-backdrop live-backdrop" onClick={onClose}>
    <section className="modal live-modal" onClick={e => e.stopPropagation()}>
      <button className="modal-close" onClick={onClose}>×</button>
      <h2><Radio size={21}/> البث المباشر</h2>
      {status === "live" ? <>
        <div className="live-preview" style={{ background: liveBackgrounds.find(item => item.id === selectedBackground)?.value || liveBackgrounds[0].value, minHeight: 260, borderRadius: 18, overflow: "hidden", position: "relative" }}><video ref={videoRef} autoPlay playsInline muted={mode === "host"}/><span className="live-indicator">● مباشر</span></div>
        <div className="live-background-picker" aria-label="اختيار خلفية البث" style={{ display: "grid", gridTemplateColumns: "repeat(3,minmax(0,1fr))", gap: 9, margin: "14px 0" }}>
          {liveBackgrounds.map(item => <button key={item.id} type="button" onClick={() => setSelectedBackground(item.id)} aria-pressed={selectedBackground === item.id} style={{ minHeight: 58, borderRadius: 12, border: selectedBackground === item.id ? "2px solid #ff3157" : "1px solid #555", background: item.value, color: "#fff", fontWeight: 800, boxShadow: selectedBackground === item.id ? "0 0 0 2px #ff315755" : "none" }}>{item.label}{selectedBackground === item.id ? " ✓" : ""}</button>)}
        </div>
        <p className="live-room-name">{mode === "host" ? "أنت تبث الآن في:" : "تشاهد البث في:"} {roomName}</p>
        <div className="live-controls">
          {mode === "host" && <>
            <button type="button" onClick={toggleCamera}>{cameraOn ? <Video/> : <VideoOff/>} الكاميرا</button>
            <button type="button" onClick={toggleMic}>{micOn ? <Mic/> : <MicOff/>} المايك</button>
          </>}
          <button type="button" className="live-stop" onClick={stopLive}><PhoneOff/> {mode === "host" ? "إنهاء البث" : "مغادرة البث"}</button>
        </div>
      </> : <form onSubmit={connectRoom}>
        <p className="live-description">ابدأ بثاً مباشراً أو ادخل إلى غرفة بث موجودة.</p>
        <div className="live-mode-choice">
          <button type="button" className={mode === "host" ? "selected" : ""} onClick={() => {setMode("host");setMessage("");}}><Video size={17}/> ابدأ بث</button>
          <button type="button" className={mode === "viewer" ? "selected" : ""} onClick={() => {setMode("viewer");setMessage("");}}><Eye size={17}/> مشاهدة بث</button>
        </div>
        <input value={roomName} onChange={e => setRoomName(e.target.value)} placeholder="اسم غرفة البث" required maxLength="60"/>
        {message && <p className="auth-error" role="alert">{message}</p>}
        <button className="login-btn modal-submit" disabled={status === "connecting"}>{status === "connecting" ? "جاري الاتصال..." : mode === "host" ? "بدء البث المباشر" : "دخول ومشاهدة البث"}</button>
        <small className="live-setup-note">يلزم إعداد LiveKit وخادم آمن لإصدار رموز الدخول. لا تضع المفتاح السري داخل التطبيق.</small>
      </form>}
    </section>
  </div>;
}
