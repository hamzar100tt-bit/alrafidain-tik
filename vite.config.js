import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // GitHub Pages uses a repository path; the Android APK loads assets from the app root.
  base: process.env.VITE_BASE_PATH || "/alrafidain-tik/",
});
