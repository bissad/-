# 环境配置指南

## 系统要求

- **操作系统**: macOS, Linux, Windows (WSL2)
- **Java版本**: JDK 17 或更高版本
- **构建工具**: Maven 3.6+
- **内存**: 至少 2GB RAM
- **磁盘空间**: 至少 500MB 可用空间

## 环境检查

### 1. 检查Java环境

打开终端，运行以下命令：

```bash
java -version
```

预期输出（版本号可能不同）：
```
java version "21.0.6" 2025-01-21 LTS
Java(TM) SE Runtime Environment (build 21.0.6+8-LTS-188)
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

#### CentOS/RHEL
```bash
sudo yum install java-17-openjdk-devel
```

### 2. 检查Maven环境

```bash
mvn -version
```

预期输出：
```
Apache Maven 3.9.4
Maven home: /usr/local/Cellar/maven/3.9.4/libexec
Java version: 21.0.6
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

#### CentOS/RHEL
```bash
sudo yum install maven
```

## 快速配置（推荐）

使用自动配置脚本一键配置环境：

```bash
# 进入项目目录
cd /Users/a123/Public/备份软件

# 运行配置脚本
./setup.sh
```

脚本会自动完成：
- ✅ 检查Java版本
- ✅ 安装Maven（如未安装）
- ✅ 配置Maven阿里云镜像（加速下载）
- ✅ 下载项目依赖
- ✅ 编译项目

## 手动配置

如果不想使用脚本，可以手动配置：

### 1. 配置Maven镜像（中国用户推荐）

编辑 `~/.m2/settings.xml` 文件：

```bash
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'EOF'
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
EOF
```

### 2. 下载依赖

```bash
mvn clean compile
```

### 3. 验证安装

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

4. **运行配置**:
   - Run → Edit Configurations → +
   - 选择 "Maven"
   - Name: "Backup App"
   - Command line: `javafx:run`

### VS Code

1. 安装插件:
   - Extension Pack for Java
   - Maven for Java

2. 打开项目文件夹

3. 按 `Ctrl+Shift+P` → "Java: Configure Java Runtime"
   - 选择 JDK 17+

4. 运行:
   - 打开 pom.xml
   - 点击右键 → "Run Maven Commands" → "compile"

## 常见问题

### Q1: Maven下载依赖很慢
**解决方案**: 配置阿里云镜像（见上文）

### Q2: Java版本过低
**解决方案**: 安装JDK 17或更高版本

### Q3: mvn命令找不到
**解决方案**: 
- macOS: `brew install maven`
- Linux: `sudo apt install maven` 或 `sudo yum install maven`
- Windows: 下载Maven并配置环境变量

### Q4: JavaFX运行时错误
**解决方案**: 确保pom.xml中JavaFX版本与JDK版本兼容（已配置为JavaFX 21 + JDK 17+）

### Q5: 权限不足（macOS/Linux）
**解决方案**: 
```bash
chmod +x setup.sh
./setup.sh
```

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

## 配置脚本说明

`setup.sh` 脚本参数：

```bash
./setup.sh          # 完整配置
./setup.sh --help   # 显示帮助
```

脚本功能：
- 自动检测操作系统
- 自动安装缺失的依赖
- 配置Maven镜像加速
- 下载并编译项目
- 显示详细的状态信息

## 卸载/清理

如需清理环境：

```bash
# 删除Maven缓存
rm -rf ~/.m2/repository

# 删除项目构建文件
cd /Users/a123/Public/备份软件
mvn clean
rm -rf target/
```
