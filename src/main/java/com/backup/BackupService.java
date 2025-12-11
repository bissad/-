package com.backup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

public class BackupService {
    
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
    
    public BackupResult backupMultiple(java.util.List<String> sourcePaths, String targetDir) throws IOException {
        int totalFilesCopied = 0;
        int totalDirectoriesCreated = 0;
        long totalSize = 0;
        java.util.List<String> messages = new java.util.ArrayList<>();
        
        for (String sourcePath : sourcePaths) {
            BackupResult result = backup(sourcePath, targetDir);
            totalFilesCopied += result.getFilesCopied();
            totalDirectoriesCreated += result.getDirectoriesCreated();
            totalSize += result.getTotalSize();
            if (!result.getMessage().equals("备份完成") && !result.getMessage().equals("文件备份完成")) {
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
}
