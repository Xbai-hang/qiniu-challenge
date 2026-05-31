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

## Git 换行符配置建议

为确保仓库中的文本文件统一使用 LF 换行符，请配置本地 Git：

```bash
git config --global core.autocrlf false
git config --global core.safecrlf warn
```

项目已通过 `.gitattributes` 统一管理行尾，请勿在 IDE 中强制改为 CRLF。
