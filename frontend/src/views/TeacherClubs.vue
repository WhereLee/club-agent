<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyClubs, createClub } from '../api/club'

const router = useRouter()

const clubs = ref([])
const loading = ref(false)

// 创建社团对话框
const createVisible = ref(false)
const createForm = ref({ name: '', description: '' })
const createLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getMyClubs()
    clubs.value = res.data
  } catch (e) {
    // 拦截器已提示
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

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h3>我管理的社团</h3>
      <el-button type="primary" @click="openCreate">创建社团</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!clubs.length && !loading" description="还没有管理的社团，点击右上角创建" />
      <el-row :gutter="16">
        <el-col v-for="c in clubs" :key="c.clubId" :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="club-card">
            <div class="club-name">{{ c.clubName }}</div>
            <p class="club-desc">{{ c.clubDescription || '（暂无简介）' }}</p>
            <div class="club-foot">
              <span class="muted">指导老师：{{ c.teacherName }}</span>
              <el-button size="small" type="primary" plain @click="router.push(`/teacher/clubs/${c.clubId}`)">
                管理
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
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
.club-card { margin-bottom: 16px; }
.club-name { font-size: 16px; font-weight: 600; color: #303133; }
.club-desc { margin: 8px 0; color: #606266; font-size: 13px; height: 40px; overflow: hidden; }
.club-foot { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 13px; }
</style>
