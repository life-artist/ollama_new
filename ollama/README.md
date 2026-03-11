## 项目简介：基于 Spring Boot 的 DeepSeek 智能聊天助手

本项目是一个基于 **Spring Boot 3** 与 **DeepSeek 大模型 API** 的全栈聊天应用，提供类 ChatGPT 的对话体验。  
前端使用自定义的 Web 聊天界面，支持多会话管理、对话历史与「聊天记忆」持久化、Markdown 渲染，以及 **文档上传（.txt / .pdf / .docx）+ 结合上下文回答** 等功能，后端通过 REST 接口统一封装模型调用与对话数据管理。

该 README 也可以作为后续在简历中提炼的一段「项目经历」基础素材。

---

## 核心功能概览

- **多轮对话与上下文记忆**
  - 支持连续多轮对话，后端会将上下文拼接为 `messages` 列表传给 DeepSeek 模型。
  - 单独维护一份 `chatMemory`，用于为每个会话保存长期对话记忆。

- **多会话管理（类似 ChatGPT 左侧对话列表）**
  - 前端左侧侧边栏展示「对话列表」，支持：
    - 新建对话（自动使用首条用户消息作为会话标题）
    - 切换不同会话
    - 删除单个会话
  - 会话历史通过后端接口持久化为本地 JSON 文件，服务重启后仍可恢复。

- **对话历史与记忆持久化**
  - 对话历史：`chat_history.json`
  - 聊天记忆：`chat_memory.json`
  - 使用后端 REST API + 本地文件存储实现，无需数据库即可落地部署。

- **文档上传 + 上下文增强问答**
  - 在聊天输入框下方支持上传单个文档：
    - 支持 `.txt / .pdf / .docx`
  - 后端使用 **Apache PDFBox** / **Apache POI** 解析文件内容，将文档内容与当前问题拼接后一起发送给 DeepSeek，实现「带文档的问答」。

- **Markdown 消息渲染 & 思维过程分块展示**
  - 使用 `marked.js` 将模型返回文本按 Markdown 渲染，支持标题、列表、代码块、表格等。
  - 支持解析消息中的 `<think>...</think>` 内容，将「思考过程」与正式回答分块展示：
    - `<think>` 内容以灰色斜体框展示
    - 其余内容按 Markdown 正常渲染

- **停止生成（前端体验优化）**
  - 前端提供「终止」按钮，可中断等待中的回复（前端逻辑停止 + 给后端发送 `/chat/api/stop` 请求）。
  - 虽然当前 DeepSeek API 尚未支持真正中断，但从用户体验角度做了完整的交互流程。

---

## 系统架构与模块设计

### 整体架构

- **前端**：纯 HTML + CSS + 原生 JavaScript
  - 使用 `axios` 与后端进行 AJAX 通信
  - 使用 `marked.js` 做 Markdown 渲染
  - 单页应用式聊天界面（`templates/chat.html`）

- **后端**：基于 Spring Boot 3.2.3 的 REST 服务
  - 控制层：`controller` 包
  - 服务层：`service` 包
  - 模型：`model` 包（`ChatMessage` / `ChatRequest` / `ChatResponse` 等）
  - 配置：`application.properties` + `application.yml`

- **第三方服务**
  - DeepSeek Chat Completion API（`https://api.deepseek.com/v1/chat/completions`）

### 主要后端模块

- `OllamaApplication.java`
  - 标准 Spring Boot 启动类，负责启动整个 Web 应用。

- `DeepSeekService.java`
  - 封装 DeepSeek API 调用逻辑：
    - 从配置中读取 `deepseek.api.key`、`deepseek.api.url`、`deepseek.model`
    - 组装请求体（包括 `model`、`messages`、`temperature`、`max_tokens` 等）
    - 使用 `RestTemplate` 发送 HTTP POST 请求
    - 从返回的 JSON 中解析出第一个 `choice` 的 `message.content` 作为模型回复
    - 对异常情况进行捕获和降级处理（返回中文错误提示）

- `OllamaClientController.java`
  - 核心聊天控制器，路径前缀 `/chat`：
  - **页面渲染**
    - `GET /chat` → 返回 `chat.html` 模板，渲染前端聊天页面。
  - **基础文本对话接口**
    - `POST /chat/api/message`
    - 请求体为 `ChatRequest`，包含：
      - 历史 `messages`（角色 + 内容）
      - 当前会话的 `chatMemory`
    - 将消息列表转换为 DeepSeek API 期望的结构后，调用 `DeepSeekService.generateResponse` 获取模型回复。
    - 返回 `ChatResponse`：
      - `reply`：模型文本回复
      - `chatMemory`：更新后的记忆（包含新一轮 user / assistant 消息）
  - **带文件的对话接口**
    - `POST /chat/message-with-file`
    - 请求参数：
      - `file`：上传的文档
      - `message`：当前用户输入的文本
      - `messages`：历史对话 JSON 字符串
    - 处理流程：
      1. 校验文件是否为空、类型是否为 `.txt / .pdf / .docx`
      2. 使用 `readFileContent` 方法解析文本内容
      3. 将文本内容拼接到当前用户输入中（`message + "\n\n文件内容：\n" + fileContent`）
      4. 构造 `messages` 列表并调用 `DeepSeekService`
      5. 将用户消息与模型回复追加进历史，并调用 `saveToHistory`（可扩展为真正持久化）
  - **终止生成接口**
    - `POST /chat/api/stop`：目前作为前端交互占位实现，返回「已终止生成」。

- `ChatHistoryController.java`
  - 路径前缀 `/chat/api/history`
  - **GET** `/chat/api/history`
    - 从 `chat.history.path` 指定的 JSON 文件读取所有对话记录
    - 若文件不存在或为空，则返回空 Map
  - **POST** `/chat/api/history`
    - 接收整个对话历史 Map，序列化为 JSON 并写入文件
  - **DELETE** `/chat/api/history/{chatId}`
    - 从文件中加载历史，删除指定对话 ID，再写回文件
  - 技术点：
    - 使用 `ObjectMapper` 进行 JSON 序列化 / 反序列化
    - 使用 `Files` + `Paths` 操作本地文件

- `ChatMemoryController.java`
  - 路径前缀 `/chat/api/memories`
  - 与 `ChatHistoryController` 类似，但用于管理每个会话的 **聊天记忆**：
    - `GET /chat/api/memories`：读取 `chat_memory.json`
    - `POST /chat/api/memories`：写入 `chat_memory.json`

---

## 前端交互设计

前端页面位于 `src/main/resources/templates/chat.html`，主要技术栈：

- 原生 HTML + CSS + JavaScript
- `axios`：发起 AJAX 请求
- `marked.js`：将 AI 回复渲染为 Markdown

### 布局与交互

- **布局结构**
  - 左侧：侧边栏（对话列表 + 新建对话按钮）
  - 右侧：主对话区域（消息气泡 + 输入框 + 上传文件按钮）

- **多会话管理**
  - 使用 `chats` 对象在前端缓存所有会话：
    - `chats[chatId] = { messages: [...], title: 'xxx' }`
  - `startNewChat()`：
    - 生成基于时间戳的 `chatId`
    - 初始化 `messages` 与 `title`
    - 同步到后端（保存历史）
  - `switchChat(chatId)`：
    - 切换当前会话，重新渲染右侧聊天窗口
  - `deleteChat(chatId)`：
    - 调用后端 DELETE 接口删除历史
    - 同时删除前端 `chats` 与 `chatMemories` 中的对应数据

- **消息渲染与 Markdown 支持**
  - `addMsg(text, cls, isHistory)`：
    - 根据 `cls` 决定是用户消息还是助手消息
    - 解析 `<think>...</think>` 部分，将「思考过程」与剩余 Markdown 内容分块展示
    - 使用 `marked.parse` 渲染正式回答为 HTML

- **文档上传与发送**
  - 通过隐藏的 `<input type="file">` 绑定上传按钮
  - 选中文件后，在输入框下方显示文件名
  - `sendMsg()` 中：
    - 若有 `selectedFile`，构造 `FormData`，附带：
      - `file`
      - `message`（当前输入）
      - `messages`（当前会话历史 JSON）
    - 调用 `/chat/message-with-file`，服务器端解析文档后合并进提示词。

- **思考中状态 & 终止按钮**
  - `showTyping()`：在消息列表底部添加「模型正在输入...」提示与「终止」按钮
  - `stopTyping()`：
    - 隐藏提示
    - 调用 `/chat/api/stop`
    - 在对话中追加「已终止生成」消息

---

## 配置与运行方式

### 环境依赖

- JDK 17+
- Maven 3+
- （可选）用于访问 DeepSeek API 的网络环境

### 配置文件

`src/main/resources/application.properties`：

```properties
spring.application.name=ollama
server.port=8088

# DeepSeek API 配置（部署时建议使用环境变量或外部化配置）
deepseek.api.key=YOUR_DEEPSEEK_API_KEY
deepseek.api.url=https://api.deepseek.com/v1/chat/completions
deepseek.model=deepseek-chat

# 历史记录与记忆文件路径
chat.history.path=./data/chat_history.json
chat.memory.path=./data/chat_memory.json
```

`src/main/resources/application.yml`：

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: deepseek-r1:1.5b
```

> 说明：当前主要使用的是 `DeepSeekService` 直接调用 DeepSeek 官方 API，`spring.ai.ollama` 配置为后续接入本地 Ollama 模型预留扩展点。

### 启动项目

1. 确保在 `application.properties` 中配置好了有效的 `deepseek.api.key`（或通过环境变量覆盖）。
2. 在项目根目录执行：

```bash
mvn spring-boot:run
```

3. 访问浏览器：

```text
http://localhost:8088/chat
```

即可打开聊天界面。

---

## 项目亮点（可提炼为简历描述）

- **从 0 到 1 设计并实现了一个完整的「大模型聊天系统」**
  - 后端基于 Spring Boot 自行封装 DeepSeek Chat Completions API
  - 前端实现了多会话管理、Markdown 渲染、文件上传等复杂交互

- **实现了对话历史与长期记忆的分层持久化**
  - 通过两个独立的 REST 控制器，将历史与记忆分别持久化到 JSON 文件
  - 支持服务重启后的会话恢复与记忆延续，为后续迁移到数据库留出清晰演进路径

- **支持文档级上下文增强的问答能力**
  - 集成 Apache PDFBox / Apache POI 对 `.pdf` / `.docx` 文档进行解析
  - 将解析后的文档内容与用户提问拼接后交给模型，实现“带文档问答”功能

- **前端体验对齐主流 AI 聊天产品**
  - 左侧会话列表 + 右侧气泡式消息结构
  - 支持 Markdown / 代码块 / 列表等富文本渲染
  - 支持 `<think>` 思维过程与正式回答的分块展示
  - 提供「正在输入」与「终止生成」交互，提升用户体验

你后续在简历里可以按需精简为 3～4 条要点，例如：

- 「基于 Spring Boot + DeepSeek API 独立实现一套类 ChatGPT 的多会话聊天系统，支持对话历史与长期记忆持久化、Markdown 渲染和文件上传问答等功能。」
- 「封装大模型调用服务，设计统一的 ChatRequest/Response 模型接口，并通过本地 JSON 文件持久化对话历史与记忆，支持服务重启后的对话恢复。」
- 「集成 PDFBox / Apache POI，实现 `.txt/.pdf/.docx` 文档解析，将文件内容与用户问题拼接后交给大模型，支持带文档上下文的智能问答场景。」

