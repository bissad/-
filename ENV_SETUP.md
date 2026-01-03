# 环境配置指南

## 系统要求

- **操作系统**: macOS, Linux, Windows (WSL2/PowerShell/CMD)
- **Java版本**: JDK 17 或更高版本
- **构建工具**: Maven 3.6+
- **内存**: 至少 2GB RAM
- **磁盘空间**: 至少 500MB 可用空间

## 环境检查

### 1. 检查Java环境

打开终端/命令提示符，运行以下命令：

```bash
java -version
```

预期输出（版本号可能不同）：
```
java version "17.0.x" 或更高版本
```

**要求**: 版本必须是 17 或更高。

如果未安装Java，请根据操作系统选择安装方式：

#### macOS
```bash
# 使用Homebrew安装OpenJDK 17
brew install openjdk@17

# 配置环境变量
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

#### Windows
下载并安装 OpenJDK 17+ from https://adoptium.net/

### 2. 检查Maven环境

```bash
mvn -version
```

预期输出：
```
Apache Maven 3.6+
Java version: 17+
```

如果未安装Maven：

#### macOS
```bash
brew install maven
```

#### Ubuntu/Debian
```bash
sudo apt install maven
```

#### Windows
1. 下载 Maven from https://maven.apache.org/download.cgi
2. 解压到目录（如 C:\apache-maven-3.x）
3. 设置环境变量 MAVEN_HOME 和添加 %MAVEN_HOME%\bin 到 PATH

## Maven配置优化（可选）

配置Maven使用国内镜像加速下载：

编辑 `~/.m2/settings.xml` 文件（Windows用户为 `%USERPROFILE%\.m2\settings.xml`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>aliyunmaven</id>
            <mirrorOf>*</mirrorOf>
            <name>阿里云公共仓库</name>
            <url>https://maven.aliyun.com/repository/public</url>
        </mirror>
    </mirrors>
</settings>
```

## 项目构建

### 1. 下载项目依赖

```bash
mvn clean compile
```

### 2. 验证安装

```bash
# 运行项目
mvn javafx:run

# 或打包后运行
mvn clean package
java -jar target/backup-software-1.0-SNAPSHOT.jar
```

## IDE配置

### IntelliJ IDEA

1. **打开项目**:
   - File → Open → 选择项目目录
   - 选择 "Open as Project"

2. **配置JDK**:
   - File → Project Structure → Project
   - SDK: 选择 JDK 17 或更高版本
   - Language level: 17

3. **配置Maven**:
   - File → Settings → Build, Execution, Deployment → Build Tools → Maven
   - Maven home path: 使用捆绑的Maven或指定本地路径

### VS Code

1. 安装插件:
   - Extension Pack for Java
   - Maven for Java

2. 打开项目文件夹

3. 按 `Ctrl+Shift+P` → "Java: Configure Java Runtime"
   - 选择 JDK 17+

## 常见问题

### Q1: Maven下载依赖很慢
**解决方案**: 配置镜像（见上文）

### Q2: Java版本过低
**解决方案**: 安装JDK 17或更高版本

### Q3: mvn命令找不到
**解决方案**: 
- macOS/Linux: 检查PATH环境变量
- Windows: 确保Maven的bin目录在系统PATH中

### Q4: JavaFX运行时错误
**解决方案**: 确保pom.xml中JavaFX版本与JDK版本兼容

### Q5: 权限不足（macOS/Linux）
**解决方案**: 确保项目目录有写权限

## 验证安装

运行以下命令验证所有组件：

```bash
# 验证Java
echo "Java版本:" && java -version

# 验证Maven
echo "Maven版本:" && mvn -version

# 验证项目
echo "项目编译:" && mvn compile -q && echo "✓ 成功"
```

所有检查通过即可开始开发！