# 绪栈 EmoStack — 项目技术文档

> 本文档基于当前代码库实际状态整理，内容如实反映已实现功能与技术栈。
> 最后核对时间：2026-10-08

---

## 1. 项目简介

**绪栈（EmoStack）** 是一个心理健康/情绪疏导平台，面向普通用户提供 AI 心理疏导对话、情绪日记记录、心理知识科普与 RAG 检索增强；面向管理员提供内容管理与数据统计后台。

- 系统架构：前后端分离（REST + SSE）
- 前端：`web-frontend/`（Vue 3 + Vite）
- 后端：`backend/`（Spring Boot 3）
- 数据库：MySQL + Chroma（向量库）
- AI：Spring AI 对接阿里云百炼 GLM-5.3（对话）+ qwen embedding（检索）

---

## 2. 技术栈

### 2.1 后端（Spring Boot 3.5.15 / Java 17）

| 技术 | 用途 | 版本 |
|------|------|------|
| Spring Boot | Web 框架 | 3.5.15 |
| Java | 语言 | 17 |
| Spring Security | 认证授权（JWT 无状态） | Boot 内置 |
| java-jwt (auth0) | JWT 生成/校验 | 4.4.0 |
| MyBatis-Plus | ORM / 数据访问 | 3.5.7 |
| MySQL Connector/J | 数据库驱动 | Boot 内置 |
| Spring AI (OpenAI 兼容) | 大模型对话接入 | 1.0.0-SNAPSHOT |
| spring-ai-chroma-store | Chroma 向量库检索 | 1.0.0-SNAPSHOT |
| Hutool | 工具库（JSON、日期等） | 5.8.25 |
| Lombok | 样板代码简化 | Boot 内置 |
| spring-boot-starter-validation | 参数校验（jakarta） | Boot 内置 |

**说明**：AI 通过 Spring AI 以 OpenAI 兼容模式对接**阿里云百炼（MaaS）**模型，走 `compatible-mode/v1` 兼容接口：
- **对话模型**：`glm-5.3`（2026-10-07 由 glm-5.2 升级，原免费额度到期）
- **Embedding 模型**：`qwen3.7-text-embedding-flash`（用于 RAG 向量化，有免费额度）

### 2.2 前端（Vue 3.5 + Vite 7）

| 技术 | 用途 | 版本 |
|------|------|------|
| Vue | 框架 | ^3.5.24 |
| Vite | 构建/开发服务器 | ^7.2.4 |
| Vue Router | 路由（3 套布局） | ^4.6.4 |
| Pinia | 状态管理 | ^3.0.4 |
| Element Plus | UI 组件库（中文 locale） | ^2.13.2 |
| @element-plus/icons-vue | 图标 | ^2.3.2 |
| Axios | HTTP 请求 | ^1.13.4 |
| @microsoft/fetch-event-source | SSE 流式接收 | ^2.0.1 |
| ECharts | 数据图表（后台仪表盘） | ^6.0.0 |
| wangEditor | 富文本编辑器（后台文章） | ^5.1.23 |
| Markdown 渲染 | 前台文章 / AI 回复展示（MarkdownRenderer） | 内置组件 |
| Sass | 样式预编译 | ^1.97.2 |

### 2.3 运行环境

| 项 | 值 |
|----|----|
| 后端端口 | 1236 |
| 前端端口 | 5173（`host: true`，IPv4/IPv6 均监听） |
| MySQL | 本机 `localhost:3306`，库名 `mental_health_assistant`，账号 `root/root` |
| Chroma（向量库） | Docker，`localhost:8000`，v2 API |
| 前端代理 | Vite `/api` → `http://localhost:1236` |

---

## 3. 数据库设计（MySQL 10 张表 + Chroma 1 个 Collection）

### 3.1 MySQL（共 10 张表）

| 表名 | 用途 | 状态 |
|------|------|------|
| `user` | 用户（含 user_type 1=普通/2=管理员） | 已使用 |
| `consultation_session` | AI 咨询会话（含 last_emotion_analysis JSON） | 已使用 |
| `consultation_message` | 咨询消息（用户/AI，sender_type 区分） | 已使用 |
| `emotion_diary` | 情绪日记（每日一条：今日感想 + 睡眠质量） | 已使用 |
| `emotion_log` | 情绪记录（每天可多条：情绪/评分/触发因素/压力） | 已使用 |
| `knowledge_category` | 知识文章分类 | 已使用 |
| `knowledge_article` | 知识文章 | 已使用 |
| `sys_file_info` | 文件信息 | 已使用 |
| `ai_analysis_task` | AI 分析任务（任务队列） | **预留，无代码引用** |
| `user_favorite` | 用户收藏 | **预留，无代码引用** |

**说明**：`ai_analysis_task` 与 `user_favorite` 两张表已建但当前没有任何实体/Mapper/接口引用，属于预留设计，尚未实现功能。

### 3.2 Chroma 向量库

| 项 | 值 |
|----|----|
| Collection | `knowledge_articles` |
| 数据源 | 本地文件夹 `rag-knowledge/`（Markdown 文章，按 `##` 标题切块索引） |
| 检索方式 | 余弦/L2 距离相似度检索，`top_k=4`，`similarity-threshold=0.15` |
| 记录 id | 「文件名::序号」（片段级），metadata 存 `source`=文件名、`heading`=标题，保障幂等重建 |
| 部署 | 外部 Docker 容器（`emostack-chroma`），挂载命名卷 `chroma-data:/data` 持久化；collection 在容器首次就绪后预建 |

---

## 4. 已实现功能

### 4.1 用户端（前台，普通用户 user_type=1）

| 功能 | 说明 | 状态 |
|------|------|------|
| 注册 / 登录 / 退出 | JWT 认证，空手机号自动置 NULL 避免唯一索引冲突 | ✅ |
| 首页 | 「开始倾诉」「记录心情」入口（未登录跳登录页） | ✅ |
| **AI 心理疏导对话** | `/consultation`：SSE 流式输出；对话记忆（ChatMemory）；会话列表；历史消息回看；删除会话 | ✅ |
| **RAG 检索增强** | 知识类问题自动检索本地知识库，注入上下文后结合知识库内容作答；库外/无资料内容明示"知识库暂无相关内容" | ✅ |
| **安全边界约束** | 人设 + 优先级体系（P0 生命安全 > P1 安全边界 > P2 回答规范）常驻注入，自伤/他伤场景输出固定安全回复（含 12356 热线） | ✅ |
| **情绪花园** | 每次 AI 回复完成后自动情绪分析（GLM 二次调用），会话级展示：主导情绪/评分/风险等级/建议/治愈行动 | ✅ |
| **情绪日记（日历式）** | `/emotion-diary`：日历按月着色（多条情绪等分渐变 + 条数角标）；今日日记每日一条可追加/更新（防抖提交）；情绪记录每天可多条；历史记录按日期查看 | ✅ |
| 知识科普 | `/knowledge`：分类树 + 文章列表 + 文章详情（Markdown 渲染） | ✅ |

### 4.2 管理端（后台，管理员 user_type=2）

| 功能 | 说明 | 状态 |
|------|------|------|
| 数据仪表盘 | `/back/dashboard`：数据总览统计（ECharts 图表） | ✅ |
| 知识文章管理 | `/back/knowledge`：增删改查、发布状态切换、富文本编辑 | ✅ |
| 咨询记录管理 | `/back/consultations`：查看咨询会话 | ✅ |
| 情绪日志管理 | `/back/emotional`：查看/删除用户情绪记录（emotion_log） | ✅ |

### 4.3 权限模型

- JWT 无状态认证，`SecurityConfig` 配置公开路径（登录、注册等）
- 路由守卫 + `CurrentUserUtil` 按 `user_type` 控制访问范围：
  - `user_type=1`（普通用户）：可访问前台，后台/认证页重定向回首页
  - `user_type=2`（管理员）：访问前台任意页强制跳转后台仪表盘
- SSE 流式响应为 async 分发，已配置 `dispatcherTypeMatchers(ASYNC, ERROR).permitAll()` 避免二次认证中断连接

### 4.4 RAG 检索增强（本轮新增重点）

完整链路（里程碑 M0–M3 已完成）：

```
本地知识库 rag-knowledge/*.md
   │  （RagService.scanAndIndex，启动时 + 定时增量扫描）
   ▼
EmbeddingModel(qwen3.7-text-embedding-flash) 向量化
   ▼
Chroma(knowledge_articles) 存储 + 相似度检索
   │  （RagService.search，top_k=4, threshold=0.15）
   ▼
RagRouter 路由判断（关键词规则层 → 模型兜底层）
   ▼
PsychologicalSupportService 注入（人设 → RAG 片段 → 边界约束 P0>P1>P2）
   ▼
SSE 流式回答
```

关键设计：
- **切块索引**：按 `##` 标题将文章切为片段（id=`文件名::序号`，metadata 存 source+heading），短查询召回与命中分显著提高（9 文件 → 52 片段）
- **增量同步**：`@Scheduled` 定时扫描（60s），以文件为单位、内容 SHA-256 对比；变化文件通过 `delete(Filter.Expression source=文件名)` 清旧片段再 `addInBatches` 重建（按批 ≤20，规避阿里云 embedding 单次 batch 25 上限），保证幂等无残留
- **上下文检索**：指代型追问（如"怎么缓解这种感觉"）会拼接最近 4 条对话上下文作为检索 query，解决单轮无主题词问题
- **路由规则层关键词**（逗号分隔配置）：焦虑/抑郁/内耗/孤独/自卑/压力/失眠/情绪/怎么办/如何/什么是……；命中即走 RAG
- **路由兜底层**：规则层未命中时由 GLM-5.3 低温调用判断是否需要检索，模型失败自动降级为不检索（不阻塞对话）
- **边界约束**：`BoundaryLoader` 每 60s 检测 `system_boundary.md` 变更并缓存，每轮 prompt 常驻注入；缺失时告警降级

`system_boundary.md` 优先级体系（位于项目根目录）：
- **P0 生命安全**：识别轻生/自伤/自杀倾向 → 固定回复（含全国心理援助热线 12356），超越一切
- **P1 安全边界**：医疗诊断、药物、无关话题 → 拒绝/引导
- **P2 回答规范**：AI 身份（小绪）、语气、信息引用（不编造统计/来源，不确定明说）
- 任何规则冲突按 **P0 > P1 > P2** 执行

---

## 5. 后端接口清单

### 用户认证 `/api/user`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/login` | 登录 |
| POST | `/api/user/add` | 注册 |
| GET | `/api/user/current` | 当前登录用户 |
| POST | `/api/user/logout` | 退出登录 |

### AI 咨询 `/api/psychological-chat`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/psychological-chat/session/start` | 新建会话 |
| POST | `/api/psychological-chat/stream` | **流式对话（SSE，含 RAG）** |
| GET | `/api/psychological-chat/sessions` | 会话列表 |
| GET | `/api/psychological-chat/sessions/{id}/messages` | 会话消息 |
| DELETE | `/api/psychological-chat/sessions/{id}` | 删除会话 |
| GET | `/api/psychological-chat/session/{id}/emotion` | 会话情绪分析结果 |

### RAG 检索 `/api/rag`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rag/search` | 检索知识库，返回 id/source/score/content（接口测试用；对话层已内置检索，非前端调用） |

### 情绪日记 `/api/emotion-diary`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/emotion-diary` | 保存/更新今日日记（每日一条） |
| POST | `/api/emotion-diary/log` | 新增情绪记录（每天可多条） |
| GET | `/api/emotion-diary/my` | 我的记录分页 |
| GET | `/api/emotion-diary/month` | 按月返回当月日记+情绪（日历着色） |
| GET | `/api/emotion-diary/admin/page` | 后台分页 |
| DELETE | `/api/emotion-diary/admin/{id}` | 后台删除 |

### 知识文章 `/api/knowledge`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge/category/tree` | 分类树 |
| GET | `/api/knowledge/article/page` | 文章分页 |
| GET | `/api/knowledge/article/{id}` | 文章详情 |
| POST | `/api/knowledge/article` | 新增文章 |
| PUT | `/api/knowledge/article/{id}` | 更新文章 |
| PUT | `/api/knowledge/article/{id}/status` | 状态切换 |
| DELETE | `/api/knowledge/article/{id}` | 删除文章 |

### 其他
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/file/upload` | 文件上传 |
| GET | `/api/data-analytics/overview` | 数据总览（后台仪表盘） |
| GET | `/api/test` | 临时测试接口（调试残留） |

---

## 6. 目录结构

### 后端 `backend/src/main/java/org/example/aispingboot/`
```
controller/       接口入口层（User / PsychologicalChat / Rag / EmotionDiary / Knowledge / File / DataAnalytics / Test）
service/          业务逻辑层（含 convert/ 实体转换）
mapper/           MyBatis-Plus 数据访问
entity/           表实体（8 个，与使用中的表对应）
DTO/              command/ 请求对象 · response/ 响应对象 · PageQueryDTO 分页
config/           SecurityConfig / JwtConfig / ChatClientConfig（Embedding+VectorStore）/ MybatisPlusConfig / WebConfig
common/           Result（统一响应）/ ResultCode / GlobarExceptionHandler（全局异常）
enumClass/        UserType / UserStatus
exception/        BusinessException
util/             JwtTokenUtil / JwtAuthticationFilter / CurrentUserUtil / ResponseUtil
AiService/        PsychologicalSupportService（对话+RAG+情绪分析）/
                  RagService（扫描+索引+检索+增量同步）/ RagRouter（路由判断）/
                  BoundaryLoader（安全边界注入）/ PromptManage（提示词）/ StructOutPut
resources/        application.yml（DB / JWT / AI / RAG 配置）
```
> 注：启动类名为 `AiSpingbootApplication`、异常处理类名为 `GlobarExceptionHandler`，沿用了初始命名拼写，未改名。

### 知识库与安全约束（项目根目录）
```
rag-knowledge/    本地 RAG 知识库文件夹（Markdown 心理文章，9 篇）
system_boundary.md   AI 安全与行为约束（P0/P1/P2 优先级体系）
RAG_PLAN.md        RAG 实施规划与风险记录
```

### 前端 `web-frontend/src/`
```
views/            页面：home / consultation / emotionDiary / frontendKnowledge / articleDetail
                  （后台）dashboard / knowledge / consultations / emotional / login / register
components/       布局：FrontendLayout / BackendLayout / AuthLayout
                  通用：Navbar / Sidebar / PageHead / TableSearch
                  富文本与渲染：RichTextEditor / MarkdownRenderer / ArticleDialog / HelloWorld（脚手架残留，未使用）
api/              frontend.js（前台接口）/ admin.js（后台接口）
stores/           admin.js（Pinia）
utils/            request.js（Axios 封装 + token 拦截器）
router/           index.js（/auth、/、/back 三套布局路由）
```

---

## 7. 启动方式

```powershell
# 1. 启动 MySQL（本机 root/root，库 mental_health_assistant）

# 2. 启动 Chroma 向量库（Docker，端口 8000；如未启动，RAG 降级为无检索模式不影响对话）
docker run -d --name emostack-chroma -p 8000:8000 -v chroma-data:/data chromadb/chroma
#   使用 `chroma-data` 持久卷，容器/Docker 重启后索引数据不丢、无需重新向量化
#   首次仅在卷为空时，容器就绪后手动预建 collection：knowledge_articles
curl -X POST http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections \
  -H "Content-Type: application/json" -d '{"name":"knowledge_articles"}'

# 3. 后端（端口 1236）
cd backend
.\mvnw.cmd spring-boot:run
#   启动时自动扫描 rag-knowledge/ 建档；NAS 全链路日志见控制台

# 4. 前端（端口 5173）
cd web-frontend
npm install
npm run dev
```

访问 `http://localhost:5173/`（内置浏览器如白屏，改用 `http://127.0.0.1:5173/`）。

内置测试账号（密码均为 `123456`）：
- 普通用户：`test` / `ces` / `emo`
- 管理员：`admin`

---

## 8. AI / RAG 配置说明

配置文件：`backend/src/main/resources/application.yml`

```yaml
spring:
  ai:
    openai:
      base-url: https://ws-xxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
      api-key: sk-xxx
      chat:
        completions-path: /chat/completions   # 修正路径拼接
        options:
          model: glm-5.3
          temperature: 0.7

rag:
  boundary-file: ../system_boundary.md   # 安全与行为约束文档（项目根目录）
  folder: ../rag-knowledge              # 本地知识库文件夹（项目根目录）
  collection: knowledge_articles        # Chroma collection 名称
  chroma-url: http://localhost:8000     # Chroma 服务地址
  top-k: 4                              # 检索返回条数
  similarity-threshold: 0.15            # 相似度阈值（1 - L2距离，即 distance≤0.85）
  scan-interval-ms: 60000               # 增量同步扫描间隔
  router-model-enabled: true            # 规则层未命中是否启用模型兜底
  router-keywords: 焦虑,抑郁,内耗,...     # 规则层触发关键词（逗号分隔）
```

- **对话模型**：GLM-5.3，经 Spring AI `OpenAiChatModel` 调用百炼兼容接口
- **Embedding 模型**：`qwen3.7-text-embedding-flash`（`ChatClientConfig` 中 `OpenAiEmbeddingModel` + `OpenAiEmbeddingOptions`）
- **向量库**：`ChromaVectorStore`（ChromaApi v2，指定 `default_tenant`/`default_database`）
- **提示词**：`AiService/PromptManage.java`（心理疏导人设 + 情绪分析 JSON 模板）；对话流程注入顺序为「人设 → RAG 片段 → 边界约束」
- **对话记忆**：`ChatMemory`（conversationId 维度的窗口记忆，保留最近 30 条）
- **情绪分析**：每轮回复完成后对整段对话再调用一次模型，解析主导情绪/评分/风险/建议写入会话 `last_emotion_analysis`，供情绪花园展示（属额外模型调用，会多耗额度并延长响应时间）

---

## 9. 已知遗留 / 待完善

- `ai_analysis_task`、`user_favorite` 两张表为预留，功能未实现
- `/api/test` 为调试残留接口，可移除
- 前端 `HelloWorld.vue` 为脚手架残留组件，未使用
- Spring AI 依赖为 `1.0.0-SNAPSHOT`，后续可升级正式版
- 情绪日记/记录的删除入口仅后台提供，用户端暂不可自主删除历史记录
- RAG 切块按 `##` 标题划分，未进一步做重叠滑动窗口或语义切块；极深文章首屏主标题下的引言会与第一章节合并为一块
- Chroma 容器 `emostack-chroma` 已挂载 `chroma-data:/data` 持久卷，Docker 重启后索引数据保留；仅当卷被删除时，后端 `scanAndIndex` 会自动全量重建（需重新向量化消耗 embedding 额度）