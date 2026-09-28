# 模块：前端控制台（frontend）

## 摘要
React 18 + TS + Vite 5 + antd 5 + echarts 产品化管理控制台，覆盖平台全部 8 个业务模块。目录 `frontend/`。

## 状态
2026-09-28 完成产品化大改版。`npm run build` 通过（tsc 0 错误，manualChunks 分包）；经真实后端逐页联调验证（含浏览器截图）。

## 视觉与布局
- `src/theme.ts` 设计令牌系统化：品牌蓝 #2B5AED 色板 + 语义色 + 中性灰阶、字号阶、radius/shadow/space 令牌；全部页面消费同一套 token，禁硬编码颜色（CSS 变量镜像于 src/index.css）。
- 登录页：左右分栏（深蓝渐变品牌区 CSS/SVG 装饰，零图片资源 + 大留白登录区）。
- 深色渐变侧边栏：SVG logo、菜单分组小标题、选中 pill 光晕+指示条、160ms hover 过渡、可收起（224↔64px）。
- 顶栏毛玻璃（backdrop-filter）：面包屑 + 可搜索项目切换器 + 彩色首字母 Avatar。
- 公共组件收敛（src/components/）：PageHeader（返回+标题+状态徽标+操作组）、StatCard（图标色块+sparkline）、
  SquareCard（渐变 Banner+两行截断+hover 浮现操作）、StatusBadge（浅底深字柔和徽标）、EmptyState（统一空态+引导按钮）。
- 工作台统计卡带 echarts 迷你趋势线；图表统一主题（淡虚线网格、渐变面积、圆角 tooltip）；列表首屏用 Skeleton；页面切换 CSS fade-in。

## 菜单与页面（14 个一级菜单，27 个路由）
工作台（统计卡+7 天趋势+Top Agent+快捷入口）｜项目空间（卡片化+详情 4 tabs）｜团队空间（用户组 CRUD/成员/授权视图）｜
广场（Agent 卡片墙 + Skill 卡片墙）｜资产中心（列表/上传/版本管理统一页 Timeline/HTTP API 转换）｜
Agent 管理（三步创建向导 + 详情发布到广场）｜可观测（stats 图表大盘 + 链路/审计/告警 tabs）｜
交付发布｜服务治理｜组件运维｜权限设置（角色×权限点矩阵，admin 可编辑）｜平台目录（模型/知识库）。

## 关键约定
- 表单字段严格来自后端 DTO；dev proxy `/v1` → 默认 `http://localhost:8080`，可用环境变量 `VITE_API_TARGET` 覆盖
  （本机 8080 常被其他服务占用时的标准姿势：后端 `--server.port=18080` 启动 + `VITE_API_TARGET=http://localhost:18080 npm run dev`）。
- 模型授权 paramPolicy 的语义是**参数白名单**（值可带 value/min/max），表单占位示例按此写（联调实测吻合）。

## 已知简化
- Skill 广场基于当前项目资产可见性（assets/list 需 projectId），非跨项目全量广场——与后端接口能力一致，后续可加平台级广场接口。
- 项目卡片角色徽标按 ownerId===当前用户 区分 OWNER/MEMBER（未逐项目拉成员表算精确角色）。
- 可观测页 Agent 选择器仅供明细表（stats/overview 只支持 projectId 维度）。
- 上传文件选择框为浏览器原生对话框；未做路由级懒加载。
