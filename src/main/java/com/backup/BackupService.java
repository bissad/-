package com.backup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BackupService {
    
    // 文件类型枚举
    public enum FileKind {
        REGULAR,
        DIRECTORY,
        SYMLINK,
        FIFO,
        BLOCK,
        CHARACTER,
        SOCKET,
        UNKNOWN
    }
    
    // 备份选项类
    public static class BackupOptions {
        private boolean includeSpecialFiles = true;
        private boolean preserveMetadata = true;
        
        public BackupOptions() {}
        
        public BackupOptions(boolean includeSpecialFiles, boolean preserveMetadata) {
            this.includeSpecialFiles = includeSpecialFiles;
            this.preserveMetadata = preserveMetadata;
        }
        
        public boolean isIncludeSpecialFiles() { return includeSpecialFiles; }
        public void setIncludeSpecialFiles(boolean includeSpecialFiles) { this.includeSpecialFiles = includeSpecialFiles; }
        
        public boolean isPreserveMetadata() { return preserveMetadata; }
        public void setPreserveMetadata(boolean preserveMetadata) { this.preserveMetadata = preserveMetadata; }
    }
    
    private static final String HISTORY_FILE = "backup_history.txt";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static class BackupResult {
        private final int filesCopied;
        private final int directoriesCreated;
        private final long totalSize;
        private final String message;
        private final List<String> successfulPaths;
        
        public BackupResult(int filesCopied, int directoriesCreated, long totalSize, String message) {
            this.filesCopied = filesCopied;
            this.directoriesCreated = directoriesCreated;
            this.totalSize = totalSize;
            this.message = message;
            this.successfulPaths = new ArrayList<>();
        }
        
        public BackupResult(int filesCopied, int directoriesCreated, long totalSize, String message, List<String> successfulPaths) {
            this.filesCopied = filesCopied;
            this.directoriesCreated = directoriesCreated;
            this.totalSize = totalSize;
            this.message = message;
            this.successfulPaths = successfulPaths;
        }
        
        public int getFilesCopied() { return filesCopied; }
        public int getDirectoriesCreated() { return directoriesCreated; }
        public long getTotalSize() { return totalSize; }
        public String getMessage() { return message; }
        public List<String> getSuccessfulPaths() { return successfulPaths; }
    }
    
    // 特殊文件记录类
    public static class SpecialFileRecord {
        private final String path;
        private final FileKind kind;
        private final String symlinkTarget;
        private final long deviceNumber;
        private final BasicFileAttributes attrs;
        
        public SpecialFileRecord(String path, FileKind kind, String symlinkTarget, long deviceNumber, BasicFileAttributes attrs) {
            this.path = path;
            this.kind = kind;
            this.symlinkTarget = symlinkTarget;
            this.deviceNumber = deviceNumber;
            this.attrs = attrs;
        }
        
        public String getPath() { return path; }
        public FileKind getKind() { return kind; }
        public String getSymlinkTarget() { return symlinkTarget; }
        public long getDeviceNumber() { return deviceNumber; }
        public BasicFileAttributes getAttrs() { return attrs; }
    }
    
    // 检测文件类型
    private FileKind detectFileKind(Path path) throws IOException {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            if (attrs.isRegularFile()) {
                return FileKind.REGULAR;
            } else if (attrs.isDirectory()) {
                return FileKind.DIRECTORY;
            } else if (attrs.isSymbolicLink()) {
                return FileKind.SYMLINK;
            } else if (attrs.isOther()) {
                // 需要进一步检测具体类型
                try {
                    PosixFileAttributes posixAttrs = Files.readAttributes(path, PosixFileAttributes.class);
                    if (posixAttrs.isSymbolicLink()) {
                        return FileKind.SYMLINK;
                    }
                } catch (Exception e) {
                    // 非POSIX系统，尝试其他方法
                }
                
                // 检查是否为特殊文件
                try {
                    if (Files.isSymbolicLink(path)) {
                        return FileKind.SYMLINK;
                    }
                } catch (Exception e) {
                    // 忽略
                }
                
                // 在Unix系统上检查设备文件
                if (System.getProperty("os.name").toLowerCase().contains("nix") ||
                    System.getProperty("os.name").toLowerCase().contains("nux") ||
                    System.getProperty("os.name").toLowerCase().contains("mac")) {
                    try {
                        FileStore store = Files.getFileStore(path);
                        // 这里简化处理，实际需要更复杂的检测
                        return FileKind.UNKNOWN;
                    } catch (Exception e) {
                        // 忽略
                    }
                }
                
                return FileKind.UNKNOWN;
            }
        } catch (Exception e) {
            // 读取属性失败
        }
        
        return FileKind.UNKNOWN;
    }
    
    // 获取特殊文件信息
    private SpecialFileRecord getSpecialFileInfo(Path path, FileKind kind) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        String symlinkTarget = null;
        long deviceNumber = 0;
        
        if (kind == FileKind.SYMLINK) {
            try {
                symlinkTarget = Files.readSymbolicLink(path).toString();
            } catch (Exception e) {
                // 读取符号链接失败
            }
        } else if (kind == FileKind.BLOCK || kind == FileKind.CHARACTER) {
            // 在Unix系统上获取设备号
            if (System.getProperty("os.name").toLowerCase().contains("nix") ||
                System.getProperty("os.name").toLowerCase().contains("nux") ||
                System.getProperty("os.name").toLowerCase().contains("mac")) {
                try {
                    if (attrs instanceof PosixFileAttributes) {
                        PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
                        // 这里简化处理，实际需要获取设备号
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
        
        return new SpecialFileRecord(path.toString(), kind, symlinkTarget, deviceNumber, attrs);
    }
    
    public BackupResult backup(String sourcePathStr, String targetDir) throws IOException {
        return backup(sourcePathStr, targetDir, new BackupOptions());
    }
    
    public BackupResult backup(String sourcePathStr, String targetDir, BackupOptions options) throws IOException {
        Path sourcePath = Paths.get(sourcePathStr);
        Path targetPath = Paths.get(targetDir);
        
        if (!Files.exists(sourcePath)) {
            return new BackupResult(0, 0, 0, "源路径不存在");
        }
        
        if (Files.isDirectory(sourcePath)) {
            return backupDirectoryWithRoot(sourcePath, targetPath, options);
        } else {
            return backupFile(sourcePath, targetPath, options);
        }
    }
    
    public BackupResult backupMultiple(List<String> sourcePaths, String targetDir) throws IOException {
        return backupMultiple(sourcePaths, targetDir, new BackupOptions());
    }
    
    public BackupResult backupMultiple(List<String> sourcePaths, String targetDir, BackupOptions options) throws IOException {
        int totalFilesCopied = 0;
        int totalDirectoriesCreated = 0;
        long totalSize = 0;
        List<String> messages = new ArrayList<>();
        List<String> successfulPaths = new ArrayList<>();
        
        for (String sourcePath : sourcePaths) {
            BackupResult result = backup(sourcePath, targetDir, options);
            totalFilesCopied += result.getFilesCopied();
            totalDirectoriesCreated += result.getDirectoriesCreated();
            totalSize += result.getTotalSize();
            
            // 如果备份成功，保存历史记录
            if (result.getMessage().equals("备份完成") || result.getMessage().equals("文件备份完成")) {
                saveBackupHistory(sourcePath, targetDir);
                successfulPaths.add(sourcePath);
            } else {
                messages.add(sourcePath + ": " + result.getMessage());
            }
        }
        
        String message = messages.isEmpty() ? "备份完成" : String.join("; ", messages);
        return new BackupResult(totalFilesCopied, totalDirectoriesCreated, totalSize, message, successfulPaths);
    }
    
    private BackupResult backupDirectoryWithRoot(Path sourcePath, Path targetPath) throws IOException {
        return backupDirectoryWithRoot(sourcePath, targetPath, new BackupOptions());
    }
    
    private BackupResult backupDirectoryWithRoot(Path sourcePath, Path targetPath, BackupOptions options) throws IOException {
        final Path sourceParent = sourcePath.getParent() != null ? sourcePath.getParent() : Paths.get(".");
        
        AtomicLong filesCopied = new AtomicLong(0);
        AtomicLong directoriesCreated = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        AtomicLong specialFilesProcessed = new AtomicLong(0);
        
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourceParent.relativize(dir);
                Path targetDirectory = targetPath.resolve(relativePath);
                
                boolean created = false;
                if (!Files.exists(targetDirectory)) {
                    Files.createDirectories(targetDirectory);
                    directoriesCreated.incrementAndGet();
                    created = true;
                }
                
                // 如果启用了元数据保留，设置目录属性
                if (options.isPreserveMetadata()) {
                    try {
                        // 设置目录时间戳
                        Files.setLastModifiedTime(targetDirectory, attrs.lastModifiedTime());
                        
                        // 尝试设置创建时间（如果系统支持）
                        try {
                            Files.setAttribute(targetDirectory, "creationTime", attrs.creationTime());
                        } catch (Exception e) {
                            // 有些系统不支持设置创建时间
                        }
                        
                        // 尝试设置访问时间
                        try {
                            Files.setAttribute(targetDirectory, "lastAccessTime", attrs.lastAccessTime());
                        } catch (Exception e) {
                            // 有些系统不支持设置访问时间
                        }
                        
                        // 如果是新创建的目录，尝试复制POSIX权限
                        if (created) {
                            try {
                                PosixFileAttributes posixAttrs = Files.readAttributes(dir, PosixFileAttributes.class);
                                Files.setPosixFilePermissions(targetDirectory, posixAttrs.permissions());
                            } catch (Exception e) {
                                // 非POSIX系统或权限不足，忽略
                            }
                        }
                    } catch (Exception e) {
                        // 设置属性失败，记录日志但不中断备份
                        System.err.println("设置目录属性失败: " + dir + " - " + e.getMessage());
                    }
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourceParent.relativize(file);
                Path targetFile = targetPath.resolve(relativePath);
                
                // 检测文件类型
                FileKind kind = detectFileKind(file);
                
                // 检查是否为特殊文件
                boolean isSpecialFile = kind == FileKind.SYMLINK || kind == FileKind.FIFO || 
                                       kind == FileKind.BLOCK || kind == FileKind.CHARACTER || 
                                       kind == FileKind.SOCKET;
                
                // 如果不包含特殊文件且当前文件是特殊文件，则跳过
                if (!options.isIncludeSpecialFiles() && isSpecialFile) {
                    return FileVisitResult.CONTINUE;
                }
                
                // 处理特殊文件
                if (isSpecialFile) {
                    try {
                        SpecialFileRecord record = getSpecialFileInfo(file, kind);
                        // 创建特殊文件元数据记录文件
                        createSpecialFileRecord(targetFile, record);
                        specialFilesProcessed.incrementAndGet();
                    } catch (Exception e) {
                        // 特殊文件处理失败，记录日志
                        System.err.println("处理特殊文件失败: " + file + " - " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                // 处理普通文件（仅限REGULAR类型）
                if (kind == FileKind.REGULAR) {
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
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // 文件访问失败，记录日志并继续
                System.err.println("访问文件失败: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // 目录遍历完成后，确保目录的修改时间正确
                // 因为目录内容可能已更新，需要更新目录的修改时间
                if (options.isPreserveMetadata()) {
                    try {
                        Path relativePath = sourceParent.relativize(dir);
                        Path targetDirectory = targetPath.resolve(relativePath);
                        
                        // 获取原始目录的修改时间
                        BasicFileAttributes sourceAttrs = Files.readAttributes(dir, BasicFileAttributes.class);
                        
                        // 设置目标目录的修改时间为原始目录的修改时间
                        // 注意：这里使用原始目录的修改时间，而不是当前时间
                        Files.setLastModifiedTime(targetDirectory, sourceAttrs.lastModifiedTime());
                    } catch (Exception e) {
                        // 设置失败不影响主要功能
                        System.err.println("更新目录修改时间失败: " + dir + " - " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        String message = "备份完成";
        if (specialFilesProcessed.get() > 0) {
            message += " (处理了 " + specialFilesProcessed.get() + " 个特殊文件)";
        }
        
        return new BackupResult(
            filesCopied.intValue(),
            directoriesCreated.intValue(),
            totalSize.longValue(),
            message
        );
    }
    
    // 创建特殊文件记录文件
    private void createSpecialFileRecord(Path targetFile, SpecialFileRecord record) throws IOException {
        Path recordFile = Paths.get(targetFile.toString() + ".special");
        List<String> lines = new ArrayList<>();
        
        lines.add("SPECIAL_FILE_RECORD");
        lines.add("path=" + record.getPath());
        lines.add("kind=" + record.getKind().name());
        if (record.getSymlinkTarget() != null) {
            lines.add("symlink_target=" + record.getSymlinkTarget());
        }
        lines.add("device_number=" + record.getDeviceNumber());
        lines.add("creation_time=" + record.getAttrs().creationTime().toMillis());
        lines.add("last_modified_time=" + record.getAttrs().lastModifiedTime().toMillis());
        lines.add("last_access_time=" + record.getAttrs().lastAccessTime().toMillis());
        lines.add("size=" + record.getAttrs().size());
        
        Files.write(recordFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    private BackupResult backupFile(Path sourceFile, Path targetDir) throws IOException {
        return backupFile(sourceFile, targetDir, new BackupOptions());
    }
    
    private BackupResult backupFile(Path sourceFile, Path targetDir, BackupOptions options) throws IOException {
        // 检测文件类型
        FileKind kind = detectFileKind(sourceFile);
        
        // 检查是否为特殊文件
        boolean isSpecialFile = kind == FileKind.SYMLINK || kind == FileKind.FIFO || 
                               kind == FileKind.BLOCK || kind == FileKind.CHARACTER || 
                               kind == FileKind.SOCKET;
        
        // 如果不包含特殊文件且当前文件是特殊文件，则跳过
        if (!options.isIncludeSpecialFiles() && isSpecialFile) {
            return new BackupResult(0, 0, 0, "跳过特殊文件: " + sourceFile.getFileName());
        }
        
        // 处理特殊文件
        if (isSpecialFile) {
            try {
                SpecialFileRecord record = getSpecialFileInfo(sourceFile, kind);
                Path targetFile = targetDir.resolve(sourceFile.getFileName());
                createSpecialFileRecord(targetFile, record);
                return new BackupResult(0, 0, 0, "特殊文件记录已保存: " + sourceFile.getFileName());
            } catch (Exception e) {
                return new BackupResult(0, 0, 0, "处理特殊文件失败: " + e.getMessage());
            }
        }
        
        // 处理普通文件
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
        return restore(sourceDir, targetDir, new BackupOptions());
    }
    
    public BackupResult restore(String sourceDir, String targetDir, BackupOptions options) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);
        
        if (!Files.exists(sourcePath)) {
            return new BackupResult(0, 0, 0, "源路径不存在");
        }
        
        if (Files.isDirectory(sourcePath)) {
            return restoreDirectoryWithRoot(sourcePath, targetPath, options);
        } else {
            return restoreFile(sourcePath, targetPath, options);
        }
    }
    
    public BackupResult restoreMultiple(java.util.List<String> sourceDirs, String targetDir) throws IOException {
        return restoreMultiple(sourceDirs, targetDir, new BackupOptions());
    }
    
    public BackupResult restoreMultiple(java.util.List<String> sourceDirs, String targetDir, BackupOptions options) throws IOException {
        int totalFilesCopied = 0;
        int totalDirectoriesCreated = 0;
        long totalSize = 0;
        java.util.List<String> messages = new java.util.ArrayList<>();
        
        for (String sourceDir : sourceDirs) {
            BackupResult result = restore(sourceDir, targetDir, options);
            totalFilesCopied += result.getFilesCopied();
            totalDirectoriesCreated += result.getDirectoriesCreated();
            totalSize += result.getTotalSize();
            if (!result.getMessage().equals("还原完成")) {
                messages.add(sourceDir + ": " + result.getMessage());
            }
        }
        
        String message = messages.isEmpty() ? "还原完成" : String.join("; ", messages);
        return new BackupResult(totalFilesCopied, totalDirectoriesCreated, totalSize, message);
    }
    
    // 恢复目录
    private BackupResult restoreDirectoryWithRoot(Path sourcePath, Path targetPath, BackupOptions options) throws IOException {
        final Path sourceParent = sourcePath.getParent() != null ? sourcePath.getParent() : Paths.get(".");
        
        AtomicLong filesCopied = new AtomicLong(0);
        AtomicLong directoriesCreated = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        AtomicLong specialFilesRestored = new AtomicLong(0);
        
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourceParent.relativize(dir);
                Path targetDirectory = targetPath.resolve(relativePath);
                
                boolean created = false;
                if (!Files.exists(targetDirectory)) {
                    Files.createDirectories(targetDirectory);
                    directoriesCreated.incrementAndGet();
                    created = true;
                }
                
                // 如果启用了元数据保留，设置目录属性
                if (options.isPreserveMetadata()) {
                    try {
                        // 设置目录时间戳
                        Files.setLastModifiedTime(targetDirectory, attrs.lastModifiedTime());
                        
                        // 尝试设置创建时间（如果系统支持）
                        try {
                            Files.setAttribute(targetDirectory, "creationTime", attrs.creationTime());
                        } catch (Exception e) {
                            // 有些系统不支持设置创建时间
                        }
                        
                        // 尝试设置访问时间
                        try {
                            Files.setAttribute(targetDirectory, "lastAccessTime", attrs.lastAccessTime());
                        } catch (Exception e) {
                            // 有些系统不支持设置访问时间
                        }
                        
                        // 如果是新创建的目录，尝试复制POSIX权限
                        if (created) {
                            try {
                                PosixFileAttributes posixAttrs = Files.readAttributes(dir, PosixFileAttributes.class);
                                Files.setPosixFilePermissions(targetDirectory, posixAttrs.permissions());
                            } catch (Exception e) {
                                // 非POSIX系统或权限不足，忽略
                            }
                        }
                    } catch (Exception e) {
                        // 设置属性失败，记录日志但不中断还原
                        System.err.println("设置目录属性失败: " + dir + " - " + e.getMessage());
                    }
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relativePath = sourceParent.relativize(file);
                Path targetFile = targetPath.resolve(relativePath);
                
                // 检查是否为特殊文件记录
                if (file.toString().endsWith(".special")) {
                    try {
                        // 读取特殊文件记录并恢复
                        restoreSpecialFileFromRecord(file, targetFile, options);
                        specialFilesRestored.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("恢复特殊文件失败: " + file + " - " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                // 处理普通文件
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
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // 目录遍历完成后，确保目录的修改时间正确
                if (options.isPreserveMetadata()) {
                    try {
                        Path relativePath = sourceParent.relativize(dir);
                        Path targetDirectory = targetPath.resolve(relativePath);
                        
                        // 获取原始目录的修改时间
                        BasicFileAttributes sourceAttrs = Files.readAttributes(dir, BasicFileAttributes.class);
                        
                        // 设置目标目录的修改时间为原始目录的修改时间
                        Files.setLastModifiedTime(targetDirectory, sourceAttrs.lastModifiedTime());
                    } catch (Exception e) {
                        // 设置失败不影响主要功能
                        System.err.println("更新目录修改时间失败: " + dir + " - " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        String message = "还原完成";
        if (specialFilesRestored.get() > 0) {
            message += " (恢复了 " + specialFilesRestored.get() + " 个特殊文件记录)";
        }
        
        return new BackupResult(
            filesCopied.intValue(),
            directoriesCreated.intValue(),
            totalSize.longValue(),
            message
        );
    }
    
    // 恢复文件
    private BackupResult restoreFile(Path sourceFile, Path targetDir, BackupOptions options) throws IOException {
        // 检查是否为特殊文件记录
        if (sourceFile.toString().endsWith(".special")) {
            try {
                Path targetFile = targetDir.resolve(sourceFile.getFileName().toString().replace(".special", ""));
                restoreSpecialFileFromRecord(sourceFile, targetFile, options);
                return new BackupResult(0, 0, 0, "特殊文件记录已恢复");
            } catch (Exception e) {
                return new BackupResult(0, 0, 0, "恢复特殊文件失败: " + e.getMessage());
            }
        }
        
        // 处理普通文件
        if (!Files.isRegularFile(sourceFile)) {
            return new BackupResult(0, 0, 0, "源路径不是有效文件");
        }
        
        Path targetFile = targetDir.resolve(sourceFile.getFileName());
        BasicFileAttributes attrs = Files.readAttributes(sourceFile, BasicFileAttributes.class);
        
        if (Files.exists(targetFile)) {
            if (Files.getLastModifiedTime(sourceFile).compareTo(Files.getLastModifiedTime(targetFile)) > 0) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return new BackupResult(1, 0, attrs.size(), "文件还原完成");
            } else {
                return new BackupResult(0, 0, 0, "目标文件已是最新");
            }
        } else {
            Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
            return new BackupResult(1, 0, attrs.size(), "文件还原完成");
        }
    }
    
    // 从记录文件恢复特殊文件
    private void restoreSpecialFileFromRecord(Path recordFile, Path targetFile, BackupOptions options) throws IOException {
        List<String> lines = Files.readAllLines(recordFile);
        
        if (lines.isEmpty() || !lines.get(0).equals("SPECIAL_FILE_RECORD")) {
            throw new IOException("无效的特殊文件记录格式");
        }
        
        String path = null;
        FileKind kind = null;
        String symlinkTarget = null;
        long deviceNumber = 0;
        long creationTime = 0;
        long lastModifiedTime = 0;
        long lastAccessTime = 0;
        long size = 0;
        
        for (String line : lines) {
            if (line.startsWith("path=")) {
                path = line.substring(5);
            } else if (line.startsWith("kind=")) {
                kind = FileKind.valueOf(line.substring(5));
            } else if (line.startsWith("symlink_target=")) {
                symlinkTarget = line.substring(15);
            } else if (line.startsWith("device_number=")) {
                deviceNumber = Long.parseLong(line.substring(14));
            } else if (line.startsWith("creation_time=")) {
                creationTime = Long.parseLong(line.substring(14));
            } else if (line.startsWith("last_modified_time=")) {
                lastModifiedTime = Long.parseLong(line.substring(19));
            } else if (line.startsWith("last_access_time=")) {
                lastAccessTime = Long.parseLong(line.substring(17));
            } else if (line.startsWith("size=")) {
                size = Long.parseLong(line.substring(5));
            }
        }
        
        if (kind == null) {
            throw new IOException("记录文件中缺少文件类型信息");
        }
        
        // 根据文件类型恢复
        switch (kind) {
            case SYMLINK:
                if (symlinkTarget != null) {
                    Files.createSymbolicLink(targetFile, Paths.get(symlinkTarget));
                }
                break;
            case FIFO:
                // 在Unix系统上创建命名管道
                if (System.getProperty("os.name").toLowerCase().contains("nix") ||
                    System.getProperty("os.name").toLowerCase().contains("nux") ||
                    System.getProperty("os.name").toLowerCase().contains("mac")) {
                    try {
                        Runtime.getRuntime().exec(new String[]{"mkfifo", targetFile.toString()});
                    } catch (Exception e) {
                        throw new IOException("创建命名管道失败: " + e.getMessage());
                    }
                }
                break;
            case BLOCK:
            case CHARACTER:
                // 设备文件在普通用户环境下通常无法创建，记录日志
                System.out.println("设备文件无法在用户空间创建: " + targetFile);
                break;
            case SOCKET:
                // 套接字文件是运行时对象，无法静态恢复
                System.out.println("套接字文件无法静态恢复: " + targetFile);
                break;
            default:
                // 其他类型不处理
                break;
        }
        
        // 如果启用了元数据保留，尝试设置时间戳
        if (options.isPreserveMetadata()) {
            try {
                if (creationTime > 0) {
                    FileTime creationFileTime = FileTime.fromMillis(creationTime);
                    Files.setAttribute(targetFile, "creationTime", creationFileTime);
                }
                if (lastModifiedTime > 0) {
                    FileTime modifiedFileTime = FileTime.fromMillis(lastModifiedTime);
                    Files.setLastModifiedTime(targetFile, modifiedFileTime);
                }
            } catch (Exception e) {
                // 设置时间戳失败，记录日志但不影响主要功能
                System.err.println("设置文件时间戳失败: " + e.getMessage());
            }
        }
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
    
    // 还原项管理相关方法
    public static class RestoreItem {
        private final String path;
        private final String addedTime;
        private boolean deleted;
        
        public RestoreItem(String path) {
            this.path = path;
            this.addedTime = LocalDateTime.now().format(DATE_FORMATTER);
            this.deleted = false;
        }
        
        public RestoreItem(String path, String addedTime, boolean deleted) {
            this.path = path;
            this.addedTime = addedTime;
            this.deleted = deleted;
        }
        
        public String getPath() { return path; }
        public String getAddedTime() { return addedTime; }
        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
        
        @Override
        public String toString() {
            return path + "|" + addedTime + "|" + deleted;
        }
        
        public static RestoreItem fromString(String line) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3) {
                boolean deleted = Boolean.parseBoolean(parts[2]);
                return new RestoreItem(parts[0], parts[1], deleted);
            }
            return null;
        }
    }
    
    private static final String RESTORE_ITEMS_FILE = "restore_items.txt";
    
    public void addRestoreItem(String path) throws IOException {
        List<RestoreItem> items = loadRestoreItems();
        
        // 检查是否已存在相同的路径
        for (RestoreItem item : items) {
            if (item.getPath().equals(path)) {
                // 如果已存在但被标记为删除，则恢复它
                if (item.isDeleted()) {
                    item.setDeleted(false);
                    saveRestoreItems(items);
                }
                return;
            }
        }
        
        // 添加新项
        items.add(new RestoreItem(path));
        saveRestoreItems(items);
    }
    
    public void removeRestoreItem(String path) throws IOException {
        List<RestoreItem> items = loadRestoreItems();
        
        for (RestoreItem item : items) {
            if (item.getPath().equals(path)) {
                item.setDeleted(true);
                saveRestoreItems(items);
                return;
            }
        }
    }
    
    public List<RestoreItem> loadRestoreItems() throws IOException {
        List<RestoreItem> items = new ArrayList<>();
        Path itemsPath = Paths.get(RESTORE_ITEMS_FILE);
        
        if (Files.exists(itemsPath)) {
            List<String> lines = Files.readAllLines(itemsPath);
            for (String line : lines) {
                RestoreItem item = RestoreItem.fromString(line);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        
        return items;
    }
    
    private void saveRestoreItems(List<RestoreItem> items) throws IOException {
        List<String> lines = new ArrayList<>();
        for (RestoreItem item : items) {
            lines.add(item.toString());
        }
        
        Path itemsPath = Paths.get(RESTORE_ITEMS_FILE);
        Files.write(itemsPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    public List<String> getAvailableRestoreItems() throws IOException {
        List<RestoreItem> items = loadRestoreItems();
        List<String> availablePaths = new ArrayList<>();
        
        for (RestoreItem item : items) {
            if (!item.isDeleted()) {
                java.io.File file = new java.io.File(item.getPath());
                if (file.exists()) {
                    availablePaths.add(item.getPath());
                }
            }
        }
        
        return availablePaths;
    }
}
