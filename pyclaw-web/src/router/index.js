import { createRouter, createWebHistory } from "vue-router";
import { api } from "../api/client.js";

const routes = [
  {
    path: "/",
    name: "welcome",
    component: () => import("../views/WelcomePage.vue"),
  },
  {
    path: "/login",
    name: "login",
    component: () => import("../views/LoginPage.vue"),
  },
  {
    path: "/register",
    name: "register",
    component: () => import("../views/RegisterPage.vue"),
  },
  {
    path: "/workspace",
    component: () => import("../views/WorkspaceLayout.vue"),
    meta: { requiresAuth: true },
    children: [
      {
        path: "",
        redirect: "/workspace/claws",
      },
      {
        path: "claws",
        name: "claws",
        component: () => import("../views/ClawListPage.vue"),
      },
      {
        path: "claws/:id",
        name: "claw-detail",
        component: () => import("../views/ClawDetailPage.vue"),
        props: true,
      },
      {
        path: "agents",
        name: "agents",
        component: () => import("../views/AgentConfigPage.vue"),
      },
      {
        path: "providers",
        name: "providers",
        component: () => import("../views/ProviderPage.vue"),
      },
      {
        path: "playground",
        name: "playground",
        component: () => import("../views/PlaygroundPage.vue"),
      },
      {
        path: "tools",
        name: "tools",
        component: () => import("../views/ToolCatalogPage.vue"),
      },
      {
        path: "tokens",
        name: "tokens",
        component: () => import("../views/TokenPage.vue"),
      },
      {
        path: "pods",
        name: "pods",
        component: () => import("../views/PodStatusPage.vue"),
      },
      {
        path: "admin/channels",
        name: "admin-channels",
        component: () => import("../views/admin/ChannelPage.vue"),
      },
      {
        path: "admin/users",
        name: "admin-users",
        component: () => import("../views/admin/UserManagePage.vue"),
      },
      {
        path: "admin/audit",
        name: "admin-audit",
        component: () => import("../views/admin/AuditLogPage.vue"),
      },
      {
        path: "admin/usage",
        name: "admin-usage",
        component: () => import("../views/admin/UsagePage.vue"),
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to, from, next) => {
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem("pyclaw.token");
    if (!token) {
      return next("/login");
    }
    const user = JSON.parse(localStorage.getItem("pyclaw.user") || "null");
    if (!user) {
      try {
        const me = await api.get("/api/auth/me");
        localStorage.setItem("pyclaw.user", JSON.stringify(me));
      } catch {
        localStorage.removeItem("pyclaw.token");
        return next("/login");
      }
    }
  }
  next();
});

export default router;
