# 文件备份软件

基于JavaFX的桌面备份工具，支持文件夹备份、打包、压缩和加密功能。

## 功能特性

- **图形化界面**：直观易用的操作界面
- **多种备份模式**：
  - 目录备份：传统文件夹备份
  - 包文件备份：生成.fbk文件，便于传输和存储
- **压缩功能**：支持哈夫曼编码、游程编码(RLE)、ZLIB压缩
- **加密功能**：支持XOR、RC4、AES-256加密算法
- **智能备份**：增量备份，只处理新增或修改的文件
- **实时进度**：显示备份/还原进度和结果
- **跨平台**：支持Windows、macOS、Linux

## 运行要求

- JDK 17 或更高版本
- Maven 3.6+

## 运行项目

### 方式1：使用Maven运行

```bash
mvn javafx:run
```

### 方式2：编译为可执行JAR

```bash
mvn clean package
java -jar target/backup-software-1.0-SNAPSHOT.jar
```

## 使用方法

### 备份功能
1. 启动应用程序
2. 点击"添加文件夹"或"添加文件"选择要备份的源
3. 点击"浏览"选择备份目标目录
4. 勾选"打包模式"启用.fbk文件生成
5. 选择压缩和加密选项
6. 点击"开始备份"按钮

### 还原功能
1. 切换到"还原"标签页
2. 选择还原模式（目录还原或包文件还原）
3. 选择要还原的备份文件/文件夹或.fbk包文件
4. 选择还原目标目录
5. 点击"开始还原"按钮

## 项目结构

```
src/main/java/com/backup/
├── BackupApplication.java          # 基础应用程序入口
├── EnhancedBackupApplication.java  # 应用程序入口
├── MainController.java             # UI控制器
├── MainControllerFixed.java        # 修复版UI控制器
├── EnhancedMainController.java     # UI控制器
├── BackupService.java              # 备份服务
├── EnhancedBackupService.java      # 备强备份服务
├── BackupPackage.java              # 打包/解包核心逻辑
├── TestExtract.java                # 测试类
└── TestPackage.java                # 测试类

src/main/resources/
├── main-view.fxml                   # UI布局
├── main-view-enhanced.fxml          # UI布局
└── main-view-simple.fxml            # UI布局
```

## 技术栈

- Java 17
- JavaFX 21
- Maven
- Gson (JSON处理)

## 核心功能

### 打包功能
- 将整个文件夹结构打包为单一.fbk文件
- 保留文件元数据（创建时间、修改时间、访问时间）
- 支持特殊文件处理

### 压缩功能
- **哈夫曼编码**：适用于文本文件
- **游程编码(RLE)**：适用于有重复数据的文件
- **ZLIB压缩**：通用压缩算法

### 加密功能
- **XOR加密**：快速加密，适合日常使用
- **RC4加密**：流加密算法
- **AES-256加密**：高级加密标准，银行级安全

## 安全特性

- 密码验证机制：确保密码正确性
- 文件完整性校验：使用SHA-256哈希验证
- 多层加密：支持压缩后加密的组合安全