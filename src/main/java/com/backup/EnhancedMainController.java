package com.backup;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 增强的主控制器 - 扩展原有MainController，添加打包功能
 */
public class EnhancedMainController extends MainControllerFixed {
    
    // 新增的UI控件（打包相关）
    @FXML
    private CheckBox packageModeCheck;
    
    @FXML
    private CheckBox compressCheck;
    
    @FXML
    private CheckBox encryptCheck;
    
    @FXML
    private ComboBox<String> compressionMethodCombo;
    
    @FXML
    private ComboBox<String> encryptionMethodCombo;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private TextField backupNameField;
    
    @FXML
    private VBox packageOptionsBox;
    
    @FXML
    private VBox compressOptionsBox;
    
    @FXML
    private VBox encryptOptionsBox;
    
    @FXML
    private Label packageInfoLabel;
    
    // 还原页面的新增控件
    @FXML
    private CheckBox restoreFromPackageCheck;
    
    @FXML
    private Button browsePackageButton;
    
    @FXML
    private TextField packagePathField;
    
    @FXML
    private VBox restorePackageOptionsBox;
    
    // 还原页面的新增密码控件
    @FXML
    private VBox restoreEncryptOptionsBox;
    
    @FXML
    private PasswordField restorePasswordField;
    
    @FXML
    private Label restorePackageInfoLabel;
    
    // 目录还原部分容器
    @FXML
    private VBox directoryRestoreBox;
    
    // 还原目标目录选择按钮（在父类中未定义）
    @FXML
    private Button restoreTargetButton;
    
    // 服务实例
    private EnhancedBackupService enhancedBackupService;
    
    @FXML
    @Override
    public void initialize() {
        // 调用父类的初始化
        super.initialize();
        
        // 初始化增强服务
        enhancedBackupService = new EnhancedBackupService();
        
        // 初始化打包选项UI
        initializePackageUI();
        
        // 设置事件监听器
        setupEventListeners();
        
        // 确保按钮可用（调试）
        if (backupButton != null) {
            backupButton.setDisable(false);
            System.out.println("DEBUG: Backup button initialized, disabled=" + backupButton.isDisabled());
        } else {
            System.out.println("DEBUG: Backup button is null!");
        }
        
        if (restoreButton != null) {
            restoreButton.setDisable(false);
            System.out.println("DEBUG: Restore button initialized, disabled=" + restoreButton.isDisabled());
        } else {
            System.out.println("DEBUG: Restore button is null!");
        }
    }
    
    /**
     * 初始化打包相关UI
     */
    private void initializePackageUI() {
        // 初始化下拉框选项
        compressionMethodCombo.getItems().addAll(
            "不压缩",
            "哈夫曼编码",
            "游程编码(RLE)",
            "Zlib压缩"
        );
        compressionMethodCombo.setValue("不压缩");
        
        encryptionMethodCombo.getItems().addAll(
            "不加密",
            "异或加密",
            "RC4加密",
            "AES256加密"
        );
        encryptionMethodCombo.setValue("不加密");
        
        // 初始状态
        packageOptionsBox.setVisible(false);
        compressOptionsBox.setVisible(false);
        encryptOptionsBox.setVisible(false);
        restorePackageOptionsBox.setVisible(false);
        
        // 禁用相关选项
        compressionMethodCombo.setDisable(true);
        encryptionMethodCombo.setDisable(true);
        passwordField.setDisable(true);
    }
    
    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        // 打包模式复选框监听
        packageModeCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            packageOptionsBox.setVisible(newVal);
            if (newVal) {
                packageInfoLabel.setText("打包模式：将生成.fbk文件");
            } else {
                packageInfoLabel.setText("目录模式：将创建备份目录");
            }
        });
        
        // 压缩复选框监听
        compressCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            compressOptionsBox.setVisible(newVal);
            compressionMethodCombo.setDisable(!newVal);
        });
        
        // 加密复选框监听
        encryptCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            encryptOptionsBox.setVisible(newVal);
            encryptionMethodCombo.setDisable(!newVal);
            passwordField.setDisable(!newVal);
        });
        
        // 从包还原复选框监听 - 两种还原模式互斥显示
        restoreFromPackageCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("DEBUG: restoreFromPackageCheck changed: " + oldVal + " -> " + newVal);
            restorePackageOptionsBox.setVisible(newVal);
            
            // 包还原模式选中时，隐藏目录还原部分；反之亦然
            if (directoryRestoreBox != null) {
                directoryRestoreBox.setVisible(!newVal);
            }
            
            if (restoreButton != null) {
                System.out.println("DEBUG: Restore button disabled state: " + restoreButton.isDisabled());
            }
        });
    }
    
    /**
     * 处理浏览包文件按钮点击
     */
    @FXML
    private void handleBrowsePackage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择备份包文件");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("备份包文件", "*.fbk"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null) {
            packagePathField.setText(selectedFile.getAbsolutePath());
            
            // 尝试读取包信息
            try {
                BackupPackage.BackupManifest manifest = 
                    enhancedBackupService.getPackageInfo(selectedFile.getAbsolutePath());
                
                String info = String.format("包信息: %d个文件, 总大小: %s",
                    manifest.getFiles().size(),
                    formatSize(manifest.getTotalBytes()));
                
                // 检查是否加密
                if (manifest.isEncrypted()) {
                    info += " (已加密)";
                    // 显示密码输入框
                    restoreEncryptOptionsBox.setVisible(true);
                    restorePackageInfoLabel.setText("此备份包已加密，需要密码才能还原。");
                    restorePackageInfoLabel.setStyle("-fx-text-fill: #e74c3c;");
                } else {
                    info += " (未加密)";
                    // 隐藏密码输入框
                    restoreEncryptOptionsBox.setVisible(false);
                    restorePackageInfoLabel.setText("");
                }
                
                // 检查是否压缩
                if (manifest.isCompressed()) {
                    info += " (已压缩)";
                }
                
                // 在还原页面显示信息
                restorePackageInfoLabel.setText(info);
                restorePackageInfoLabel.setStyle("-fx-text-fill: #27ae60;");
                
            } catch (Exception e) {
                restorePackageInfoLabel.setText("无法读取包信息: " + e.getMessage());
                restorePackageInfoLabel.setStyle("-fx-text-fill: #e74c3c;");
                restoreEncryptOptionsBox.setVisible(false);
            }
        }
    }
    
    /**
     * 处理增强备份按钮点击
     */
    @FXML
    private void handleEnhancedBackup() {
        System.out.println("DEBUG: handleEnhancedBackup called");
        
        // 检查源路径
        if (sourcePaths.isEmpty()) {
            showAlert("错误", "请先添加要备份的源路径");
            return;
        }
        
        // 检查目标路径
        String target = targetField.getText();
        if (target == null || target.trim().isEmpty()) {
            showAlert("错误", "请选择备份目标目录");
            return;
        }
        
        // 检查打包模式下的备份名称
        String backupName = backupNameField.getText();
        if (packageModeCheck.isSelected() && (backupName == null || backupName.trim().isEmpty())) {
            // 使用默认名称
            backupName = "backup_" + System.currentTimeMillis();
            backupNameField.setText(backupName);
        }
        
        // 检查加密密码
        String password = passwordField.getText();
        if (encryptCheck.isSelected() && (password == null || password.trim().isEmpty())) {
            showAlert("错误", "启用加密需要设置密码");
            return;
        }
        
        // 创建增强备份选项
        EnhancedBackupService.EnhancedBackupOptions options = createEnhancedBackupOptions();
        
        // 执行备份
        performEnhancedBackup(target, options);
    }
    
    /**
     * 创建增强备份选项
     */
    private EnhancedBackupService.EnhancedBackupOptions createEnhancedBackupOptions() {
        EnhancedBackupService.EnhancedBackupOptions options = 
            new EnhancedBackupService.EnhancedBackupOptions(
                includeSpecialFilesCheck.isSelected(),
                preserveMetadataCheck.isSelected()
            );
        
        // 设置打包选项
        options.setPackageMode(packageModeCheck.isSelected());
        options.setCompress(compressCheck.isSelected());
        options.setEncrypt(encryptCheck.isSelected());
        options.setBackupName(backupNameField.getText());
        options.setPassword(passwordField.getText());
        
        // 设置压缩方法
        String compressionMethod = compressionMethodCombo.getValue();
        if ("哈夫曼编码".equals(compressionMethod)) {
            options.setCompressionMethod(BackupPackage.CompressionMethod.HUFFMAN);
        } else if ("游程编码(RLE)".equals(compressionMethod)) {
            options.setCompressionMethod(BackupPackage.CompressionMethod.RLE);
        } else if ("Zlib压缩".equals(compressionMethod)) {
            options.setCompressionMethod(BackupPackage.CompressionMethod.ZLIB);
        } else {
            options.setCompressionMethod(BackupPackage.CompressionMethod.NONE);
        }
        
        // 设置加密方法
        String encryptionMethod = encryptionMethodCombo.getValue();
        if ("异或加密".equals(encryptionMethod)) {
            options.setEncryptionMethod(BackupPackage.EncryptionMethod.XOR);
        } else if ("RC4加密".equals(encryptionMethod)) {
            options.setEncryptionMethod(BackupPackage.EncryptionMethod.RC4);
        } else if ("AES256加密".equals(encryptionMethod)) {
            options.setEncryptionMethod(BackupPackage.EncryptionMethod.AES256);
        } else {
            options.setEncryptionMethod(BackupPackage.EncryptionMethod.NONE);
        }
        
        return options;
    }
    
    /**
     * 执行增强备份
     */
    private void performEnhancedBackup(String target, EnhancedBackupService.EnhancedBackupOptions options) {
        backupButton.setDisable(true);
        progressBar.setVisible(true);
        resultBox.setVisible(false);
        statusLabel.setText("正在备份...");
        
        Task<EnhancedBackupService.EnhancedBackupResult> backupTask = new Task<>() {
            @Override
            protected EnhancedBackupService.EnhancedBackupResult call() throws Exception {
                List<String> actualPaths = new ArrayList<>(sourcePaths);
                return enhancedBackupService.enhancedBackupMultiple(actualPaths, target, options);
            }
        };
        
        backupTask.setOnSucceeded(event -> {
            EnhancedBackupService.EnhancedBackupResult result = backupTask.getValue();
            statusLabel.setText(result.getMessage());
            filesCopiedLabel.setText("文件备份: " + result.getFilesCopied());
            directoriesCreatedLabel.setText("目录创建: " + result.getDirectoriesCreated());
            totalSizeLabel.setText("总大小: " + formatSize(result.getTotalSize()));
            
            // 显示打包信息
            if (result.isPackaged() && result.getPackagePath() != null) {
                String packageInfo = "包文件: " + new File(result.getPackagePath()).getName();
                if (result.isCompressed()) {
                    packageInfo += " (已压缩)";
                }
                if (result.isEncrypted()) {
                    packageInfo += " (已加密)";
                }
                showTempStatusMessage(packageInfoLabel, packageInfo, "#3498db");
            }
            
            resultBox.setVisible(true);
            progressBar.setVisible(false);
            backupButton.setDisable(false);
            
            // 保存历史记录
            try {
                for (String sourcePath : result.getSuccessfulPaths()) {
                    backupService.saveBackupHistory(sourcePath, target);
                }
            } catch (IOException e) {
                System.err.println("保存历史记录失败: " + e.getMessage());
            }
        });
        
        backupTask.setOnFailed(event -> {
            Throwable exception = backupTask.getException();
            statusLabel.setText("备份失败: " + exception.getMessage());
            progressBar.setVisible(false);
            backupButton.setDisable(false);
        });
        
        Thread backupThread = new Thread(backupTask);
        backupThread.setDaemon(true);
        backupThread.start();
    }
    
    /**
     * 处理增强还原按钮点击
     */
    @FXML
    private void handleEnhancedRestore() {
        System.out.println("DEBUG: handleEnhancedRestore called, fromPackage=" + restoreFromPackageCheck.isSelected());
        
        // 立即更新状态标签，确认按钮被点击
        if (restoreStatusLabel != null) {
            restoreStatusLabel.setText("正在处理还原请求...");
        }
        
        // 检查是否从包还原
        if (restoreFromPackageCheck.isSelected()) {
            String packagePath = packagePathField.getText();
            System.out.println("DEBUG: Package path: " + packagePath);
            if (packagePath == null || packagePath.trim().isEmpty()) {
                showAlert("错误", "请选择要还原的包文件");
                return;
            }
            
            File packageFile = new File(packagePath);
            if (!packageFile.exists()) {
                showAlert("错误", "包文件不存在: " + packagePath);
                return;
            }
            
            // 检查目标路径
            String target = restoreTargetField.getText();
            System.out.println("DEBUG: Target path: " + target);
            if (target == null || target.trim().isEmpty()) {
                showAlert("错误", "请选择还原目标目录");
                return;
            }
            
            // 创建还原选项
            EnhancedBackupService.EnhancedBackupOptions options = createEnhancedRestoreOptions();
            System.out.println("DEBUG: Options created, encrypt=" + options.isEncrypt() + ", password present=" + 
                             (options.getPassword() != null && !options.getPassword().isEmpty()));
            
            // 执行从包还原
            performEnhancedRestoreFromPackage(packagePath, target, options);
        } else {
            // 使用原有的目录还原逻辑
            System.out.println("DEBUG: Calling super.handleRestore()");
            
            // 添加安全检查以避免潜在的空指针异常
            if (restoreBackupItems == null) {
                System.out.println("DEBUG: restoreBackupItems is null");
                showAlert("错误", "还原项列表未初始化");
                return;
            }
            
            int selectedCount = 0;
            if (restoreBackupItems != null) {
                selectedCount = restoreBackupItems.stream()
                    .filter(item -> item != null)  // 过滤空项
                    .mapToInt(item -> item.isSelected() ? 1 : 0)
                    .sum();
            }
            
            System.out.println("DEBUG: Restore items count: " + restoreBackupItems.size());
            System.out.println("DEBUG: Selected items count: " + selectedCount);
            System.out.println("DEBUG: Restore target: " + (restoreTargetField != null ? restoreTargetField.getText() : "null"));
            
            super.handleRestore();
        }
    }
    
    /**
     * 创建增强还原选项
     */
    private EnhancedBackupService.EnhancedBackupOptions createEnhancedRestoreOptions() {
        EnhancedBackupService.EnhancedBackupOptions options = 
            new EnhancedBackupService.EnhancedBackupOptions(
                true,  // 还原时总是包含特殊文件记录
                restorePreserveMetadataCheck.isSelected()
            );
        
        // 设置加密密码（如果用户在还原页面输入了密码）
        if (restorePasswordField != null && restorePasswordField.getText() != null && 
            !restorePasswordField.getText().trim().isEmpty()) {
            options.setPassword(restorePasswordField.getText());
            options.setEncrypt(true);  // 标记为需要解密
            options.setEncryptionMethod(BackupPackage.EncryptionMethod.XOR); // 默认使用异或加密
        }
        
        return options;
    }
    
    /**
     * 执行从包还原
     */
    private void performEnhancedRestoreFromPackage(String packagePath, String target,
                                                  EnhancedBackupService.EnhancedBackupOptions options) {
        restoreButton.setDisable(true);
        restoreProgressBar.setVisible(true);
        restoreResultBox.setVisible(false);
        restoreStatusLabel.setText("正在从包中还原...");
        
        Task<EnhancedBackupService.EnhancedRestoreResult> restoreTask = new Task<>() {
            @Override
            protected EnhancedBackupService.EnhancedRestoreResult call() throws Exception {
                List<String> packagePaths = new ArrayList<>();
                packagePaths.add(packagePath);
                return enhancedBackupService.enhancedRestoreMultiple(packagePaths, target, options);
            }
        };
        
        restoreTask.setOnSucceeded(event -> {
            EnhancedBackupService.EnhancedRestoreResult result = restoreTask.getValue();
            String message = result.getError(); // 注意：这里error字段存储的是消息
            
            restoreStatusLabel.setText(message);
            restoreFilesCopiedLabel.setText("文件还原: " + result.getRestoredFiles());
            restoreDirectoriesCreatedLabel.setText("目录创建: 0"); // 包还原不单独统计目录
            restoreTotalSizeLabel.setText("总大小: 计算中...");
            
            // 尝试获取包信息以显示总大小
            try {
                BackupPackage.BackupManifest manifest = 
                    enhancedBackupService.getPackageInfo(packagePath);
                restoreTotalSizeLabel.setText("总大小: " + formatSize(manifest.getTotalBytes()));
            } catch (Exception e) {
                // 忽略错误
            }
            
            restoreResultBox.setVisible(true);
            restoreProgressBar.setVisible(false);
            restoreButton.setDisable(false);
            
            // 如果还原失败，显示错误对话框
            if (!result.isSuccess()) {
                showAlert("还原失败", message);
            }
        });
        
        restoreTask.setOnFailed(event -> {
            Throwable exception = restoreTask.getException();
            String errorMsg = "还原失败: " + exception.getMessage();
            
            // 检查是否是密码错误
            if (exception.getMessage() != null && 
                (exception.getMessage().contains("密码错误") || 
                 exception.getMessage().contains("未提供密码"))) {
                errorMsg = "密码错误: " + exception.getMessage();
            }
            
            restoreStatusLabel.setText(errorMsg);
            restoreProgressBar.setVisible(false);
            restoreButton.setDisable(false);
            
            // 显示错误对话框以便用户看到
            showAlert("还原失败", errorMsg);
        });
        
        Thread restoreThread = new Thread(restoreTask);
        restoreThread.setDaemon(true);
        restoreThread.start();
    }
    
    /**
     * 处理验证包按钮点击
     */
    @FXML
    private void handleVerifyPackage() {
        String packagePath = packagePathField.getText();
        if (packagePath == null || packagePath.trim().isEmpty()) {
            showAlert("错误", "请选择要验证的包文件");
            return;
        }
        
        File packageFile = new File(packagePath);
        if (!packageFile.exists()) {
            showAlert("错误", "包文件不存在: " + packagePath);
            return;
        }
        
        performPackageVerification(packagePath);
    }
    
    /**
     * 执行包验证
     */
    private void performPackageVerification(String packagePath) {
        Task<EnhancedBackupService.VerifyResult> verifyTask = new Task<>() {
            @Override
            protected EnhancedBackupService.VerifyResult call() throws Exception {
                return enhancedBackupService.verifyPackage(packagePath);
            }
        };
        
        verifyTask.setOnSucceeded(event -> {
            EnhancedBackupService.VerifyResult result = verifyTask.getValue();
            if (result.isSuccess()) {
                showAlert("验证成功", 
                    String.format("包文件验证通过\n检查文件数: %d\n失败文件数: %d",
                        result.getCheckedFiles(), result.getFailedFiles()));
            } else {
                showAlert("验证失败", result.getError());
            }
        });
        
        verifyTask.setOnFailed(event -> {
            Throwable exception = verifyTask.getException();
            showAlert("验证失败", "验证过程中发生错误: " + exception.getMessage());
        });
        
        Thread verifyThread = new Thread(verifyTask);
        verifyThread.setDaemon(true);
        verifyThread.start();
    }
    
    /**
     * 显示临时状态消息
     */
    @Override
    protected void showTempStatusMessage(Label label, String message, String color) {
        javafx.application.Platform.runLater(() -> {
            label.setText(message);
            label.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: " + color + ";");
        });
        
        // 创建守护线程来清除提示
        Thread clearThread = new Thread(() -> {
            try {
                Thread.sleep(5000);
                javafx.application.Platform.runLater(() -> {
                    if (label.getText().equals(message)) {
                        label.setText("");
                        label.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                    }
                });
            } catch (InterruptedException e) {
                // 忽略中断
            }
        });
        clearThread.setDaemon(true);
        clearThread.start();
    }
    
    /**
     * 获取舞台
     */
    @Override
    protected Stage getStage() {
        return (Stage) packageModeCheck.getScene().getWindow();
    }
}