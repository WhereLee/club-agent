<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClubDetail, getMembers, applyClub, approveMember, rejectMember, appointMember, resignClub } from '../api/club'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId

const detail = ref(null)
const members = ref([])
const loading = ref(false)

// 我的身份派生
const isTeacher = computed(() => detail.value?.myRoleCode === 'teacher')
const canApprove = computed(() => ['teacher', 'president', 'vice_president'].includes(detail.value?.myRoleCode))
const canAppoint = computed(() => detail.value?.myRoleCode === 'teacher')
const isManagement = computed(() => canApprove.value)
const myStatus = computed(() => detail.value?.myStatus ?? -1)

const statusText = (s) => ({ 0: '申请中', 1: '已加入', 2: '已拒绝' }[s] ?? '无关系')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] ?? 'info')

async function load() {
  loading.value = true
  try {
    detail.value = (await getClubDetail(clubId)).data
    if (isManagement.value) {
      members.value = (await getMembers(clubId)).data
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onApply() {
  try {
    await applyClub(clubId)
    ElMessage.success('申请已提交，等待审批')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onApprove(m) {
  await approveMember(clubId, m.membershipId)
  ElMessage.success(`已通过 ${m.nickname} 的申请`)
  load()
}

async function onReject(m) {
  try {
    await ElMessageBox.confirm(`确定拒绝 ${m.nickname} 的申请吗？`, '拒绝申请', { type: 'warning' })
  } catch (e) { return }
  await rejectMember(clubId, m.membershipId)
  ElMessage.success('已拒绝')
  load()
}

async function onAppoint(m, role) {
  const roleName = role === 'president' ? '社长' : '副社长'
  try {
    await ElMessageBox.confirm(`确定任命 ${m.nickname} 为${roleName}吗？`, '任命', { type: 'info' })
  } catch (e) { return }
  try {
    await appointMember(clubId, m.membershipId, role)
    ElMessage.success(`已任命为${roleName}`)
    load()
  } catch (e) { /* 拦截器已提示（含槽位满/跨社团等） */ }
}

async function onResign() {
  try {
    await ElMessageBox.confirm('确定离职吗？职务将空出，由指导老师重新任命。', '离职确认', { type: 'warning' })
  } catch (e) { return }
  try {
    await resignClub(clubId)
    ElMessage.success('已离职')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-page-header @back="router.push('/clubs')" :content="detail?.name || '社团详情'" class="page-header" />

    <el-card shadow="never" class="info-card" v-if="detail">
      <div class="info-row">
        <span class="label">指导老师</span><span>{{ detail.teacherName }}</span>
        <span class="label">成员数</span><span>{{ detail.memberCount }}</span>
        <el-tag :type="statusType(myStatus)" size="small">{{ statusText(myStatus) }}</el-tag>
        <el-tag v-if="myStatus === 1" size="small" type="primary">{{ detail.myRoleName }}</el-tag>
      </div>
      <p class="desc">{{ detail.description || '（暂无简介）' }}</p>
      <div class="actions">
        <el-button v-if="myStatus === -1" type="primary" @click="onApply">申请加入</el-button>
        <el-button v-else-if="myStatus === 2" type="primary" plain @click="onApply">重新申请</el-button>
        <el-tag v-else-if="myStatus === 0" type="warning">等待审批中</el-tag>
        <el-button v-if="isManagement" type="danger" plain @click="onResign">离职</el-button>
      </div>
    </el-card>

    <el-card shadow="never" v-if="isManagement && members.length" class="member-card">
      <template #header>成员管理（含待审批申请）</template>
      <el-table :data="members" stripe>
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="roleName" label="角色" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <template v-if="row.status === 0 && canApprove">
              <el-button size="small" type="success" plain @click="onApprove(row)">通过</el-button>
              <el-button size="small" type="danger" plain @click="onReject(row)">拒绝</el-button>
            </template>
            <template v-else-if="row.status === 1 && canAppoint">
              <el-button size="small" type="primary" plain @click="onAppoint(row, 'president')">任命社长</el-button>
              <el-button size="small" type="primary" plain @click="onAppoint(row, 'vice_president')">任命副社长</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-empty v-else-if="isManagement" description="暂无成员申请" />
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 16px; }
.info-card { margin-bottom: 16px; }
.info-row { display: flex; align-items: center; gap: 12px; }
.label { color: #909399; font-size: 13px; }
.desc { margin-top: 12px; color: #606266; }
.actions { margin-top: 12px; }
</style>
