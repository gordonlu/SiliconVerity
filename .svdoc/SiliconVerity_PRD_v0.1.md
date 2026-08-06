# 芯鉴（SiliconVerity）产品需求文档

> **版本**：PRD v0.1  
> **日期**：2026-08-06  
> **平台**：Android  
> **产品类型**：硬件信息展示、性能基准测试、持续性能分析  
> **暂定中文名**：芯鉴  
> **暂定英文名**：SiliconVerity  
> **一句话定位**：一款公开数据来源、测试方法和结果可信度的 Android 硬件信息与性能测量工具。

---

## 1. 文档摘要

芯鉴不是另一个只展示参数或给出不透明总分的跑分工具，而是一套面向普通用户、硬件爱好者和开发者的 **可追溯硬件信息系统与可复现性能测量工具**。

产品围绕三项核心价值构建：

1. **信息可信**：每个硬件字段都显示其来源、采集方式、可信度及可能误差，不把数据库映射或推测结果伪装成硬件实测。
2. **测试严谨**：每个 Benchmark 都定义测量目标、工作负载、预热、计时、重复、校验和无效条件。
3. **结果透明**：同时展示原始指标、波动、热状态、运行环境和评分版本，用户可以理解“测到了什么”以及“为什么得到这个结果”。

产品初期应优先完成可靠的数据采集、标准化测试协议和原始指标展示。评分系统独立版本化，在积累足够设备样本并完成校准后上线，避免先制造一个看似精确但缺乏依据的总分。

---

## 2. 产品命名

### 2.1 推荐名称

## 芯鉴 / SiliconVerity

- **芯**：指 SoC、CPU、GPU、内存、存储等设备核心硬件。
- **鉴**：包含鉴别、检验、评估和给出依据的含义。
- **Verity**：意为真实性、真实状态，符合“来源透明、方法可信”的产品核心。
- 不局限于 Benchmark，可覆盖硬件信息、真实性核验、持续性能和后续设备数据库。

### 2.2 品牌表达

- 中文全名：**芯鉴：硬件信息与性能测试**
- 英文全名：**SiliconVerity: Hardware Facts & Benchmark**
- 短标语：**每个参数有来源，每次跑分有依据。**
- 英文标语：**Hardware facts. Reproducible performance.**

### 2.3 备选名称

| 名称 | 优点 | 问题 | 建议 |
|---|---|---|---|
| CoreProof / 芯证 | 简短，强调证据 | “Proof”容易被理解为安全证明；已有其他行业使用 | 备选 |
| CoreLens / 芯镜 | 有观察硬件内部的感觉 | 已有同名应用和服务 | 不建议 |
| SiliconScope | 系统监控感强 | 已有活跃的 Apple Silicon 监控项目 | 不建议 |
| TruthBench | 直接强调真实测试 | 已用于 AI 评测项目 | 不建议 |
| OriginBench / 原测 | 强调原始测量 | 已有同名 AI 测评平台 | 不建议 |

> 注：当前只是产品命名初筛，不构成商标、域名和应用商店名称的法律可用性确认。正式发布前仍需做完整检索。

---

## 3. 背景与问题

Android 硬件工具大致分为两类：

1. **硬件信息工具**：展示 SoC、CPU、GPU、RAM、存储、电池和传感器等参数。
2. **Benchmark 工具**：运行一组测试并输出分项分数或综合总分。

现有产品常见问题如下。

### 3.1 硬件信息问题

- 同一个字段可能来自 Android 公共 API、Linux `/proc`、`/sys`、系统属性或本地机型数据库，但界面不说明来源。
- 将 SoC ID、设备型号或属性映射得到的商业名称显示为“实测硬件型号”。
- 把内核可访问内存、可用内存、应用堆上限和厂商标称内存混为一谈。
- 将任意 thermal zone 节点标成 CPU 或 GPU 温度。
- 把 CPU 当前请求频率、策略频率或历史值当作真实瞬时频率。
- ROM、驱动或厂商填写错误时，应用静默选择一个结果，不展示来源冲突。

### 3.2 Benchmark 问题

- 测试名称声称测 CPU 或 GPU，结果却大量受到缓存、内存、JIT、调度或驱动提交开销影响。
- 不进行充分预热、重复和稳定性判断，仅执行一次或三次取平均值。
- 不区分峰值性能、稳定性能和长期热衰减。
- 不记录电池、省电模式、充电、热状态、后台负载、系统版本和编译版本。
- GPU 测试按设备原生分辨率运行，却用于跨设备比较。
- 存储测试实际测到 page cache，却标为闪存读写速度。
- 更新测试算法后仍沿用旧分数尺度，导致不同版本成绩不可比较。
- 综合分权重和参考基线不透明。

### 3.3 用户机会

市场中仍缺少一款同时满足以下条件的 Android 工具：

- 对普通用户足够易懂；
- 对硬件爱好者足够透明；
- 对开发者足够可复现；
- 不依赖 Root 也能给出诚实的可用数据；
- 明确区分读取值、厂商声明、数据库映射和算法推测；
- 允许用户查看每项成绩背后的原始测量和有效性证据。

---

## 4. 产品目标与非目标

### 4.1 产品目标

1. 建立 Android 硬件信息的来源分级与交叉验证体系。
2. 建立可复现、可解释、可版本化的性能测试协议。
3. 提供 CPU、GPU、内存、存储和持续性能的原始测量。
4. 为每次测试生成完整 Run Manifest 和有效性判断。
5. 在校准后提供自研分类分数和综合指数。
6. 支持历史结果比较和设备状态诊断。
7. 为后续匿名设备数据库和横向对比奠定基础。

### 4.2 非目标

首个版本不追求：

- 读取普通应用没有权限访问的“精确 CPU/GPU 核心温度”。
- 判断 RAM 颗粒厂商、LPDDR 代际或 UFS 版本，除非存在可信公开接口。
- 通过机型数据库伪装成硬件实测工具。
- 复刻安兔兔或 Geekbench 的分数尺度。
- 第一版就提供权威排行榜。
- 为 Root、工程机和系统签名权限设计主流程。
- 用一个综合总分替代所有原始指标。
- 将设备跑分用于医疗、安全或保修结论。

---

## 5. 目标用户

### 5.1 普通用户

需求：

- 查看手机真实硬件和系统信息。
- 判断手机是否处于异常性能状态。
- 比较性能模式、省电模式、升级前后差异。
- 查看手机长时间运行是否明显降速。

### 5.2 硬件爱好者与评测用户

需求：

- 查看每项数据的来源和可信度。
- 获得单核、多核、GPU、内存、存储和持续性能原始数据。
- 导出完整运行环境与结果。
- 对比不同设备或同一设备不同设置。

### 5.3 Android 开发者

需求：

- 了解目标设备的 ABI、CPU features、图形 API 和硬件能力。
- 验证算法在不同核心、线程数、图形后端下的表现。
- 发现热限制、调度、存储或内存瓶颈。
- 获取 JSON/CSV 结果用于分析。

---

## 6. 核心产品原则

### 6.1 来源优先于展示数量

无法可靠获取的信息可以不显示。显示“未知”优于显示未经说明的推测结果。

### 6.2 原始值与解释分离

系统原始报告、规范化展示、商业名称映射和算法推测必须分别保存。

例如：

```text
系统报告 SoC：SM8650
厂商：Qualcomm
商业名称映射：Snapdragon 8 Gen 3
映射来源：内置 SoC 数据库 v3
可信度：高，但商业名称不是硬件实测
```

### 6.3 不使用虚假精度

无法确认传感器类型时，不显示“CPU 43.2℃”；应显示内核节点名称、厂商标签和推测类型。

### 6.4 测试方法必须版本化

每个测试项拥有独立 `workload_id` 和 `workload_version`。算法、编译参数、数据规模、计时方式或图形着色器变化时必须升级版本。

### 6.5 原始指标长期可见

评分可以改变，原始耗时、吞吐率、FPS、延迟、性能曲线和运行清单必须保留。

### 6.6 无效结果不参与评分

环境明显不合格、波动过大、校验失败或测试被中断时，结果可以保存，但不生成正式分数。

### 6.7 峰值与持续性能分开

不将短时 Boost 和长期稳定性能混成一个难以解释的数字。

### 6.8 测试期间不运行广告和网络任务

广告加载、埋点上传、数据库同步和后台网络请求必须在测试期间暂停，防止污染结果。

---

## 7. 产品范围与版本规划

### 7.1 Alpha：测量基础

目标：验证硬件采集和 Benchmark Core。

包含：

- 硬件信息来源模型。
- CPU 单核、多核基础测试。
- 内存带宽和延迟测试。
- 存储顺序读写测试。
- GPU OpenGL ES 基础测试。
- 热状态、运行环境、原始样本和统计值。
- JSON 导出。
- 不提供正式总分。

### 7.2 MVP：公开测试版

包含：

- 完整硬件概览。
- CPU、GPU、内存、存储分项测试。
- 峰值测试与 10 分钟持续性能测试。
- 每项结果的说明页。
- 历史记录和同机对比。
- 结果有效性等级。
- 分类分数 Beta。
- 无账号本地使用。

### 7.3 V1.0：正式版

包含：

- 稳定的评分规范 v1。
- 综合性能指数、持续性能指数和稳定性指数。
- 匿名成绩上传与设备对比，默认关闭、用户主动加入。
- CSV、JSON、图片报告导出。
- 测试包增量更新和版本兼容策略。
- 广告版与 Pro 版。

### 7.4 后续版本

- Vulkan Benchmark。
- AI/NPU 可用性与标准模型推理测试。
- 能效评估，但仅在能源数据可信时启用。
- 外接散热、性能模式和系统升级 A/B 对比。
- 面向开发者的自动化测试接口。
- Root/Lab 模式，独立于普通消费者成绩池。

---

## 8. 信息架构与主要页面

### 8.1 首页

显示：

- SoC、CPU、GPU、RAM、存储、系统版本。
- 最近一次分项成绩。
- 当前热状态和测试准备状态。
- “查看硬件”“快速测试”“完整测试”“持续测试”。

### 8.2 硬件页

分类：

- 设备与系统
- SoC 与 CPU
- GPU 与图形 API
- 内存
- 存储
- 显示
- 电池与电源
- 热状态
- 摄像头
- 传感器
- 网络与连接
- 安全与 DRM

每个字段可点击展开来源说明。

### 8.3 测试页

模式：

- 快速测试：约 2～3 分钟。
- 完整测试：约 8～12 分钟。
- 持续测试：10、20、30 分钟。
- 单项测试：用户选择特定 workload。

### 8.4 结果页

展示：

- 原始指标。
- 分类分数。
- 性能曲线。
- 各轮样本。
- 中位数、MAD、CV。
- 测试前后热状态。
- 有效性等级。
- 与本机历史成绩对比。
- 评分版本和 workload 版本。

### 8.5 方法页

公开：

- 每项测试测量目标。
- 算法与数据规模。
- 计时方式。
- 主要干扰因素。
- 有效条件。
- 评分方法和版本历史。

---

## 9. 硬件信息可信获取设计

## 9.1 信息来源等级

### A 级：Android 公共 SDK/NDK API

特点：官方、兼容性较高、普通应用可用。

示例：

- `Build.SOC_MODEL`、`Build.SOC_MANUFACTURER`，API 31+。
- `Build.SUPPORTED_ABIS`。
- `ActivityManager.MemoryInfo`。
- `StorageStatsManager`、`StatFs`。
- `BatteryManager`。
- `PowerManager` Thermal API。
- EGL/OpenGL ES `GL_VENDOR`、`GL_RENDERER`、`GL_VERSION`。
- Vulkan `VkPhysicalDeviceProperties`。
- NDK `sysconf()`、`getauxval()`。

默认可信度：高，但仍需遵守字段本身定义，不能扩大解释。

### B 级：Linux 标准运行时接口

示例：

- `/proc/cpuinfo`
- `/proc/meminfo`
- `/proc/stat`
- `/proc/self/auxv`
- `uname()`
- `statvfs()`
- CPU affinity 和当前运行 CPU

默认可信度：中高。它反映内核向当前进程暴露的运行环境，不一定等于硬件完整规格。

### C 级：厂商属性与 sysfs 节点

示例：

- `ro.board.platform`
- `ro.hardware`
- `ro.soc.model`
- `/sys/devices/system/cpu/`
- `/sys/class/thermal/`
- 厂商 GPU 频率节点

默认可信度：中或中低。需要逐机型、逐 Android 版本验证，并明确兼容性和权限限制。

> 普通应用不得依赖反射调用隐藏的 `android.os.SystemProperties` 作为核心方案。系统属性读取应封装为可失败的兼容层，并优先使用公共 API或允许访问的命令/文件来源。

### D 级：本地或远程数据库映射

示例：

- SoC ID → 商业名称。
- 设备型号 → 可能的 SoC。
- 设备 SKU → 宣传 RAM、存储配置。

默认可信度：低至中，只可作为补充信息，不得标记为实测。

---

## 9.2 HardwareFact 数据模型

```kotlin
data class HardwareFact(
    val key: String,
    val rawValue: String?,
    val displayValue: String?,
    val sourceType: SourceType,
    val sourceId: String,
    val collectedAt: Instant,
    val confidence: Confidence,
    val isInferred: Boolean,
    val inferenceRuleVersion: String?,
    val apiLevelRequirement: Int?,
    val warnings: List<String>,
    val conflictingEvidence: List<Evidence>
)
```

### SourceType

```text
PUBLIC_API
NDK_API
PROCFS
SYSFS
SYSTEM_PROPERTY
DRIVER_REPORTED
DATABASE_MAPPING
ALGORITHM_INFERENCE
USER_PROVIDED
```

### Confidence

```text
HIGH
MEDIUM
LOW
UNKNOWN
CONFLICTED
```

---

## 9.3 字段冲突处理

当多个来源不一致时：

1. 不静默覆盖。
2. 保留全部原始证据。
3. 按来源等级和字段适用性选择默认展示值。
4. 将可信度标记为 `CONFLICTED` 或降低等级。
5. 展开页显示冲突详情。

示例：

```text
SoC 默认显示：SM8650
Build.SOC_MODEL：SM8650
ro.soc.model：SM8650-AB
/proc/cpuinfo Hardware：Qualcomm Technologies, Inc SM8650
结论：同一 SoC 的不同标识，可信度高
```

---

## 9.4 重点字段规则

### CPU 核心数

不得只使用 `Runtime.availableProcessors()` 作为物理核心数，因为它表示当前 Java 虚拟机可使用的处理器数量，且运行期间可能变化。

应交叉采集：

- `Runtime.availableProcessors()`：JVM 当前可用处理器。
- `sysconf(_SC_NPROCESSORS_CONF)`：系统配置核心数。
- `sysconf(_SC_NPROCESSORS_ONLN)`：当前在线核心数。
- `/sys/devices/system/cpu/present`。
- `/sys/devices/system/cpu/online`。
- 当前进程 affinity mask。

展示时区分“配置核心”“在线核心”“本应用可用核心”。

### CPU Features

首选：

- NDK `getauxval(AT_HWCAP/AT_HWCAP2)`。
- Google `cpu_features` 作为兼容和已知硬件问题修正层。

`/proc/cpuinfo` 作为补充证据，不单独决定最终 feature 集。

### CPU 频率

频率字段必须标注具体语义：

- policy min/max
- scaling current
- cpuinfo min/max
- 当前采样值
- 是否可能是请求频率而非真实硬件时钟

频率仅作为辅助遥测，不直接作为核心性能评分依据。

### RAM

区分：

- 厂商标称内存：`advertisedMem`，API 34+。
- 内核可访问总内存：`totalMem`。
- 系统估计可用内存：`availMem`。
- 当前完全空闲内存：在支持版本读取 `freeMem`。
- 应用 Java 堆上限：单独放在开发者信息中。

不得把 `Runtime.maxMemory()` 当作设备总 RAM。

### 存储

区分：

- 底层介质面向用户的总容量：`StorageStatsManager.getTotalBytes()`。
- 指定文件系统总容量：`StatFs.getTotalBytes()`。
- 应用可用空间：`StatFs.getAvailableBytes()`。
- 系统可回收后空间：对应公开存储 API。

不得将文件系统容量直接称为闪存芯片裸容量。

### GPU

从有效 EGL/Vulkan Context 获取：

- OpenGL ES Vendor、Renderer、Version、GLSL。
- Vulkan device name、vendor ID、device ID、API version、driver version。
- 支持扩展和能力限制。

这些值属于驱动报告，不等同于独立验证的物理芯片身份。

### 温度与热状态

普通应用主流程使用：

- `PowerManager.getCurrentThermalStatus()`。
- `PowerManager.getThermalHeadroom()`，API 30+。
- Thermal thresholds，支持版本读取。
- Android 16/API 36+ 的 CPU/GPU headroom，在设备支持时读取。

sysfs thermal zone 只作为“高级传感器列表”展示：

- 保留节点和 type 原文。
- 不保证映射为 CPU/GPU。
- 不参与跨设备温度比较。
- 不用虚假精度输出芯片核心温度。

---

## 10. Benchmark 总体方法

## 10.1 三类 Benchmark

### Micro Workload

隔离测试单一能力，例如：

- 整数吞吐。
- FP32/FP16 向量计算。
- 哈希。
- 内存带宽。
- 指针追逐延迟。
- GPU Fill Rate。
- GPU Shader ALU。

用途：理解硬件能力和瓶颈。

### Scenario Workload

模拟真实任务，例如：

- 图片缩放与滤镜。
- 压缩与解压。
- JSON/数据处理。
- 视频帧变换，但需避免依赖厂商私有编解码差异。
- 小型离线渲染场景。

用途：贴近应用体验，但必须拆分说明受哪些硬件共同影响。

### Sustained Workload

长期重复一个稳定工作负载，记录：

- 每个时间窗口吞吐。
- 性能保持率。
- Thermal status/headroom。
- 电池电量和供电状态。
- 频率遥测，若可用。

用途：评估散热和长期可持续性能。

---

## 10.2 BenchmarkSpec 数据模型

```kotlin
data class BenchmarkSpec(
    val workloadId: String,
    val workloadVersion: SemVer,
    val category: BenchmarkCategory,
    val measurementTarget: String,
    val algorithm: String,
    val implementationBackend: Backend,
    val dataSize: Long,
    val threadPolicy: ThreadPolicy,
    val timingMethod: TimingMethod,
    val warmupPolicy: WarmupPolicy,
    val repetitionPolicy: RepetitionPolicy,
    val correctnessCheck: CorrectnessCheck,
    val invalidationRules: List<InvalidationRule>,
    val knownInterferences: List<String>
)
```

---

## 10.3 测试环境检查

测试开始前检查：

- 必须为物理设备；模拟器结果只进入开发模式。
- App 必须为非 Debug、优化构建。
- 电量建议不低于 25%，低于阈值时警告或阻止正式成绩。
- 记录是否充电以及充电类型。
- 记录省电模式、低电量模式和厂商性能模式，若可识别。
- 检查 Thermal status/headroom。
- 检查当前系统负载和可用内存。
- 检查是否正在录屏、分屏、画中画或后台。
- Benchmark Activity 保持前台、不透明、屏幕常亮。
- 测试期间暂停广告、网络同步和非必要后台任务。

### 环境等级

```text
CLEAN：符合正式比较条件
ACCEPTABLE：存在轻微干扰，仍可给分并警告
NOISY：结果仅供参考，不进入排行榜
INVALID：测试失败或环境不满足基本条件
```

---

## 10.4 预热策略

不使用固定“一次预热”作为通用规则。

建议：

1. 达到最小预热时间。
2. 按窗口计算吞吐。
3. 最近若干窗口的变化低于阈值时进入正式测量。
4. 若达到最大预热时间仍不稳定，记录警告。

预热目的：

- 让 native 代码路径、缓存和线程进入稳定状态。
- 对 Kotlin/Java 场景降低 JIT/AOT 状态差异。
- 避免把频率爬升过程误认为稳定性能。

对于峰值测试，不应通过长时间预热把设备提前加热；需使用专门的短预热协议。

---

## 10.5 重复与统计

每项正式测量至少 5 轮，推荐 7～9 轮；具体轮数可由稳定性自适应调整。

主要统计：

- Median：正式中心值。
- MAD：抗异常值离散程度。
- CV：用于判断相对波动。
- Min/Max：用于诊断，不作为主成绩。
- 每轮原始值：永久保留。

初期有效性建议阈值：

```text
CV ≤ 3%：稳定
3% < CV ≤ 7%：可用但存在波动
CV > 7%：结果不进入正式评分，建议重测
```

阈值必须在设备实验后校准，不作为永远不变的规范。

不建议只取三次算术平均。

---

## 10.6 正确性校验

所有 workload 必须防止：

- 编译器删除无用计算。
- 输入数据被常量折叠。
- 结果溢出后仍继续计时。
- GPU 绘制未完成就停止 CPU 计时。
- 存储测试写入失败或实际文件尺寸不足。

措施：

- 使用动态种子生成输入。
- 输出 checksum 或抽样结果。
- 预置已知答案测试。
- Debug/CI 环境执行完整正确性验证。
- Release Benchmark 保留低开销校验。
- 每个 workload 版本包含 golden vectors。

---

## 11. CPU Benchmark 设计

## 11.1 实现原则

- 核心 workload 使用 C/C++ NDK。
- Kotlin/Compose 负责调度、状态和展示。
- 使用单调时钟，避免系统时间调整。
- 测量主体中禁止动态内存分配、日志、JNI 往返和对象创建。
- 检查 release 汇编，确认目标指令确实生成。
- 标量、NEON、FP16、Dot Product 等路径独立测试和独立标记。

## 11.2 单核测试

不能只启动一个自由调度线程并称为“单核性能”。

协议：

1. 枚举当前应用允许使用的 CPU。
2. 依次将线程绑定到每个可用核心。
3. 记录 affinity、实际运行 CPU 和迁核情况。
4. 每个核心独立完成短测试。
5. 根据能力、频率和实测聚类为核心簇。
6. 报告：
   - 最强核心。
   - 各核心簇。
   - 系统默认调度单线程。

若设备或系统阻止 affinity，结果标记为“调度单线程”，不得标成“固定大核”。

## 11.3 多核测试

测试线程规模：

- 1
- 2
- 4
- 主要性能核心数量
- 全部当前允许核心

输出：

- 总吞吐。
- 每线程吞吐。
- 扩展效率。
- 是否出现明显内存带宽饱和。
- 测试期间吞吐曲线。

## 11.4 初期 CPU Workloads

| Workload | 目标 | 主要指标 | 注意事项 |
|---|---|---|---|
| INT64 ALU | 整数算术与依赖链 | ops/s | 区分吞吐与延迟路径 |
| FP32 FMA | 浮点向量吞吐 | GFLOPS | 控制数据驻留，检查向量化 |
| SHA-256 | 加密/哈希能力 | MB/s | 区分硬件指令和通用实现 |
| Compression | 压缩场景 | MB/s | 固定算法、级别和数据集 |
| Matrix | 矩阵计算 | GFLOPS/时间 | 固定尺寸与实现，避免调用不可控厂商库 |
| Image Kernel | 图像处理场景 | MP/s | 固定像素格式与算法 |

---

## 12. GPU Benchmark 设计

## 12.1 渲染模式

每个图形测试至少提供：

1. **Fixed Offscreen**：固定 1920×1080 或固定像素数量，用于跨设备比较。
2. **Native Experience**：设备原生或当前窗口分辨率，用于实际体验，不参与同一跨设备主分数。

## 12.2 GPU 计时

优先使用 GPU 侧计时：

- Vulkan timestamp query。
- OpenGL ES timer query，扩展可用时。
- 显式等待 query 结果。

必须区分：

- CPU command generation。
- Driver submission。
- GPU execution。
- Present/display pacing。

不得仅用 CPU 包围 `draw()` 的耗时作为 GPU 性能。

## 12.3 初期 GPU Workloads

| Workload | 主要瓶颈 | 输出 |
|---|---|---|
| Fill Rate | ROP/像素填充 | GPixel/s |
| Shader ALU | 片元或计算 ALU | ops/s、GPU time |
| Texture Sampling | 纹理采样与缓存 | GTexel/s |
| Geometry | 顶点处理 | M vertices/s |
| Compute | Compute Shader | ops/s |
| Driver Overhead | Draw-call 提交 | draws/s，单独分类 |
| Scene | 综合图形场景 | GPU time/FPS，标为场景指标 |

初期采用 OpenGL ES 3.x；Vulkan 在后续版本加入，避免第一版同时维护两套复杂后端。

---

## 13. 内存 Benchmark 设计

### 13.1 带宽

测试：

- Sequential Read
- Sequential Write
- Copy
- Triad
- 单线程与多线程

工作集应明显大于系统级缓存，避免只测到 L1/L2/LLC。

### 13.2 延迟

使用随机 pointer chasing，降低硬件预取作用。

数据规模建议：

```text
16 KB
64 KB
256 KB
1 MB
4 MB
16 MB
64 MB
256 MB
```

输出延迟曲线，而不是只给一个 RAM 分数。

### 13.3 注意事项

- 预先分配并触碰内存，排除首次缺页影响。
- 使用固定内存对齐。
- 避免在计时区间分配。
- 记录线程绑定策略。
- 大内存测试根据设备 RAM 自适应，但必须映射到明确 workload 版本。

---

## 14. 存储 Benchmark 设计

### 14.1 测试分类

- Buffered Sequential Write
- Durable Sequential Write
- Warm Read
- Cold-ish Read
- Random Read/Write
- Small-file Create/Read/Delete

### 14.2 核心要求

- 测试文件必须足够大，降低纯 page cache 影响。
- 使用高熵、不可轻易压缩的数据。
- 明确是否执行 `fsync`/`fdatasync`。
- 记录 block size、queue depth、线程数和文件大小。
- 记录目标路径和对应存储卷。
- 记录剩余空间比例。
- 测试完成后可靠清理文件。
- 写入后校验文件大小和抽样内容。

### 14.3 命名原则

普通无 Root App 无法保证清空系统 page cache，因此不得宣称“绝对冷读”。应使用“Cold-ish Read”或“降低缓存影响的读取”。

Buffered Write 与 Durable Write 必须分开，前者可能主要反映系统缓存速度，后者更接近持久化写入。

---

## 15. 持续性能测试

### 15.1 测试模式

- CPU Sustained：10/20/30 分钟。
- GPU Sustained：10/20/30 分钟。
- Mixed Sustained：CPU + GPU 的真实高负载。

### 15.2 输出

- 初始峰值。
- 前 60 秒中位数。
- 稳定平台性能。
- 最低 60 秒中位数。
- 性能保持率。
- 达到热状态变化的时间。
- Thermal status/headroom 曲线。
- 电量变化。
- 频率和 CPU/GPU headroom，设备支持时。

### 15.3 性能保持率

概念定义：

```text
稳定阶段中位吞吐 / 初始稳定窗口中位吞吐
```

不使用单个瞬时峰值作为分母，避免偶发 Boost 放大衰减。

---

## 16. 测试顺序与公平性

### 16.1 分类独立

CPU、GPU、内存和存储可单独运行。每项开始前重新检查环境。

### 16.2 冷却门槛

分类之间不只使用固定等待时间，应使用：

- Thermal status。
- Thermal headroom。
- 最近性能探针恢复情况。
- 最长等待上限。

### 16.3 顺序偏差

完整套件可采用：

- 预定义多套合法顺序，根据 Run ID 选择。
- 对关键校准实验使用正序/逆序对照。
- Run Manifest 记录实际测试顺序。

### 16.4 不同模式不混池

以下结果不能进入同一比较池：

- 普通消费者模式。
- ADB Fixed Performance Mode。
- Root 锁频模式。
- 外接主动散热模式。
- 模拟器或云真机。

---

## 17. Run Manifest

每次测试必须保存：

```text
run_id
app_version
benchmark_engine_version
score_version
workload_id + workload_version
build_type
compiler_version
compiler_flags
ABI
Android_version
security_patch
kernel_version
GPU_driver_version
device_model
SoC_reported_values
battery_level
charging_state
power_save_mode
performance_mode_if_known
thermal_status_start/end
thermal_headroom_start/end
screen_state
screen_resolution
refresh_rate
available_memory
storage_free_ratio
thread_count
cpu_affinity
actual_cpu_samples
test_order
warmup_samples
measurement_samples
median
MAD
CV
correctness_status
validity_level
warnings
```

---

## 18. 评分系统设计边界

评分系统由项目自行设计，但必须建立在稳定原始测量之上。

## 18.1 评分阶段

1. Alpha 只展示原始指标和统计。
2. 收集覆盖不同性能档次的真实设备数据。
3. 冻结 workload v1。
4. 建立参考常数和归一化规则。
5. 发布 Score Specification v1。
6. 分数算法变更时升级 score version。

## 18.2 评分原则

- 原始数据永久保留。
- 每个子分可追溯到原始 workload。
- 不用一台物理设备的一次成绩作为永久基线。
- 参考值应来自多次、跨样本的稳健统计。
- 高者优和低者优指标分别处理。
- 优先使用比值或对数尺度，减少跨代性能跨度带来的失真。
- 分类内可使用加权几何平均，但权重必须公开。
- 峰值、持续、稳定性和能效应分开呈现。
- 运行波动作为“可信度”展示，不建议直接乘入硬件性能分。
- 环境不合格则不给正式分，而不是用惩罚系数制造伪精度。

## 18.3 建议的分数结构

```text
CPU Performance Index
GPU Performance Index
Memory Performance Index
Storage Performance Index
Sustained Performance Index
Stability Grade
Overall Performance Index（V1.0 再决定是否启用）
```

“稳定性等级”表达本次测量可信程度；“持续性能指数”表达设备长期性能。二者不应混成同一个概念。

---

## 19. 结果有效性与反作弊

### 19.1 无效条件

- workload 正确性校验失败。
- 测试进程进入后台或被系统限制。
- 设备 Thermal 状态在峰值测试开始前已经明显过热。
- 波动超过阈值且重试后仍不稳定。
- 存储文件写入或同步失败。
- GPU query 不可用且 fallback 无法准确计时。
- 系统时间、进程或设备状态异常。
- 版本信息不完整。

### 19.2 异常检测

- 成绩与同机历史值偏差过大。
- 多项硬件信息冲突。
- 运行时核心数、ABI 或驱动信息异常变化。
- 性能曲线出现不符合工作负载的突变。
- 测试耗时和结果 checksum 不符合规范。

### 19.3 厂商专项优化风险

无法完全阻止系统针对 Benchmark 包名或进程进行特殊调度。应采取：

- 公开该风险，不声称绝对防作弊。
- 多种 workload 和动态输入，减少对单一循环的定向优化价值。
- 记录频率、核心、热状态和性能曲线。
- 社区版本可复现测试逻辑。
- 比较同设备不同安装包或开发构建，用于研究异常，但不作为普通用户功能。

---

## 20. 技术架构

### 20.1 推荐技术栈

- Kotlin
- Jetpack Compose + Material 3
- Coroutines + Flow
- Room
- Kotlin Serialization
- C++20 Android NDK
- CMake
- JNI
- OpenGL ES 3.x
- Vulkan 后续加入
- AndroidX Benchmark 用于开发阶段验证
- Perfetto/Simpleperf 用于内部分析

### 20.2 模块划分

```text
:app
:core:model
:core:hardware
:core:provenance
:core:telemetry
:core:storage
:core:export

:benchmark:api
:benchmark:engine
:benchmark:cpu
:benchmark:memory
:benchmark:storage
:benchmark:gpu-gles
:benchmark:sustained
:benchmark:scoring

:feature:overview
:feature:hardware
:feature:benchmark
:feature:result
:feature:history
:feature:methodology
:feature:settings

:native:cpu
:native:memory
:native:storage
:native:gpu
```

### 20.3 核心边界

- `hardware`：只负责信息采集和证据，不负责 UI 文案。
- `provenance`：来源、冲突、可信度和推断。
- `benchmark:engine`：生命周期、预热、重复、统计和无效判定。
- workload 模块：只实现工作负载和正确性校验。
- `scoring`：只消费稳定原始结果，不直接访问设备硬件。
- UI：展示，不修改原始测量。

---

## 21. 数据与隐私

### 21.1 默认本地

- 硬件信息和跑分默认只保存在本机。
- 不要求登录。
- 不读取设备标识、通讯录、位置或用户文件。
- 存储测试只使用 App 专属测试目录。

### 21.2 匿名数据上传

后续排行榜功能必须用户主动加入，并明确展示上传字段。

上传前：

- 删除 Android ID、序列号、广告 ID 和账号信息。
- 使用随机安装 ID 或每次上传 ID。
- 允许预览 JSON。
- 允许删除服务器数据。
- 设备型号、系统版本和成绩属于可能形成设备指纹的数据，应在隐私说明中明确。

---

## 22. 商业化设计

### 22.1 免费版

- 硬件信息。
- 快速测试。
- 基础完整测试。
- 本地历史记录。
- 页面底部或结果页非侵入广告。

### 22.2 Pro 版

- 无广告。
- 20/30 分钟持续测试。
- 无限历史记录。
- JSON/CSV/高清图片报告。
- 多次结果叠加曲线。
- 高级硬件来源详情。
- A/B 对比和开发者模式。

### 22.3 广告完整性规则

- Benchmark 运行期间不得请求或刷新广告。
- 测试前预加载广告也不能占用测试线程和显存。
- 测试结果不得因广告版与 Pro 版产生差异。
- 测试期间网络和埋点事件进入缓冲，结束后再发送。

---

## 23. 成功指标

### 23.1 产品指标

- 首次安装后完成一次硬件扫描的比例。
- 完成快速测试的比例。
- 完成完整测试的比例。
- 7 日内再次测试比例。
- 历史对比使用率。
- 方法说明页打开率。
- Pro 转化率。

### 23.2 技术质量指标

- A 级来源字段占核心字段比例。
- 无来源字段比例必须为 0。
- 硬件来源冲突识别率。
- 同机同环境重复测试 CV。
- workload 正确性测试覆盖率。
- Benchmark crash/ANR 率。
- 结果无效原因可解释率。
- 不同 app 版本下同 workload version 的回归偏差。

### 23.3 MVP 技术门槛

- 主 CPU workloads 在实验设备上中位 CV ≤ 3%。
- GPU 固定分辨率测试在稳定环境下中位 CV ≤ 5%。
- 存储测试能明确区分 Buffered 与 Durable 模式。
- 所有正式结果均含完整 Run Manifest。
- 所有硬件字段均可展开查看来源。
- 任意评分均可追溯到原始样本。

---

## 24. 测试与验证计划

### 24.1 设备矩阵

至少覆盖：

- Qualcomm 新旧旗舰与中端。
- MediaTek 旗舰与中端。
- Samsung Exynos。
- Google Tensor。
- 不同 Android 版本和厂商 ROM。
- 4 GB、8 GB、12 GB 以上内存。
- eMMC、UFS 不同性能档次。
- 高刷新率和高分辨率设备。

### 24.2 硬件信息验证

- 与厂商公开规格交叉验证，但不把规格页当作设备实测。
- 与 ADB、系统 dumpsys 和实验室工具对照。
- 验证 API 级别 fallback。
- 构造冲突属性和缺失字段测试。
- 验证不支持时是否诚实显示未知。

### 24.3 Benchmark 验证

- 与 Google Benchmark 或 AndroidX Benchmark 对照核心循环。
- 使用 Perfetto/Simpleperf 确认实际瓶颈。
- 检查 native 汇编和 SIMD 路径。
- 使用固定输入的 golden vectors 验证正确性。
- 在 Root 实验设备锁频后测量算法自身噪声。
- 在普通设备测试消费者模式波动。
- 对比插电、未插电、省电、性能模式和不同热状态。
- 测试后台干扰和低存储空间情况。

---

## 25. 开源项目参考方式

| 项目 | 主要审计目标 | 不直接继承的部分 |
|---|---|---|
| Athena | 字段采集链路、模块化、sysfs/属性 fallback | 不默认相信所有字段标签 |
| CPU Info | Android API、native CPU feature 和跨平台采集 | 不继承不适合 Android-only 的额外抽象 |
| FinalBenchmark 2 | Benchmark 套件组织、历史与持续测试 | 不采用其现成评分基线 |
| CPDT | 文件测试模式、缓存配置、RAM/Storage 分类 | 不采用旧 UI 技术栈和未经复核的测试语义 |
| Google cpu_features | CPU feature 探测与已知设备修正 | 不用商业名称映射替代原始 feature |
| Google Benchmark | 预热、重复、统计和 correctness 设计 | 不直接将桌面默认配置用于移动设备 |
| AndroidX Benchmark | Android 性能测试环境与一致性检查 | 不把 App 自身微基准等同于全设备跑分 |
| glmark2 / vkmark | GPU workload 分解和场景思想 | 注意许可证，不直接复制受限代码 |

---

## 26. 对现有技术材料的评审

用户提供的材料 **有用，适合作为入门版技术提纲**，其中“分层获取”“严谨性、公平性、有效性”和“测试引擎—采集—分析—展示”四层结构都可以保留。但不能原样作为工程规范，需做以下修正。

### 26.1 可直接保留的观点

- 优先使用 Android 公共 API，再按需下探 NDK、`/proc`、`/sys`。
- `/proc` 和 `/sys` 需要容错，并受内核、厂商和 SELinux 影响。
- `getauxval()` 和 Google `cpu_features` 对 CPU feature 检测很有价值。
- Benchmark 需要预热、重复、环境记录和热状态监测。
- 真实场景和持续负载能够补充纯理论峰值测试。
- 测试执行、指标采集、分析和展示应分层。

### 26.2 必须修正的内容

#### `SystemProperties.get()` 不是普通应用的标准公共 API

`android.os.SystemProperties` 属于非 SDK 接口，Android 对隐藏 API 有访问限制。不能将其写成与 `Build`、`ActivityManager` 同等级的稳定方案。系统属性可以作为兼容证据层，但必须允许失败，并优先使用公共字段。

#### 系统属性来源描述过于简化

系统属性并不是简单地全部从 `/proc/cmdline` 导入并“固化在内存”。Android property service 会从多类属性文件、构建配置、启动参数和厂商分区加载与管理。PRD 不需要依赖这种过度简化的内部描述。

#### `availableProcessors()` 不是可靠的物理核心总数

它返回当前 JVM 可使用的处理器数量，可能在运行中变化。应与 `sysconf()`、sysfs、online/present 和 affinity 交叉验证。

#### BogoMIPS 不应成为有效硬件指标

BogoMIPS 不是 CPU 性能指标，不应进入产品主要字段或评分。

#### `MemFree` 不等于用户理解的可用内存

应优先使用 Android `availMem` 或 Linux `MemAvailable` 语义。完全空闲内存和可回收后可用内存必须区分。

#### `scaling_cur_freq` 不一定是真实时钟

不同 cpufreq driver 下，它可能表示驱动返回值、请求值或近似值。只能作为遥测证据，不能声称是精确实际频率。

#### “三次取平均值”不够严谨

应使用自适应预热、至少 5 次正式测量、中位数、MAD、CV 和无效判定。平均值易受一次系统中断影响。

#### CPU 温度和频率不一定可可靠监控

普通应用通常不能获得统一、准确的 CPU/GPU 核心温度。测试有效性应主要依赖吞吐曲线、Android Thermal status/headroom 和环境状态，而不是假设有精确温度。

#### 归一化和几何平均属于评分设计，不等同于公平性

公平性首先来自：

- 相同 workload。
- 相同数据规模。
- 固定渲染分辨率。
- 明确线程和核心策略。
- 一致计时方法。
- 环境记录与无效判定。
- workload 和 score 版本隔离。

之后才是归一化和综合方式。

#### 单一参考设备不应成为永久基线

应使用版本化参考常数和多次样本的稳健统计。某一台设备的系统更新、散热状态或个体差异不应改变整个评分尺度。

#### 真实场景不能替代微基准

真实场景有效，但瓶颈混合。产品需要同时提供 micro、scenario 和 sustained 三种测试，分别回答不同问题。

---

## 27. 关键风险

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| Android 权限和接口随版本变化 | 字段失效 | 来源分级、fallback、兼容测试 |
| 厂商节点不统一 | 温度/频率误判 | 原始标签展示，不做强制映射 |
| DVFS 和热状态造成波动 | 成绩不稳定 | 预热、重复、环境等级、持续曲线 |
| 厂商针对跑分优化 | 公平性受损 | 多 workload、动态输入、透明遥测 |
| 存储 page cache 污染 | 结果虚高 | 分类命名、足够文件、同步模式 |
| GPU 计时错误 | 测到 CPU/驱动 | GPU query、后端校验 |
| 评分版本漂移 | 新旧结果混乱 | workload/score 双版本化 |
| 测试耗电和发热 | 用户不满 | 明确提示、时长选择、可中止 |
| 广告污染跑分 | 可信度下降 | 测试期间冻结广告与网络 |
| 上传数据形成设备指纹 | 隐私风险 | 默认本地、主动加入、字段预览 |

---

## 28. MVP 验收标准

### 硬件信息

- [ ] 核心字段均有来源类型和来源说明。
- [ ] 读取值与数据库映射分开。
- [ ] 能识别并展示来源冲突。
- [ ] CPU 核心数区分配置、在线和应用可用。
- [ ] RAM 区分 advertised、kernel accessible 和 available。
- [ ] 存储区分介质总量和文件系统总量。
- [ ] GPU 信息来自有效图形 Context。
- [ ] 温度与频率不做无依据的确定性命名。

### Benchmark

- [ ] CPU、GPU、内存、存储各至少 2 个有效 workload。
- [ ] 每个 workload 有版本、目标和 correctness check。
- [ ] 支持预热、重复、median、MAD、CV。
- [ ] 支持环境检查和 INVALID/NOISY 标记。
- [ ] 保存完整 Run Manifest。
- [ ] 峰值和持续性能分开。
- [ ] GPU 跨设备分数使用固定 offscreen 分辨率。
- [ ] 存储区分 buffered 和 durable。
- [ ] 测试期间无广告或网络干扰。

### 产品体验

- [ ] 普通用户可一键完成快速测试。
- [ ] 高级用户可查看全部原始样本。
- [ ] 每项结果可进入方法说明。
- [ ] 可导出 JSON。
- [ ] 支持本机历史结果对比。

---

## 29. 初步里程碑

### Milestone 1：信息采集原型

- HardwareFact 数据模型。
- SoC、CPU、GPU、RAM、存储、热状态。
- 来源详情页。
- 设备兼容性测试。

### Milestone 2：Benchmark Core

- Native workload 接口。
- Warmup/measurement 状态机。
- 统计、校验、无效判定。
- Run Manifest。

### Milestone 3：四大分项

- CPU。
- GPU GLES。
- Memory。
- Storage。

### Milestone 4：持续性能与历史

- 10 分钟持续测试。
- 曲线展示。
- 本机历史对比。
- JSON/CSV 导出。

### Milestone 5：校准与评分 Beta

- 设备样本采集。
- Reference constants。
- Score Specification v0.9。
- 分类分数和版本迁移。

### Milestone 6：Play Store MVP

- 隐私政策。
- 广告与 Pro。
- 崩溃、ANR、发热和耗电验证。
- 商店素材与公开方法页面。

---

## 30. 官方参考资料

1. Android `Build.SOC_MODEL` / `SOC_MANUFACTURER`  
   https://developer.android.com/reference/android/os/Build
2. Android `ActivityManager.MemoryInfo`  
   https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
3. Android NDK CPU features  
   https://developer.android.com/ndk/guides/cpu-features
4. Android 非 SDK 接口限制  
   https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
5. Android Microbenchmark  
   https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview
6. Android Benchmark overview  
   https://developer.android.com/topic/performance/benchmarking/benchmarking-overview
7. Android Thermal API / `PowerManager`  
   https://developer.android.com/reference/android/os/PowerManager
8. Android `SystemHealthManager`  
   https://developer.android.com/reference/android/os/health/SystemHealthManager
9. Android `StorageStatsManager`  
   https://developer.android.com/reference/android/app/usage/StorageStatsManager
10. Android `StatFs`  
    https://developer.android.com/reference/android/os/StatFs
11. Google cpu_features  
    https://android.googlesource.com/platform/external/cpu_features/

---

## 31. 最终产品定义

芯鉴的核心竞争力不是“测试项目最多”，而是：

> **它能清楚告诉用户一个硬件信息从哪里来，一个性能数字怎么测出来，这次结果是否值得相信。**

产品第一阶段的正确顺序应当是：

```text
可信采集
→ 工作负载定义
→ 正确性验证
→ 环境与统计协议
→ 原始指标
→ 稳定样本库
→ 自研评分
→ 设备对比与排行榜
```

不能反过来先设计一个漂亮总分，再为总分寻找测试项目。
