# 绪栈 AI 咨询 RAG 方案规划

> 状态：M3 已完成（增量同步 + 全链路回归），RAG 三个里程碑全部交付
> 制定时间：2026-10-05
> 前置验证：百炼 `qwen3.7-text-embedding-flash` 嵌入接口已确认可用（免费额度 1M tokens，至 2026/11/30）；AI 模型已切换为 `glm-5.3`（推理型）

---

## 1. 背景与目标

### 1.1 现状问题

AI 心理疏导目前完全依赖大模型自身知识回答，存在两个短板：

- **约束不足**：模型可能凭印象"编造"知识，回答缺乏依据，难以保证与平台知识库（`knowledge_article` 文章）口径一致。
- **增强不足**：平台维护的知识文章（心理科普、应对方法）没有被 AI 利用，回答泛化、不够深入。

### 1.2 目标

引入 **RAG（检索增强生成）**，让 AI 在回答知识类问题时**先检索平台知识库，再基于检索结果回答**，实现：

| 目标 | 说明 |
|------|------|
| 约束 | AI 只依据知识库资料回答，资料不足时明确说明，不编造 |
| 增强 | 回答内容来自真实维护的文章，专业、可信、贴合平台 |
| 按需 | **不是每轮都检索**：问知识/心理问题时才触发，日常陪伴聊天走原流程，零额外成本 |

---

## 2. 要做什么（功能范围）

### 2.1 核心功能

1. **本地知识库扫描**：扫描 `rag-knowledge/` 文件夹下的 `.md` / `.txt` 文件（**文件名即文章标题**），切块、向量化，写入 Chroma。
2. **按需触发路由**：每轮对话前判断"这条消息是否需要知识库支撑"：
   - 需要 → 向量检索 → 注入上下文 → 增强回答
   - 不需要 → 完全走原对话流程
3. **增强回答**：命中 RAG 时，把检索片段 + 约束提示词拼入该轮 prompt，由 GLM 流式回答。
4. **文件变更同步**：启动时全量扫描 + 运行时定时检查文件变更（新增/修改/删除），增量更新向量库。

### 2.2 明确不做（本期范围外）

- 情绪花园分析（`analyzeEmotion`）不走 RAG，保持现状
- 不引入个人数据（咨询历史、情绪记录）作为检索源
- 数据库 `knowledge_article` **不再进入 RAG**，仅保留前台知识页展示（方案 A，用户已确认）
- 不做其他来源的向量化（FAQ、分类字典等）

---

## 3. 怎么做（方案设计）

### 3.1 总体架构

```
rag-knowledge/ 本地文件夹（.md / .txt，文件名即标题）
        │  启动全量扫描 + 定时增量检查（新增/修改/删除）
        ▼
  切块 → embedding（qwen3.7-text-embedding-flash）→ Chroma
        │
system_boundary.md（项目根目录，安全与行为约束）
        │  定时检测文件变更 → 常驻 SystemMessage（不进 RAG）
        ▼
用户消息 + 最近 2-3 轮上下文
        │
        ▼
┌─ RagRouter 意图路由 ──────────────────────────────┐
│  ① 规则层：命中关键词表 → 判定 needRag = true      │
│  ② 模型层：未命中规则 → GLM 轻量判断               │
│     （返回 {"needRag": true/false}，低温）         │
└──────────────────────────────────────────────────┘
   │ needRag = true                       │ needRag = false
   ▼                                       ▼
向量检索（Chroma，Docker 8000 端口）  原对话流程
topK=3~5 + 相似度阈值过滤                  （心理疏导人设 + 对话记忆）
   ▼
注入该轮 prompt：
SystemMessage(心理疏导人设) + SystemMessage(边界约束) 
+ [命中时] 检索片段 + RAG 约束提示词
   → GLM-5.3 流式回答（SSE）
```

### 3.2 处理流程（按轮次）

1. 用户发送消息（SSE 接口 `streamPsychologicalChat` 入口）
2. **路由判断**：取最近 2-3 轮对话 → 规则层 → 模型层
3. **命中 RAG**：
   - 当前消息 embedding 化 → 向量检索 topK
   - 过滤相似度低于阈值的片段
   - 检索片段 + 约束提示词 + 心理疏导人设 → GLM-5.3 流式输出
4. **未命中**：原样走现有对话流程（人设 + 记忆），无任何额外调用
5. 回复完成后，情绪分析（现有逻辑）不受影响
6. **只影响当轮**：下一轮重新路由判断

### 3.3 路由判断规则（示例，可调）

| 走 RAG | 不走 RAG |
|--------|----------|
| 问症状/病因/治疗方法/药物 | 情绪倾诉（"我今天好累"） |
| 心理学术语解释 | 分享日常 |
| "我该怎么做"类求助 | 表达感受 |
| 问平台知识、心理知识科普 | 闲聊寒暄 |

关键词表按主题分组维护（症状、疾病、治疗、睡眠、情绪障碍等），放在配置中，可随时调整。

### 3.4 约束提示词（命中轮注入）

在 `PromptManage` 中追加约束要求：

- 优先使用检索到的资料回答问题，并说明信息来源
- 资料无法覆盖时，坦诚说明"知识库暂无相关内容"，**不得编造**
- 保持心理疏导师的语气（与原有 system prompt 合并）

### 3.5 边界与安全约束（system_boundary.md）【硬约束】

**原则：安全规则不进 RAG**。RAG 是相似度检索，可能搜不到安全规则——安全约束必须**每轮常驻**，不依赖检索命中。

| 项 | 设计 |
|----|------|
| 文件 | 项目根目录 `system_boundary.md`（已由用户编写，含优先级总则 P0/P1/P2、通用边界、无关话题、轻生自伤 P0 固定回复、回答规范、风险场景；AI 身份为「小绪」） |
| 性质 | **系统级硬约束**：每轮对话注入 `SystemMessage`（与人设并列常驻），不走向量检索 |
| 注入方式 | Prompt = `SystemMessage(心理疏导人设·小绪)` + `SystemMessage(边界文档全文)` + [命中时] 检索片段 + RAG 约束提示词 |
| 优先级 | 三级体系 **P0 生命安全 > P1 安全边界 > P2 回答规范**，任何冲突按 P0>P1>P2 执行；高于知识库与模型知识 |
| 刷新 | 定时（60 秒）检测文件变更，改完无需重启 |
| 降级 | 文件缺失/读取失败 → 记录告警日志，跳过注入、**不阻塞对话** |
| 隔离 | `RagService` 只扫描 `rag-knowledge/`，天然不检索边界文档；该目录约定保持清晰，勿将约束类文件放入 `rag-knowledge/` |

---

## 4. 使用什么做（技术选型）

### 4.1 选型清单

| 组件 | 选型 | 理由 |
|------|------|------|
| 向量库 | **Chroma（Docker 容器，端口 8000，`chroma-data` 数据卷持久化）** | 独立持久化向量库，重启不丢向量、**免重复 embedding**；Spring AI 官方支持（`spring-ai-chroma-store`，需新增依赖）；检索毫秒级 |
| 嵌入模型 | **阿里云百炼 `qwen3.7-text-embedding-flash`**（OpenAI 兼容 `/embeddings`） | 免费额度 1M tokens（至 2026/11/30）；**已验证可用**，返回 1024 维向量 |
| 对话模型 | **`glm-5.3`**（已切换） | 推理型模型，Spring AI 自动忽略 `reasoning_content`，已验证流式对话正常 |
| RAG 编排 | Spring AI `RetrievalAugmentationAdvisor`（或 `QuestionAnswerAdvisor`） | Spring AI 1.0 官方 RAG 组件，挂 ChatClient advisors 链 |
| 路由规则层 | 关键词表（application.yml 配置） | 零成本、可解释、可调 |
| 路由模型层 | GLM-5.3 轻量判断（低温、JSON 输出） | 语义理解准确，规则未命中时兜底 |

### 4.2 涉及代码改动

| 位置 | 改动 |
|------|------|
| `pom.xml` | 新增 `spring-ai-chroma-store` 依赖（与现有 `spring-ai-starter-model-openai` 同版本） |
| `application.yml` | 新增 embedding 配置（模型名、路径，注意路径拼接避免 chat 曾踩的重复 `/v1` 坑）、Chroma 连接配置（host/port/collection 名）、本地知识库文件夹路径、定时扫描间隔；关键词表 |
| `config/ChatClientConfig.java` | 新增 `EmbeddingModel`、`ChromaVectorStore` Bean；咨询 ChatClient 挂 RAG advisor |
| 新增 `AiService/RagRouter.java` | 规则层 + 模型层路由判断（输入最近几轮上下文） |
| 新增 `AiService/RagService.java` | 扫描本地文件夹（启动全量 + 定时增量）、切块、建索引、检索封装 |
| `AiService/PromptManage.java` | 追加 RAG 约束提示词 |
| 新增 `AiService/BoundaryLoader.java` | 读取并缓存 `system_boundary.md`（60 秒检测变更），供每轮对话注入；缺失时告警降级 |
| 部署 | Docker 容器 `chromadb/chroma`（端口 8000，数据卷 `chroma-data`）——**已部署并验证健康** |
| 前端 | **无改动**（SSE 接口与数据结构不变） |

### 4.3 关键参数（初值，可调）

| 参数 | 初值 |
|------|------|
| topK | 3~5 |
| 相似度阈值 | ~0.3（低于则视为无相关内容） |
| 路由判断温度 | 0.1（低温度，稳定输出 JSON） |
| 检索上下文轮次 | 最近 2-3 轮 |
| Chroma collection | `knowledge_articles`（向量维度 1024，与 embedding 模型一致） |
| Chroma 连接 | `localhost:8000`（Docker 已部署） |
| 知识库文件夹 | `rag-knowledge/`（项目根目录，已创建） |
| 文件格式 | `.md` / `.txt`（文件名即标题，UTF-8） |
| 定时扫描间隔 | 60 秒（可调） |
| 边界文件 | 项目根目录 `system_boundary.md`（后端运行时相对路径 `../system_boundary.md`，走配置项） |

---

## 5. 有什么用（价值与成本）

### 5.1 收益

- **回答更可信**：知识类回答有真实文章依据，可溯源
- **平台知识被利用**：后台维护的文章真正服务到用户，内容建设不浪费
- **成本可控**：只有"疑似知识类"消息才触发检索；规则命中不花模型钱，纯闲聊零成本
- **体验不降级**：闲聊场景完全走原流程，不被检索打断

### 5.2 成本与权衡

| 场景 | 额外成本 |
|------|----------|
| 未命中（闲聊） | 0 模型调用 |
| 规则命中 | 仅 1 次向量检索（毫秒级），0 模型调用 |
| 模型兜底命中 | +1 次路由判断调用（~0.5–1s 延迟、少量 token）+ 1 次检索 |
| 向量库 | Chroma 持久化在 Docker 数据卷，**重启免重新 embedding**；首次建索引按 `rag-knowledge/` 实际文件量计（几篇文章 ≈ 数千 tokens） |
| 嵌入用量 | 文件向量化（新增/修改时按需）+ 命中时单条消息嵌入，量级极小；**免费额度内（1M tokens，至 2026/11/30）零成本** |

**注意**：glm-5.3 为推理型模型，回答本身会"先思考后输出"，比 5.2 稍慢；这是模型特性，非 RAG 引入。另：百炼 `qwen3.7-text-rerank` 免费额度存在，但 OpenAI 兼容端点不支持 rerank（实测 `/rerank` 无响应），如需精排需走 DashScope 原生 API，本期不做。

---

## 6. 实施计划

### 6.1 实施步骤

1. **Chroma 部署**：✅ 已完成（Docker 容器 `chromadb/chroma`，端口 8000，健康验证通过）
2. **知识文件夹**：✅ 已创建（`rag-knowledge/`，用户写入 .md/.txt）
3. **依赖与配置**：✅ 已完成（pom 新增 `spring-ai-chroma-store`；yml 加 embedding（`qwen3.7-text-embedding-flash`）、Chroma 连接、文件夹路径与扫描间隔；`ChatClientConfig` 新增 `EmbeddingModel`、`ChromaVectorStore` Bean）
4. **建索引**：✅ 已完成（`RagService` 启动时扫描 `rag-knowledge/` 9 个 .md 文件 → 向量化 → 写入 Chroma；Document id 为文件名，重复启动 upsert 覆盖不累积）
5. **检索自测**：✅ 已完成（`/api/rag/search` 接口验证：`情绪内耗`→命中《什么是情绪内耗…》score 0.42、`如何温和地拒绝`→0.43、`正念练习`→0.39；`美食菜谱推荐` 低于阈值 0.3 正确返回空）
6. **路由层**：✅ 已完成（`RagRouter`：规则层关键词表（`rag.router-keywords` 配置）→ 模型层兜底（GLM-5.3 低温判断 needRag）；上下文取最近对话记忆；模型失败降级为不检索）
7. **接入对话**：✅ 已完成（`streamPsychologicalChat` 中：needsRag=true → 检索命中注入 `buildRagContext` 片段、未命中注入 `RAG_MISS_PROMPT` 明示"暂无相关内容"；SSE 结构不变；注入顺序：人设 → RAG → 边界，边界优先级最高）
8. **边界约束注入**：✅ 已完成（BoundaryLoader 读取 `system_boundary.md`，每轮 prompt 常驻注入；60s 变更检测；缺失告警降级；P0 固定回复已验证）
9. **约束提示词**：✅ 已完成（四类场景实测通过：知识库内→基于资料回答；库外心理知识→明说"暂无相关内容"+谨慎补充；库外无关话题→礼貌拒绝引导；闲聊→不检索走原流程；P0"我想死"→固定安全回复不受 RAG 影响）
10. **增量同步**：✅ 已完成（`RagService` 增加 `@Scheduled` 定时扫描 + 内容 SHA-256 签名对比：新增/修改 → upsert 覆盖同 id；删除 → `vectorStore.delete`；60s 间隔，实测新增/修改/删除均在下一周期生效）
11. **全链路回归**：✅ 已完成（SSE 流式正常；多轮记忆正常（追问"那怎么缓解呢"正确衔接上下文）；未命中提示正常；边界约束不受影响；生产配置 `scan-interval-ms=60000` 已恢复）

### 6.2 验收标准

- 问知识库内问题 → 回答内容与文件内容一致且有据可查（能提到来源文件名）
- 问库外问题 → AI 明说"知识库暂无相关内容"，不编造
- 闲聊 → 不触发检索，回复速度与现状一致
- 修改/新增 `rag-knowledge/` 文件 → 最多 60 秒内下次提问能检索到新内容
- 情绪花园、SSE 流式、多轮记忆均不受影响
- **边界约束每轮生效**：自伤/轻生表述 → 输出固定安全回复；无关话题 → 礼貌拒绝（即使同时命中 RAG）

### 6.3 里程碑

| 阶段 | 内容 | 可交付验证 |
|------|------|-----------|
| M1 | embedding 配置 + 建索引 + 检索 | ✅ 已完成：`/api/rag/search` 命中相关文章（含 source/score），无关查询按阈值过滤为空 |
| M2 | 路由层 + 接入对话 | ✅ 已完成：四类实测通过（知识库内命中注入 / 库外心理知识未命中明示 / 库外无关话题拒绝 / 闲聊不检索）；P0 固定回复不受影响 |
| M3 | 增量同步 + 回归 | ✅ 已完成：新增/修改/删除均在下一扫描周期（60s）生效；SSE、多轮记忆、未命中提示、边界约束全回归通过 |

---

## 7. 风险与注意

1. **依赖与 API 兼容**：`spring-ai-chroma-store`（1.0.0-SNAPSHOT）的 API 细节可能随版本微调，以实际编译为准；Chroma v2 API 已确认可用（当前部署版本不再支持 v1 端点）
   - **M1 实测两个坑（已解决）**：① Spring AI 默认查 `SpringAiTenant`/`SpringAiDatabase`，与 Chroma 默认 `default_tenant`/`default_database` 不匹配 → 已在 `ChatClientConfig` 显式指定 tenant/database；② 该版本 `ChromaApi.getCollection` 对"集合不存在"的错误消息按 `does not exists`（Chroma 旧拼写）匹配，新 Chroma 返回 `does not exist` 导致 `initializeSchema(true)` 无法自动建集合 → 需在 Chroma 中预建 collection（或保持现有 collection 不删）
2. **百炼 embedding 免费额度**：`qwen3.7-text-embedding-flash` 免费额度 1M tokens 至 2026/11/30 到期；到期前需确认续期或切换付费模型（量小，成本可忽略）；注意接口 rate limit
3. **glm-5.3 推理输出**：reasoning 字段已确认被 Spring AI 忽略；若流式中出现 reasoning delta 影响解析，需针对性处理（实施时验证）
4. **Chroma 服务依赖**：后端启动依赖 Chroma 容器可用；若容器未启动，需降级处理（实施时在检索处加 try-catch，失败走原对话流程）
5. **关键词表误判**：规则误命中/漏命中可通过模型层兜底与关键词表迭代修正
6. **边界文件路径/变更**：后端运行时工作目录是 `backend/`，相对路径需指到项目根（`../system_boundary.md`，用配置项避免硬编码）；文件被编辑时靠定时检测刷新（≤60s），极端情况下一两轮对话可能仍用旧约束（可接受，降级日志会提示）
7. **边界与知识冲突**：边界文档已自述"优先级最高"，但 prompt 拼接顺序同样重要（边界 SystemMessage 置于检索片段之后、靠近用户输入），实施时验证冲突场景
8. **检索质量（已修复，2026-10-07）**：三层修复后多轮指代型提问可稳定命中——① `router-keywords` 原为 YAML 列表，`@Value` 无法注入（规则层一直空转，全靠模型兜底），已改为逗号分隔字符串；② 检索 query 拼接最近 4 条对话上下文（`buildSearchQuery`），解决"怎么缓解这种感觉"无主题词问题；③ `similarity-threshold` 0.3 → 0.15（对应 L2 distance ≤ 0.85），孤独感文档实测距离 0.80 原会被误过滤；④ 路由新增指代型提问回溯（`isReferentialQuery` + 上下文关键词），不再依赖模型判断
8b. **切块索引（已完成，2026-10-08）**：`RagService` 改为按 `##` 标题切块——每个标题段为一个 Document，id=`文件名::序号`，metadata 存 `source`（文件名）与 `heading`（标题）。9 文件 → 52 片段。同文件多片段返回，短查询召回与命中分提高。增量同步按文件整体重建：利用 `vectorStore.delete(Filter.Expression)`（`eq source=文件名`）先清旧片段再 `add` 新片段，保证幂等（已验证新增/更新/删除全链路）。**坑 A**：阿里云 embedding 单次 batch 上限 25，全量向量化会被 400 拒绝，已加 `addInBatches`（每批 20）
9. **SSE 异步认证（已修复，2026-10-07）**：流式响应 async dispatch 时 Security 链二次认证抛 Access Denied，导致连接被掐断（日志 ERROR + 客户端收到不完整流）。已加 `.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`
10. **Chroma 记录幂等性**：Document id 使用「文件名::序号」，增量同步以文件为单位，通过 `delete(Filter.Expression source=文件名)` 清旧再重建，避免重复/残留；collection 由外部预建，若误删需手动重建（可加启动时自检）
