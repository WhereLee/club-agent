<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyClubs, resignClub } from '../api/club'

const router = useRouter()
const clubs = ref([])
const loading = ref(false)

const statusText = (s) => ({ 0: '申请中', 1: '已加入', 2: '已拒绝' }[s] ?? '')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] ?? 'info')
const isManagement = (c) => ['president', 'vice_president'].includes(c.roleCode)

async function load() {
  loading.value = true
  try {
    clubs.value = (await getMyClubs()).data
  } finally {
    loading.value = false
  }
}

async function onResign(club) {
  const mgmt = isManagement(club)
  const tip = mgmt
    ? `确定从「${club.clubName}」离职吗？职务将空出，任职记录保留为第X任。`
    : `确定退出「${club.clubName}」吗？`
  try {
    await ElMessageBox.confirm(tip, mgmt ? '离职确认' : '退出确认', { type: 'warning' })
  } catch (e) { return }
  try {
    await resignClub(club.clubId)
    ElMessage.success(mgmt ? '已离职' : '已退出社团')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<template>
  <div>
    <h3 class="page-title">我的社团</h3>
    <div v-loading="loading">
      <el-empty v-if="!clubs.length" description="还没有加入任何社团，去社团列表看看吧">
        <el-button type="primary" @click="router.push('/clubs')">浏览社团</el-button>
      </el-empty>
      <el-row :gutter="16">
        <el-col v-for="c in clubs" :key="c.clubId" :span="12" class="club-item">
          <el-card shadow="hover" @click="router.push(`/clubs/${c.clubId}`)" class="club-card">
            <div class="club-head">
              <span class="club-name">{{ c.clubName }}</span>
              <el-tag :type="statusType(c.status)" size="small">{{ statusText(c.status) }}</el-tag>
            </div>
            <div class="club-meta">
              <span>角色：{{ c.roleName }}</span>
              <span>指导老师：{{ c.teacherName }}</span>
            </div>
            <div class="club-foot">
              <el-button v-if="isManagement(c)" size="small" type="danger" plain @click.stop="onResign(c)">离职</el-button>
              <el-button v-else-if="c.status === 1 && c.roleCode !== 'teacher'" size="small" type="danger" plain @click.stop="onResign(c)">退出</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.page-title { margin-bottom: 16px; }
.club-item { margin-bottom: 16px; }
.club-card { cursor: pointer; }
.club-head { display: flex; justify-content: space-between; align-items: center; }
.club-name { font-size: 15px; font-weight: 600; color: #303133; }
.club-meta { margin-top: 10px; display: flex; gap: 20px; color: #909399; font-size: 13px; }
.club-foot { margin-top: 12px; text-align: right; }
</style>
