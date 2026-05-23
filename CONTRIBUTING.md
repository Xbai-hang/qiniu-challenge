# 协作开发规范

## 基本原则

所有功能开发必须通过以下流程，禁止直接向 main 分支提交代码：

Issue → Branch → Commit → Pull Request → Review → Squash Merge

## 分支规范

所有开发都应从 main 分支拉取新分支。

分支命名：

- feat/功能名
- fix/问题名
- docs/文档名
- refactor/重构名
- chore/配置名

示例：

- feat/user-login
- feat/chat-page
- fix/api-timeout
- docs/update-readme
- chore/github-templates
