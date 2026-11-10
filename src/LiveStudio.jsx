import { useEffect, useRef, useState } from "react";
import { Room, RoomEvent, createLocalTracks } from "livekit-client";
import { Radio, Video, VideoOff, Mic, MicOff, PhoneOff } from "lucide-react";

export default function LiveStudio({ user, onClose }) {
  const [roomName, setRoomName] = useState("");
  const [status, setStatus] = useState("idle");
  const [message, setMessage] = useState("");
  const [cameraOn, setCameraOn] = useState(true);
  const [micOn, setMicOn] = useState(true);
  const roomRef = useRef(null);
  const videoRef = useRef(null);

  const startLive = async (event) => {
    event.preventDefault();
    const serverUrl = import.meta.env.VITE_LIVEKIT_URL;
    const tokenEndpoint = import.meta.env.VITE_LIVEKIT_TOKEN_ENDPOINT;
    if (!serverUrl || !tokenEndpoint) {
      setMessage("البث يحتاج إعداد خدمة LiveKit أولاً. أضف VITE_LIVEKIT_URL و VITE_LIVEKIT_TOKEN_ENDPOINT إلى إعدادات المشروع.");
      return;
    }
    if (!roomName.trim()) return setMessage("اكتب اسم غرفة البث.");
    setStatus("connecting"); setMessage("");
    try {
      const response = await fetch(tokenEndpoint, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ room: roomName.trim(), identity: user.id, name: user.user_metadata?.username || user.email })
      });
      if (!response.ok) throw new Error("تعذر الحصول على تصريح البث من الخادم.");
      const payload = await response.json();
      if (!payload.token) throw new Error("خادم التصاريح لم يرجع رمز دخول صالحاً.");
      const room = new Room();
      roomRef.current = room;
      room.on(RoomEvent.Disconnected, () => { setStatus("idle"); });
      await room.connect(serverUrl, payload.token);
      const tracks = await createLocalTracks({ audio: true, video: true });
      for (const track of tracks) await room.localParticipant.publishTrack(track);
      const cameraTrack = tracks.find(track => track.kind === "video");
      if (cameraTrack && videoRef.current) cameraTrack.attach(videoRef.current);
      setStatus("live");
    } catch (error) {
      setMessage(error.message || "تعذر بدء البث.");
      setStatus("idle");
      await roomRef.current?.disconnect();
      roomRef.current = null;
    }
  };

  const stopLive = async () => {
    await roomRef.current?.disconnect();
    roomRef.current = null;
    setStatus("idle");
  };

  useEffect(() => () => { roomRef.current?.disconnect(); }, []);

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
        <div className="live-preview"><video ref={videoRef} autoPlay muted playsInline/><span className="live-indicator">● مباشر</span></div>
        <p className="live-room-name">غرفة البث: {roomName}</p>
        <div className="live-controls">
          <button type="button" onClick={toggleCamera}>{cameraOn ? <Video/> : <VideoOff/>} الكاميرا</button>
          <button type="button" onClick={toggleMic}>{micOn ? <Mic/> : <MicOff/>} المايك</button>
          <button type="button" className="live-stop" onClick={stopLive}><PhoneOff/> إنهاء البث</button>
        </div>
      </> : <form onSubmit={startLive}>
        <p className="live-description">ابدأ بثاً بالكاميرا والمايك وتواصل مباشرةً مع جمهورك.</p>
        <input value={roomName} onChange={e => setRoomName(e.target.value)} placeholder="اسم غرفة البث" required maxLength="60"/>
        {message && <p className="auth-error" role="alert">{message}</p>}
        <button className="login-btn modal-submit" disabled={status === "connecting"}>{status === "connecting" ? "جاري الاتصال..." : "بدء البث المباشر"}</button>
        <small className="live-setup-note">يتطلب إعداد LiveKit وخادم آمن لإصدار رموز الدخول. لا تضع المفتاح السري داخل التطبيق.</small>
      </form>}
    </section>
  </div>;
}
