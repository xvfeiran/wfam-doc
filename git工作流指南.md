# Git 工作流指南

## 仓库结构说明

本地开发使用**单一 monorepo**（`wfam/`），包含三个子目录，分别对应三个独立的远程仓库：

| 本地目录 | 远程仓库 | 远程别名 |
|---|---|---|
| `frontend/` | `qmc.wfam-frontend` | `frontend-origin` |
| `backend/` | `qmc.wfam-backend` | `backend-origin` |
| `doc/` | `qmc.wfam-doc` | `doc-origin` |

## 初次克隆配置（新成员）

新成员克隆任意一个远程仓库后，需手动添加另外两个远程别名：

```bash
# 添加三个远程
git remote add frontend-origin ssh://git@sourcecode.socialcoding.bosch.com:7999/cngp_i40/qmc.wfam-frontend.git
git remote add backend-origin  ssh://git@sourcecode.socialcoding.bosch.com:7999/cngp_i40/qmc.wfam-backend.git
git remote add doc-origin      ssh://git@sourcecode.socialcoding.bosch.com:7999/cngp_i40/qmc.wfam-doc.git
```

> 注意：本地 monorepo 没有 `origin`，直接使用上述三个别名。

## 日常推送

在本地 monorepo 根目录下，使用 `git subtree push` 将对应子目录推送到各自远程仓库：

```bash
# 推送前端变更
git subtree push --prefix=frontend frontend-origin master

# 推送后端变更
git subtree push --prefix=backend backend-origin master

# 推送文档变更
git subtree push --prefix=doc doc-origin master
```

**说明**：`git subtree push` 会自动将 `prefix` 子目录的内容作为远程仓库的根目录推送，无需额外操作。

## 日常拉取

从远程拉取并合并到本地对应子目录：

```bash
# 拉取前端
git subtree pull --prefix=frontend frontend-origin master --squash

# 拉取后端
git subtree pull --prefix=backend backend-origin master --squash

# 拉取文档
git subtree pull --prefix=doc doc-origin master --squash
```

> `--squash` 选项将远端历史压缩为一个 commit，避免混入远端仓库的大量历史记录。

## 常见问题

**Q：直接 `git push` 会推到哪里？**
A：本地没有配置 `origin`，直接 `git push` 会报错。必须使用 `git subtree push` 指定目标。

**Q：能否只推某一个仓库？**
A：可以。三个 `git subtree push` 命令完全独立，按需执行即可。

**Q：如何确认推送成功？**
A：命令输出末尾会显示 `* [new branch]` 或 `master -> master`，无报错即为成功。
