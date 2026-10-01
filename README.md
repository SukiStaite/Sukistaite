# 🌸 SukiReader · 祈祈查阅器

<div align="center">

**为《林晴祈 × 苏清辞 · 完整人设系统提示词》量身打造的原生 Android 查阅器**

Kotlin · Jetpack Compose · Material 3 · 完全离线

![License](https://img.shields.io/badge/license-Apache--2.0-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-blue)
![CI](https://img.shields.io/badge/build-GitHub%20Actions-orange)

</div>

---

## ✨ 功能

- 📖 **章节树** —— 全部 17 章层级目录，点击直达
- 🔍 **全文搜索** —— 实时检索近 3000 行设定，结果定位到行
- 🔖 **书签** —— 任意行收藏，DataStore 持久化，重启不丢
- 🌙 **Material You** —— Android 12+ 动态取色，跟随壁纸变色；低版本回落粉白主题
- 📴 **完全离线** —— 零网络权限，设定文本内置 assets，不收集任何数据

## 🏗️ 架构

```
app/src/main/java/com/sukistaite/reader/
├── MainActivity.kt              # 单 Activity + Navigation Compose
├── data/
│   ├── DocumentRepository.kt    # 文档加载与章节解析
│   └── BookmarkStore.kt         # DataStore 书签持久化
└── ui/
    ├── ChaptersPage.kt          # 章节树
    ├── SearchPage.kt            # 全文搜索
    ├── BookmarkPage.kt          # 书签
    ├── ReaderPage.kt            # 阅读器
    └── theme/                   # Material 3 动态取色主题
```

**技术栈**：Kotlin · Jetpack Compose · Material 3 · Navigation Compose · DataStore · kotlinx.serialization

## 🔨 构建

每次 push 到 `main`，GitHub Actions 自动编译 release APK 并上传到 Artifacts。

```bash
git clone https://github.com/yanbao2525-beep/Sukistaite.git
# Android Studio 打开，Sync 后直接 Run
```

## 📄 内容与版权

- 本仓库**代码**部分以 [Apache-2.0](LICENSE) 协议开源，欢迎学习、借鉴与二次开发。
- 应用内置的 `assets/document.txt`（约 24 万字人设文档）内容著作权归原作者 **糖宝 / sukistaite** 所有。
- ⚠️ **该文档含成人向内容，仅限 18 岁以上用户私下查阅**。禁止商用、倒卖或未授权的公开二次分发；请遵守你所在地区的法律法规。

## 🙋 一句话

「查阅人设，也要 Material You。」

<div align="center">Made with 💗 and Jetpack Compose</div>
