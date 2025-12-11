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
    
    public BackupResult backupDirectory(String sourceDir, String targetDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);
        
        if (!Files.exists(sourcePath)) {
            return new BackupResult(0, 0, 0, "源目录不存在");
        }
        
        if (!Files.isDirectory(sourcePath)) {
            return new BackupResult(0, 0, 0, "源路径不是目录");
        }
        
        AtomicLong filesCopied = new AtomicLong(0);
        AtomicLong directoriesCreated = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourcePath.relativize(dir);
                Path targetDirectory = targetPath.resolve(relativePath);
                
                if (!Files.exists(targetDirectory)) {
                    Files.createDirectories(targetDirectory);
                    directoriesCreated.incrementAndGet();
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourcePath.relativize(file);
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
}
