# AppSelect Migration Report

**Date:** 2026-07-18
**Branch:** main
**Component:** `pyclaw-web/src/components/ui/AppSelect.vue`

## Summary

Replaced all 13 native `<select>` elements across 7 view files with the new `AppSelect` component, which provides a deep-themed dropdown with keyboard navigation and teleported panel.

## Files Changed

| # | File | Selects Replaced | Details |
|---|------|-----------------|---------|
| 1 | `src/views/admin/ChannelPage.vue` | 1 | `form.channelType` -- static options (微信/飞书), `:disabled="!!editing"`, `required` |
| 2 | `src/views/AgentConfigPage.vue` | 2 | `form.providerId` (dynamic from `providers`), `form.toolProfile` (static: minimal/readonly/coding/messaging/full) |
| 3 | `src/views/ClawChatPage.vue` | 1 | `selectedRoleKey` -- dynamic from `roles`, with `.role-select` class for header auto-width |
| 4 | `src/views/ClawDetailPage.vue` | 2 | `editForm.defaultAgentId` (dynamic from `allAgents`), `roleForm.agentId` (required, `@change="syncSelectedAgent"`) |
| 5 | `src/views/ClawListPage.vue` | 2 | `createForm.defaultAgentId` (dynamic from `agents`), `roleForm.agentId` (required, `@change="syncSelectedAgent"`) |
| 6 | `src/views/ProviderPage.vue` | 2 | `form.providerType` (openai/openai-compatible), `form.apiMode` (chat_completions/responses) -- both `required` |
| 7 | `src/views/SecretPage.vue` | 3 | `createForm.type` (Provider/飞书/自定义), `createForm.scope` (用户级/Claw级), `createForm.clawId` (dynamic from `claws`, `required`) |

## Changes Per File

### 1. ChannelPage.vue
- Added import: `import AppSelect from "../../components/ui/AppSelect.vue";`
- Replaced native `<select>` with `<AppSelect>` using inline `:options` array

### 2. AgentConfigPage.vue
- Added import: `import AppSelect from "../components/ui/AppSelect.vue";`
- `form.providerId`: options built with `[{value:'',label:'默认'}, ...providers.map(p => ({value:p.id, label:p.name + ' (' + p.model + ')'}))]`
- `form.toolProfile`: static options array

### 3. ClawChatPage.vue
- Added import: `import AppSelect from "../components/ui/AppSelect.vue";`
- `selectedRoleKey`: options from `roles.map(r => ({value:r.roleKey, label: r.defaultRole ? r.displayName + ' · 默认' : r.displayName}))`
- Updated `.role-select` CSS to `width: auto; min-width: 160px;` (removed native select styling now handled by AppSelect)

### 4. ClawDetailPage.vue
- Added import: `import AppSelect from "../components/ui/AppSelect.vue";`
- `editForm.defaultAgentId`: `[{value:'',label:'不选择'}, ...allAgents.map(a => ({value:a.id, label:a.name}))]`
- `roleForm.agentId`: `[{value:'',label:'请选择 Agent'}, ...allAgents.map(a => ({value:a.id, label:a.name + ' (' + a.agentKey + ')'}))]`, retains `required` and `@change="syncSelectedAgent"`

### 5. ClawListPage.vue
- Added import: `import AppSelect from "../components/ui/AppSelect.vue";`
- `createForm.defaultAgentId`: `[{value:'',label:'不选择'}, ...agents.map(a => ({value:a.id, label:a.name + ' (' + a.agentKey + ')'}))]`
- `roleForm.agentId`: `[{value:'',label:'请选择 Agent'}, ...agents.map(a => ({value:a.id, label:a.name + ' (' + a.agentKey + ')'}))]`, retains `required` and `@change="syncSelectedAgent"`

### 6. ProviderPage.vue
- Added import: `import AppSelect from "../components/ui/AppSelect.vue";`
- `form.providerType`: `[{value:'openai',label:'openai'},{value:'openai-compatible',label:'openai-compatible'}]`, `required`
- `form.apiMode`: `[{value:'chat_completions',label:'chat_completions'},{value:'responses',label:'responses'}]`, `required`

### 7. SecretPage.vue
- Added import: `import AppSelect from "../components/ui/AppSelect.vue";`
- `createForm.type`: `[{value:'provider',label:'Provider'},{value:'feishu',label:'飞书'},{value:'custom',label:'自定义'}]`
- `createForm.scope`: `[{value:'user',label:'用户级'},{value:'claw',label:'Claw 级'}]`
- `createForm.clawId`: `[{value:'',label:'选择 Claw'}, ...claws.map(c => ({value:c.id, label:c.name}))]`, `required`

## Build Result

```
vite v6.4.3 building for production...
✓ 90 modules transformed.
✓ built in 1.00s
```

Build passed with zero errors. All 7 modified view pages compiled successfully. The `AppSelect` component CSS chunk (`AppSelect-ZoL_B7Av.css`, 2.40 kB) and JS chunk (`AppSelect-Bs6Xp8Ak.js`, 3.52 kB) are included in the output.

## Verification

- `grep '<select' src/views/` returned **zero matches** -- all 13 native selects fully replaced
- All `v-model` bindings preserved (variable names unchanged)
- All `:disabled`, `required`, `@change` attributes preserved with identical behavior
- `AppSelect` import added to each file's `<script setup>` block
- No business logic, API calls, or form submission logic modified
- ClawChatPage `.role-select` CSS updated to maintain header layout compatibility

## Concerns

None. Migration is clean and complete.
