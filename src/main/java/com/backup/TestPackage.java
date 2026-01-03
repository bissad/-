package com.backup;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * 测试打包功能的简单程序
 */
public class TestPackage {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: TestPackage <源目录> <目标文件.fbk>");
            System.out.println("示例: TestPackage test_source backup.fbk");
            return;
        }
        
        String source = args[0];
        String target = args[1];
        
        try {
            System.out.println("开始打包: " + source + " -> " + target);
            
            // 创建备份选项
            BackupService.BackupOptions options = new BackupService.BackupOptions(true, true);
            
            // 执行打包
            boolean success = BackupPackage.createPackage(source, target, options);
            
            if (success) {
                System.out.println("打包成功!");
                System.out.println("包文件: " + target);
                
                // 获取包信息
                System.out.println("\n获取包信息...");
                try {
                    BackupPackage.BackupManifest manifest = BackupPackage.getPackageInfo(target);
                    System.out.println("包文件读取成功!");
                    System.out.println("包含 " + manifest.getFiles().size() + " 个文件");
                    System.out.println("总大小: " + manifest.getTotalBytes() + " 字节");
                    System.out.println("根目录: " + manifest.getRootName());
                } catch (Exception e) {
                    System.out.println("获取包信息失败: " + e.getMessage());
                }
            } else {
                System.out.println("打包失败!");
            }
        } catch (IOException e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("未知错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}