# 文件备份软件

基于JavaFX的桌面备份工具，支持文件夹增量备份。

## 功能特性

- 图形化界面，操作简单
- 选择源目录和目标目录
- 增量备份（只复制新增或修改的文件）
- 实时显示备份进度和结果
- 跨平台支持（Windows、macOS、Linux）

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

### 方式3：开发模式运行

```bash
mvn clean compile exec:java -Dexec.mainClass="com.backup.BackupApplication"
```

## 使用方法

1. 启动应用程序
2. 点击"浏览..."选择要备份的源目录
3. 点击"浏览..."选择备份目标目录
4. 点击"开始备份"按钮
5. 等待备份完成，查看备份结果

## 项目结构

```
src/main/java/com/backup/
├── BackupApplication.java  # 应用程序入口
├── MainController.java     # UI控制器
└── BackupService.java      # 备份核心逻辑

src/main/resources/
└── main-view.fxml          # UI布局文件
```

## 技术栈

- Java 17
- JavaFX 21
- Maven