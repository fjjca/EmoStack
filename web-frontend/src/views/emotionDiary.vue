<template>
    <div class="emotion-diary">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px; height: 60px"></el-image>
                <div>
                    <h1>情绪日记</h1>
                    <p class="header-sub">每天的心情是一天的「日记」，当天的心情变化记成多条「情绪记录」</p>
                </div>
            </div>
        </div>

        <div class="content">
            <!-- 左侧：情绪日历 -->
            <div class="calendar-panel">
                <el-calendar v-model="calendarValue">
                    <template #date-cell="{ data }">
                        <div
                            class="day-cell"
                            :class="{
                                'is-selected': dayKey(data.date) === selectedDay,
                                'is-other-month': data.type !== 'current-month',
                                'has-content': dayCellStyle(dayKey(data.date)).background !== ''
                            }"
                            :style="dayCellStyle(dayKey(data.date))"
                            @click="selectDay(dayKey(data.date))"
                        >
                            <span class="day-num">{{ dayDate(data.date) }}</span>
                            <span v-if="dayRecordCount(dayKey(data.date)) > 1" class="day-count">{{ dayRecordCount(dayKey(data.date)) }}</span>
                        </div>
                    </template>
                </el-calendar>
                <div class="mood-legend">
                    <span>情绪图例：</span>
                    <span v-for="l in legend" :key="l.text" class="legend-item">
                        <i class="legend-dot" :style="{ background: l.color }"></i>{{ l.text }}
                    </span>
                </div>
            </div>

            <!-- 右侧 -->
            <div class="right-panel">
                <!-- 选中日期：当日日记 + 当天情绪记录 -->
                <div class="card selected-card">
                    <div class="card-title">{{ displaySelectedDay }}</div>
                    <!-- 当日日记 -->
                    <div class="day-diary-block">
                        <div class="sec-title">今日日记</div>
                        <template v-if="selectedDiary">
                            <div class="diary-content">{{ selectedDiary.diaryContent || '（未写感想）' }}</div>
                            <div class="diary-meta">
                                <el-tag v-if="selectedDiary.sleepQuality" size="small">睡眠 {{ sleepText(selectedDiary.sleepQuality) }}</el-tag>
                            </div>
                        </template>
                        <div v-else class="empty-tip">这一天还没有写日记。右键下面的「今日日记」可随时补充/修改。</div>
                    </div>
                    <!-- 当天情绪记录 -->
                    <div class="sec-title">当日情绪记录（{{ dayRecords.length }}）</div>
                    <div v-if="dayRecords.length === 0" class="empty-tip">这一天还没有情绪记录，记一条吧。</div>
                    <div v-else class="record-list">
                        <div v-for="r in dayRecords" :key="r.id" class="record-item">
                            <div class="record-head">
                                <span class="record-emotion">
                                    <el-image v-if="emotionImg(r.dominantEmotion)" :src="emotionImg(r.dominantEmotion)" style="width: 20px; height: 20px"></el-image>
                                    {{ r.dominantEmotion }}
                                </span>
                                <span class="record-score">{{ emotionStatus[r.moodScore - 1] || '' }} · {{ r.moodScore }}分</span>
                                <span class="record-time">{{ formatTime(r.recordTime || r.createdAt) }}</span>
                            </div>
                            <div class="record-body">
                                <p v-if="r.emotionTriggers" class="record-trigger">触发：{{ r.emotionTriggers }}</p>
                                <el-tag v-if="r.stressLevel" size="small" type="warning">压力 {{ stressText(r.stressLevel) }}</el-tag>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 新增情绪记录：可多条，支持自定义时分 -->
                <div class="card write-card">
                    <div class="card-title">记录心情</div>
                    <div class="form">
                        <div class="form-row">
                            <div class="form-label">日期与时间（可改，比如晚上补记）</div>
                            <el-date-picker
                                v-model="logForm.recordTime"
                                type="datetime"
                                value-format="YYYY-MM-DD HH:mm:ss"
                                placeholder="选择日期时间"
                                style="width: 100%"
                            ></el-date-picker>
                        </div>
                        <div class="form-row">
                            <div class="form-label">情绪评分（1-10）</div>
                            <el-rate v-model="logForm.moodScore" :texts="emotionStatus" show-text :max="10" size="large"></el-rate>
                        </div>
                        <div class="form-row">
                            <div class="form-label">主要情绪</div>
                            <div class="emotion-grid">
                                <div v-for="e in emotionOptions" :key="e.name" class="emotion-opt" :class="{ active: e.name === logForm.dominantEmotion }" @click="logForm.dominantEmotion = e.name">
                                    <el-image :src="e.url" style="width: 44px; height: 44px"></el-image>
                                    <span>{{ e.name }}</span>
                                </div>
                            </div>
                        </div>
                        <div class="form-row">
                            <div class="form-label">触发因素 / 当时发生了什么</div>
                            <el-input v-model="logForm.emotionTriggers" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="当时什么事情影响了您的情绪？"></el-input>
                        </div>
                        <div class="form-row">
                            <div class="form-label">压力水平</div>
                            <el-select v-model="logForm.stressLevel" placeholder="请选择当时的压力" style="width: 100%">
                                <el-option label="很低" :value="1"></el-option>
                                <el-option label="较低" :value="2"></el-option>
                                <el-option label="中等" :value="3"></el-option>
                                <el-option label="较高" :value="4"></el-option>
                                <el-option label="很高" :value="5"></el-option>
                            </el-select>
                        </div>
                        <div class="form-actions">
                            <el-button @click="resetLogForm">重置</el-button>
                            <el-button type="primary" :loading="submittingLog" :disabled="submittingLog" @click="submitLog">新增一条情绪</el-button>
                        </div>
                    </div>
                </div>

                <!-- 今日日记：一天一条，可更新/追加 -->
                <div class="card write-card">
                    <div class="card-title">今日日记</div>
                    <div class="form">
                        <div class="form-row">
                            <div class="form-label">日期</div>
                            <el-date-picker v-model="diaryForm.diaryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%"></el-date-picker>
                        </div>
                        <div class="form-row">
                            <div class="form-label">睡眠质量</div>
                            <el-select v-model="diaryForm.sleepQuality" placeholder="请选择昨天睡得如何" style="width: 100%">
                                <el-option label="很差" :value="1"></el-option>
                                <el-option label="较差" :value="2"></el-option>
                                <el-option label="一般" :value="3"></el-option>
                                <el-option label="良好" :value="4"></el-option>
                                <el-option label="优秀" :value="5"></el-option>
                            </el-select>
                        </div>
                        <div class="form-row">
                            <div class="form-label">今日感想</div>
                            <el-input v-model="diaryForm.diaryContent" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="写下您今天的想法、感受或发生的值得记录的事…"></el-input>
                        </div>
                        <div class="form-row">
                            <el-checkbox v-model="diaryForm.append">追加到已有的今日感想（不覆盖）</el-checkbox>
                        </div>
                        <div class="form-actions">
                            <el-button @click="resetDiaryForm">重置</el-button>
                            <el-button type="primary" :loading="submittingDiary" :disabled="submittingDiary" @click="submitDiary">保存今日日记</el-button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
    import { ref, reactive, computed, onMounted, watch } from 'vue'
    import { dayjs, ElMessage } from 'element-plus'
    import { addEmotionDiary, addEmotionLog, getMyEmotionDiariesByMonth } from '@/api/frontend'

    const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']

    const emotionOptions = [
        { name: '开心', url: new URL('@/assets/images/开心.png', import.meta.url).href },
        { name: '平静', url: new URL('@/assets/images/平静.png', import.meta.url).href },
        { name: '焦虑', url: new URL('@/assets/images/焦虑.png', import.meta.url).href },
        { name: '悲伤', url: new URL('@/assets/images/悲伤.png', import.meta.url).href },
        { name: '兴奋', url: new URL('@/assets/images/兴奋.png', import.meta.url).href },
        { name: '疲惫', url: new URL('@/assets/images/疲惫.png', import.meta.url).href },
        { name: '惊讶', url: new URL('@/assets/images/惊讶.png', import.meta.url).href },
        { name: '困惑', url: new URL('@/assets/images/困惑.png', import.meta.url).href },
    ]

    const iconUrl = new URL('@/assets/images/like.png', import.meta.url).href

    const emotionImg = (name) => {
        const item = emotionOptions.find(e => e.name === name)
        return item ? item.url : ''
    }
    const sleepText = (v) => ({ 1: '很差', 2: '较差', 3: '一般', 4: '良好', 5: '优秀' }[v] || v)
    const stressText = (v) => ({ 1: '很低', 2: '较低', 3: '中等', 4: '较高', 5: '很高' }[v] || v)
    const formatTime = (t) => (t ? dayjs(t).format('MM-DD HH:mm') : '')

    // ===== 日历数据 =====
    const calendarValue = ref(new Date())
    const selectedDay = ref(dayjs().format('YYYY-MM-DD'))
    const monthData = reactive({ diaries: [], logs: [] })

    // el-calendar 的 data.date/day 在 element-plus 2.x 是 Date 对象，统一用 dayjs 包装
    const dayKey = (v) => dayjs(v).format('YYYY-MM-DD')
    const dayDate = (v) => dayjs(v).date()

    // 'YYYY-MM-DD' -> { diary, logs[] }
    const dayMap = computed(() => {
        const map = {}
        for (const d of monthData.diaries) {
            const k = dayjs(d.diaryDate).format('YYYY-MM-DD')
            if (!map[k]) map[k] = { diary: null, logs: [] }
            map[k].diary = d
        }
        for (const l of monthData.logs) {
            const k = dayjs(l.diaryDate).format('YYYY-MM-DD')
            if (!map[k]) map[k] = { diary: null, logs: [] }
            map[k].logs.push(l)
        }
        for (const k in map) {
            map[k].logs.sort((a, b) => dayjs(a.recordTime || a.createdAt).valueOf() - dayjs(b.recordTime || b.createdAt).valueOf())
        }
        return map
    })

    // 日历格子：当天 n 条情绪就竖向等分成 n 段，每段用该条情绪对应颜色
    const dayCellStyle = (key) => {
        const info = dayMap.value[key]
        if (!info) return {}
        const logs = info.logs || []
        if (logs.length === 0) {
            return info.diary ? { background: '#F3F4F6' } : {}
        }
        if (logs.length === 1) {
            return { background: moodColor(logs[0].moodScore) }
        }
        const seg = 100 / logs.length
        const stops = logs.map((l, i) => `${moodColor(l.moodScore)} ${(i * seg).toFixed(2)}% ${((i + 1) * seg).toFixed(2)}%`).join(',')
        return { background: `linear-gradient(to bottom, ${stops})` }
    }

    const dayRecordCount = (key) => (dayMap.value[key]?.logs.length || 0)
    const dayRecords = computed(() => dayMap.value[selectedDay.value]?.logs || [])
    const selectedDiary = computed(() => dayMap.value[selectedDay.value]?.diary || null)
    const displaySelectedDay = computed(() => dayjs(selectedDay.value).format('YYYY年MM月DD日'))

    const moodColor = (score) => {
        if (!score) return ''
        if (score <= 3) return '#E8EAED'
        if (score <= 5) return '#C7DBFE'
        if (score <= 7) return '#FDE68A'
        if (score <= 9) return '#A7F3D0'
        return '#FBCFE8'
    }

    const legend = [
        { color: '#E8EAED', text: '1-3 低落' },
        { color: '#C7DBFE', text: '4-5 消沉' },
        { color: '#FDE68A', text: '6-7 平稳' },
        { color: '#A7F3D0', text: '8-9 愉悦' },
        { color: '#FBCFE8', text: '10 幸福' },
    ]

    // el-calendar 2.x 不提供 change 事件（翻月/点选只会更新 v-model），
    // 所以必须监听 calendarValue 变化来按月份加载数据
    let lastLoadedMonth = ''
    const loadMonth = (ym, force = false) => {
        if (!force && ym === lastLoadedMonth) return
        lastLoadedMonth = ym
        getMyEmotionDiariesByMonth(ym).then(r => {
            monthData.diaries = r.diaries || []
            monthData.logs = r.logs || []
        })
    }

    watch(calendarValue, (val) => {
        loadMonth(dayjs(val).format('YYYY-MM'))
    })

    const selectDay = (key) => {
        selectedDay.value = key
        diaryForm.diaryDate = key
        logForm.recordTime = key + ' ' + dayjs().format('HH:mm:ss')
    }

    // ===== 情绪记录表单 =====
    const logForm = reactive({
        recordTime: dayjs().format('YYYY-MM-DD HH:mm:ss'),
        moodScore: null,
        dominantEmotion: '',
        emotionTriggers: '',
        stressLevel: null
    })
    const resetLogForm = () => {
        Object.assign(logForm, {
            recordTime: selectedDay.value + ' ' + dayjs().format('HH:mm:ss'),
            moodScore: null,
            dominantEmotion: '',
            emotionTriggers: '',
            stressLevel: null
        })
    }
    const submittingLog = ref(false)
    const submitLog = () => {
        if (submittingLog.value) return
        if (!logForm.moodScore) { ElMessage.error('请选择情绪评分'); return }
        if (!logForm.dominantEmotion) { ElMessage.error('请选择主要情绪'); return }
        submittingLog.value = true
        addEmotionLog({ ...logForm }).then(() => {
            ElMessage.success('已记录这条情绪')
            const ym = dayjs(logForm.recordTime).format('YYYY-MM')
            loadMonth(ym, true)
            selectedDay.value = dayjs(logForm.recordTime).format('YYYY-MM-DD')
            resetLogForm()
        }).finally(() => { submittingLog.value = false })
    }

    // ===== 今日日记表单 =====
    const diaryForm = reactive({
        diaryDate: dayjs().format('YYYY-MM-DD'),
        sleepQuality: null,
        diaryContent: '',
        append: false
    })
    const resetDiaryForm = () => {
        Object.assign(diaryForm, {
            diaryDate: selectedDay.value,
            sleepQuality: null,
            diaryContent: '',
            append: false
        })
        // 若该天已有日记，预填内容便于修改
        const d = dayMap.value[selectedDay.value]?.diary
        if (d) {
            diaryForm.sleepQuality = d.sleepQuality
            diaryForm.diaryContent = d.diaryContent || ''
        }
    }
    const submittingDiary = ref(false)
    const submitDiary = () => {
        if (submittingDiary.value) return
        submittingDiary.value = true
        addEmotionDiary({ ...diaryForm }).then(() => {
            ElMessage.success('今日日记已保存')
            const ym = dayjs(diaryForm.diaryDate).format('YYYY-MM')
            loadMonth(ym, true)
            selectedDay.value = dayjs(diaryForm.diaryDate).format('YYYY-MM-DD')
        }).finally(() => { submittingDiary.value = false })
    }

    onMounted(() => {
        loadMonth(dayjs().format('YYYY-MM'))
    })
</script>

<style scoped lang="scss">
    .emotion-diary {
        background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
        min-height: 100vh;

        .header-section {
            background: linear-gradient(135deg, #7ED321 0%, #F5A623 100%);
            color: white;
            padding: 40px 48px;
            .header-content {
                display: flex;
                align-items: center;
                gap: 14px;
                max-width: 1200px;
                margin: 0 auto;
                h1 { margin: 0; font-size: 30px; }
                .header-sub { margin: 6px 0 0; opacity: .92; font-size: 14px; }
            }
        }

        .content {
            max-width: 1200px;
            margin: 24px auto;
            padding: 0 16px;
            display: flex;
            gap: 24px;
            align-items: flex-start;

            .calendar-panel {
                flex: 0 0 55%;
                background: white;
                border-radius: 14px;
                padding: 16px;
                box-shadow: 0 4px 12px rgba(0,0,0,.05);
                position: sticky;
                top: 16px;

                :deep(.el-calendar) {
                    .el-calendar__body { padding: 8px; }
                    .el-calendar-table .el-calendar-day { height: 66px; padding: 2px; }
                }

                .day-cell {
                    position: relative;
                    height: 100%;
                    border-radius: 8px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: transform .12s;
                    .day-num { font-size: 14px; font-weight: 600; color: #333; }
                    &.is-other-month { .day-num { opacity: .35; } }
                    &:hover { transform: scale(1.06); }
                    &.is-selected {
                        outline: 2px solid #4A90E2;
                        outline-offset: -2px;
                        z-index: 1;
                    }
                    .day-count {
                        position: absolute;
                        top: 2px;
                        right: 5px;
                        font-size: 10px;
                        color: #fff;
                        background: rgba(0,0,0,.45);
                        border-radius: 7px;
                        padding: 0 4px;
                        font-weight: 700;
                        line-height: 14px;
                    }
                }

                .mood-legend {
                    display: flex;
                    align-items: center;
                    flex-wrap: wrap;
                    gap: 12px;
                    margin-top: 12px;
                    padding: 0 8px 4px;
                    font-size: 12px;
                    color: #6B7280;
                    .legend-item { display: inline-flex; align-items: center; gap: 4px; }
                    .legend-dot { display: inline-block; width: 12px; height: 12px; border-radius: 3px; }
                }
            }

            .right-panel {
                flex: 1;
                display: flex;
                flex-direction: column;
                gap: 20px;

                .card {
                    background: white;
                    border-radius: 14px;
                    padding: 20px;
                    box-shadow: 0 4px 12px rgba(0,0,0,.05);
                    .card-title { font-size: 19px; font-weight: 700; color: #1F2937; margin-bottom: 14px; }
                    .sec-title { font-size: 14px; font-weight: 600; color: #374151; margin: 6px 0 10px; }
                }

                .selected-card {
                    .empty-tip { color: #9CA3AF; font-size: 13px; padding: 10px 0; }
                    .day-diary-block {
                        margin-bottom: 14px;
                        padding: 12px 14px;
                        background: #FFFDF5;
                        border: 1px solid #F3EED7;
                        border-radius: 10px;
                        .diary-content { font-size: 14px; color: #1F2937; line-height: 1.7; white-space: pre-wrap; }
                        .diary-meta { margin-top: 8px; }
                    }
                    .record-item {
                        border: 1px solid #E5E7EB;
                        border-radius: 10px;
                        padding: 12px;
                        margin-bottom: 10px;
                        background: #FAFBFC;
                        .record-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; flex-wrap: wrap; }
                        .record-emotion { display: inline-flex; align-items: center; gap: 5px; color: #4A90E2; font-size: 14px; font-weight: 600; }
                        .record-score { font-size: 13px; color: #6B7280; }
                        .record-time { margin-left: auto; font-size: 12px; color: #9CA3AF; }
                        .record-body .record-trigger { font-size: 13px; color: #6B7280; margin-bottom: 6px; }
                    }
                }

                .write-card {
                    .form-label { font-size: 14px; color: #374151; margin: 14px 0 8px; font-weight: 500; }
                    .emotion-grid { display: flex; flex-wrap: wrap; gap: 10px; }
                    .emotion-opt {
                        display: flex; flex-direction: column; align-items: center; gap: 4px;
                        padding: 10px 8px; border: 2px solid #E5E7EB; border-radius: 12px; cursor: pointer;
                        background: #F9FAFB; font-size: 13px; color: #374151; transition: all .15s;
                        &:hover { transform: translateY(-2px); }
                        &.active { border-color: #7ED321; background: #F0FDF4; }
                    }
                    .form-actions { margin-top: 24px; text-align: right; }
                }
            }
        }
    }
</style>