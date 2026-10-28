<div align="center">

**OAISS CHAIN — ENTERPRISE DUAL-CARBON BLOCKCHAIN PLATFORM**  
### 双碳链动系统 · 架构制图实操指南分册
#### 基于 draw.io 与 Enterprise Architect 12 的高精度架构流程图与 UML 2.5 用例图绘制指南

<br>

# draw.io 与 EA 12 架构图绘制实操手册
### （OAISS CHAIN · ARCHITECTURE DIAGRAMMING & MODELING MANUAL）

<br>

| 项目 | 内容详情 |
| :--- | :--- |
| **作品名称** | OAISS CHAIN 双碳链动系统 |
| **文档版本** | v2.2 (规范发布版) |
| **文档定位** | 业务流程泳道图与 UML 2.5 标准用例图制图工程实操指南 |
| **执笔团队** | OAISS CHAIN 架构建模组 |
| **审核人员** | 首席架构师 & 技术委员会 |
| **所属单位** | 软件学院 & 双碳区块链工程中心 |
| **完成日期** | 二〇二六年十月 |

</div>

---

### 文档修订控制记录

**表 0.1 架构图绘制实操手册修订历史记录表**

| 编号 | 变更状态 | 修订日期 | 执笔人 | 审核人 | 批准人 | 变更摘要与说明 |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | 文档创建 | 2026-06-15 | 架构组 | 首席架构师 | 委员会 | 初始化 draw.io 泳道图与 EA 12 用例图绘制规范 |
| 2 | 矢量工程 | 2026-09-02 | 建模组 | 首席架构师 | 委员会 | 沉淀原生 .drawio 源码与 300 DPI 导出规约 |
| 3 | 规范对齐 | 2026-10-12 | 全栈架构师 | 技术总监 | 委员会 | 全面落实 UML 2.5 标准用例卡片与工业级版式标准 |

---

> **定位说明**：本手册为系统“核心业务流程图”（泳道图）与“系统用例图”（UML 2.5 Use Case Diagram）在 **draw.io (diagrams.net)** 与 **Enterprise Architect 12 (EA 12)** 中的**实际操作流程复现步骤**。  
> 无论是毕业设计答辩、工程设计说明书编制还是日常系统维护，均可按照本手册步骤 100% 高质量复现与导出印刷级（300 DPI）矢量/位图图表。

---

## 目录

- [第一部分 工具环境准备与工程源码资产](#第一部分-工具环境准备与工程源码资产)
- [第二部分 draw.io 绘制“核心业务流程图（多角色泳道流程图）”实操步骤](#第二部分-drawio-绘制核心业务流程图多角色泳道流程图实操步骤)
- [第三部分 Enterprise Architect 12 绘制“系统用例图”实操步骤](#第三部分-enterprise-architect-12-绘制系统用例图实操步骤)
- [第四部分 批量快速产出工作流（一键复用技巧）](#第四部分-批量快速产出工作流一键复用技巧)

---

## 第一部分 工具环境准备与工程源码资产

系统已在代码仓库中内置了原生矢量工程源文件，可直接双击或在工具中打开编辑：
- **核心业务流程图源文件**：`docs/assets/architecture/03-core-business-flowchart.drawio`
- **系统用例图全套源文件 (4个Tab页)**：`docs/assets/architecture/02-system-usecases.drawio`
- **绘图工具选择**：
  * **draw.io**：支持免安装 Web 端（访问 [app.diagrams.net](https://app.diagrams.net)）或官方桌面客户端（draw.io Desktop）。
  * **Enterprise Architect 12**：Sparx Systems 官方 UML 建模工具（支持 `.eap` / `.eapx` 项目文件）。

---

## 第二部分 draw.io 绘制“核心业务流程图（多角色泳道流程图）”实操步骤

核心业务流程图采用 **二维矩阵多角色泳道图（Cross-Functional Swimlane Diagram）** 规范，清晰表达控排企业、数据审核员、第三方监管机构与底层区块链/撮合引擎的协同流转。

### 步骤 1：画布创建与页面设置
1. 打开 draw.io，点击菜单栏 **文件 (File)** -> **新建 (New...)**，选择 **空白图表 (Blank Diagram)**。
2. 在右侧属性面板（Format Panel）的 **图表 (Diagram)** 标签页中进行页面配置：
   - **纸张大小 (Paper Size)**：选择 `A4 (210 mm x 297 mm)`，方向勾选 **横向 (Landscape)**。
   - **网格 (Grid)**：勾选启用，网格大小设置为 `10 pt`。
   - **参考线 (Guides)**：勾选启用，吸附对齐设为开启。
   - **背景 (Background)**：保持纯白色 `#FFFFFF`，不勾选透明（Transparent）。

### 步骤 2：构建二维矩阵泳道池（Swimlane Pool）
1. 在左侧图形库搜索或展开 **高级 (Advanced)** / **表格与泳道 (Tables/Lanes)**。
2. 拖入一个 **横向/纵向复合泳道池 (Cross-Functional Pool)**，或者通过矩形自定义绘制：
   - 整体画布宽度设定为 `1500 pt`，高度设定为 `1000 pt`。
   - **纵向绘制 5 大角色泳道（设置顶端表头，填充色 `#F2F4F7`，边框色 `#333333`，粗细 1.2pt）**：
     * 泳道 1（宽 280 pt）：`控排企业主体 (ENTERPRISE)`
     * 泳道 2（宽 280 pt）：`系统数据中台 / 计算引擎 (Spring Boot & ML)`
     * 泳道 3（宽 250 pt）：`数据审核员 (REVIEWER)`
     * 泳道 4（宽 250 pt）：`第三方监管机构 (THIRD_PARTY)`
     * 泳道 5（宽 250 pt）：`可信存证与市场底座 (Fabric & Engine)`
   - **左侧绘制 3 大阶段分组侧边栏（宽度 140 pt，填充色 `#F8F9FA`）**：
     * 上部：`阶段一：数据采集与国标核算`
     * 中部：`阶段二：数字签名与多级审查`
     * 下部：`阶段三：联盟链存证与市场履约`
   - 阶段之间拉一条水平实线（线宽 1.2pt，颜色 `#333333`）作为阶段分界。

### 步骤 3：绘制流程活动节点（Nodes）
从左侧 **通用 (General)** 图形库中拖拽标准图元并设置样式：
1. **开始 / 结束节点（Terminals）**：
   - 形状选择 **圆角矩形 (Rounded Rectangle)**，右侧面板勾选 **圆角 (Rounded)**，并将圆角弧度调整为胶囊状（`arcSize=50`）。
   - 样式：填充色 `#E8EEF5`，边框色 `#2B4C7E`（粗细 1.3pt），文字加粗。
2. **处理活动节点（Process Boxes）**：
   - 形状选择 **圆角矩形**（`arcSize=10`），宽度统一为 `200 pt`，高度 `55 pt`。
   - 样式：填充色 `#FFFFFF`，边框色 `#374151`（粗细 1.2pt），文字使用 **微软雅黑 9.5pt / 10pt**，居中排版。
   - 驳回节点特殊样式：填充色浅红 `#FEF2F2`，边框色 `#EF4444`，文字深红 `#991B1B`。
   - 存证节点特殊样式：填充色浅绿 `#ECFDF5`，边框色 `#10B981`，文字深绿 `#065F46`。
3. **条件决策节点（Decision Diamonds）**：
   - 形状选择 **菱形 (Rhombus)**，宽度 `160 pt`，高度 `70 pt`。
   - 样式：填充色浅黄 `#FFFBEB`，边框色 `#D97706`（粗细 1.3pt），文字加粗深琥珀色 `#92400E`。

### 步骤 4：正交折线连接（Orthogonal Connectors）
1. 鼠标悬停在源节点边缘出现的蓝色“x”锚点上，按住左键拖拽至目标节点的边缘锚点。
2. 选中连线，在右侧属性面板的 **样式 (Style)** 中设置：
   - **线条类型**：选择 **正交 (Orthogonal)**（直角弯折）。
   - **线条颜色**：`#1F2937`（深黑灰），线宽 `1.3 pt`。
   - **箭头样式**：末端选择实心三角形箭头（`Classic` 或 `Block`）。
3. **添加分支条件文本**：
   - 双击连线中间位置，直接输入文字（如 `通过`、`驳回整改`、`提交试算`）。
   - 选中文字标签，设置文字背景色为 `#FFFFFF`（避免被连线遮挡穿透），文字颜色按成功（绿色 `#047857`）与失败（红色 `#B91C1C`）设置。

### 步骤 5：无损导出 300 DPI 印刷级图片
1. 点击顶部菜单 **文件 (File)** -> **导出为 (Export as)** -> **PNG...**。
2. 在弹出的导出参数对话框中进行关键设置：
   - **缩放 (Zoom)**：填写 `300%`（核心参数！将 72 DPI 原生位图放大 3~4 倍达到 300 DPI 印刷质量）。
   - **边距 (Border Width)**：输入 `20`。
   - **透明背景 (Transparent Background)**：**取消勾选**（保持纯白底，确保插入 Word 时对比度最高）。
3. 点击 **导出 (Export)**，保存为 `03-carbon-lifecycle-state-machine.png`。

---

## 第三部分 Enterprise Architect 12 绘制“系统用例图”实操步骤

Enterprise Architect 12（简称 EA 12）是国际标准的 UML 2.5 建模环境。以下是绘制四角色用例图的完整复现操作：

### 步骤 1：新建 EA 工程与模型包（Package）
1. 启动 Enterprise Architect 12，点击 **File** -> **New Project...**，保存为 `OAISS_CHAIN_Model.eap`。
2. 在右侧 **Project Browser** 视图中，右键点击根模型，选择 **Add a Package...**，命名为 `02_Use_Case_Model`。
3. 在新建的包下右键选择 **Add Diagram...**：
   - **Type** 选择 `UML Behavioral`；
   - **Diagram Types** 选中 `Use Case`；
   - 命名为 `企业用户用例图`（或 `系统多角色核心用例全景图`），点击 **OK**。

### 步骤 2：绘制参与者（Actor - Stick Man）
1. 打开左侧的 **Toolbox** 工具箱，切换到 **Use Case** 分组。
2. 找到 **Actor** 图元，将其拖拽至绘图区域左侧：
   - 在弹出的属性对话框中，**Name** 输入 `控排企业主体 (ENTERPRISE)`；
   - **Stereotype** 输入 `Business Actor`；
   - 点击 **OK**。
3. **设置外观样式**：
   - 选中 Actor 小人，右键选择 **Appearance** -> **Default Appearance...**；
   - 背景色填充设为浅灰 `#F3F4F6`，线条边框色设为深灰 `#1F2937`。

### 步骤 3：绘制系统边界矩形框（System Boundary）
1. 在 Toolbox 的 **Common** 或 **Use Case** 分组中，选择 **Boundary**（或 Boundary Element）拖入画布。
2. 在属性中将名称设置为 `OAISS CHAIN 控排企业核心业务系统边界`。
3. 调整大小为宽约 `12 cm`，高约 `20 cm`，覆盖 Actor 右侧的整个用例放置区。
4. 右键点击边界框，选择 **Z-Order** -> **Send to Back**（置于底层），避免遮挡椭圆用例。

### 步骤 4：放置标准 UML 用例椭圆（Use Cases）
1. 从 Toolbox 拖拽 **Use Case** 图元放置在系统边界内部：
   - 输入用例名称，如 `选择国标核算模板`、`实时试算公式推导`、`生成 RSA 数字签名`、`提交核算报告送审`。
2. **设置淡雅工科色彩**：
   - 选中用例椭圆，按快捷键 `F4` 或右键选择 **Appearance**：
     * 企业用例：填充色（Fill Color）设置为浅冰蓝 `#F0F9FF`，边框色（Border Color）设置为 `#0284C7`（线宽 1pt）。
     * 审核用例：填充色设为浅薄荷绿 `#F0FDF4`，边框色设为 `#16A34A`。
     * 监管用例：填充色设为浅琥珀黄 `#FFFBEB`，边框色设为 `#D97706`。
     * 管理用例：填充色设为浅丁香紫 `#FAF5FF`，边框色设为 `#9333EA`。

### 步骤 5：建立 UML 关系连接（Relationships）
1. **实线关联 (Association)**：
   - 从 Toolbox 拖拽 **Associate** 线段，连接 Actor 小人与核心主干用例（如 `提交核算报告送审`）。
   - 实线无箭头，表示参与者直接交互。
2. **包含关系 (`«include»`)**：
   - 从 Toolbox 拖拽 **Include** 连线（或拖拽 **Dependency** 并在 Stereotype 中选择 `include`）。
   - 从基础用例（如 `提交核算报告送审`）指向被包含用例（如 `生成 RSA 数字签名`）。
   - 连线呈现为标准虚线箭头，中间带有 `«include»` 标签。
3. **扩展关系 (`«extend»`)**：
   - 从 Toolbox 拖拽 **Extend** 连线。
   - 从扩展用例（如 `火电25项参数推导`）指向基用例（如 `选择国标核算模板`），连线带有 `«extend»` 标签。

### 步骤 6：从 EA 12 导出高清无损图片
1. 打开并激活要导出的用例图。
2. 点击 EA 12 顶部菜单 **Diagram** -> **Save Image As...**（或按快捷键 `Ctrl + T`）。
3. 关键导出设置：
   - **Image Format**：选择 **PNG**（或高保真增强图元文件 **EMF (Enhanced Metafile)** 用于 Word 矢量级插入）。
   - **Resolution**：在 EA 的全局选项中（**Tools** -> **Options** -> **Diagram** -> **Image Export Format**），确保勾选了 **High Quality / 300 DPI**。
4. 保存图片并在毕业设计文档中插入。

---

## 第四部分 批量快速产出工作流（一键复用技巧）

如果您需要在最短时间内重新生成全套图表：
1. **直接编辑已生成的 `.drawio` 文件**：
   - 在 draw.io 中点击 **打开已有图表**，选择 `docs/assets/architecture/02-system-usecases.drawio`。
   - 底部包含完整的 4 个 Tab 标签页，所有小人、椭圆、文字、连线均已完成精确定位，您可以直接双击文字进行业务微调，无需从零排版连线！
2. **批量自动渲染命令**：
   - 运行项目根目录下的生成脚本：
     ```bash
     python scripts/common/generate_drawio_business_flowchart.py
     python scripts/common/generate_ea_usecase_diagrams.py
     ```
   - 即可瞬间重新生成全套 300 DPI 超高清图像，并自动覆盖刷新到 Word 文档中！
