<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClubDetail, getMembers, applyClub, approveMember, rejectMember, appointMember, resignClub, updateClub } from '../api/club'
import { getConcepts, getConceptDetail, withdrawConcept, abandonConcept, voteConcept, reviewConcept } from '../api/concept'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId
const userStore = useUserStore()
// 登录时存 userId；fetchMe 刷新后是 SysUser 实体（字段 id）——两者兼容
const myUserId = computed(() => userStore.userInfo?.userId ?? userStore.userInfo?.id)

const detail = ref(null)
const members = ref([])
const loading = ref(false)

// 我的身份派生
const isTeacher = computed(() => detail.value?.myRoleCode === 'teacher')
const canApprove = computed(() => ['teacher', 'president', 'vice_president'].includes(detail.value?.myRoleCode))
const canAppoint = computed(() => detail.value?.myRoleCode === 'teacher')
const isManagement = computed(() => canApprove.value)
const canUpdate = computed(() => ['teacher', 'president'].includes(detail.value?.myRoleCode))
// 发起活动：仅本社团管理层（社长/副社长），老师是审批角色不发起
const canCreateConcept = computed(() => isManagement.value && !isTeacher.value)
const myStatus = computed(() => detail.value?.myStatus ?? -1)

// ---------- 概念（活动酝酿）区块 ----------
const STATUS = { DRAFTING: 1, SUBMITTED: 2, REVOTING: 3, TEACHER_REVIEW: 4, APPROVED: 5, VOIDED: 6 }
const conceptStatusText = (s) => ({ 1: '起草中', 2: '已提交待审', 3: '复议中', 4: '待老师批复', 5: '已通过', 6: '已作废' }[s] ?? '未知')
const conceptStatusType = (s) => ({ 1: 'primary', 2: 'warning', 3: 'warning', 4: 'warning', 5: 'success', 6: 'info' }[s] ?? 'info')
const concepts = ref([])
const conceptTotal = ref(0)
const conceptPage = ref(1)
const conceptLoading = ref(false)
const viewVisible = ref(false)
const viewItem = ref(null)
// 有活跃概念（起草中~待老师批复）时禁用发起入口
const activeConcept = computed(() => concepts.value.some((c) => [1, 2, 3, 4].includes(c.status)))
// 发起者本人（撤回/放弃/继续编辑仅本人可见）
// 雪花 ID 超 JS 安全整数：后端 userId 序列化为字符串，store 里是数字，统一转字符串比较
const isRequester = (c) => String(c.userId) === String(myUserId.value)
// 投票：非老师、非发起者、已提交待审/复议中、且当前轮未投（myVoted 由列表接口返回）
const canVote = (c) => !isTeacher.value && !isRequester(c) && [STATUS.SUBMITTED, STATUS.REVOTING].includes(c.status) && !c.myVoted
// 老师批复：指导老师 + 待老师批复
const canReview = (c) => isTeacher.value && c.status === STATUS.TEACHER_REVIEW

// 投票弹窗
const voteVisible = ref(false)
const voteItem = ref(null)
const voteForm = ref({ result: 1, comment: '' })
const voteLoading = ref(false)

// 批复弹窗
const reviewVisible = ref(false)
const reviewItem = ref(null)
const reviewForm = ref({ approve: true, comment: '' })
const reviewLoading = ref(false)

// 动作留痕文案
const traceActionText = (a) => ({
  create: '发起概念', save: '保存草稿', submit: '提交', vote: '投票', revote: '复议投票',
  withdraw: '撤回', abandon: '放弃', teacher_approve: '老师批复·通过', teacher_reject: '老师批复·否决',
  timeout_void: '超时自动作废', revote_needed: '进入复议', to_teacher: '进入待老师批复',
  revote_failed: '复议再次拒绝·作废', resign_void: '离职作废', ai_draft: '采纳 AI 草案', ai_brief: 'AI 生成想法简析'
}[a] ?? a)

async function loadConcepts() {
  if (!isManagement.value) return
  conceptLoading.value = true
  try {
    const res = await getConcepts(clubId, { page: conceptPage.value, size: 10 })
    concepts.value = res.data.records
    conceptTotal.value = res.data.total
  } catch (e) { /* 拦截器已提示 */ } finally {
    conceptLoading.value = false
  }
}

async function onWithdraw(c) {
  try {
    await ElMessageBox.confirm('确定撤回该概念吗？将回到起草中，已投的票作废，可修改后重新提交。', '撤回概念', { type: 'warning' })
  } catch (e) { return }
  try {
    await withdrawConcept(clubId, c.id)
    ElMessage.success('已撤回至起草中')
    loadConcepts()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onAbandon(c) {
  try {
    await ElMessageBox.confirm('确定放弃该概念吗？放弃后不可恢复，名额将释放。', '放弃概念', { type: 'warning' })
  } catch (e) { return }
  try {
    await abandonConcept(clubId, c.id)
    ElMessage.success('已放弃概念')
    loadConcepts()
  } catch (e) { /* 拦截器已提示 */ }
}

function onView(c) {
  viewVisible.value = true
  viewItem.value = c
  // 详情接口附带投票记录 + 全量时间线（透明留痕）
  getConceptDetail(clubId, c.id).then((res) => { viewItem.value = res.data }).catch(() => {})
}

function openVote(c) {
  voteItem.value = c
  voteForm.value = { result: 1, comment: '' }
  voteVisible.value = true
}

async function onVote() {
  if (!voteForm.value.comment.trim()) {
    ElMessage.warning('投票必须填写理由（赞成/拒绝均必填，留痕透明）')
    return
  }
  voteLoading.value = true
  try {
    await voteConcept(clubId, voteItem.value.id, { ...voteForm.value })
    const label = voteForm.value.result === 1 ? '赞成' : '拒绝'
    ElMessage.success(`已投${label}票`)
    voteVisible.value = false
    loadConcepts()
  } catch (e) { /* 拦截器已提示（含发起人/重复投票/状态异常） */ } finally {
    voteLoading.value = false
  }
}

function openReview(c) {
  reviewItem.value = c
  reviewForm.value = { approve: true, comment: '' }
  reviewVisible.value = true
}

async function onReview() {
  if (!reviewForm.value.approve && !reviewForm.value.comment.trim()) {
    ElMessage.warning('否决概念必须填写理由')
    return
  }
  reviewLoading.value = true
  try {
    await reviewConcept(clubId, reviewItem.value.id, { ...reviewForm.value })
    ElMessage.success(reviewForm.value.approve ? '已通过批复，概念转为活动' : '已否决，概念作废')
    reviewVisible.value = false
    loadConcepts()
  } catch (e) { /* 拦截器已提示 */ } finally {
    reviewLoading.value = false
  }
}

// 操作列：起草中=继续编辑；审批链=撤回；非终局=放弃；全部=查看
const conceptActions = (c) => ({
  canEdit: c.status === STATUS.DRAFTING,
  canWithdraw: [STATUS.SUBMITTED, STATUS.REVOTING, STATUS.TEACHER_REVIEW].includes(c.status),
  canAbandon: ![STATUS.APPROVED, STATUS.VOIDED].includes(c.status)
})

// 编辑社团对话框
const editVisible = ref(false)
const editForm = ref({ name: '', description: '' })
const editLoading = ref(false)

// 成员角色展示：第X任社长/副社长；已卸任管理层标记前任职务
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
    if (isManagement.value) {
      members.value = (await getMembers(clubId)).data
    }
    loadConcepts()
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

async function onResign() {
  const isMgmt = isManagement.value
  const tip = isMgmt
    ? '确定离职吗？职务将空出，由指导老师重新任命，任职记录保留为第X任。'
    : '确定退出该社团吗？'
  try {
    await ElMessageBox.confirm(tip, isMgmt ? '离职确认' : '退出确认', { type: 'warning' })
  } catch (e) { return }
  try {
    await resignClub(clubId)
    ElMessage.success(isMgmt ? '已离职' : '已退出社团')
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
        <el-button v-if="canCreateConcept" type="primary" :disabled="activeConcept" @click="router.push(`/clubs/${clubId}/concept`)">发起活动</el-button>
        <el-button v-if="canCreateConcept" plain @click="router.push(`/clubs/${clubId}/qa`)">经验问答</el-button>
        <el-button v-if="canUpdate" @click="openEdit">编辑社团</el-button>
        <el-button v-if="myStatus === -1" type="primary" @click="onApply">申请加入</el-button>
        <el-button v-else-if="myStatus === 2" type="primary" plain @click="onApply">重新申请</el-button>
        <el-tag v-else-if="myStatus === 0" type="warning">等待审批中</el-tag>
        <!-- 老师无 membership，不提供离职/退出入口（老师生命周期暂不考虑） -->
        <el-button v-if="myStatus === 1 && !isTeacher" type="danger" plain @click="onResign">{{ isManagement ? '离职' : '退出社团' }}</el-button>
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

    <el-card shadow="never" v-if="isManagement && members.length" class="member-card">
      <template #header>成员管理（含待审批申请）</template>
      <el-table :data="members" stripe>
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="roleName" label="角色" width="150">
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

    <el-card shadow="never" v-if="isManagement" class="concept-card">
      <template #header>
        <div class="card-header">
          <span>活动（概念）</span>
          <el-button size="small" type="primary" plain @click="router.push(`/clubs/${clubId}/activities`)" style="margin-right: 8px">活动管理</el-button>
          <el-tooltip :disabled="!activeConcept" content="已有进行中的概念，需完成或作废后才能发起新的">
            <el-button size="small" type="primary" plain :disabled="activeConcept" @click="router.push(`/clubs/${clubId}/concept`)">发起概念</el-button>
          </el-tooltip>
        </div>
      </template>
      <el-table v-loading="conceptLoading" :data="concepts" stripe v-if="concepts.length">
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="conceptStatusType(row.status)" size="small">{{ conceptStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requesterNickname" label="发起人" width="110" />
        <el-table-column prop="plannedTime" label="预计时间" min-width="140" />
        <el-table-column prop="plannedLocation" label="预计地点" min-width="160" />
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain v-if="conceptActions(row).canEdit && isRequester(row)"
              @click="router.push(`/clubs/${clubId}/concept/${row.id}`)">继续编辑</el-button>
            <el-button size="small" type="warning" plain v-if="conceptActions(row).canWithdraw && isRequester(row)"
              @click="onWithdraw(row)">撤回</el-button>
            <el-button size="small" type="danger" plain v-if="conceptActions(row).canAbandon && isRequester(row)"
              @click="onAbandon(row)">放弃</el-button>
            <el-button size="small" type="success" plain v-if="canVote(row)" @click="openVote(row)">投票</el-button>
            <el-button size="small" type="primary" plain v-if="canReview(row)" @click="openReview(row)">批复</el-button>
            <el-button size="small" @click="onView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无概念" />
      <el-pagination v-if="conceptTotal > 10" layout="prev, pager, next" :total="conceptTotal" :page-size="10"
        v-model:current-page="conceptPage" @current-change="loadConcepts" class="pager" />
    </el-card>

    <el-dialog v-model="voteVisible" title="概念投票" width="480px">
      <el-alert type="info" :closable="false" show-icon class="dialog-tip"
        title="两票制：您与另一位管理层各一票；出现拒绝票将进入复议，复议再次拒绝立即作废" />
      <el-form label-width="80px" v-if="voteItem">
        <el-form-item label="投票" required>
          <el-radio-group v-model="voteForm.result">
            <el-radio :value="1">赞成</el-radio>
            <el-radio :value="0">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="理由" required>
          <el-input v-model="voteForm.comment" type="textarea" :rows="3" maxlength="200"
            placeholder="赞成/拒绝均必须填写理由（留痕透明，管理层与指导老师可见）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voteVisible = false">取消</el-button>
        <el-button type="primary" :loading="voteLoading" @click="onVote">提交投票</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="指导老师批复" width="480px">
      <el-alert type="info" :closable="false" show-icon class="dialog-tip"
        title="通过后概念转为活动进入筹备；否决后概念作废并通知三位管理层（一票否决权）" />
      <el-form label-width="80px" v-if="reviewItem">
        <el-form-item label="批复" required>
          <el-radio-group v-model="reviewForm.approve">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">否决</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="理由" :required="!reviewForm.approve">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="3" maxlength="200"
            :placeholder="reviewForm.approve ? '通过理由（建议填写，留痕透明）' : '否决必须填写理由'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="onReview">提交批复</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" title="概念详情" width="640px">
      <el-descriptions v-if="viewItem" :column="1" border>
        <el-descriptions-item label="状态">
          <el-tag :type="conceptStatusType(viewItem.status)" size="small">{{ conceptStatusText(viewItem.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起人">{{ viewItem.requesterNickname }}</el-descriptions-item>
        <el-descriptions-item label="发起理由">{{ viewItem.reason || '（空）' }}</el-descriptions-item>
        <el-descriptions-item label="预计时间">{{ viewItem.plannedTime || '（空）' }}</el-descriptions-item>
        <el-descriptions-item label="预计地点">{{ viewItem.plannedLocation || '（空）' }}</el-descriptions-item>
        <el-descriptions-item label="活动简述">{{ viewItem.content || '（空）' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ viewItem.submittedAt || '（未提交）' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ viewItem.deadline || '（未定）' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="viewItem?.aiBrief">
        <h4 class="sub-title">发起人思路（AI 简析）</h4>
        <div class="ai-brief">{{ viewItem.aiBrief }}</div>
      </template>

      <template v-if="viewItem?.votes?.length">
        <h4 class="sub-title">投票记录</h4>
        <el-table :data="viewItem.votes" size="small" stripe>
          <el-table-column label="轮次" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.round === 1 ? 'primary' : 'warning'">{{ row.round === 1 ? '首轮' : '复议' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="voterNickname" label="投票人" width="100" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '赞成' : '拒绝' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="理由" min-width="160" />
          <el-table-column prop="createdAt" label="时间" width="160" />
        </el-table>
      </template>

      <template v-if="viewItem?.traces?.length">
        <h4 class="sub-title">流程记录（谁在什么时候做了什么）</h4>
        <el-timeline class="trace-timeline">
          <el-timeline-item v-for="(t, i) in viewItem.traces" :key="i" :timestamp="t.createdAt" placement="top">
            <b>{{ t.operatorName }}</b> {{ traceActionText(t.action) }}
            <span v-if="t.detail" class="trace-detail">：{{ t.detail }}</span>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 16px; }
.info-card { margin-bottom: 16px; }
.info-row { display: flex; align-items: center; gap: 12px; }
.label { color: #909399; font-size: 13px; }
.desc { margin-top: 12px; color: #606266; }
.actions { margin-top: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.concept-card { margin-top: 16px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.dialog-tip { margin-bottom: 16px; }
.sub-title { margin: 16px 0 8px; font-size: 14px; color: #303133; }
.trace-timeline { max-height: 320px; overflow-y: auto; }
.trace-detail { color: #606266; }
.ai-brief { white-space: pre-line; background: #f4f4ff; border: 1px solid #c8c8f0; border-radius: 8px; padding: 10px 12px; font-size: 13px; line-height: 1.7; color: #303133; }
</style>
