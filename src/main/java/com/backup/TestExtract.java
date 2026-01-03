package com.backup;

import java.io.IOException;

/**
 * 测试解包功能的简单程序
 */
public class TestExtract {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: TestExtract <包文件.fbk> <输出目录>");
            System.out.println("示例: TestExtract test_package.fbk extracted");
            return;
        }
        
        String packageFile = args[0];
        String outputDir = args[1];
        
        try {
            System.out.println("开始解包: " + packageFile + " -> " + outputDir);
            
            // 执行解包
            boolean success = BackupPackage.extractPackage(packageFile, outputDir);
            
            if (success) {
                System.out.println("解包成功!");
                System.out.println("文件提取到: " + outputDir);
            } else {
                System.out.println("解包失败!");
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