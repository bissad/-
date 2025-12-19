import com.backup.BackupService;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestFolderMetadata {
    public static void main(String[] args) {
        try {
            BackupService service = new BackupService();
            
            // 创建测试目录结构
            Path testDir = Paths.get("test_folder_metadata");
            if (Files.exists(testDir)) {
                deleteDirectory(testDir);
            }
            Files.createDirectory(testDir);
            
            // 设置测试目录的特殊时间戳（设置为昨天）
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            FileTime yesterdayTime = FileTime.fromMillis(
                yesterday.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            );
            
            Files.setLastModifiedTime(testDir, yesterdayTime);
            System.out.println("设置测试目录修改时间为: " + yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // 在测试目录中创建子目录和文件
            Path subDir = testDir.resolve("subfolder");
            Files.createDirectory(subDir);
            
            // 设置子目录的特殊权限（如果系统支持）
            try {
                Files.setPosixFilePermissions(subDir, 
                    java.util.EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE
                    ));
                System.out.println("设置子目录特殊权限");
            } catch (Exception e) {
                System.out.println("无法设置POSIX权限: " + e.getMessage());
            }
            
            // 创建测试文件
            Path testFile = testDir.resolve("test.txt");
            Files.writeString(testFile, "这是一个测试文件");
            
            // 记录原始目录属性
            System.out.println("\n=== 原始目录属性 ===");
            printDirectoryAttributes(testDir, "测试目录");
            printDirectoryAttributes(subDir, "子目录");
            
            // 测试备份（保留元数据）
            System.out.println("\n=== 测试备份（保留元数据）===");
            BackupService.BackupOptions optionsWithMetadata = new BackupService.BackupOptions(true, true);
            BackupService.BackupResult backupResult = service.backup(testDir.toString(), "backup_with_metadata", optionsWithMetadata);
            System.out.println("备份结果: " + backupResult.getMessage());
            
            // 检查备份后的目录属性
            Path backupDir = Paths.get("backup_with_metadata").resolve(testDir.getFileName());
            Path backupSubDir = backupDir.resolve("subfolder");
            
            System.out.println("\n=== 备份目录属性（应保留元数据）===");
            printDirectoryAttributes(backupDir, "备份目录");
            printDirectoryAttributes(backupSubDir, "备份子目录");
            
            // 测试备份（不保留元数据）
            System.out.println("\n=== 测试备份（不保留元数据）===");
            BackupService.BackupOptions optionsWithoutMetadata = new BackupService.BackupOptions(true, false);
            BackupService.BackupResult backupResult2 = service.backup(testDir.toString(), "backup_without_metadata", optionsWithoutMetadata);
            System.out.println("备份结果: " + backupResult2.getMessage());
            
            // 检查备份后的目录属性
            Path backupDir2 = Paths.get("backup_without_metadata").resolve(testDir.getFileName());
            
            System.out.println("\n=== 备份目录属性（不保留元数据）===");
            printDirectoryAttributes(backupDir2, "备份目录（无元数据）");
            
            // 测试还原
            System.out.println("\n=== 测试还原 ===");
            BackupService.BackupResult restoreResult = service.restore("backup_with_metadata", "restore_test", optionsWithMetadata);
            System.out.println("还原结果: " + restoreResult.getMessage());
            
            Path restoreDir = Paths.get("restore_test").resolve(testDir.getFileName());
            
            System.out.println("\n=== 还原目录属性 ===");
            printDirectoryAttributes(restoreDir, "还原目录");
            
            // 清理测试文件
            System.out.println("\n=== 清理测试文件 ===");
            deleteDirectory(testDir);
            deleteDirectory(Paths.get("backup_with_metadata"));
            deleteDirectory(Paths.get("backup_without_metadata"));
            deleteDirectory(Paths.get("restore_test"));
            
            System.out.println("测试完成！");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void printDirectoryAttributes(Path dir, String label) throws IOException {
        if (!Files.exists(dir)) {
            System.out.println(label + ": 目录不存在");
            return;
        }
        
        BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
        System.out.println(label + ":");
        System.out.println("  路径: " + dir);
        System.out.println("  修改时间: " + attrs.lastModifiedTime());
        System.out.println("  创建时间: " + attrs.creationTime());
        System.out.println("  访问时间: " + attrs.lastAccessTime());
        
        try {
            PosixFileAttributes posixAttrs = Files.readAttributes(dir, PosixFileAttributes.class);
            System.out.println("  权限: " + posixAttrs.permissions());
            System.out.println("  所有者: " + posixAttrs.owner().getName());
            System.out.println("  用户组: " + posixAttrs.group().getName());
        } catch (Exception e) {
            System.out.println("  权限: (非POSIX系统)");
        }
        System.out.println();
    }
    
    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                 .sorted((a, b) -> -a.compareTo(b))
                 .forEach(p -> {
                     try {
                         Files.delete(p);
                     } catch (IOException e) {
                         System.err.println("删除失败: " + p + " - " + e.getMessage());
                     }
                 });
            System.out.println("已删除: " + path);
        }
    }
}