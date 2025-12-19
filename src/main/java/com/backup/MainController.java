package com.backup;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
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

public class MainController {
    
    @FXML
    private ListView<String> sourceListView;
    
    @FXML
    private TextField targetField;
    
    private ObservableList<String> sourcePaths;
    
    @FXML
    private Button sourceButton;
    
    @FXML
    private Button targetButton;
    
    @FXML
    private Button backupButton;
    
    @FXML
    private CheckBox includeSpecialFilesCheck;
    
    @FXML
    private CheckBox preserveMetadataCheck;
    
    @FXML
    private ProgressBar progressBar;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private VBox resultBox;
    
    @FXML
    private Label filesCopiedLabel;
    
    @FXML
    private Label directoriesCreatedLabel;
    
    @FXML
    private Label totalSizeLabel;
    
    @FXML
    private ListView<BackupItem> restoreSourceListView;
    
    @FXML
    private TextField restoreTargetField;
    
    @FXML
    private Button selectAllRestoreButton;
    
    @FXML
    private Button removeRestoreSelectedButton;
    
    @FXML
    private Button refreshRestoreButton;
    
    private ObservableList<BackupItem> restoreBackupItems;
    
    @FXML
    private Button restoreButton;
    
    @FXML
    private CheckBox restorePreserveMetadataCheck;
    
    @FXML
    private ProgressBar restoreProgressBar;
    
    @FXML
    private Label restoreStatusLabel;
    
    @FXML
    private VBox restoreResultBox;
    
    @FXML
    private Label restoreFilesCopiedLabel;
    
    @FXML
    private Label restoreDirectoriesCreatedLabel;
    
    @FXML
    private Label restoreTotalSizeLabel;
    
    private BackupService backupService;
    
    @FXML
    public void initialize() {
        backupService = new BackupService();
        
        sourcePaths = FXCollections.observableArrayList();
        sourceListView.setItems(sourcePaths);
        resultBox.setVisible(false);
        progressBar.setVisible(false);
        
        restoreBackupItems = FXCollections.observableArrayList();
        restoreSourceListView.setItems(restoreBackupItems);
        restoreResultBox.setVisible(false);
        restoreProgressBar.setVisible(false);
        
        // 修复复选框功能，避免监听器重复添加
        restoreSourceListView.setCellFactory(listView -> new javafx.scene.control.ListCell<BackupItem>() {
            private final javafx.scene.control.CheckBox checkBox = new javafx.scene.control.CheckBox();
            private BackupItem currentItem = null;
            private javafx.beans.value.ChangeListener<Boolean> checkboxListener = null;
            private javafx.beans.value.ChangeListener<Boolean> itemListener = null;
            
            {
                setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                setGraphic(checkBox);
                setText(null);
            }
            
            @Override
            protected void updateItem(BackupItem item, boolean empty) {
                super.updateItem(item, empty);
                
                // 移除旧item的监听器
                if (currentItem != null && itemListener != null) {
                    currentItem.selectedProperty().removeListener(itemListener);
                    itemListener = null;
                }
                
                // 移除复选框的旧监听器
                if (checkboxListener != null) {
                    checkBox.selectedProperty().removeListener(checkboxListener);
                    checkboxListener = null;
                }
                
                currentItem = item;
                
                if (empty || item == null) {
                    setText(null);
                    checkBox.setText(null);
                    checkBox.setSelected(false);
                    setGraphic(null);
                } else {
                    setText(item.getPath());
                    checkBox.setText(null);
                    
                    // 检查文件是否存在
                    java.io.File file = new java.io.File(item.getPath());
                    if (file.exists()) {
                        setGraphic(checkBox);
                        // 同步复选框状态
                        checkBox.setSelected(item.isSelected());
                        
                        // 监听复选框变化 -> 更新item
                        checkboxListener = (obs, oldVal, newVal) -> {
                            if (item.isSelected() != newVal) {
                                item.setSelected(newVal);
                            }
                        };
                        checkBox.selectedProperty().addListener(checkboxListener);
                        
                        // 监听item状态变化 -> 更新复选框
                        itemListener = (obs, oldVal, newVal) -> {
                            if (checkBox.isSelected() != newVal) {
                                checkBox.setSelected(newVal);
                            }
                        };
                        item.selectedProperty().addListener(itemListener);
                    } else {
                        setGraphic(null);
                        item.setSelected(false);
                    }
                }
            }
        });
        
        // 在后台线程中加载还原项列表，避免阻塞UI初始化
        javafx.concurrent.Task<Void> loadRestoreTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    List<String> restoreItems = backupService.getAvailableRestoreItems();
                    javafx.application.Platform.runLater(() -> {
                        try {
                            for (String path : restoreItems) {
                                restoreBackupItems.add(new BackupItem(path));
                            }
                            if (restoreBackupItems.isEmpty()) {
                                restoreStatusLabel.setText("没有备份记录，请先进行备份操作");
                            } else {
                                restoreStatusLabel.setText("已加载 " + restoreBackupItems.size() + " 个备份记录");
                            }
                        } catch (Exception e) {
                            System.err.println("UI更新错误: " + e.getMessage());
                            restoreStatusLabel.setText("加载备份记录完成");
                        }
                    });
                } catch (Exception e) {
                    System.err.println("加载还原项错误: " + e.getMessage());
                    javafx.application.Platform.runLater(() -> {
                        restoreStatusLabel.setText("加载备份记录完成");
                    });
                }
                return null;
            }
        };
        
        Thread loadThread = new Thread(loadRestoreTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }
    
    @FXML
    private void handleAddFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要备份的源文件夹");
        File selected = chooser.showDialog(getStage());
        if (selected != null) {
            String path = selected.getAbsolutePath();
            String displayText = "[文件夹] " + path;
            if (!sourcePaths.contains(displayText)) {
                sourcePaths.add(displayText);
            } else {
                // 使用状态标签显示提示
                showTempStatusMessage(statusLabel, "该路径已存在", "#e74c3c");
            }
        }
    }
    
    @FXML
    private void handleAddFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要备份的文件");
        File selected = chooser.showOpenDialog(getStage());
        if (selected != null) {
            String path = selected.getAbsolutePath();
            String displayText = "[文件] " + path;
            if (!sourcePaths.contains(displayText)) {
                sourcePaths.add(displayText);
            } else {
                // 使用状态标签显示提示
                statusLabel.setText("该路径已存在");
                statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
                
                // 3秒后清除提示
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        javafx.application.Platform.runLater(() -> {
                            if (statusLabel.getText().equals("该路径已存在")) {
                                statusLabel.setText("");
                                statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                            }
                        });
                    } catch (InterruptedException e) {
                        // 忽略中断
                    }
                }).start();
            }
        }
    }
    
    @FXML
    private void handleRemoveSource() {
        String selected = sourceListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            sourcePaths.remove(selected);
        }
    }
    
    @FXML
    private void handleSelectTarget() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择备份目标目录");
        File selected = chooser.showDialog(getStage());
        if (selected != null) {
            targetField.setText(selected.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleBackup() {
        String target = targetField.getText();
        
        if (sourcePaths.isEmpty()) {
            // 使用状态标签显示提示
            statusLabel.setText("请至少添加一个要备份的路径");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
            
            // 3秒后清除提示
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> {
                        if (statusLabel.getText().equals("请至少添加一个要备份的路径")) {
                            statusLabel.setText("");
                            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                        }
                    });
                } catch (InterruptedException e) {
                    // 忽略中断
                }
            }).start();
            return;
        }
        
        if (target.isEmpty()) {
            // 使用状态标签显示提示
            statusLabel.setText("请选择目标目录");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
            
            // 3秒后清除提示
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> {
                        if (statusLabel.getText().equals("请选择目标目录")) {
                            statusLabel.setText("");
                            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                        }
                    });
                } catch (InterruptedException e) {
                    // 忽略中断
                }
            }).start();
            return;
        }
        
        for (String source : sourcePaths) {
            if (source.equals(target)) {
                // 使用状态标签显示提示
                statusLabel.setText("源路径和目标目录不能相同: " + source);
                statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
                
                // 3秒后清除提示
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        javafx.application.Platform.runLater(() -> {
                            if (statusLabel.getText().equals("源路径和目标目录不能相同: " + source)) {
                                statusLabel.setText("");
                                statusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                            }
                        });
                    } catch (InterruptedException e) {
                        // 忽略中断
                    }
                }).start();
                return;
            }
        }
        
        performBackup(target);
    }
    
    private void performBackup(String target) {
        backupButton.setDisable(true);
        progressBar.setVisible(true);
        resultBox.setVisible(false);
        statusLabel.setText("正在备份...");
        
        // 保存实际路径，以便在备份成功后添加还原项
        final ArrayList<String> actualPaths = new ArrayList<>();
        for (String displayPath : sourcePaths) {
            String actualPath = displayPath.substring(displayPath.indexOf("] ") + 2);
            actualPaths.add(actualPath);
        }
        
        // 创建备份选项
        final BackupService.BackupOptions options = new BackupService.BackupOptions(
            includeSpecialFilesCheck.isSelected(),
            preserveMetadataCheck.isSelected()
        );
        
        Task<BackupService.BackupResult> backupTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                return backupService.backupMultiple(actualPaths, target, options);
            }
        };
        
        backupTask.setOnSucceeded(event -> {
            BackupService.BackupResult result = backupTask.getValue();
            statusLabel.setText(result.getMessage());
            filesCopiedLabel.setText("文件复制: " + result.getFilesCopied());
            directoriesCreatedLabel.setText("目录创建: " + result.getDirectoriesCreated());
            totalSizeLabel.setText("总大小: " + formatSize(result.getTotalSize()));
            resultBox.setVisible(true);
            progressBar.setVisible(false);
            backupButton.setDisable(false);
            
            // 备份成功后清空备份列表，方便进行新一轮备份
            sourcePaths.clear();
            
            // 备份成功后添加还原项到持久化存储
            try {
                // 获取成功备份的路径列表
                List<String> successfulPaths = result.getSuccessfulPaths();
                
                // 为每个成功备份的路径添加还原项
                for (String sourcePath : successfulPaths) {
                    Path sourcePathObj = Paths.get(sourcePath);
                    Path targetPathObj = Paths.get(target);
                    
                    // 计算备份文件的路径
                    Path backupPath = targetPathObj.resolve(sourcePathObj.getFileName());
                    if (Files.exists(backupPath)) {
                        backupService.addRestoreItem(backupPath.toString());
                    }
                }
            } catch (Exception e) {
                // 添加还原项失败，但不影响备份操作
                System.err.println("添加还原项失败: " + e.getMessage());
            }
            
            // 备份成功后刷新还原列表
            refreshRestoreList();
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
    
    
    
    @FXML
    private void handleSelectRestoreTarget() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择还原目标目录");
        File selected = chooser.showDialog(getStage());
        if (selected != null) {
            restoreTargetField.setText(selected.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleSelectAllRestore() {
        boolean allSelected = true;
        // 检查是否已经全选
        for (BackupItem item : restoreBackupItems) {
            if (!item.isSelected()) {
                allSelected = false;
                break;
            }
        }
        
        // 如果已经全选，则取消全选；否则全选
        boolean newState = !allSelected;
        for (BackupItem item : restoreBackupItems) {
            item.setSelected(newState);
        }
    }
    
    @FXML
    private void handleRefreshRestoreList() {
        refreshRestoreList();
    }
    
    @FXML
    private void handleRemoveRestoreSelected() {
        // 创建要删除的项目列表
        List<BackupItem> itemsToRemove = new ArrayList<>();
        for (BackupItem item : restoreBackupItems) {
            if (item.isSelected()) {
                itemsToRemove.add(item);
            }
        }
        
        if (itemsToRemove.isEmpty()) {
            // 使用状态标签显示提示，而不是弹出对话框
            restoreStatusLabel.setText("请先勾选要删除的备份项");
            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
            
            // 3秒后清除提示
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> {
                        if (restoreStatusLabel.getText().equals("请先勾选要删除的备份项")) {
                            restoreStatusLabel.setText("");
                            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                        }
                    });
                } catch (InterruptedException e) {
                    // 忽略中断
                }
            }).start();
            return;
        }
        
        // 从持久化存储中删除选中的项目
        try {
            for (BackupItem item : itemsToRemove) {
                backupService.removeRestoreItem(item.getPath());
            }
            
            // 从UI列表中删除选中的项目
            restoreBackupItems.removeAll(itemsToRemove);
            restoreStatusLabel.setText("已删除选中的备份项");
            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #27ae60;");
            
        } catch (IOException e) {
            restoreStatusLabel.setText("删除备份项失败: " + e.getMessage());
            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
        }
        
        // 3秒后清除提示
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                javafx.application.Platform.runLater(() -> {
                    if (restoreStatusLabel.getText().equals("已删除选中的备份项") || 
                        restoreStatusLabel.getText().startsWith("删除备份项失败")) {
                        restoreStatusLabel.setText("");
                        restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                    }
                });
            } catch (InterruptedException e) {
                // 忽略中断
            }
        }).start();
    }
    
    @FXML
    private void handleRestore() {
        String target = restoreTargetField.getText();
        
        // 检查是否有选中的备份项
        boolean hasSelected = false;
        for (BackupItem item : restoreBackupItems) {
            if (item.isSelected()) {
                hasSelected = true;
                break;
            }
        }
        if (!hasSelected) {
            // 使用状态标签显示提示
            restoreStatusLabel.setText("请至少勾选一个要还原的备份文件/文件夹");
            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
            
            // 3秒后清除提示
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> {
                        if (restoreStatusLabel.getText().equals("请至少勾选一个要还原的备份文件/文件夹")) {
                            restoreStatusLabel.setText("");
                            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                        }
                    });
                } catch (InterruptedException e) {
                    // 忽略中断
                }
            }).start();
            return;
        }
        
        if (target.isEmpty()) {
            // 使用状态标签显示提示
            restoreStatusLabel.setText("请选择还原目标目录");
            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #e74c3c;");
            
            // 3秒后清除提示
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    javafx.application.Platform.runLater(() -> {
                        if (restoreStatusLabel.getText().equals("请选择还原目标目录")) {
                            restoreStatusLabel.setText("");
                            restoreStatusLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                        }
                    });
                } catch (InterruptedException e) {
                    // 忽略中断
                }
            }).start();
            return;
        }
        
        performRestore(target);
    }
    
    private void performRestore(String target) {
        restoreButton.setDisable(true);
        restoreProgressBar.setVisible(true);
        restoreResultBox.setVisible(false);
        restoreStatusLabel.setText("正在还原...");
        
        // 创建还原选项
        final BackupService.BackupOptions options = new BackupService.BackupOptions(
            true,  // 还原时总是包含特殊文件记录
            restorePreserveMetadataCheck.isSelected()
        );
        
        Task<BackupService.BackupResult> restoreTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                ArrayList<String> actualPaths = new ArrayList<>();
                for (BackupItem item : restoreBackupItems) {
                    if (item.isSelected()) {
                        actualPaths.add(item.getPath());
                    }
                }
                return backupService.restoreMultiple(actualPaths, target, options);
            }
        };
        
        restoreTask.setOnSucceeded(event -> {
            BackupService.BackupResult result = restoreTask.getValue();
            restoreStatusLabel.setText(result.getMessage());
            restoreFilesCopiedLabel.setText("文件还原: " + result.getFilesCopied());
            restoreDirectoriesCreatedLabel.setText("目录创建: " + result.getDirectoriesCreated());
            restoreTotalSizeLabel.setText("总大小: " + formatSize(result.getTotalSize()));
            restoreResultBox.setVisible(true);
            restoreProgressBar.setVisible(false);
            restoreButton.setDisable(false);
        });
        
        restoreTask.setOnFailed(event -> {
            Throwable exception = restoreTask.getException();
            restoreStatusLabel.setText("还原失败: " + exception.getMessage());
            restoreProgressBar.setVisible(false);
            restoreButton.setDisable(false);
        });
        
        Thread restoreThread = new Thread(restoreTask);
        restoreThread.setDaemon(true);
        restoreThread.start();
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void refreshRestoreList() {
        try {
            List<String> restoreItems = backupService.getAvailableRestoreItems();
            Set<String> existingPaths = new HashSet<>();
            
            // 收集现有路径
            for (BackupItem item : restoreBackupItems) {
                existingPaths.add(item.getPath());
            }
            
            // 添加新的还原项
            for (String path : restoreItems) {
                if (!existingPaths.contains(path)) {
                    restoreBackupItems.add(new BackupItem(path));
                }
            }
            
            // 检查并删除不存在的文件（这些文件在getAvailableRestoreItems中已经被过滤掉了）
            // 所以这里只需要处理UI列表中可能存在的无效项
            List<BackupItem> itemsToRemove = new ArrayList<>();
            for (BackupItem item : restoreBackupItems) {
                File file = new File(item.getPath());
                if (!file.exists()) {
                    itemsToRemove.add(item);
                }
            }
            if (!itemsToRemove.isEmpty()) {
                restoreBackupItems.removeAll(itemsToRemove);
            }
            
            // 更新状态标签
            if (restoreBackupItems.isEmpty()) {
                restoreStatusLabel.setText("没有备份记录，请先进行备份操作");
            } else {
                restoreStatusLabel.setText("已加载备份记录");
            }
        } catch (IOException e) {
            restoreStatusLabel.setText("刷新备份记录失败: " + e.getMessage());
        }
    }
    
    private Stage getStage() {
        return (Stage) sourceListView.getScene().getWindow();
    }
    
    private static class BackupItem {
        private final String path;
        private final javafx.beans.property.BooleanProperty selected;
        
        public BackupItem(String path) {
            this.path = path;
            this.selected = new javafx.beans.property.SimpleBooleanProperty(false);
        }
        
        public String getPath() { return path; }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }
        
        @Override
        public String toString() {
            return path;
        }
    }
    
    private void showTempStatusMessage(Label label, String message, String color) {
        javafx.application.Platform.runLater(() -> {
            label.setText(message);
            label.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: " + color + ";");
        });
        
        // 创建守护线程来清除提示
        Thread clearThread = new Thread(() -> {
            try {
                Thread.sleep(3000);
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
}
