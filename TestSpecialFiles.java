import com.backup.BackupService;
import java.nio.file.*;
import java.io.IOException;

public class TestSpecialFiles {
    public static void main(String[] args) {
        try {
            BackupService service = new BackupService();
            
            // 创建测试目录
            Path testDir = Paths.get("test_special_files");
            if (!Files.exists(testDir)) {
                Files.createDirectory(testDir);
            }
            
            // 创建普通文件
            Path regularFile = testDir.resolve("regular.txt");
            Files.writeString(regularFile, "这是一个普通文件");
            
            // 创建符号链接（如果系统支持）
            try {
                Path symlinkFile = testDir.resolve("symlink.txt");
                Files.createSymbolicLink(symlinkFile, regularFile);
                System.out.println("创建符号链接: " + symlinkFile);
            } catch (Exception e) {
                System.out.println("无法创建符号链接: " + e.getMessage());
            }
            
            // 测试备份
            System.out.println("\n=== 测试备份功能 ===");
            BackupService.BackupOptions options = new BackupService.BackupOptions(true, true);
            BackupService.BackupResult result = service.backup(testDir.toString(), "backup_test", options);
            
            System.out.println("备份结果: " + result.getMessage());
            System.out.println("文件复制: " + result.getFilesCopied());
            System.out.println("目录创建: " + result.getDirectoriesCreated());
            System.out.println("总大小: " + result.getTotalSize() + " bytes");
            
            // 测试还原
            System.out.println("\n=== 测试还原功能 ===");
            BackupService.BackupResult restoreResult = service.restore("backup_test", "restore_test", options);
            
            System.out.println("还原结果: " + restoreResult.getMessage());
            System.out.println("文件还原: " + restoreResult.getFilesCopied());
            System.out.println("目录创建: " + restoreResult.getDirectoriesCreated());
            System.out.println("总大小: " + restoreResult.getTotalSize() + " bytes");
            
            // 清理测试文件
            System.out.println("\n=== 清理测试文件 ===");
            deleteDirectory(testDir);
            deleteDirectory(Paths.get("backup_test"));
            deleteDirectory(Paths.get("restore_test"));
            
            System.out.println("测试完成！");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
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