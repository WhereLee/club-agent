<script setup>
import { ref, onMounted } from 'vue'
import { getOperLogs, getLoginLogs } from '../api/teacher'

const tab = ref('oper')

// 操作日志
const operRows = ref([])
const operTotal = ref(0)
const operPage = ref(1)
const operLoading = ref(false)

// 登录日志
const loginRows = ref([])
const loginTotal = ref(0)
const loginPage = ref(1)
const loginLoading = ref(false)

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

async function loadOper() {
  operLoading.value = true
  try {
    const res = await getOperLogs(operPage.value, 10)
    operRows.value = res.data.records
    operTotal.value = res.data.total
  } finally {
    operLoading.value = false
  }
}

async function loadLogin() {
  loginLoading.value = true
  try {
    const res = await getLoginLogs(loginPage.value, 10)
    loginRows.value = res.data.records
    loginTotal.value = res.data.total
  } finally {
    loginLoading.value = false
  }
}

onMounted(loadOper)

// tab 首次切换时才加载对应日志（el-tab-pane 懒渲染，切换不会自动触发请求）
function onTabChange(name) {
  if (name === 'login') {
    loadLogin()
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h3>日志查看</h3>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="操作日志" name="oper">
        <el-table :data="operRows" v-loading="operLoading" stripe>
          <el-table-column prop="module" label="模块" width="110" />
          <el-table-column prop="operation" label="操作" min-width="140" />
          <el-table-column prop="operatorName" label="操作人" width="120" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 1 ? 'success' : 'danger'" size="small">
                {{ row.result === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.costTime }} ms</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="160">
            <template #default="{ row }">
              <span class="err-msg">{{ row.errorMsg || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination background layout="prev, pager, next" :total="operTotal" :page-size="10"
                         v-model:current-page="operPage" @current-change="loadOper" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="登录日志" name="login">
        <el-table :data="loginRows" v-loading="loginLoading" stripe>
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="ip" label="IP" width="150" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="200">
            <template #default="{ row }">
              <span class="err-msg">{{ row.message || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination background layout="prev, pager, next" :total="loginTotal" :page-size="10"
                         v-model:current-page="loginPage" @current-change="loadLogin" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.err-msg { color: #909399; font-size: 13px; }
</style>
