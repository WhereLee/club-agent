<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '', captchaKey: '', captchaCode: '' })
const captchaImg = ref('')
const loading = ref(false)

async function refreshCaptcha() {
  const res = await getCaptcha()
  form.captchaKey = res.data.captchaKey
  // 后端 imgBase64 已返回完整 data URI（data:image/png;base64,...），直接使用，勿再拼前缀
  captchaImg.value = res.data.imgBase64
}

async function onSubmit() {
  if (!form.username || !form.password || !form.captchaCode) {
    ElMessage.warning('请填写完整的登录信息')
    return
  }
  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    // 老师进管理台，学生进个人中心
    router.push(userStore.userInfo?.isTeacher ? '/teacher' : '/profile')
  } catch (e) {
    // 验证码已一次性消费，失败必须刷新
    refreshCaptcha()
    form.captchaCode = ''
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="always">
      <h2 class="auth-title">社团管理 Agent</h2>
      <el-form :model="form" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" maxlength="20" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="请输入验证码" maxlength="4" />
            <img :src="captchaImg" class="captcha-img" alt="验证码" title="点击刷新" @click="refreshCaptcha" />
          </div>
        </el-form-item>
        <el-button type="primary" class="auth-btn" :loading="loading" @click="onSubmit">登 录</el-button>
        <div class="auth-footer">
          还没有账号？
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page { display: flex; justify-content: center; padding-top: 12vh; }
.auth-card { width: 380px; }
.auth-title { text-align: center; margin-bottom: 24px; color: #303133; }
.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-img { height: 32px; cursor: pointer; border-radius: 4px; }
.auth-btn { width: 100%; margin-top: 4px; }
.auth-footer { margin-top: 16px; text-align: center; color: #909399; font-size: 13px; }
.link { color: #409eff; text-decoration: none; }
</style>
