<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMessages, markMessageRead } from '../api/message'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const messages = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getMessages({ page: page.value, size: 10 })
    messages.value = res.data.records
    total.value = res.data.total
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

async function onRead(m) {
  if (m.readFlag === 1) return
  try {
    await markMessageRead(m.id)
    m.readFlag = 1
    userStore.refreshUnread()
    ElMessage.success('已标记已读')
  } catch (e) { /* 拦截器已提示 */ }
}

const typeText = (t) => (t === 'concept_void' ? '概念作废' : t === 'concept_approved' ? '概念通过' : t)

onMounted(load)
</script>

<template>
  <div>
    <el-page-header @back="router.push('/clubs')" content="站内消息" class="page-header" />
    <el-card shadow="never">
      <el-table v-loading="loading" :data="messages" stripe>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'concept_approved' ? 'success' : 'danger'">
              {{ typeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'msg-unread': row.readFlag === 0 }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.readFlag === 1" @click="onRead(row)">
              {{ row.readFlag === 1 ? '已读' : '标记已读' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !messages.length" description="暂无消息" />
      <el-pagination v-if="total > 10" layout="prev, pager, next" :total="total" :page-size="10"
        v-model:current-page="page" @current-change="load" class="pager" />
    </el-card>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 16px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.msg-unread { font-weight: 600; }
</style>
