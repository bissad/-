@echo off
echo 测试备份打包功能
echo.

REM 创建测试目录和文件
echo 创建测试目录和文件...
mkdir test_source 2>nul
mkdir test_target 2>nul

echo This is a test file. > test_source\test1.txt
echo This is another test file. > test_source\test2.txt
mkdir test_source\subdir
echo File in subdirectory. > test_source\subdir\test3.txt

echo 测试目录结构已创建。
echo.

REM 运行Java程序测试打包功能
echo 运行备份打包测试...
java -cp "target/backup-software-1.0-SNAPSHOT-shaded.jar" com.backup.BackupPackage test_source test_target\backup.fbk

echo.
echo 测试完成。
echo 检查 test_target 目录中的 backup.fbk 文件。

REM 清理（可选）
REM rmdir /s /q test_source
REM rmdir /s /q test_target

pause