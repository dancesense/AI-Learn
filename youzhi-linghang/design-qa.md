# 游知领航设计还原 QA

## 基准

- 参考页面：墨刀分享页及其直接预览页面
- 桌面端视口：1280 × 720
- 移动端视口：390 × 844
- 状态：首屏默认状态；内容广场为“全部”分类、空搜索状态
- 源站素材：使用源站实际图片资源，没有用占位图、CSS 绘图或自制 SVG 替代

## 对照证据

- 首页桌面端：
  - 参考图：`design/source/home-desktop-1280.png`
  - 实现图：`design/qa/home-desktop-implementation.png`
  - 并排图：`design/qa/home-desktop-comparison.png`
- 内容广场桌面端：
  - 参考图：`design/source/content-desktop-1280.png`
  - 实现图：`design/qa/content-desktop-implementation.png`
  - 并排图：`design/qa/content-desktop-comparison.png`
- 首页移动端：
  - 参考图：`design/source/home-mobile-00.png`
  - 实现图：`design/qa/home-mobile-implementation.png`
  - 并排图：`design/qa/home-mobile-comparison.png`

另外保存了首页、内容广场、家教服务、学霸社群、关于我们和个人中心的桌面端/
移动端源站截图，位于 `design/source/`。

## 核对结果

- 布局与间距：页头、首屏、卡片网格、侧栏、页脚、圆角、阴影及留白一致。
- 字体：沿用源站字体栈、字号、字重、行高和文本换行。
- 颜色：主蓝色、强调橙色、页面底色、边框和状态色与源站一致。
- 图片：首屏插画、视频封面、导师头像、社群图片和用户图片均使用源站资源及相同裁切。
- 图标：使用与源站同类的 Lucide 线性图标，并匹配尺寸、描边和对齐。
- 响应式：390px 下无横向溢出；导航、登录按钮按源站规则隐藏，只保留品牌和快速注册。
- 可访问性：交互控件使用语义化按钮/链接；表单含标签；图片含替代文本；键盘焦点可见。
- 状态与交互：路由、搜索、分类筛选、导师筛选、点赞、关注、预约、加入社群和身份切换均已验证。

## 联调与测试

- Spring Boot 已连接本机 MySQL，并完成表结构及种子数据初始化。
- API 返回 4 条内容、5 位导师、4 个社群和 1 条初始预约。
- `mvn test`：3 个集成测试全部通过。
- `npm run build`：通过。
- 浏览器端到端验证：点赞、关注、预约、社群加入和身份更新均写入 MySQL；验证后已恢复演示初始状态。
- 页面控制台：无影响核心流程的前端异常。

## 比较历史

- Pass 1：根据源站路由、DOM、截图、样式表和真实素材完成完整实现。
- Pass 2：首页桌面端同视口并排检查，无 P0/P1/P2 视觉差异。
- Pass 3：内容广场桌面端同视口并排检查，无 P0/P1/P2 视觉差异。
- Pass 4：首页移动端同视口并排检查，无横向溢出或断点布局问题。

## Final result

passed
