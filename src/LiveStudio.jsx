import { useEffect, useRef, useState } from "react";
import { Room, RoomEvent } from "livekit-client";
import { Radio, Video, VideoOff, Mic, MicOff, PhoneOff, Eye } from "lucide-react";
import { supabase } from "./lib/supabase.js";

export default function LiveStudio({ user, onClose }) {
  const [roomName, setRoomName] = useState("");
  const [mode, setMode] = useState("host");
  const [status, setStatus] = useState("idle");
  const [message, setMessage] = useState("");
  const [cameraOn, setCameraOn] = useState(true);
  const [micOn, setMicOn] = useState(true);
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
    const serverUrl = import.meta.env.VITE_LIVEKIT_URL;
    const tokenEndpoint = import.meta.env.VITE_LIVEKIT_TOKEN_ENDPOINT;
    if (!serverUrl || !tokenEndpoint) {
      setMessage("البث يحتاج إعداد LiveKit أولاً. أضف VITE_LIVEKIT_URL و VITE_LIVEKIT_TOKEN_ENDPOINT إلى إعدادات المشروع.");
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
      if (!payload.token) throw new Error("خادم التصاريح لم يرجع رمز دخول صالحاً.");

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
      await room.connect(serverUrl, payload.token);

      if (mode === "host") {
        const tracks = await room.localParticipant.createTracks({ audio: true, video: true });
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
    await roomRef.current?.disconnect();
    roomRef.current = null;
    clearMedia();
    setStatus("idle");
  };

  useEffect(() => () => {
    roomRef.current?.disconnect();
    clearMedia();
  }, []);

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
        <div className="live-preview"><video ref={videoRef} autoPlay playsInline muted={mode === "host"}/><span className="live-indicator">● مباشر</span></div>
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
