import { reactive, computed } from "vue";
import { api } from "../api/client.js";

const state = reactive({
  user: JSON.parse(localStorage.getItem("pyclaw.user") || "null"),
  token: localStorage.getItem("pyclaw.token") || null,
  loading: false,
});

export function useAuth() {
  const isLoggedIn = computed(() => !!state.token && !!state.user);
  const isAdmin = computed(() => {
    return state.user?.authorities?.includes("user:manage") ?? false;
  });
  const authorities = computed(() => state.user?.authorities ?? []);

  function hasAuthority(auth) {
    return authorities.value.includes(auth);
  }

  async function login(username, password) {
    state.loading = true;
    try {
      const res = await api.post("/api/auth/login", { username, password });
      state.token = res.accessToken;
      localStorage.setItem("pyclaw.token", res.accessToken);
      await fetchMe();
      return true;
    } finally {
      state.loading = false;
    }
  }

  async function register(username, password, displayName) {
    state.loading = true;
    try {
      const res = await api.post("/api/auth/register", { username, password, displayName });
      state.token = res.accessToken;
      localStorage.setItem("pyclaw.token", res.accessToken);
      await fetchMe();
      return true;
    } finally {
      state.loading = false;
    }
  }

  async function fetchMe() {
    try {
      const user = await api.get("/api/auth/me");
      state.user = user;
      localStorage.setItem("pyclaw.user", JSON.stringify(user));
    } catch {
      state.user = null;
      state.token = null;
      localStorage.removeItem("pyclaw.token");
      localStorage.removeItem("pyclaw.user");
    }
  }

  function logout() {
    state.user = null;
    state.token = null;
    localStorage.removeItem("pyclaw.token");
    localStorage.removeItem("pyclaw.user");
  }

  async function checkAuth() {
    if (!state.token) return false;
    if (state.user) return true;
    await fetchMe();
    return !!state.user;
  }

  return {
    user: computed(() => state.user),
    isLoggedIn,
    isAdmin,
    authorities,
    loading: computed(() => state.loading),
    hasAuthority,
    login,
    register,
    logout,
    fetchMe,
    checkAuth,
  };
}
