#!/bin/bash
cd "$(dirname "$0")"

# 查找JavaFX库
JAVAFX_PATH=""
if [ -d "/usr/local/opt/openjfx/lib" ]; then
    JAVAFX_PATH="/usr/local/opt/openjfx/lib"
elif [ -d "/opt/homebrew/opt/openjfx/lib" ]; then
    JAVAFX_PATH="/opt/homebrew/opt/openjfx/lib"
fi

if [ -n "$JAVAFX_PATH" ]; then
    # 收集所有JavaFX JAR文件
    JAVAFX_JARS=$(find "$JAVAFX_PATH" -name "*.jar" | tr '\n' ':')
    
    # 运行应用程序
    java --module-path "$JAVAFX_JARS" \
         --add-modules javafx.controls,javafx.fxml \
         -cp "target/classes" \
         com.backup.BackupApplication
else
    echo "错误: 未找到JavaFX库"
    echo "请安装JavaFX: brew install openjfx"
    exit 1
fi