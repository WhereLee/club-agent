<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { updateAvatar, updatePassword, updateProfile } from '../api/user'
import { useUserStore } from '../stores/user'
import { isNicknameValid, NICKNAME_MESSAGE } from '../utils/nickname'

const userStore = useUserStore()

const profileForm = reactive({ nickname: '', email: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileRef = ref()
const passwordRef = ref()
const passwordDialog = ref(false)
const saving = ref(false)

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9@#$%^&*._\-]{8,32}$/, message: '密码限 8-32 位字母/数字/常见符号', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        value === passwordForm.newPassword ? callback() : callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur'
    }
  ]
}

function fillProfile() {
  profileForm.nickname = userStore.userInfo?.nickname || ''
  profileForm.email = userStore.userInfo?.email || ''
}

// 后端 LocalDateTime 序列化为 ISO 无时区格式（2026-08-27T03:52:21.001105），转为可读展示
function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

async function onAvatarChange(uploadFile) {
  // Element Plus 2.7 的 http-request options.file 即为原始 File（无 .raw 包装层）
  const file = uploadFile.file
  try {
    const res = await updateAvatar(file)
    userStore.userInfo.avatarUrl = res.data.avatarUrl
    localStorage.setItem('club_user', JSON.stringify(userStore.userInfo))
    ElMessage.success('头像已更新')
  } catch (e) {
    // 错误提示由拦截器统一处理
  }
}

async function onSaveProfile() {
  await profileRef.value.validate()
  saving.value = true
  try {
    const res = await updateProfile({ ...profileForm })
    userStore.userInfo.nickname = res.data.nickname
    userStore.userInfo.email = res.data.email
    localStorage.setItem('club_user', JSON.stringify(userStore.userInfo))
    ElMessage.success('资料已更新')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onSavePassword() {
  await passwordRef.value.validate()
  saving.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    passwordDialog.value = false
    await userStore.logout()
    location.href = '/login'
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await userStore.fetchMe()
  fillProfile()
})
</script>

<template>
  <el-row :gutter="24">
    <el-col :span="8">
      <el-card shadow="never">
        <div class="avatar-area">
          <el-avatar :size="96" :src="userStore.userInfo?.avatarUrl" />
          <div class="avatar-name">{{ userStore.userInfo?.nickname }}</div>
          <div class="avatar-username">@{{ userStore.userInfo?.username }}</div>
          <el-upload :show-file-list="false" :before-upload="() => true" :http-request="onAvatarChange"
                     accept="image/jpeg,image/png,image/gif,image/webp">
            <el-button size="small" type="primary" plain>更换头像</el-button>
          </el-upload>
        </div>
      </el-card>
    </el-col>

    <el-col :span="16">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>基本资料</span>
            <el-button text type="primary" @click="passwordDialog = true">修改密码</el-button>
          </div>
        </template>
        <el-form ref="profileRef" :model="profileForm" label-width="80px" :rules="{
          nickname: [
            { required: true, message: '请输入昵称', trigger: 'blur' },
            {
              validator: (rule, value, callback) => (isNicknameValid(value) ? callback() : callback(new Error(NICKNAME_MESSAGE))),
              trigger: 'blur'
            }
          ],
          email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
        }">
          <el-form-item label="用户名">
            <el-input :model-value="userStore.userInfo?.username" disabled />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" maxlength="24" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" />
          </el-form-item>
          <el-form-item label="注册时间">
            <span class="muted">{{ formatTime(userStore.userInfo?.createdAt) }}</span>
          </el-form-item>
          <el-button type="primary" :loading="saving" @click="onSaveProfile">保存修改</el-button>
        </el-form>
      </el-card>
    </el-col>
  </el-row>

  <el-dialog v-model="passwordDialog" title="修改密码" width="420px">
    <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="8-32 位字母/数字/常见符号" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSavePassword">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.avatar-area { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 16px 0; }
.avatar-name { font-size: 17px; font-weight: 600; color: #303133; }
.avatar-username { font-size: 13px; color: #909399; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 13px; }
</style>
