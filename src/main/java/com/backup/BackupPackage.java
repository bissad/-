package com.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 备份包管理类 - 负责打包和解包操作
 * 实现类似C++项目的.fbk文件格式
 */
public class BackupPackage {
    
    // 包文件魔数和版本
    private static final byte[] PACKAGE_MAGIC = "FBS1".getBytes(StandardCharsets.UTF_8);
    private static final int PACKAGE_VERSION = 1;
    
    // 存储模式枚举
    public enum StorageMode {
        DIRECTORY,      // 目录模式
        PACKAGE         // 打包模式
    }
    
    // 压缩方法枚举
    public enum CompressionMethod {
        NONE,           // 不压缩
        HUFFMAN,        // 哈夫曼编码
        RLE,            // 游程编码
        ZLIB            // zlib压缩
    }
    
    // 加密方法枚举
    public enum EncryptionMethod {
        NONE,           // 不加密
        XOR,            // 简单异或加密
        RC4,            // RC4流加密
        AES256          // AES-256加密
    }
    
    // 文件记录类
    public static class FileRecord {
        private String relativePath;        // 相对路径
        private BackupService.FileKind kind; // 文件类型
        private long size;                  // 原始大小
        private long storedSize;           // 存储大小
        private String hash;               // 原始哈希
        private String storedHash;         // 存储后哈希
        private int permissions;           // 权限
        private long createdAt;            // 创建时间
        private long modifiedAt;           // 修改时间
        private long accessedAt;           // 访问时间
        private String symlinkTarget;      // 符号链接目标
        private long specialDevice;        // 设备号
        private boolean hasData;           // 是否有数据
        private boolean metadataOnly;      // 是否仅元数据
        private long dataOffset;           // 数据偏移量
        private String storedRelativePath; // 存储相对路径
        
        // 构造函数
        public FileRecord() {}
        
        public FileRecord(String relativePath, BackupService.FileKind kind, long size) {
            this.relativePath = relativePath;
            this.kind = kind;
            this.size = size;
            this.hasData = (kind == BackupService.FileKind.REGULAR);
        }
        
        // Getters and Setters
        public String getRelativePath() { return relativePath; }
        public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
        
        public BackupService.FileKind getKind() { return kind; }
        public void setKind(BackupService.FileKind kind) { this.kind = kind; }
        
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        
        public long getStoredSize() { return storedSize; }
        public void setStoredSize(long storedSize) { this.storedSize = storedSize; }
        
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
        
        public String getStoredHash() { return storedHash; }
        public void setStoredHash(String storedHash) { this.storedHash = storedHash; }
        
        public int getPermissions() { return permissions; }
        public void setPermissions(int permissions) { this.permissions = permissions; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        
        public long getModifiedAt() { return modifiedAt; }
        public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }
        
        public long getAccessedAt() { return accessedAt; }
        public void setAccessedAt(long accessedAt) { this.accessedAt = accessedAt; }
        
        public String getSymlinkTarget() { return symlinkTarget; }
        public void setSymlinkTarget(String symlinkTarget) { this.symlinkTarget = symlinkTarget; }
        
        public long getSpecialDevice() { return specialDevice; }
        public void setSpecialDevice(long specialDevice) { this.specialDevice = specialDevice; }
        
        public boolean isHasData() { return hasData; }
        public void setHasData(boolean hasData) { this.hasData = hasData; }
        
        public boolean isMetadataOnly() { return metadataOnly; }
        public void setMetadataOnly(boolean metadataOnly) { this.metadataOnly = metadataOnly; }
        
        public long getDataOffset() { return dataOffset; }
        public void setDataOffset(long dataOffset) { this.dataOffset = dataOffset; }
        
        public String getStoredRelativePath() { return storedRelativePath; }
        public void setStoredRelativePath(String storedRelativePath) { this.storedRelativePath = storedRelativePath; }
        
        // 加密和压缩相关字段
        private boolean compressed = false;
        private CompressionMethod compressionMethod = CompressionMethod.NONE;
        private boolean encrypted = false;
        private EncryptionMethod encryptionMethod = EncryptionMethod.NONE;
        
        public boolean isCompressed() { return compressed; }
        public void setCompressed(boolean compressed) { this.compressed = compressed; }
        
        public CompressionMethod getCompressionMethod() { return compressionMethod; }
        public void setCompressionMethod(CompressionMethod compressionMethod) { this.compressionMethod = compressionMethod; }
        
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        
        public EncryptionMethod getEncryptionMethod() { return encryptionMethod; }
        public void setEncryptionMethod(EncryptionMethod encryptionMethod) { this.encryptionMethod = encryptionMethod; }
    }
    
    // Manifest类
    public static class BackupManifest {
        private String manifestVersion = "1.0";
        private String backupId;
        private String rootName;
        private String createdAt;
        private String sourcePath;
        private StorageMode storageMode = StorageMode.DIRECTORY;
        private boolean compressed = false;
        private boolean encrypted = false;
        private CompressionMethod compressionMethod = CompressionMethod.NONE;
        private EncryptionMethod encryptionMethod = EncryptionMethod.NONE;
        private boolean preserveMetadata = true;
        private boolean includeSpecialFiles = true;
        private boolean verificationEnabled = true;
        private long totalFiles = 0;
        private long totalBytes = 0;
        private String dataRelativePath = "data";
        private List<FileRecord> files = new ArrayList<>();
        
        // 运行时信息（不序列化）
        private transient String manifestPath;
        private transient String basePath;
        private transient String dataDirectory;
        private transient String packagePath;
        
        // 构造函数
        public BackupManifest() {
            this.backupId = UUID.randomUUID().toString();
            this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        
        // Getters and Setters
        public String getManifestVersion() { return manifestVersion; }
        public void setManifestVersion(String manifestVersion) { this.manifestVersion = manifestVersion; }
        
        public String getBackupId() { return backupId; }
        public void setBackupId(String backupId) { this.backupId = backupId; }
        
        public String getRootName() { return rootName; }
        public void setRootName(String rootName) { this.rootName = rootName; }
        
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        
        public String getSourcePath() { return sourcePath; }
        public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
        
        public StorageMode getStorageMode() { return storageMode; }
        public void setStorageMode(StorageMode storageMode) { this.storageMode = storageMode; }
        
        public boolean isCompressed() { return compressed; }
        public void setCompressed(boolean compressed) { this.compressed = compressed; }
        
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        
        public CompressionMethod getCompressionMethod() { return compressionMethod; }
        public void setCompressionMethod(CompressionMethod compressionMethod) { this.compressionMethod = compressionMethod; }
        
        public EncryptionMethod getEncryptionMethod() { return encryptionMethod; }
        public void setEncryptionMethod(EncryptionMethod encryptionMethod) { this.encryptionMethod = encryptionMethod; }
        
        public boolean isPreserveMetadata() { return preserveMetadata; }
        public void setPreserveMetadata(boolean preserveMetadata) { this.preserveMetadata = preserveMetadata; }
        
        public boolean isIncludeSpecialFiles() { return includeSpecialFiles; }
        public void setIncludeSpecialFiles(boolean includeSpecialFiles) { this.includeSpecialFiles = includeSpecialFiles; }
        
        public boolean isVerificationEnabled() { return verificationEnabled; }
        public void setVerificationEnabled(boolean verificationEnabled) { this.verificationEnabled = verificationEnabled; }
        
        public long getTotalFiles() { return totalFiles; }
        public void setTotalFiles(long totalFiles) { this.totalFiles = totalFiles; }
        
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public String getDataRelativePath() { return dataRelativePath; }
        public void setDataRelativePath(String dataRelativePath) { this.dataRelativePath = dataRelativePath; }
        
        public List<FileRecord> getFiles() { return files; }
        public void setFiles(List<FileRecord> files) { this.files = files; }
        
        public String getManifestPath() { return manifestPath; }
        public void setManifestPath(String manifestPath) { this.manifestPath = manifestPath; }
        
        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        
        public String getDataDirectory() { return dataDirectory; }
        public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
        
        public String getPackagePath() { return packagePath; }
        public void setPackagePath(String packagePath) { this.packagePath = packagePath; }
        
        // 添加文件记录
        public void addFileRecord(FileRecord record) {
            files.add(record);
            totalFiles++;
            if (record.isHasData()) {
                totalBytes += record.getSize();
            }
        }
        
        // 转换为JSON
        public String toJson() {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
            return gson.toJson(this);
        }
        
        // 从JSON解析
        public static BackupManifest fromJson(String json) {
            Gson gson = new Gson();
            return gson.fromJson(json, BackupManifest.class);
        }
    }
    
    // 包文件头结构
    private static class PackageHeader {
        long manifestOffset;  // Manifest偏移量
        long manifestLength;  // Manifest长度
        
        PackageHeader(long offset, long length) {
            this.manifestOffset = offset;
            this.manifestLength = length;
        }
        
        // 写入到输出流
        void writeTo(DataOutputStream out) throws IOException {
            out.writeLong(manifestOffset);
            out.writeLong(manifestLength);
        }
        
        // 从输入流读取
        static PackageHeader readFrom(DataInputStream in) throws IOException {
            long offset = in.readLong();
            long length = in.readLong();
            return new PackageHeader(offset, length);
        }
    }
    
    /**
     * 创建备份包
     * @param sourcePath 源路径
     * @param outputPath 输出包文件路径
     * @param options 备份选项
     * @return 是否成功
     */
    public static boolean createPackage(String sourcePath, String outputPath, 
                                       BackupService.BackupOptions options) throws IOException {
        Path source = Paths.get(sourcePath);
        Path output = Paths.get(outputPath);
        
        if (!Files.exists(source)) {
            throw new IOException("源路径不存在: " + sourcePath);
        }
        
        // 创建Manifest
        BackupManifest manifest = new BackupManifest();
        manifest.setSourcePath(sourcePath);
        manifest.setStorageMode(StorageMode.PACKAGE);
        manifest.setRootName(source.getFileName().toString());
        
        try (RandomAccessFile raf = new RandomAccessFile(output.toFile(), "rw")) {
            // 写入魔数和版本
            raf.write(PACKAGE_MAGIC);
            raf.writeInt(PACKAGE_VERSION);
            
            // 预留Header位置（后面再写）
            long headerPosition = raf.getFilePointer();
            raf.writeLong(0); // manifestOffset
            raf.writeLong(0); // manifestLength
            
            // 收集文件并写入数据
            List<FileRecord> records = collectFiles(source, manifest, raf, options);
            manifest.setFiles(records);
            
            // 写入Manifest
            long manifestOffset = raf.getFilePointer();
            String manifestJson = manifest.toJson();
            byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
            raf.write(manifestBytes);
            long manifestLength = manifestBytes.length;
            
            // 回写Header
            raf.seek(headerPosition);
            raf.writeLong(manifestOffset);
            raf.writeLong(manifestLength);
            
            return true;
        }
    }
    
    /**
     * 创建带加密和压缩的包
     * @param sourcePath 源路径
     * @param outputPath 输出包文件路径
     * @param options 备份选项
     * @param compress 是否压缩
     * @param encrypt 是否加密
     * @param compressionMethod 压缩方法
     * @param encryptionMethod 加密方法
     * @param password 加密密码
     * @return 是否成功
     */
    public static boolean createPackage(String sourcePath, String outputPath, 
                                       BackupService.BackupOptions options,
                                       boolean compress, boolean encrypt,
                                       CompressionMethod compressionMethod,
                                       EncryptionMethod encryptionMethod,
                                       String password) throws IOException {
        Path source = Paths.get(sourcePath);
        Path output = Paths.get(outputPath);
        
        if (!Files.exists(source)) {
            throw new IOException("源路径不存在: " + sourcePath);
        }
        
        // 创建Manifest
        BackupManifest manifest = new BackupManifest();
        manifest.setSourcePath(sourcePath);
        manifest.setStorageMode(StorageMode.PACKAGE);
        manifest.setRootName(source.getFileName().toString());
        manifest.setCompressed(compress);
        manifest.setEncrypted(encrypt);
        manifest.setCompressionMethod(compressionMethod);
        manifest.setEncryptionMethod(encryptionMethod);
        
        try (RandomAccessFile raf = new RandomAccessFile(output.toFile(), "rw")) {
            // 写入魔数和版本
            raf.write(PACKAGE_MAGIC);
            raf.writeInt(PACKAGE_VERSION);
            
            // 预留Header位置（后面再写）
            long headerPosition = raf.getFilePointer();
            raf.writeLong(0); // manifestOffset
            raf.writeLong(0); // manifestLength
            
            // 收集文件并写入数据（带加密和压缩）
            List<FileRecord> records = collectFilesWithEncryption(source, manifest, raf, options, 
                                                                compress, encrypt, compressionMethod, 
                                                                encryptionMethod, password);
            manifest.setFiles(records);
            
            // 写入Manifest
            long manifestOffset = raf.getFilePointer();
            String manifestJson = manifest.toJson();
            byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
            raf.write(manifestBytes);
            long manifestLength = manifestBytes.length;
            
            // 回写Header
            raf.seek(headerPosition);
            raf.writeLong(manifestOffset);
            raf.writeLong(manifestLength);
            
            return true;
        }
    }
    
    /**
     * 从包中提取文件
     * @param packagePath 包文件路径
     * @param outputDir 输出目录
     * @return 是否成功
     */
    public static boolean extractPackage(String packagePath, String outputDir) throws IOException {
        return extractPackage(packagePath, outputDir, null, true); // 默认保留元数据
    }
    
    /**
     * 从包中提取文件（支持控制元数据保留）
     * @param packagePath 包文件路径
     * @param outputDir 输出目录
     * @param preserveMetadata 是否保留元数据
     * @return 是否成功
     */
    public static boolean extractPackage(String packagePath, String outputDir, boolean preserveMetadata) throws IOException {
        return extractPackage(packagePath, outputDir, null, preserveMetadata);
    }
    
    /**
     * 从包中提取文件（支持加密和解压缩）
     * @param packagePath 包文件路径
     * @param outputDir 输出目录
     * @param password 解密密码（如果需要）
     * @return 是否成功
     */
    public static boolean extractPackage(String packagePath, String outputDir, String password) throws IOException {
        return extractPackage(packagePath, outputDir, password, true); // 默认保留元数据
    }
    
    /**
     * 从包中提取文件（支持加密和解压缩，以及控制元数据保留）
     * @param packagePath 包文件路径
     * @param outputDir 输出目录
     * @param password 解密密码（如果需要）
     * @param preserveMetadata 是否保留元数据
     * @return 是否成功
     */
    public static boolean extractPackage(String packagePath, String outputDir, String password, boolean preserveMetadata) throws IOException {
        Path packageFile = Paths.get(packagePath);
        Path output = Paths.get(outputDir);
        
        if (!Files.exists(packageFile)) {
            throw new IOException("包文件不存在: " + packagePath);
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(packageFile.toFile(), "r")) {
            // 读取魔数和版本
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!new String(magic, StandardCharsets.UTF_8).equals("FBS1")) {
                throw new IOException("无效的包文件格式");
            }
            
            int version = raf.readInt();
            if (version != PACKAGE_VERSION) {
                throw new IOException("不支持的包版本: " + version);
            }
            
            // 读取Header
            long manifestOffset = raf.readLong();
            long manifestLength = raf.readLong();
            
            // 读取Manifest
            raf.seek(manifestOffset);
            byte[] manifestBytes = new byte[(int) manifestLength];
            raf.readFully(manifestBytes);
            String manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);
            
            BackupManifest manifest = BackupManifest.fromJson(manifestJson);
            
            // 提取文件（带解密和解压缩）
            if (preserveMetadata) {
                return extractFilesWithDecryption(raf, manifest, output, password);
            } else {
                return extractFilesWithDecryption(raf, manifest, output, password, false); // 不保留元数据
            }
        } catch (IOException e) {
            // 重新抛出IOException，这样调用者可以知道具体错误
            throw e;
        } catch (Exception e) {
            // 将其他异常包装为IOException
            throw new IOException("提取包文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 收集文件信息并写入数据
     */
    private static List<FileRecord> collectFiles(Path source, BackupManifest manifest, 
                                                RandomAccessFile raf, BackupService.BackupOptions options) throws IOException {
        List<FileRecord> records = new ArrayList<>();
        Path sourceParent = source.getParent() != null ? source.getParent() : Paths.get(".");
        
        Files.walk(source)
            .forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        // 目录记录
                        FileRecord record = new FileRecord();
                        record.setRelativePath(sourceParent.relativize(path).toString());
                        record.setKind(BackupService.FileKind.DIRECTORY);
                        record.setHasData(false);
                        record.setMetadataOnly(true);
                        
                        // 设置目录属性
                        if (options.isPreserveMetadata()) {
                            try {
                                java.nio.file.attribute.FileTime creationTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "creationTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setCreatedAt(creationTime.toMillis());
                                record.setModifiedAt(Files.getLastModifiedTime(path).toMillis());
                                java.nio.file.attribute.FileTime accessTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "lastAccessTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setAccessedAt(accessTime.toMillis());
                            } catch (Exception e) {
                                // 忽略属性获取失败
                            }
                        }
                        
                        records.add(record);
                        manifest.addFileRecord(record);
                    } else if (Files.isRegularFile(path)) {
                        // 普通文件
                        FileRecord record = new FileRecord();
                        record.setRelativePath(sourceParent.relativize(path).toString());
                        record.setKind(BackupService.FileKind.REGULAR);
                        record.setSize(Files.size(path));
                        record.setHasData(true);
                        
                        // 记录数据偏移量
                        record.setDataOffset(raf.getFilePointer());
                        
                        // 读取并写入文件数据
                        byte[] fileData = Files.readAllBytes(path);
                        raf.write(fileData);
                        record.setStoredSize(fileData.length);
                        
                        // 计算哈希
                        record.setHash(calculateHash(fileData));
                        
                        // 设置文件属性
                        if (options.isPreserveMetadata()) {
                            try {
                                java.nio.file.attribute.FileTime creationTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "creationTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setCreatedAt(creationTime.toMillis());
                                record.setModifiedAt(Files.getLastModifiedTime(path).toMillis());
                                java.nio.file.attribute.FileTime accessTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "lastAccessTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setAccessedAt(accessTime.toMillis());
                            } catch (Exception e) {
                                // 忽略属性获取失败
                            }
                        }
                        
                        records.add(record);
                        manifest.addFileRecord(record);
                    }
                } catch (Exception e) {
                    System.err.println("处理文件失败: " + path + " - " + e.getMessage());
                }
            });
        
        return records;
    }
    
    /**
     * 收集文件并写入数据（支持加密和压缩）
     */
    private static List<FileRecord> collectFilesWithEncryption(Path source, BackupManifest manifest, 
                                                              RandomAccessFile raf, BackupService.BackupOptions options,
                                                              boolean compress, boolean encrypt,
                                                              CompressionMethod compressionMethod,
                                                              EncryptionMethod encryptionMethod,
                                                              String password) throws IOException {
        List<FileRecord> records = new ArrayList<>();
        Path sourceParent = source.getParent() != null ? source.getParent() : Paths.get(".");
        
        Files.walk(source)
            .forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        // 目录记录
                        FileRecord record = new FileRecord();
                        record.setRelativePath(sourceParent.relativize(path).toString());
                        record.setKind(BackupService.FileKind.DIRECTORY);
                        record.setHasData(false);
                        record.setMetadataOnly(true);
                        
                        // 设置目录属性
                        if (options.isPreserveMetadata()) {
                            try {
                                java.nio.file.attribute.FileTime creationTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "creationTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setCreatedAt(creationTime.toMillis());
                                record.setModifiedAt(Files.getLastModifiedTime(path).toMillis());
                                java.nio.file.attribute.FileTime accessTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "lastAccessTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setAccessedAt(accessTime.toMillis());
                            } catch (Exception e) {
                                // 忽略属性获取失败
                            }
                        }
                        
                        records.add(record);
                        manifest.addFileRecord(record);
                    } else if (Files.isRegularFile(path)) {
                        // 普通文件
                        FileRecord record = new FileRecord();
                        record.setRelativePath(sourceParent.relativize(path).toString());
                        record.setKind(BackupService.FileKind.REGULAR);
                        record.setSize(Files.size(path));
                        record.setHasData(true);
                        
                        // 记录数据偏移量
                        record.setDataOffset(raf.getFilePointer());
                        
                        // 读取原始文件数据
                        byte[] fileData = Files.readAllBytes(path);
                        byte[] processedData = fileData;
                        
                        // 应用压缩（如果需要）
                        if (compress && compressionMethod != CompressionMethod.NONE) {
                            byte[] compressedData = compressData(processedData, compressionMethod);
                            // 只有在压缩后数据显著减小（至少5%）时才使用压缩
                            if (compressedData.length < processedData.length * 0.95) {
                                processedData = compressedData;
                                record.setCompressed(true);
                                record.setCompressionMethod(compressionMethod);
                                record.setStoredSize(processedData.length); // 更新存储大小
                            } else {
                                // 压缩无效，保持原始数据
                                record.setCompressed(false);
                                record.setCompressionMethod(CompressionMethod.NONE);
                            }
                        }
                        
                        // 应用加密（如果需要）
                        if (encrypt && encryptionMethod != EncryptionMethod.NONE && password != null && !password.isEmpty()) {
                            processedData = encryptData(processedData, password, encryptionMethod);
                            record.setEncrypted(true);
                            record.setEncryptionMethod(encryptionMethod);
                        }
                        
                        // 写入处理后的数据
                        raf.write(processedData);
                        record.setStoredSize(processedData.length);
                        
                        // 计算原始数据的哈希（不是处理后的数据）
                        record.setHash(calculateHash(fileData));
                        
                        // 设置文件属性
                        if (options.isPreserveMetadata()) {
                            try {
                                java.nio.file.attribute.FileTime creationTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "creationTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setCreatedAt(creationTime.toMillis());
                                record.setModifiedAt(Files.getLastModifiedTime(path).toMillis());
                                java.nio.file.attribute.FileTime accessTime = (java.nio.file.attribute.FileTime) 
                                    Files.getAttribute(path, "lastAccessTime", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                record.setAccessedAt(accessTime.toMillis());
                            } catch (Exception e) {
                                // 忽略属性获取失败
                            }
                        }
                        
                        records.add(record);
                        manifest.addFileRecord(record);
                    }
                } catch (Exception e) {
                    System.err.println("处理文件失败: " + path + " - " + e.getMessage());
                }
            });
        
        return records;
    }
    
    /**
     * 从包中提取文件
     */
    private static boolean extractFilesFromPackage(RandomAccessFile raf, BackupManifest manifest, 
                                                  Path outputDir) throws IOException {
        return extractFilesFromPackage(raf, manifest, outputDir, true); // 默认保留元数据
    }
    
    /**
     * 从包中提取文件，支持控制元数据保留
     */
    private static boolean extractFilesFromPackage(RandomAccessFile raf, BackupManifest manifest, 
                                                  Path outputDir, boolean preserveMetadata) throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        for (FileRecord record : manifest.getFiles()) {
            Path targetPath = outputDir.resolve(record.getRelativePath());
            
            if (record.getKind() == BackupService.FileKind.DIRECTORY) {
                // 创建目录（先创建父目录）
                Path parentPath = targetPath.getParent();
                if (parentPath != null && !Files.exists(parentPath)) {
                    Files.createDirectories(parentPath);
                }
                
                // 如果目录不存在则创建
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                }
                
                // 如果保留元数据，设置目录属性
                if (preserveMetadata) {
                    try {
                        // 设置修改时间
                        if (record.getModifiedAt() > 0) {
                            FileTime modifiedTime = FileTime.fromMillis(record.getModifiedAt());
                            Files.setLastModifiedTime(targetPath, modifiedTime);
                        }
                        
                        // 设置访问时间（如果系统支持）
                        if (record.getAccessedAt() > 0) {
                            FileTime accessedTime = FileTime.fromMillis(record.getAccessedAt());
                            try {
                                Files.setAttribute(targetPath, "lastAccessTime", accessedTime);
                            } catch (Exception e) {
                                // 某些系统不支持设置访问时间
                            }
                        }
                        
                        // 设置权限（如果系统支持）
                        if (record.getPermissions() > 0) {
                            try {
                                PosixFileAttributes posixAttrs = Files.readAttributes(targetPath, PosixFileAttributes.class);
                                Set<PosixFilePermission> permissions = new java.util.HashSet<>();
                                permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
                                if ((record.getPermissions() & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
                                if ((record.getPermissions() & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
                                if ((record.getPermissions() & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
                                if ((record.getPermissions() & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
                                if ((record.getPermissions() & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
                                if ((record.getPermissions() & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
                                if ((record.getPermissions() & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                                
                                Files.setPosixFilePermissions(targetPath, permissions);
                            } catch (Exception e) {
                                // 非POSIX系统，忽略权限设置
                            }
                        }
                    } catch (Exception e) {
                        // 设置元数据失败，不影响主要功能
                        System.err.println("设置目录元数据失败: " + targetPath + " - " + e.getMessage());
                    }
                }
            } else if (record.getKind() == BackupService.FileKind.REGULAR && record.isHasData()) {
                // 创建文件并写入数据
                Files.createDirectories(targetPath.getParent());
                
                // 定位并读取数据
                raf.seek(record.getDataOffset());
                byte[] fileData = new byte[(int) record.getStoredSize()];
                raf.readFully(fileData);
                
                // 写入文件
                Files.write(targetPath, fileData);
                
                // 验证哈希
                String calculatedHash = calculateHash(fileData);
                if (!calculatedHash.equals(record.getHash())) {
                    System.err.println("文件哈希验证失败: " + record.getRelativePath());
                }
                
                // 设置文件元数据（如果保留元数据）
                if (preserveMetadata) {
                    try {
                        // 设置修改时间
                        if (record.getModifiedAt() > 0) {
                            FileTime modifiedTime = FileTime.fromMillis(record.getModifiedAt());
                            Files.setLastModifiedTime(targetPath, modifiedTime);
                        }
                        
                        // 设置创建时间（如果系统支持）
                        if (record.getCreatedAt() > 0) {
                            FileTime createdTime = FileTime.fromMillis(record.getCreatedAt());
                            try {
                                Files.setAttribute(targetPath, "creationTime", createdTime);
                            } catch (Exception e) {
                                // 某些系统不支持设置创建时间
                            }
                        }
                        
                        // 设置访问时间（如果系统支持）
                        if (record.getAccessedAt() > 0) {
                            FileTime accessedTime = FileTime.fromMillis(record.getAccessedAt());
                            try {
                                Files.setAttribute(targetPath, "lastAccessTime", accessedTime);
                            } catch (Exception e) {
                                // 某些系统不支持设置访问时间
                            }
                        }
                        
                        // 设置权限（如果系统支持）
                        if (record.getPermissions() > 0) {
                            try {
                                PosixFileAttributes posixAttrs = Files.readAttributes(targetPath, PosixFileAttributes.class);
                                Set<PosixFilePermission> permissions = new java.util.HashSet<>();
                                permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
                                if ((record.getPermissions() & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
                                if ((record.getPermissions() & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
                                if ((record.getPermissions() & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
                                if ((record.getPermissions() & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
                                if ((record.getPermissions() & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
                                if ((record.getPermissions() & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
                                if ((record.getPermissions() & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                                
                                Files.setPosixFilePermissions(targetPath, permissions);
                            } catch (Exception e) {
                                // 非POSIX系统，忽略权限设置
                            }
                        }
                    } catch (Exception e) {
                        // 设置元数据失败，不影响主要功能
                        System.err.println("设置文件元数据失败: " + targetPath + " - " + e.getMessage());
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * 提取文件（支持解密和解压缩）
     */
    private static boolean extractFilesWithDecryption(RandomAccessFile raf, BackupManifest manifest, 
                                                     Path outputDir, String password) throws IOException {
        return extractFilesWithDecryption(raf, manifest, outputDir, password, true); // 默认保留元数据
    }
    
    /**
     * 提取文件（支持解密和解压缩，以及控制元数据保留）
     */
    private static boolean extractFilesWithDecryption(RandomAccessFile raf, BackupManifest manifest, 
                                                     Path outputDir, String password, boolean preserveMetadata) throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        for (FileRecord record : manifest.getFiles()) {
            Path targetPath = outputDir.resolve(record.getRelativePath());
            
            if (record.getKind() == BackupService.FileKind.DIRECTORY) {
                // 创建目录（先创建父目录）
                Path parentPath = targetPath.getParent();
                if (parentPath != null && !Files.exists(parentPath)) {
                    Files.createDirectories(parentPath);
                }
                
                // 如果目录不存在则创建
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                }
                
                // 如果保留元数据，设置目录属性
                if (preserveMetadata) {
                    try {
                        // 设置修改时间
                        if (record.getModifiedAt() > 0) {
                            FileTime modifiedTime = FileTime.fromMillis(record.getModifiedAt());
                            Files.setLastModifiedTime(targetPath, modifiedTime);
                        }
                        
                        // 设置访问时间（如果系统支持）
                        if (record.getAccessedAt() > 0) {
                            FileTime accessedTime = FileTime.fromMillis(record.getAccessedAt());
                            try {
                                Files.setAttribute(targetPath, "lastAccessTime", accessedTime);
                            } catch (Exception e) {
                                // 某些系统不支持设置访问时间
                            }
                        }
                        
                        // 设置权限（如果系统支持）
                        if (record.getPermissions() > 0) {
                            try {
                                PosixFileAttributes posixAttrs = Files.readAttributes(targetPath, PosixFileAttributes.class);
                                Set<PosixFilePermission> permissions = new java.util.HashSet<>();
                                permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
                                if ((record.getPermissions() & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
                                if ((record.getPermissions() & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
                                if ((record.getPermissions() & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
                                if ((record.getPermissions() & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
                                if ((record.getPermissions() & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
                                if ((record.getPermissions() & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
                                if ((record.getPermissions() & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                                
                                Files.setPosixFilePermissions(targetPath, permissions);
                            } catch (Exception e) {
                                // 非POSIX系统，忽略权限设置
                            }
                        }
                    } catch (Exception e) {
                        // 设置元数据失败，不影响主要功能
                        System.err.println("设置目录元数据失败: " + targetPath + " - " + e.getMessage());
                    }
                }
            } else if (record.getKind() == BackupService.FileKind.REGULAR && record.isHasData()) {
                // 创建文件并写入数据
                Files.createDirectories(targetPath.getParent());
                
                // 定位并读取数据
                raf.seek(record.getDataOffset());
                byte[] fileData = new byte[(int) record.getStoredSize()];
                raf.readFully(fileData);
                
                // 解密数据（如果需要）
                if (record.isEncrypted()) {
                    if (password == null || password.isEmpty()) {
                        throw new IOException("文件已加密，但未提供密码: " + record.getRelativePath());
                    }
                    
                    try {
                        // 直接尝试解密，如果密码错误会抛出异常
                        fileData = decryptData(fileData, password, record.getEncryptionMethod());
                    } catch (Exception e) {
                        throw new IOException("密码错误或解密失败: 无法解密文件 " + record.getRelativePath() + " - " + e.getMessage());
                    }
                }
                
                // 解压缩数据（如果需要）
                if (record.isCompressed() && record.getCompressionMethod() != CompressionMethod.NONE) {
                    fileData = decompressData(fileData, record.getCompressionMethod());
                }
                
                // 写入文件
                Files.write(targetPath, fileData);
                
                // 验证哈希
                String calculatedHash = calculateHash(fileData);
                if (!calculatedHash.equals(record.getHash())) {
                    System.err.println("文件哈希验证失败: " + record.getRelativePath());
                }
                
                // 设置文件元数据（如果保留元数据）
                if (preserveMetadata) {
                    try {
                        // 设置修改时间
                        if (record.getModifiedAt() > 0) {
                            FileTime modifiedTime = FileTime.fromMillis(record.getModifiedAt());
                            Files.setLastModifiedTime(targetPath, modifiedTime);
                        }
                        
                        // 设置创建时间（如果系统支持）
                        if (record.getCreatedAt() > 0) {
                            FileTime createdTime = FileTime.fromMillis(record.getCreatedAt());
                            try {
                                Files.setAttribute(targetPath, "creationTime", createdTime);
                            } catch (Exception e) {
                                // 某些系统不支持设置创建时间
                            }
                        }
                        
                        // 设置访问时间（如果系统支持）
                        if (record.getAccessedAt() > 0) {
                            FileTime accessedTime = FileTime.fromMillis(record.getAccessedAt());
                            try {
                                Files.setAttribute(targetPath, "lastAccessTime", accessedTime);
                            } catch (Exception e) {
                                // 某些系统不支持设置访问时间
                            }
                        }
                        
                        // 设置权限（如果系统支持）
                        if (record.getPermissions() > 0) {
                            try {
                                PosixFileAttributes posixAttrs = Files.readAttributes(targetPath, PosixFileAttributes.class);
                                Set<PosixFilePermission> permissions = new java.util.HashSet<>();
                                permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
                                if ((record.getPermissions() & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
                                if ((record.getPermissions() & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
                                if ((record.getPermissions() & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
                                if ((record.getPermissions() & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
                                if ((record.getPermissions() & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
                                if ((record.getPermissions() & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
                                if ((record.getPermissions() & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
                                if ((record.getPermissions() & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                                
                                Files.setPosixFilePermissions(targetPath, permissions);
                            } catch (Exception e) {
                                // 非POSIX系统，忽略权限设置
                            }
                        }
                    } catch (Exception e) {
                        // 设置元数据失败，不影响主要功能
                        System.err.println("设置文件元数据失败: " + targetPath + " - " + e.getMessage());
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * 计算数据的SHA-256哈希
     */
    private static String calculateHash(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 获取包文件信息
     */
    public static BackupManifest getPackageInfo(String packagePath) throws IOException {
        Path packageFile = Paths.get(packagePath);
        
        if (!Files.exists(packageFile)) {
            throw new IOException("包文件不存在: " + packagePath);
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(packageFile.toFile(), "r")) {
            // 读取魔数和版本
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!new String(magic, StandardCharsets.UTF_8).equals("FBS1")) {
                throw new IOException("无效的包文件格式");
            }
            
            int version = raf.readInt();
            if (version != PACKAGE_VERSION) {
                throw new IOException("不支持的包版本: " + version);
            }
            
            // 读取Header
            long manifestOffset = raf.readLong();
            long manifestLength = raf.readLong();
            
            // 读取Manifest
            raf.seek(manifestOffset);
            byte[] manifestBytes = new byte[(int) manifestLength];
            raf.readFully(manifestBytes);
            String manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);
            
            return BackupManifest.fromJson(manifestJson);
        }
    }
    
    // =============== 加密相关方法 ===============
    
    /**
     * 使用XOR算法加密数据
     * @param data 原始数据
     * @param password 密码
     * @return 加密后的数据
     */
    public static byte[] encryptXOR(byte[] data, String password) {
        if (password == null || password.isEmpty()) {
            return data;
        }
        
        try {
            // 使用SHA256哈希密码作为密钥
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // 应用XOR加密
            byte[] encrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                encrypted[i] = (byte) (data[i] ^ key[i % key.length]);
            }
            return encrypted;
        } catch (java.security.NoSuchAlgorithmException e) {
            // 如果SHA-256不可用，使用简单的方法
            byte[] key = password.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                encrypted[i] = (byte) (data[i] ^ key[i % key.length]);
            }
            return encrypted;
        }
    }
    
    /**
     * 使用XOR算法解密数据（XOR加密是对称的）
     * @param data 加密数据
     * @param password 密码
     * @return 解密后的数据
     */
    public static byte[] decryptXOR(byte[] data, String password) {
        // XOR是对称加密，解密和加密使用相同的方法
        return encryptXOR(data, password);
    }
    
    /**
     * 使用RC4算法加密数据
     * @param data 原始数据
     * @param password 密码
     * @return 加密后的数据
     */
    public static byte[] encryptRC4(byte[] data, String password) {
        if (data == null || data.length == 0 || password == null || password.isEmpty()) {
            return data;
        }
        
        try {
            // 使用密码生成密钥
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // RC4算法实现
            byte[] state = new byte[256];
            for (int i = 0; i < 256; i++) {
                state[i] = (byte) i;
            }
            
            int j = 0;
            for (int i = 0; i < 256; i++) {
                j = (j + state[i] + key[i % key.length]) & 0xFF;
                byte temp = state[i];
                state[i] = state[j];
                state[j] = temp;
            }
            
            byte[] output = data.clone();
            int i = 0, k = 0;
            for (int counter = 0; counter < data.length; counter++) {
                i = (i + 1) & 0xFF;
                k = (k + state[i]) & 0xFF;
                
                byte temp = state[i];
                state[i] = state[k];
                state[k] = temp;
                
                int byteIndex = (state[i] + state[k]) & 0xFF;
                output[counter] = (byte) (data[counter] ^ state[byteIndex]);
            }
            
            return output;
        } catch (Exception e) {
            // 如果RC4加密失败，返回原始数据
            return data;
        }
    }
    
    /**
     * 使用RC4算法解密数据（RC4加密是对称的）
     * @param data 加密数据
     * @param password 密码
     * @return 解密后的数据
     */
    public static byte[] decryptRC4(byte[] data, String password) {
        // RC4是对称加密，解密和加密使用相同的方法
        return encryptRC4(data, password);
    }
    
    /**
     * 使用AES-256算法加密数据
     * @param data 原始数据
     * @param password 密码
     * @return 加密后的数据
     */
    public static byte[] encryptAES256(byte[] data, String password) {
        if (data == null || data.length == 0 || password == null || password.isEmpty()) {
            return data;
        }
        
        try {
            // 使用密码生成256位密钥
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // 生成随机IV（初始化向量）
            java.security.SecureRandom random = new java.security.SecureRandom();
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            
            // 创建AES密钥和密码器（使用CBC模式，更安全）
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(key, "AES");
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            // 加密数据
            byte[] encrypted = cipher.doFinal(data);
            
            // 将IV和加密数据合并：IV + 加密数据
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            
            return result;
        } catch (Exception e) {
            // 如果AES加密失败，抛出异常
            throw new RuntimeException("AES加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用AES-256算法解密数据
     * @param data 加密数据（包含IV）
     * @param password 密码
     * @return 解密后的数据
     */
    public static byte[] decryptAES256(byte[] data, String password) {
        if (data == null || data.length == 0 || password == null || password.isEmpty()) {
            return data;
        }
        
        try {
            // 使用密码生成256位密钥
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // 分离IV和加密数据（前16字节是IV）
            if (data.length < 16) {
                throw new IllegalArgumentException("加密数据太短，无法提取IV");
            }
            
            byte[] iv = new byte[16];
            byte[] encryptedData = new byte[data.length - 16];
            System.arraycopy(data, 0, iv, 0, 16);
            System.arraycopy(data, 16, encryptedData, 0, encryptedData.length);
            
            // 创建AES密钥和密码器
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(key, "AES");
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            // 解密数据
            return cipher.doFinal(encryptedData);
        } catch (javax.crypto.BadPaddingException e) {
            // BadPaddingException通常表示密码错误
            throw new RuntimeException("密码错误或数据损坏: " + e.getMessage(), e);
        } catch (Exception e) {
            // 其他解密失败
            throw new RuntimeException("AES解密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据指定的加密方法加密数据
     * @param data 原始数据
     * @param password 密码
     * @param method 加密方法
     * @return 加密后的数据
     */
    public static byte[] encryptData(byte[] data, String password, EncryptionMethod method) {
        if (method == EncryptionMethod.NONE || password == null || password.isEmpty()) {
            return data;
        }
        
        switch (method) {
            case XOR:
                return encryptXOR(data, password);
            case RC4:
                return encryptRC4(data, password);
            case AES256:
                return encryptAES256(data, password);
            default:
                return data;
        }
    }
    
    /**
     * 根据指定的加密方法解密数据
     * @param data 加密数据
     * @param password 密码
     * @param method 加密方法
     * @return 解密后的数据
     * @throws IOException 如果密码错误或解密失败
     */
    public static byte[] decryptData(byte[] data, String password, EncryptionMethod method) throws IOException {
        if (method == EncryptionMethod.NONE || password == null || password.isEmpty()) {
            return data;
        }
        
        try {
            switch (method) {
                case XOR:
                    return decryptXOR(data, password);
                case RC4:
                    return decryptRC4(data, password);
                case AES256:
                    return decryptAES256(data, password);
                default:
                    return data;
            }
        } catch (Exception e) {
            throw new IOException("解密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证密码是否正确
     * @param data 加密数据
     * @param password 密码
     * @param method 加密方法
     * @return 密码是否正确
     */
    public static boolean verifyPassword(byte[] data, String password, EncryptionMethod method) {
        if (password == null || password.isEmpty()) {
            return method == EncryptionMethod.NONE;
        }
        
        try {
            // 对于AES256，需要特殊处理，因为数据包含IV
            if (method == EncryptionMethod.AES256) {
                // AES256数据至少需要16字节（IV）才能验证
                if (data.length < 32) { // 至少IV + 一个加密块
                    return false;
                }
                
                try {
                    // 尝试解密数据，如果密码错误会抛出异常
                    byte[] decrypted = decryptAES256(data, password);
                    // 如果解密成功且数据不为空，则认为密码正确
                    return decrypted != null && decrypted.length > 0;
                } catch (Exception e) {
                    // 解密失败，密码错误
                    return false;
                }
            }
            
            // 对于其他加密方法，使用原来的验证逻辑
            byte[] testData = new byte[Math.min(16, data.length)];
            System.arraycopy(data, 0, testData, 0, testData.length);
            
            byte[] decrypted = null;
            byte[] reEncrypted = null;
            
            switch (method) {
                case XOR:
                    decrypted = decryptXOR(testData, password);
                    reEncrypted = encryptXOR(decrypted, password);
                    break;
                case RC4:
                    decrypted = decryptRC4(testData, password);
                    reEncrypted = encryptRC4(decrypted, password);
                    break;
                case NONE:
                    // 对于未加密的数据，验证总是成功（如果密码为空则返回true）
                    return true;
                default:
                    return false;
            }
            
            // 比较原始数据和重新加密后的数据是否相同
            for (int i = 0; i < testData.length; i++) {
                if (testData[i] != reEncrypted[i]) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // =============== 压缩相关方法 ===============
    
    /**
     * 哈夫曼压缩的节点类
     */
    private static class HuffmanNode implements Comparable<HuffmanNode> {
        byte value;
        int frequency;
        HuffmanNode left;
        HuffmanNode right;
        
        HuffmanNode(byte value, int frequency) {
            this.value = value;
            this.frequency = frequency;
        }
        
        HuffmanNode(int frequency, HuffmanNode left, HuffmanNode right) {
            this.frequency = frequency;
            this.left = left;
            this.right = right;
        }
        
        boolean isLeaf() {
            return left == null && right == null;
        }
        
        @Override
        public int compareTo(HuffmanNode other) {
            return this.frequency - other.frequency;
        }
    }
    
    /**
     * 使用哈夫曼编码压缩数据
     * @param data 原始数据
     * @return 压缩后的数据
     */
    public static byte[] compressHuffman(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        try {
            // 1. 统计频率
            int[] freq = new int[256];
            for (byte b : data) {
                freq[b & 0xFF]++;
            }
            
            // 2. 构建哈夫曼树
            java.util.PriorityQueue<HuffmanNode> pq = new java.util.PriorityQueue<>();
            for (int i = 0; i < 256; i++) {
                if (freq[i] > 0) {
                    pq.offer(new HuffmanNode((byte) i, freq[i]));
                }
            }
            
            // 特殊情况：只有一种字节值
            if (pq.size() == 1) {
                pq.offer(new HuffmanNode((byte) 0, 0));
            }
            
            // 构建哈夫曼树
            while (pq.size() > 1) {
                HuffmanNode left = pq.poll();
                HuffmanNode right = pq.poll();
                HuffmanNode parent = new HuffmanNode(left.frequency + right.frequency, left, right);
                pq.offer(parent);
            }
            
            HuffmanNode root = pq.poll();
            
            // 3. 生成编码表
            String[] codes = new String[256];
            buildHuffmanCodes(root, "", codes);
            
            // 4. 编码数据
            java.util.BitSet bitSet = new java.util.BitSet();
            int bitIndex = 0;
            
            for (byte b : data) {
                String code = codes[b & 0xFF];
                for (char c : code.toCharArray()) {
                    if (c == '1') {
                        bitSet.set(bitIndex);
                    }
                    bitIndex++;
                }
            }
            
            // 5. 构建输出：频率表 + 编码数据
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(output);
            
            // 写入频率表
            for (int i = 0; i < 256; i++) {
                dos.writeInt(freq[i]);
            }
            
            // 写入位集
            byte[] bitArray = bitSet.toByteArray();
            dos.writeInt(bitArray.length);
            dos.write(bitArray);
            dos.writeInt(bitIndex); // 实际位数
            
            return output.toByteArray();
            
        } catch (Exception e) {
            // 如果压缩失败，返回原始数据
            return data;
        }
    }
    
    /**
     * 递归构建哈夫曼编码
     */
    private static void buildHuffmanCodes(HuffmanNode node, String code, String[] codes) {
        if (node == null) return;
        
        if (node.isLeaf()) {
            codes[node.value & 0xFF] = code;
        } else {
            buildHuffmanCodes(node.left, code + "0", codes);
            buildHuffmanCodes(node.right, code + "1", codes);
        }
    }
    
    /**
     * 解压哈夫曼编码的数据
     * @param compressedData 压缩数据
     * @return 解压后的数据
     */
    public static byte[] decompressHuffman(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(compressedData);
            DataInputStream dis = new DataInputStream(input);
            
            // 1. 读取频率表
            int[] freq = new int[256];
            for (int i = 0; i < 256; i++) {
                freq[i] = dis.readInt();
            }
            
            // 2. 读取位集
            int bitArrayLength = dis.readInt();
            byte[] bitArray = new byte[bitArrayLength];
            dis.readFully(bitArray);
            int bitCount = dis.readInt();
            
            java.util.BitSet bitSet = java.util.BitSet.valueOf(bitArray);
            
            // 3. 重建哈夫曼树
            java.util.PriorityQueue<HuffmanNode> pq = new java.util.PriorityQueue<>();
            for (int i = 0; i < 256; i++) {
                if (freq[i] > 0) {
                    pq.offer(new HuffmanNode((byte) i, freq[i]));
                }
            }
            
            if (pq.size() == 1) {
                pq.offer(new HuffmanNode((byte) 0, 0));
            }
            
            while (pq.size() > 1) {
                HuffmanNode left = pq.poll();
                HuffmanNode right = pq.poll();
                HuffmanNode parent = new HuffmanNode(left.frequency + right.frequency, left, right);
                pq.offer(parent);
            }
            
            HuffmanNode root = pq.poll();
            
            // 4. 解码数据
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            HuffmanNode currentNode = root;
            
            for (int i = 0; i < bitCount; i++) {
                boolean bit = bitSet.get(i);
                currentNode = bit ? currentNode.right : currentNode.left;
                
                if (currentNode.isLeaf()) {
                    output.write(currentNode.value & 0xFF);
                    currentNode = root;
                }
            }
            
            return output.toByteArray();
            
        } catch (Exception e) {
            // 如果解压失败，返回原始数据
            return compressedData;
        }
    }
    
    /**
     * 使用游程编码(RLE)压缩数据
     * @param data 原始数据
     * @return 压缩后的数据
     */
    public static byte[] compressRLE(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        int count = 1;
        byte current = data[0];
        
        for (int i = 1; i < data.length; i++) {
            if (data[i] == current && count < 255) {
                count++;
            } else {
                output.write(count);
                output.write(current);
                current = data[i];
                count = 1;
            }
        }
        
        // 写入最后一组
        output.write(count);
        output.write(current);
        
        return output.toByteArray();
    }
    
    /**
     * 使用Deflate算法压缩数据
     * @param data 原始数据
     * @return 压缩后的数据
     */
    public static byte[] compressDeflate(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        try {
            java.util.zip.Deflater deflater = new java.util.zip.Deflater();
            deflater.setInput(data);
            deflater.finish();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            
            deflater.end();
            return outputStream.toByteArray();
        } catch (Exception e) {
            // 如果压缩失败，返回原始数据
            return data;
        }
    }
    
    /**
     * 解压Deflate算法压缩的数据
     * @param compressedData 压缩数据
     * @return 解压后的数据
     */
    public static byte[] decompressDeflate(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        
        try {
            java.util.zip.Inflater inflater = new java.util.zip.Inflater();
            inflater.setInput(compressedData);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            
            inflater.end();
            return outputStream.toByteArray();
        } catch (Exception e) {
            // 如果解压失败，返回原始数据
            return compressedData;
        }
    }
    
    /**
     * 解压RLE编码的数据
     * @param compressedData 压缩数据
     * @return 解压后的数据
     */
    public static byte[] decompressRLE(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        for (int i = 0; i < compressedData.length; i += 2) {
            if (i + 1 >= compressedData.length) break;
            
            int count = compressedData[i] & 0xFF;
            byte value = compressedData[i + 1];
            
            for (int j = 0; j < count; j++) {
                output.write(value);
            }
        }
        
        return output.toByteArray();
    }
    
    /**
     * 根据指定的压缩方法压缩数据
     * @param data 原始数据
     * @param method 压缩方法
     * @return 压缩后的数据
     */
    public static byte[] compressData(byte[] data, CompressionMethod method) {
        if (method == CompressionMethod.NONE) {
            return data;
        }
        
        switch (method) {
            case HUFFMAN:
                return compressHuffman(data);
            case RLE:
                return compressRLE(data);
            case ZLIB:
                return compressDeflate(data);
            default:
                // 其他方法返回原始数据
                return data;
        }
    }
    
    /**
     * 根据指定的压缩方法解压数据
     * @param data 压缩数据
     * @param method 压缩方法
     * @return 解压后的数据
     */
    public static byte[] decompressData(byte[] data, CompressionMethod method) {
        if (method == CompressionMethod.NONE) {
            return data;
        }
        
        switch (method) {
            case HUFFMAN:
                return decompressHuffman(data);
            case RLE:
                return decompressRLE(data);
            case ZLIB:
                return decompressDeflate(data);
            default:
                // 其他方法返回原始数据
                return data;
        }
    }
    
    /**
     * 验证包文件的完整性
     * @param packagePath 包文件路径
     * @return 是否验证成功
     */
    public static boolean verifyPackage(String packagePath) throws IOException {
        // 调用重载方法，不提供密码，仅验证存储数据的完整性
        return verifyPackage(packagePath, null);
    }
    
    /**
     * 验证包文件的完整性（支持加密包）
     * @param packagePath 包文件路径
     * @param password 解密密码（如果包被加密）
     * @return 是否验证成功
     */
    public static boolean verifyPackage(String packagePath, String password) throws IOException {
        Path packageFile = Paths.get(packagePath);
        
        if (!Files.exists(packageFile)) {
            throw new IOException("包文件不存在: " + packagePath);
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(packageFile.toFile(), "r")) {
            // 读取魔数和版本
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!new String(magic, StandardCharsets.UTF_8).equals("FBS1")) {
                throw new IOException("无效的包文件格式");
            }
            
            int version = raf.readInt();
            if (version != PACKAGE_VERSION) {
                throw new IOException("不支持的包版本: " + version);
            }
            
            // 读取Header
            long manifestOffset = raf.readLong();
            long manifestLength = raf.readLong();
            
            // 读取Manifest
            raf.seek(manifestOffset);
            byte[] manifestBytes = new byte[(int) manifestLength];
            raf.readFully(manifestBytes);
            String manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);
            
            BackupManifest manifest = BackupManifest.fromJson(manifestJson);
            
            // 验证所有文件的哈希
            for (FileRecord record : manifest.getFiles()) {
                if (record.isHasData()) {
                    // 定位并读取数据
                    raf.seek(record.getDataOffset());
                    byte[] fileData = new byte[(int) record.getStoredSize()];
                    raf.readFully(fileData);
                    
                    // 如果数据被压缩且加密，备份时的处理顺序是：原始数据 -> 压缩 -> 加密
                    // 验证时需要逆向：加密数据 -> 解密 -> 解压 -> 原始数据
                    
                    // 如果数据被加密，先解密
                    if (record.isEncrypted()) {
                        if (password == null || password.isEmpty()) {
                            throw new IOException("包文件已加密，需要提供密码进行验证: " + record.getRelativePath());
                        }
                        
                        try {
                            // 尝试解密数据
                            fileData = decryptData(fileData, password, record.getEncryptionMethod());
                        } catch (Exception e) {
                            // 解密失败，密码错误或数据损坏
                            throw new IOException("密码错误或解密失败: 无法验证文件 " + record.getRelativePath() + " - " + e.getMessage());
                        }
                    }
                    
                    // 如果数据被压缩（注意：解密后才解压）
                    if (record.isCompressed()) {
                        fileData = decompressData(fileData, record.getCompressionMethod());
                    }
                    
                    // 计算最终解密/解压后数据的哈希（即原始数据的哈希）
                    String calculatedHash = calculateHash(fileData);
                    if (!calculatedHash.equals(record.getHash())) {
                        if (record.isEncrypted() && (password == null || password.isEmpty())) {
                            throw new IOException("包文件已加密，需要提供密码进行完整验证: " + record.getRelativePath());
                        }
                        System.err.println("文件哈希验证失败: " + record.getRelativePath() + 
                                         " (Expected: " + record.getHash() + 
                                         ", Got: " + calculatedHash + 
                                         ", Encrypted: " + record.isEncrypted() + 
                                         ", Compressed: " + record.isCompressed() + ")");
                        return false;
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("包验证失败: " + e.getMessage());
            return false;
        }
    }
}