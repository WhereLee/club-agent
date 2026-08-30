<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClubDetail, getMembers, approveMember, rejectMember, appointMember, updateClub } from '../api/club'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId

const detail = ref(null)
const members = ref([])
const loading = ref(false)

// 编辑社团对话框
const editVisible = ref(false)
const editForm = ref({ name: '', description: '' })
const editLoading = ref(false)

// 成员角色展示：第X任社长/副社长；已卸任管理层标记前任职务（与 ClubDetail 一致）
const roleLabel = (row) => {
  if (row.termNo) {
    const roleText = row.roleCode === 'president' ? '社长' : row.roleCode === 'vice_president' ? '副社长' : ''
    if (roleText) return `第${row.termNo}任${roleText}`
  }
  if (row.formerRoleCode && row.termNo) {
    const former = row.formerRoleCode === 'president' ? '社长' : '副社长'
    return `第${row.termNo}任${former}·已卸任`
  }
  return row.roleName
}

const statusText = (s) => ({ 0: '申请中', 1: '已加入', 2: '已拒绝' }[s] ?? '无关系')
const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger' }[s] ?? 'info')

async function load() {
  loading.value = true
  try {
    detail.value = (await getClubDetail(clubId)).data
    members.value = (await getMembers(clubId)).data
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onApprove(m) {
  try {
    await approveMember(clubId, m.membershipId)
    ElMessage.success(`已通过 ${m.nickname} 的申请`)
    load()
  } catch (e) { /* 拦截器已提示（含防重） */ }
}

async function onReject(m) {
  try {
    await ElMessageBox.confirm(`确定拒绝 ${m.nickname} 的申请吗？`, '拒绝申请', { type: 'warning' })
  } catch (e) { return }
  try {
    await rejectMember(clubId, m.membershipId)
    ElMessage.success('已拒绝')
    load()
  } catch (e) { /* 拦截器已提示 */ }
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

function openEdit() {
  editForm.value = { name: detail.value?.name || '', description: detail.value?.description || '' }
  editVisible.value = true
}

async function onEditSave() {
  if (!editForm.value.name.trim()) {
    ElMessage.warning('请输入社团名称')
    return
  }
  editLoading.value = true
  try {
    const res = await updateClub(clubId, { ...editForm.value })
    ElMessage.success(`社团已更新为「${res.data.name}」`)
    editVisible.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    editLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-page-header @back="router.push('/teacher/clubs')" :content="detail?.name || '社团管理'" class="page-header" />

    <el-card shadow="never" class="info-card" v-if="detail">
      <div class="info-row">
        <span class="label">指导老师</span><span>{{ detail.teacherName }}</span>
        <span class="label">成员数</span><span>{{ detail.memberCount }}</span>
      </div>
      <p class="desc">{{ detail.description || '（暂无简介）' }}</p>
      <div class="actions">
        <el-button @click="openEdit">编辑社团</el-button>
      </div>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑社团" width="440px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="100" placeholder="社团名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="500" placeholder="一句话介绍社团" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="onEditSave">保存</el-button>
      </template>
    </el-dialog>

    <el-card shadow="never" class="member-card">
      <template #header>成员管理（含待审批申请）</template>
      <el-table v-if="members.length" :data="members" stripe>
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="角色" width="150">
          <template #default="{ row }">
            <span>{{ roleLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button size="small" type="success" plain @click="onApprove(row)">通过</el-button>
              <el-button size="small" type="danger" plain @click="onReject(row)">拒绝</el-button>
            </template>
            <template v-else-if="row.status === 1">
              <el-button size="small" type="primary" plain @click="onAppoint(row, 'president')">任命社长</el-button>
              <el-button size="small" type="primary" plain @click="onAppoint(row, 'vice_president')">任命副社长</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!members.length" description="暂无成员" />
    </el-card>
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
