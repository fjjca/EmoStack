<template>
    <div class="container">
        <div class="title">
            <div class="back-home">
                <el-icon><Back /></el-icon>
                <span>返回首页</span>
            </div>
            <div class="title-text">
                <h2>登录您的账户</h2>
                <p>请输入您的登录信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form
                ref="ruleFormRef"
                :model="formData"
                :rules="rules"
                label-position="top"
            >
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" size="large" placeholder="请输入用户名" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password />
                </el-form-item>
                <el-button class="btn" size="large" type="primary" @click="submitForm(ruleFormRef)">登录</el-button>
            </el-form>
            <div class="footer">
                <p>还没有账户？<router-link to="/auth/register">去注册</router-link></p>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { login } from '@/api/admin'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const ruleFormRef = ref()

const formData = reactive({
    username: '',
    password: ''
})
const rules = reactive({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ]
})

// 登录
const router = useRouter()
const submitForm = async (formEl) => {
    if (!formEl) return
    await formEl.validate((valid, fields) => {
        if (valid) {
            console.log(formData, 'formData')
            login(formData).then(res => {
                // 后端业务失败时返回完整 response，成功时返回 {token, roleType, userInfo}
                if (res && res.data && res.data.code !== '200') {
                    ElMessage.error(res.data.msg || '登录失败')
                    return
                }
                // 判断token是否存在
                if (!res.token) {
                    ElMessage.error('登录失败')
                    return
                }
                // 登录成功，保存token和用户信息
                localStorage.setItem('token', res.token)
                localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
                // 根据用户角色决定跳转的路径
                if (res.userInfo.userType === 2) {
                    router.push('/back/dashboard')
                } else {
                    router.push('/')
                }
            }).catch(() => {
                ElMessage.error('登录失败，请检查网络或账号密码')
            })
        }
    })
}

</script>
<style scoped lang="scss">
    .container {
        width: 384px;
        .title {
            .back-home {
                margin-bottom: 60px;
            }
            .title-text {
                text-align: center;
                h2 {
                    font-size: 36px;
                    margin-bottom: 10px;
                }
                p {
                    font-size: 18px;
                    color: #6b7280;
                }
            }
        }
        .form-container {
            margin-top: 30px;
            .btn {
                margin-top: 40px;
                width: 100%;
            }
            .footer {
                padding: 30px;
                text-align: center;
            }
        }
    }
</style>