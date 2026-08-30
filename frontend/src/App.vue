<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isPublic = () => route.meta.public === true

// 站内消息未读数（概念作废/通过通知）
const { unreadCount } = storeToRefs(userStore)
onMounted(() => userStore.refreshUnread())
watch(() => route.path, () => userStore.refreshUnread())

// 老师管理台路由：导航切换为老师导航（社团管理/待办审批/日志查看）
const isTeacherRoute = computed(() => route.path.startsWith('/teacher'))
// 社团管理子页（/teacher/clubs/:clubId）时导航高亮到列表项
const activeMenu = computed(() => {
  if (route.path.startsWith('/teacher/clubs/')) return '/teacher/clubs'
  return route.path
})

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
      <el-menu v-if="isTeacherRoute" mode="horizontal" :default-active="activeMenu" router class="nav-menu" :ellipsis="false">
        <el-menu-item index="/teacher/clubs">社团管理</el-menu-item>
        <el-menu-item index="/teacher/todos">待办审批</el-menu-item>
        <el-menu-item index="/teacher/logs">日志查看</el-menu-item>
      </el-menu>
      <el-menu v-else mode="horizontal" :default-active="route.path" router class="nav-menu" :ellipsis="false">
        <el-menu-item index="/clubs">社团列表</el-menu-item>
        <el-menu-item index="/my-clubs">我的社团</el-menu-item>
      </el-menu>
      <div class="user-area" v-if="userStore.token">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="msg-badge" @click="router.push('/messages')">
          <el-button size="small" circle>
            <el-icon><Bell /></el-icon>
          </el-button>
        </el-badge>
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
.nav-menu { flex: 1; margin-left: 24px; border-bottom: none; }
.user-area { display: flex; align-items: center; }
.user-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #606266; }
.app-main { max-width: 960px; margin: 24px auto; padding: 0 16px; }
</style>
