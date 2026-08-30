<template>
  <div class="page">
    <div class="page-header">
      <el-button link @click="router.push(`/clubs/${clubId}`)">← 返回社团</el-button>
      <h2>活动管理</h2>
      <el-tabs v-model="status" @tab-change="load">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="公示中" name="1" />
        <el-tab-pane label="问卷中" name="2" />
        <el-tab-pane label="讨论中" name="3" />
        <el-tab-pane label="已发布" name="4" />
        <el-tab-pane label="已取消" name="10" />
      </el-tabs>
    </div>

    <el-table :data="rows" v-loading="loading" empty-text="暂无活动">
      <el-table-column label="时间" min-width="150">
        <template #default="{ row }">{{ row.plannedTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="地点" prop="plannedLocation" min-width="120" show-overflow-tooltip />
      <el-table-column label="内容" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.content || '').slice(0, 40) }}</template>
      </el-table-column>
      <el-table-column label="发起人" width="110">
        <template #default="{ row }">{{ row.creatorName }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/clubs/${clubId}/activities/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="size"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getActivities } from '../api/activity'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const loading = ref(false)

const statusText = (s) => ({ 1: '公示中', 2: '问卷中', 3: '讨论中', 4: '已发布', 5: '报名中', 6: '执行中', 7: '留痕中', 8: '总结中', 9: '已归档', 10: '已取消' }[s] ?? '未知')
const statusType = (s) => ({ 1: 'primary', 2: 'warning', 3: 'warning', 4: 'success', 5: 'primary', 6: 'warning', 7: 'warning', 8: 'info', 9: 'info', 10: 'info' }[s] ?? 'info')

async function load() {
  loading.value = true
  try {
    const res = await getActivities(clubId, page.value, size, status.value === '' ? null : Number(status.value))
    rows.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}
.page-header h2 {
  margin: 8px 0 0;
}
</style>
