<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClubs, createClub, applyClub } from '../api/club'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const clubs = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

// 创建社团对话框（仅老师）
const createVisible = ref(false)
const createForm = ref({ name: '', description: '' })
const createLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getClubs(page.value, 10)
    clubs.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.value = { name: '', description: '' }
  createVisible.value = true
}

async function onCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入社团名称')
    return
  }
  createLoading.value = true
  try {
    const res = await createClub({ ...createForm.value })
    createVisible.value = false
    ElMessage.success(`社团「${res.data.name}」创建成功`)
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    createLoading.value = false
  }
}

async function onApply(club) {
  try {
    await ElMessageBox.confirm(`确定申请加入「${club.name}」吗？`, '申请加入', { type: 'info' })
  } catch (e) {
    return
  }
  try {
    await applyClub(club.id)
    ElMessage.success('申请已提交，等待审批')
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h3>社团列表</h3>
      <el-button v-if="userStore.userInfo?.isTeacher" type="primary" @click="openCreate">创建社团</el-button>
    </div>

    <el-table :data="clubs" v-loading="loading" stripe>
      <el-table-column prop="name" label="社团名称" min-width="140" />
      <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip />
      <el-table-column prop="teacherName" label="指导老师" width="120" />
      <el-table-column prop="memberCount" label="成员数" width="80" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="router.push(`/clubs/${row.id}`)">详情</el-button>
          <el-button size="small" type="primary" plain @click="onApply(row)">申请加入</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="10"
                     v-model:current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="createVisible" title="创建社团" width="440px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" maxlength="100" placeholder="社团名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="500"
                    placeholder="一句话介绍社团" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
