<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isPublic = () => route.meta.public === true

async function onCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    await userStore.logout()
    router.push('/login')
  }
}
</script>

<template>
  <div class="app-shell">
    <header v-if="!isPublic()" class="app-header">
      <div class="brand">社团管理 Agent</div>
      <div class="user-area" v-if="userStore.token">
        <el-dropdown @command="onCommand">
          <span class="user-name">
            <el-avatar :size="28" :src="userStore.userInfo?.avatarUrl" />
            {{ userStore.userInfo?.nickname || userStore.userInfo?.username }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #f5f7fa; }
.app-header {
  display: flex; justify-content: space-between; align-items: center;
  height: 56px; padding: 0 24px;
  background: #fff; box-shadow: 0 1px 4px rgba(0, 21, 41, .08);
}
.brand { font-size: 17px; font-weight: 600; color: #303133; }
.user-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #606266; }
.app-main { max-width: 960px; margin: 24px auto; padding: 0 16px; }
</style>
