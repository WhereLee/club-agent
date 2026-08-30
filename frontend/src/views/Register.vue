<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isNicknameValid, NICKNAME_MESSAGE } from '../utils/nickname'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()
const loading = ref(false)

const form = reactive({ username: '', password: '', confirmPassword: '', email: '', nickname: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,20}$/, message: '用户名限 3-20 位字母/数字/下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9@#$%^&*._\-]{8,32}$/, message: '密码限 8-32 位字母/数字/常见符号', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        value === form.password ? callback() : callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    {
      validator: (rule, value, callback) => (isNicknameValid(value) ? callback() : callback(new Error(NICKNAME_MESSAGE))),
      trigger: 'blur'
    }
  ]
}

const formRef = ref()

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      email: form.email,
      nickname: form.nickname
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="always">
      <h2 class="auth-title">注册账号</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-20 位字母/数字/下划线" maxlength="20" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="8-32 位字母/数字/常见符号" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="2-12 个汉字或 4-24 位英文/数字" maxlength="24" />
        </el-form-item>
        <el-button type="primary" class="auth-btn" :loading="loading" @click="onSubmit">注 册</el-button>
        <div class="auth-footer">
          已有账号？
          <router-link to="/login" class="link">返回登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page { display: flex; justify-content: center; padding-top: 8vh; }
.auth-card { width: 400px; }
.auth-title { text-align: center; margin-bottom: 20px; color: #303133; }
.auth-btn { width: 100%; margin-top: 4px; }
.auth-footer { margin-top: 16px; text-align: center; color: #909399; font-size: 13px; }
.link { color: #409eff; text-decoration: none; }
</style>
