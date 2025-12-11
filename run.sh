#!/bin/bash

# 快速启动脚本
# 用于快速运行备份软件

set -e

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}INFO:${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}WARNING:${NC} $1"
}

print_error() {
    echo -e "${RED}ERROR:${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v "$1" &> /dev/null; then
        print_error "$1 未安装"
        return 1
    fi
    return 0
}

# 显示使用说明
show_usage() {
    echo "使用方法:"
    echo "  ./run.sh              # 运行项目"
    echo "  ./run.sh --compile    # 编译后运行"
    echo "  ./run.sh --package    # 打包项目"
    echo "  ./run.sh --clean      # 清理并运行"
    echo "  ./run.sh --help       # 显示帮助"
}

# 运行项目
run_project() {
    print_info "正在运行备份软件..."
    mvn javafx:run
}

# 编译并运行
compile_and_run() {
    print_info "正在编译项目..."
    mvn compile -q
    print_info "编译完成，正在运行..."
    mvn javafx:run
}

# 清理并运行
clean_and_run() {
    print_info "正在清理并重新编译..."
    mvn clean compile -q
    print_info "编译完成，正在运行..."
    mvn javafx:run
}

# 打包项目
package_project() {
    print_info "正在打包项目..."
    mvn clean package -DskipTests
    print_info "打包完成！"
    echo ""
    echo "运行打包后的程序:"
    echo "  java -jar target/backup-software-1.0-SNAPSHOT.jar"
}

# 检查环境
check_environment() {
    print_info "检查运行环境..."
    
    if ! check_command "java"; then
        print_error "请先安装JDK 17或更高版本"
        exit 1
    fi
    
    if ! check_command "mvn"; then
        print_error "请先安装Maven"
        echo "可以运行 ./setup.sh 自动配置环境"
        exit 1
    fi
    
    # 检查Java版本
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        print_error "Java版本过低，需要17或更高版本"
        exit 1
    fi
    
    print_info "环境检查通过"
}

# 主函数
main() {
    case "${1:-}" in
        "")
            check_environment
            run_project
            ;;
        --compile)
            check_environment
            compile_and_run
            ;;
        --clean)
            check_environment
            clean_and_run
            ;;
        --package)
            check_environment
            package_project
            ;;
        --help)
            show_usage
            ;;
        *)
            print_error "未知参数: $1"
            show_usage
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
