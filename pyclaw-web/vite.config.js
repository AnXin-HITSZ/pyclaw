import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/backend-healthz": {
        target: "http://localhost:8080",
        rewrite: () => "/healthz"
      }
    }
  }
});
