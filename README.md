# 绪栈 EmoStack · 心理健康 AI 助手

基于 Spring Boot + Vue3 的前后端分离心理支持应用。内置 AI 心理疏导对话（GLM-5.3）、情绪花园 / 情绪日记、心理资讯、情绪数据看板，并通过 **RAG（检索增强生成）** 让 AI 基于本地知识库回答，同时受安全边界约束（自伤/他伤等场景固定回复）。

## 功能一览

- **AI 咨询对话**：流式（SSE）心理陪伴，多轮上下文记忆，知识库检索增强（RAG）
- **安全边界**：`system_boundary.md` 常驻注入，三级优先级 P0 生命安全 > P1 安全边界 > P2 回答规范；P0 场景输出固定安全回复与心理援助热线
- **情绪花园 / 情绪日记**：日历视图按日期展示，情绪标签可视化
- **心理资讯**：文章 + 分类管理（后台）
- **情绪数据看板**：统计分析
- **用户系统**：注册 / 登录（JWT）、角色权限（普通用户 / 管理员）

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 · Spring Boot 3.5 · MyBatis-Plus · Spring Security · JWT · Spring AI |
| 前端 | Vue 3 · Vite · Element Plus · Pinia · Axios |
| 数据库 | MySQL 8.0 |
| 向量库 | Chroma（Docker 容器，挂载 `chroma-data` 数据卷持久化） |
| AI | 阿里云百炼智漾 GLM-5.3（对话） · qwen3.7-text-embedding-flash（Embedding） |

## 目录结构

```
EmoStack
├── backend/            # Spring Boot 后端（端口 1236）
│   └── src/main/resources/application-example.yml   # 配置模板（占位符）
├── web-frontend/       # Vue3 前端（端口 5173）
├── rag-knowledge/      # RAG 本地知识库源文件（Markdown，按 ## 切块）
├── system_boundary.md  # AI 安全与行为边界约束（常驻注入）
├── PROJECT_DOCUMENTATION.md
└── RAG_PLAN.md
```

## 快速启动

> ⚠️ 敏感配置不入库：`application.yml` 被 `.gitignore` 忽略。
> 首次使用请复制模板并填入真实密钥：

```bash
cd backend/src/main/resources
cp application-example.yml application.yml
# 编辑 application.yml，填入 OPENAI_API_KEY、JWT_SECRET、DB_PASSWORD、专属 base-url
```

1. **MySQL**：库 `mental_health_assistant`，账号按本地配置
2. **Chroma**（端口 8000，数据卷持久化，Docker 重启数据不丢）：

   ```bash
   docker run -d --name emostack-chroma -p 8000:8000 -v chroma-data:/data chromadb/chroma
   # 仅当卷为空时，首次预建 collection：
   curl -X POST http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections \
     -H "Content-Type: application/json" -d '{"name":"knowledge_articles"}'
   ```

3. **后端**（端口 1236）：

   ```bash
   cd backend
   ./mvnw.cmd spring-boot:run
   # 启动时自动扫描 rag-knowledge/ 建立 RAG 索引
   ```

4. **前端**（端口 5173）：

   ```bash
   cd web-frontend
   npm install
   npm run dev
   ```

## RAG 说明

- 数据源为本地文件夹 `rag-knowledge/`，按 `##` 标题自动切块，id = `文件名::序号`
- 每 60s 定时增量同步——新增 / 修改 / 删除文件均自动生效，无需手动重建
- 检索注入流程：人设 → RAG 片段 → 安全边界（边界优先级最高）
- 关键词规则 + 模型兜底的双层路由判断是否检索；未命中时 AI 明示"知识库暂无相关内容"，不编造

## 安全

- 真实 API Key、JWT secret、数据库密码**不会提交**到仓库（见 `.gitignore`）
- AI 被约束为心理支持助手（"小绪"），不提供医疗诊断；识别到自伤/他伤风险时按 P0 输出固定安全回复并引导求助