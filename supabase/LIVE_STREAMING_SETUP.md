# Live streaming setup (LiveKit)

The Android app's live streaming feature uses LiveKit for realtime audio/video. GitHub stores the source code; it does not provide the media relay itself.

## Server configuration

Create a LiveKit Cloud project (or use a compatible self-hosted LiveKit server), then add these secrets to Supabase project gxzbgwgovtejjsmkouuv under Edge Functions → Secrets:

- LIVEKIT_URL: the project's WebSocket URL (usually starts with wss://)
- LIVEKIT_API_KEY: LiveKit API key
- LIVEKIT_API_SECRET: LiveKit API secret

Never commit these values to GitHub or put them in the Android app.

## Token endpoint

Deploy supabase/functions/livekit-token/index.ts as the Supabase Edge Function named livekit-token, with JWT verification enabled. The function checks the caller's Supabase session and returns a short-lived LiveKit token for the requested room. It accepts:
- { "roomName": "live_123", "role": "host" } for a broadcaster
- { "roomName": "live_123", "role": "viewer" } for a viewer

Only authenticated users receive tokens. Room names are restricted to safe characters. Host tokens can publish; viewer tokens are subscribe-only.

## Remaining client work

The native Android client must request a token from this endpoint, connect using the LiveKit Android SDK, publish camera/microphone tracks for hosts, and render subscribed tracks for viewers. Do not consider live streaming enabled until the Android client flow is implemented and tested on two devices.
