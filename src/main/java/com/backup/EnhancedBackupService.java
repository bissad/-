package com.backup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的备份服务 - 扩展原有BackupService，添加打包功能
 */
public class EnhancedBackupService extends BackupService {
    
    // 扩展的备份选项
    public static class EnhancedBackupOptions extends BackupOptions {
        private boolean packageMode = false;           // 是否打包模式
        private boolean compress = false;             // 是否压缩
        private boolean encrypt = false;              // 是否加密
        private BackupPackage.CompressionMethod compressionMethod = BackupPackage.CompressionMethod.NONE;
        private BackupPackage.EncryptionMethod encryptionMethod = BackupPackage.EncryptionMethod.NONE;
        private String password = "";                 // 加密密码
        private String backupName = "";               // 备份名称
        
        public EnhancedBackupOptions() {
            super();
        }
        
        public EnhancedBackupOptions(boolean includeSpecialFiles, boolean preserveMetadata) {
            super(includeSpecialFiles, preserveMetadata);
        }
        
        // Getters and Setters
        public boolean isPackageMode() { return packageMode; }
        public void setPackageMode(boolean packageMode) { this.packageMode = packageMode; }
        
        public boolean isCompress() { return compress; }
        public void setCompress(boolean compress) { this.compress = compress; }
        
        public boolean isEncrypt() { return encrypt; }
        public void setEncrypt(boolean encrypt) { this.encrypt = encrypt; }
        
        public BackupPackage.CompressionMethod getCompressionMethod() { return compressionMethod; }
        public void setCompressionMethod(BackupPackage.CompressionMethod compressionMethod) { this.compressionMethod = compressionMethod; }
        
        public BackupPackage.EncryptionMethod getEncryptionMethod() { return encryptionMethod; }
        public void setEncryptionMethod(BackupPackage.EncryptionMethod encryptionMethod) { this.encryptionMethod = encryptionMethod; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getBackupName() { return backupName; }
        public void setBackupName(String backupName) { this.backupName = backupName; }
    }
    
    // 增强的备份结果
    public static class EnhancedBackupResult extends BackupResult {
        private final String packagePath;      // 包文件路径（如果打包）
        private final boolean packaged;        // 是否已打包
        private final boolean compressed;      // 是否已压缩
        private final boolean encrypted;       // 是否已加密
        
        public EnhancedBackupResult(int filesCopied, int directoriesCreated, long totalSize, 
                                   String message, String packagePath, boolean packaged, 
                                   boolean compressed, boolean encrypted) {
            super(filesCopied, directoriesCreated, totalSize, message);
            this.packagePath = packagePath;
            this.packaged = packaged;
            this.compressed = compressed;
            this.encrypted = encrypted;
        }
        
        public EnhancedBackupResult(int filesCopied, int directoriesCreated, long totalSize, 
                                   String message, List<String> successfulPaths, 
                                   String packagePath, boolean packaged, 
                                   boolean compressed, boolean encrypted) {
            super(filesCopied, directoriesCreated, totalSize, message, successfulPaths);
            this.packagePath = packagePath;
            this.packaged = packaged;
            this.compressed = compressed;
            this.encrypted = encrypted;
        }
        
        public String getPackagePath() { return packagePath; }
        public boolean isPackaged() { return packaged; }
        public boolean isCompressed() { return compressed; }
        public boolean isEncrypted() { return encrypted; }
    }
    
    // 增强的还原结果
    public static class EnhancedRestoreResult extends RestoreResult {
        private final boolean fromPackage;     // 是否从包中还原
        private final String packagePath;      // 包文件路径
        
        public EnhancedRestoreResult(boolean success, String targetPath, String error, 
                                    int restoredFiles, boolean fromPackage, String packagePath) {
            super(success, targetPath, error, restoredFiles);
            this.fromPackage = fromPackage;
            this.packagePath = packagePath;
        }
        
        public boolean isFromPackage() { return fromPackage; }
        public String getPackagePath() { return packagePath; }
    }
    
    private static final DateTimeFormatter BACKUP_NAME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * 执行增强备份（支持打包）
     */
    public EnhancedBackupResult enhancedBackup(String sourcePathStr, String targetDir, 
                                              EnhancedBackupOptions options) throws IOException {
        Path sourcePath = Paths.get(sourcePathStr);
        Path targetPath = Paths.get(targetDir);
        
        if (!Files.exists(sourcePath)) {
            return new EnhancedBackupResult(0, 0, 0, "源路径不存在", null, false, false, false);
        }
        
        // 如果启用打包模式
        if (options.isPackageMode()) {
            return backupWithPackage(sourcePathStr, targetDir, options);
        } else {
            // 使用原有的目录模式备份
            BackupResult result = super.backup(sourcePathStr, targetDir, options);
            
            // 确保成功路径列表不为空
            List<String> successfulPaths = result.getSuccessfulPaths();
            if (successfulPaths.isEmpty() && 
                (result.getMessage().equals("备份完成") || 
                 result.getMessage().equals("文件备份完成") ||
                 result.getMessage().startsWith("备份完成"))) {
                successfulPaths = new ArrayList<>();
                successfulPaths.add(sourcePathStr);
            }
            
            return new EnhancedBackupResult(
                result.getFilesCopied(),
                result.getDirectoriesCreated(),
                result.getTotalSize(),
                result.getMessage(),
                successfulPaths,
                null, false, false, false
            );
        }
    }
    
    /**
     * 打包模式备份
     */
    private EnhancedBackupResult backupWithPackage(String sourcePathStr, String targetDir,
                                                  EnhancedBackupOptions options) throws IOException {
        Path sourcePath = Paths.get(sourcePathStr);
        Path targetPath = Paths.get(targetDir);
        
        // 生成备份名称
        String backupName = options.getBackupName();
        if (backupName == null || backupName.trim().isEmpty()) {
            backupName = sourcePath.getFileName().toString() + "_" + 
                        LocalDateTime.now().format(BACKUP_NAME_FORMATTER);
        }
        
        // 生成包文件路径
        String packageFileName = backupName + ".fbk";
        Path packagePath = targetPath.resolve(packageFileName);
        
        try {
            // 创建备份包（支持加密和压缩）
            boolean success;
            if (options.isCompress() || options.isEncrypt()) {
                // 使用加密/压缩版本
                success = BackupPackage.createPackage(
                    sourcePathStr, 
                    packagePath.toString(), 
                    options,
                    options.isCompress(),
                    options.isEncrypt(),
                    options.getCompressionMethod(),
                    options.getEncryptionMethod(),
                    options.getPassword()
                );
            } else {
                // 使用普通版本
                success = BackupPackage.createPackage(sourcePathStr, packagePath.toString(), options);
            }
            
            if (success) {
                // 保存历史记录
                saveBackupHistory(sourcePathStr, targetDir);
                
                // 创建成功路径列表
                List<String> successfulPaths = new ArrayList<>();
                successfulPaths.add(sourcePathStr);
                
                return new EnhancedBackupResult(
                    1,  // 包文件算作一个文件
                    0,  // 没有创建目录
                    Files.size(packagePath),
                    "打包备份完成: " + packageFileName,
                    successfulPaths,
                    packagePath.toString(),
                    true,
                    options.isCompress(),
                    options.isEncrypt()
                );
            } else {
                return new EnhancedBackupResult(0, 0, 0, "打包备份失败", new ArrayList<>(), null, false, false, false);
            }
        } catch (Exception e) {
            return new EnhancedBackupResult(0, 0, 0, "打包备份失败: " + e.getMessage(), null, false, false, false);
        }
    }
    
    /**
     * 执行增强还原（支持从包中还原）
     */
    public EnhancedRestoreResult enhancedRestore(String sourcePathStr, String targetDir,
                                                EnhancedBackupOptions options) throws IOException {
        Path sourcePath = Paths.get(sourcePathStr);
        
        if (!Files.exists(sourcePath)) {
            return new EnhancedRestoreResult(false, targetDir, "源路径不存在", 0, false, null);
        }
        
        // 检查是否为包文件
        if (sourcePathStr.toLowerCase().endsWith(".fbk")) {
            return restoreFromPackage(sourcePathStr, targetDir, options);
        } else {
            // 使用原有的目录模式还原
            BackupResult result = super.restore(sourcePathStr, targetDir, options);
            // 根据消息判断是否成功
            boolean success = result.getMessage().equals("还原完成") || 
                             result.getMessage().equals("文件还原完成") ||
                             result.getMessage().contains("还原完成");
            return new EnhancedRestoreResult(
                success,
                result.getMessage(), // 使用message作为targetPath
                success ? "" : result.getMessage(), // 如果不成功，使用message作为错误信息
                result.getFilesCopied(), // 使用filesCopied作为restoredFiles
                false,
                null
            );
        }
    }
    
    /**
     * 从包中还原
     */
    private EnhancedRestoreResult restoreFromPackage(String packagePathStr, String targetDir,
                                                    EnhancedBackupOptions options) throws IOException {
        Path packagePath = Paths.get(packagePathStr);
        Path targetPath = Paths.get(targetDir);
        
        try {
            // 提取包文件（支持解密）
            boolean success = false;
            String errorMessage = null;
            
            try {
                if (options.isEncrypt() && options.getPassword() != null && !options.getPassword().isEmpty()) {
                    // 使用支持密码的版本
                    success = BackupPackage.extractPackage(packagePathStr, targetDir, options.getPassword());
                } else {
                    // 使用普通版本
                    success = BackupPackage.extractPackage(packagePathStr, targetDir);
                }
                
                if (success) {
                    // 获取包信息以统计文件数量
                    BackupPackage.BackupManifest manifest = BackupPackage.getPackageInfo(packagePathStr);
                    int fileCount = manifest.getFiles().size();
                    
                    return new EnhancedRestoreResult(
                        true,
                        targetDir,
                        "从包中还原完成",
                        fileCount,
                        true,
                        packagePathStr
                    );
                } else {
                    errorMessage = "从包中还原失败";
                }
            } catch (IOException e) {
                errorMessage = e.getMessage();
            }
            
            return new EnhancedRestoreResult(false, targetDir, "从包中还原失败: " + errorMessage, 0, true, packagePathStr);
        } catch (Exception e) {
            return new EnhancedRestoreResult(false, targetDir, "从包中还原失败: " + e.getMessage(), 0, true, packagePathStr);
        }
    }
    
    /**
     * 验证备份包
     */
    public VerifyResult verifyPackage(String packagePathStr) throws IOException {
        try {
            BackupPackage.BackupManifest manifest = BackupPackage.getPackageInfo(packagePathStr);
            
            // 这里可以添加更详细的验证逻辑
            // 例如：检查文件哈希、数据完整性等
            
            return new VerifyResult(true, "包文件验证通过", 
                manifest.getFiles().size(), 0);
        } catch (Exception e) {
            return new VerifyResult(false, "包文件验证失败: " + e.getMessage(), 0, 0);
        }
    }
    
    /**
     * 获取包文件信息
     */
    public BackupPackage.BackupManifest getPackageInfo(String packagePathStr) throws IOException {
        return BackupPackage.getPackageInfo(packagePathStr);
    }
    
    /**
     * 批量增强备份
     */
    public EnhancedBackupResult enhancedBackupMultiple(List<String> sourcePaths, String targetDir,
                                                      EnhancedBackupOptions options) throws IOException {
        int totalFilesCopied = 0;
        int totalDirectoriesCreated = 0;
        long totalSize = 0;
        List<String> messages = new ArrayList<>();
        List<String> successfulPaths = new ArrayList<>();
        List<String> packagePaths = new ArrayList<>();
        boolean anyPackaged = false;
        boolean anyCompressed = false;
        boolean anyEncrypted = false;
        
        for (String sourcePath : sourcePaths) {
            EnhancedBackupResult result = enhancedBackup(sourcePath, targetDir, options);
            totalFilesCopied += result.getFilesCopied();
            totalDirectoriesCreated += result.getDirectoriesCreated();
            totalSize += result.getTotalSize();
            
            if (result.isPackaged() && result.getPackagePath() != null) {
                packagePaths.add(result.getPackagePath());
                anyPackaged = true;
            }
            if (result.isCompressed()) anyCompressed = true;
            if (result.isEncrypted()) anyEncrypted = true;
            
            if (!result.getSuccessfulPaths().isEmpty()) {
                // 如果成功路径列表不为空，说明备份成功
                successfulPaths.add(sourcePath);
            } else if (result.getMessage().startsWith("备份完成") || 
                result.getMessage().startsWith("打包备份完成") ||
                result.getMessage().startsWith("文件备份完成")) {
                // 兼容旧逻辑：根据消息前缀判断
                successfulPaths.add(sourcePath);
            } else {
                messages.add(sourcePath + ": " + result.getMessage());
            }
        }
        
        String message = messages.isEmpty() ? "备份完成" : String.join("; ", messages);
        
        // 如果有多个包文件，返回第一个包路径（或可以修改为返回列表）
        String packagePath = packagePaths.isEmpty() ? null : packagePaths.get(0);
        
        return new EnhancedBackupResult(
            totalFilesCopied,
            totalDirectoriesCreated,
            totalSize,
            message,
            successfulPaths,
            packagePath,
            anyPackaged,
            anyCompressed,
            anyEncrypted
        );
    }
    
    /**
     * 批量增强还原
     */
    public EnhancedRestoreResult enhancedRestoreMultiple(List<String> sourcePaths, String targetDir,
                                                        EnhancedBackupOptions options) throws IOException {
        int totalRestoredFiles = 0;
        List<String> messages = new ArrayList<>();
        boolean anyFromPackage = false;
        String packagePath = null;
        
        for (String sourcePath : sourcePaths) {
            EnhancedRestoreResult result = enhancedRestore(sourcePath, targetDir, options);
            totalRestoredFiles += result.getRestoredFiles();
            
            if (result.isFromPackage()) {
                anyFromPackage = true;
                packagePath = result.getPackagePath();
            }
            
            if (!result.isSuccess()) {
                messages.add(sourcePath + ": " + result.getError());
            }
        }
        
        String message = messages.isEmpty() ? "还原完成" : String.join("; ", messages);
        boolean success = messages.isEmpty();
        
        return new EnhancedRestoreResult(
            success,
            targetDir,
            message,
            totalRestoredFiles,
            anyFromPackage,
            packagePath
        );
    }
    
    // 验证结果类
    public static class VerifyResult {
        private final boolean success;
        private final String error;
        private final int checkedFiles;
        private final int failedFiles;
        
        public VerifyResult(boolean success, String error, int checkedFiles, int failedFiles) {
            this.success = success;
            this.error = error;
            this.checkedFiles = checkedFiles;
            this.failedFiles = failedFiles;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public int getCheckedFiles() { return checkedFiles; }
        public int getFailedFiles() { return failedFiles; }
    }
    
    // 还原结果类（基类）
    public static class RestoreResult {
        private final boolean success;
        private final String targetPath;
        private final String error;
        private final int restoredFiles;
        
        public RestoreResult(boolean success, String targetPath, String error, int restoredFiles) {
            this.success = success;
            this.targetPath = targetPath;
            this.error = error;
            this.restoredFiles = restoredFiles;
        }
        
        public boolean isSuccess() { return success; }
        public String getTargetPath() { return targetPath; }
        public String getError() { return error; }
        public int getRestoredFiles() { return restoredFiles; }
    }
}