package com.backup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BackupService {
    
    private static final String HISTORY_FILE = "backup_history.txt";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static class BackupResult {
        private final int filesCopied;
        private final int directoriesCreated;
        private final long totalSize;
        private final String message;
        
        public BackupResult(int filesCopied, int directoriesCreated, long totalSize, String message) {
            this.filesCopied = filesCopied;
            this.directoriesCreated = directoriesCreated;
            this.totalSize = totalSize;
            this.message = message;
        }
        
        public int getFilesCopied() { return filesCopied; }
        public int getDirectoriesCreated() { return directoriesCreated; }
        public long getTotalSize() { return totalSize; }
        public String getMessage() { return message; }
    }
    
    public BackupResult backup(String sourcePathStr, String targetDir) throws IOException {
        Path sourcePath = Paths.get(sourcePathStr);
        Path targetPath = Paths.get(targetDir);
        
        if (!Files.exists(sourcePath)) {
            return new BackupResult(0, 0, 0, "源路径不存在");
        }
        
        if (Files.isDirectory(sourcePath)) {
            return backupDirectoryWithRoot(sourcePath, targetPath);
        } else {
            return backupFile(sourcePath, targetPath);
        }
    }
    
    public BackupResult backupMultiple(List<String> sourcePaths, String targetDir) throws IOException {
        int totalFilesCopied = 0;
        int totalDirectoriesCreated = 0;
        long totalSize = 0;
        List<String> messages = new ArrayList<>();
        
        for (String sourcePath : sourcePaths) {
            BackupResult result = backup(sourcePath, targetDir);
            totalFilesCopied += result.getFilesCopied();
            totalDirectoriesCreated += result.getDirectoriesCreated();
            totalSize += result.getTotalSize();
            
            // 如果备份成功，保存历史记录
            if (result.getMessage().equals("备份完成") || result.getMessage().equals("文件备份完成")) {
                saveBackupHistory(sourcePath, targetDir);
            } else {
                messages.add(sourcePath + ": " + result.getMessage());
            }
        }
        
        String message = messages.isEmpty() ? "备份完成" : String.join("; ", messages);
        return new BackupResult(totalFilesCopied, totalDirectoriesCreated, totalSize, message);
    }
    
    private BackupResult backupDirectoryWithRoot(Path sourcePath, Path targetPath) throws IOException {
        final Path sourceParent = sourcePath.getParent() != null ? sourcePath.getParent() : Paths.get(".");
        
        AtomicLong filesCopied = new AtomicLong(0);
        AtomicLong directoriesCreated = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourceParent.relativize(dir);
                Path targetDirectory = targetPath.resolve(relativePath);
                
                if (!Files.exists(targetDirectory)) {
                    Files.createDirectories(targetDirectory);
                    directoriesCreated.incrementAndGet();
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourceParent.relativize(file);
                Path targetFile = targetPath.resolve(relativePath);
                
                if (Files.exists(targetFile)) {
                    if (Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(targetFile)) > 0) {
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        filesCopied.incrementAndGet();
                        totalSize.addAndGet(attrs.size());
                    }
                } else {
                    Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                    filesCopied.incrementAndGet();
                    totalSize.addAndGet(attrs.size());
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        return new BackupResult(
            filesCopied.intValue(),
            directoriesCreated.intValue(),
            totalSize.longValue(),
            "备份完成"
        );
    }
    
    private BackupResult backupFile(Path sourceFile, Path targetDir) throws IOException {
        if (!Files.isRegularFile(sourceFile)) {
            return new BackupResult(0, 0, 0, "源路径不是有效文件");
        }
        
        Path targetFile = targetDir.resolve(sourceFile.getFileName());
        BasicFileAttributes attrs = Files.readAttributes(sourceFile, BasicFileAttributes.class);
        
        if (Files.exists(targetFile)) {
            if (Files.getLastModifiedTime(sourceFile).compareTo(Files.getLastModifiedTime(targetFile)) > 0) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return new BackupResult(1, 0, attrs.size(), "文件备份完成");
            } else {
                return new BackupResult(0, 0, 0, "目标文件已是最新");
            }
        } else {
            Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
            return new BackupResult(1, 0, attrs.size(), "文件备份完成");
        }
    }
    
    public BackupResult restore(String sourceDir, String targetDir) throws IOException {
        return backupDirectoryWithRoot(Paths.get(sourceDir), Paths.get(targetDir));
    }
    
    public BackupResult restoreMultiple(java.util.List<String> sourceDirs, String targetDir) throws IOException {
        int totalFilesCopied = 0;
        int totalDirectoriesCreated = 0;
        long totalSize = 0;
        java.util.List<String> messages = new java.util.ArrayList<>();
        
        for (String sourceDir : sourceDirs) {
            BackupResult result = restore(sourceDir, targetDir);
            totalFilesCopied += result.getFilesCopied();
            totalDirectoriesCreated += result.getDirectoriesCreated();
            totalSize += result.getTotalSize();
            if (!result.getMessage().equals("备份完成")) {
                messages.add(sourceDir + ": " + result.getMessage());
            }
        }
        
        String message = messages.isEmpty() ? "还原完成" : String.join("; ", messages);
        return new BackupResult(totalFilesCopied, totalDirectoriesCreated, totalSize, message);
    }
    
    // 备份历史记录相关方法
    public static class BackupHistoryRecord {
        private final String timestamp;
        private final String sourcePath;
        private final String targetDir;
        
        public BackupHistoryRecord(String sourcePath, String targetDir) {
            this.timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            this.sourcePath = sourcePath;
            this.targetDir = targetDir;
        }
        
        public String getTimestamp() { return timestamp; }
        public String getSourcePath() { return sourcePath; }
        public String getTargetDir() { return targetDir; }
        
        @Override
        public String toString() {
            return timestamp + "|" + sourcePath + "|" + targetDir;
        }
        
        public static BackupHistoryRecord fromString(String line) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3) {
                BackupHistoryRecord record = new BackupHistoryRecord(parts[1], parts[2]);
                // 我们不能修改timestamp字段，因为它final，但解析时可以忽略时间戳
                return record;
            }
            return null;
        }
    }
    
    public void saveBackupHistory(String sourcePath, String targetDir) throws IOException {
        BackupHistoryRecord record = new BackupHistoryRecord(sourcePath, targetDir);
        List<String> lines = new ArrayList<>();
        
        // 读取现有历史
        Path historyPath = Paths.get(HISTORY_FILE);
        if (Files.exists(historyPath)) {
            lines = Files.readAllLines(historyPath);
        }
        
        // 添加新记录
        lines.add(record.toString());
        
        // 保存历史（最多保留100条记录）
        if (lines.size() > 100) {
            lines = lines.subList(lines.size() - 100, lines.size());
        }
        
        Files.write(historyPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    public List<BackupHistoryRecord> loadBackupHistory() throws IOException {
        List<BackupHistoryRecord> history = new ArrayList<>();
        Path historyPath = Paths.get(HISTORY_FILE);
        
        if (Files.exists(historyPath)) {
            List<String> lines = Files.readAllLines(historyPath);
            for (String line : lines) {
                BackupHistoryRecord record = BackupHistoryRecord.fromString(line);
                if (record != null) {
                    history.add(record);
                }
            }
        }
        
        return history;
    }
    
    public List<String> getAvailableBackupSources() throws IOException {
        List<BackupHistoryRecord> history = loadBackupHistory();
        List<String> sources = new ArrayList<>();
        
        for (BackupHistoryRecord record : history) {
            String sourcePathStr = record.getSourcePath();
            String targetDirStr = record.getTargetDir();
            
            Path sourcePath = Paths.get(sourcePathStr);
            Path targetPath = Paths.get(targetDirStr);
            
            if (!Files.exists(targetPath)) {
                continue; // 备份目标目录不存在
            }
            
            // 尝试两种可能性：文件备份和目录备份
            // 因为源路径可能不存在，我们不知道原始是文件还是目录
            
            // 可能性1：文件备份
            Path possibleBackupFile = targetPath.resolve(sourcePath.getFileName());
            if (Files.exists(possibleBackupFile)) {
                sources.add(possibleBackupFile.toString());
                continue; // 找到文件备份，跳过目录检查
            }
            
            // 可能性2：目录备份
            // 尝试从源路径提取最后一级目录名
            String sourceFileName = sourcePath.getFileName().toString();
            Path possibleBackupDir = targetPath.resolve(sourceFileName);
            if (Files.exists(possibleBackupDir) && Files.isDirectory(possibleBackupDir)) {
                // 这是目录备份
                sources.add(possibleBackupDir.toString());
                continue;
            }
            
            // 可能性3：目录备份（保留完整相对路径的情况）
            // 对于像 /path/to/folder 这样的目录，备份可能在 targetPath/folder 下
            // 但 sourcePath.getFileName() 已经处理了这种情况
            
            // 可能性4：目录备份（使用sourceParent.relativize()的情况）
            // 这需要源路径存在才能计算相对路径，但我们不知道源路径是否存在
            // 跳过这种情况，因为如果源路径不存在，我们无法计算相对路径
        }
        
        // 去重
        return sources.stream().distinct().collect(java.util.stream.Collectors.toList());
    }
}
