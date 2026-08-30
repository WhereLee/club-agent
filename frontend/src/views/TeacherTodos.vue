<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTodos } from '../api/teacher'
import { approveMember, rejectMember } from '../api/club'

const router = useRouter()
const todos = ref([])
const loading = ref(false)

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    const res = await getTodos()
    todos.value = res.data
  } finally {
    loading.value = false
  }
}

async function onApprove(todo) {
  try {
    await approveMember(todo.clubId, todo.membershipId)
    ElMessage.success(`已通过 ${todo.nickname} 的申请`)
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onReject(todo) {
  try {
    await ElMessageBox.confirm(`确定拒绝 ${todo.nickname} 加入「${todo.clubName}」的申请吗？`, '拒绝申请', { type: 'warning' })
  } catch (e) { return }
  try {
    await rejectMember(todo.clubId, todo.membershipId)
    ElMessage.success('已拒绝')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h3>待办审批</h3>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-table :data="todos" v-loading="loading" stripe>
      <el-table-column prop="clubName" label="社团" min-width="140" />
      <el-table-column prop="nickname" label="申请人" min-width="120" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column label="申请时间" width="170">
        <template #default="{ row }">{{ formatTime(row.appliedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" type="success" plain @click="onApprove(row)">通过</el-button>
          <el-button size="small" type="danger" plain @click="onReject(row)">拒绝</el-button>
          <el-button size="small" @click="router.push(`/teacher/clubs/${row.clubId}`)">管理社团</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!todos.length && !loading" description="暂无待审批申请" />
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
