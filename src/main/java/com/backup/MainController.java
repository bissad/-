package com.backup;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
    
    private ObservableList<BackupItem> restoreBackupItems;
    
    @FXML
    private Button restoreButton;
    
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
        
        // 设置复选框单元格工厂 - 使用自定义ListCell实现
        restoreSourceListView.setCellFactory(listView -> new javafx.scene.control.ListCell<BackupItem>() {
            private final javafx.scene.control.CheckBox checkBox = new javafx.scene.control.CheckBox();
            private BackupItem currentItem = null;
            private javafx.beans.value.ChangeListener<Boolean> itemSelectionListener = null;
            
            {
                setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                setGraphic(checkBox);
                setText(null);
            }
            
            @Override
            protected void updateItem(BackupItem item, boolean empty) {
                super.updateItem(item, empty);
                
                // 移除旧item的监听器
                if (currentItem != null && itemSelectionListener != null) {
                    currentItem.selectedProperty().removeListener(itemSelectionListener);
                    itemSelectionListener = null;
                }
                
                // 移除复选框的旧监听器
                checkBox.selectedProperty().removeListener(this::handleCheckBoxChange);
                
                currentItem = item;
                
                if (empty || item == null) {
                    setText(null);
                    checkBox.setText(null);
                    checkBox.setSelected(false);
                    setGraphic(null); // 清空图形
                } else {
                    setText(item.getPath());
                    checkBox.setText(null); // 不在复选框内显示文本，文本显示在单元格中
                    
                    // 检查文件是否存在，如果不存在则不显示复选框
                    java.io.File file = new java.io.File(item.getPath());
                    if (file.exists()) {
                        setGraphic(checkBox);
                        // 同步初始状态
                        checkBox.setSelected(item.isSelected());
                        
                        // 监听复选框状态变化 -> 更新item
                        checkBox.selectedProperty().addListener(this::handleCheckBoxChange);
                        
                        // 监听item状态变化 -> 更新复选框
                        itemSelectionListener = (obs, oldVal, newVal) -> {
                            if (checkBox.isSelected() != newVal) {
                                checkBox.setSelected(newVal);
                            }
                        };
                        item.selectedProperty().addListener(itemSelectionListener);
                    } else {
                        // 文件不存在，不显示复选框
                        setGraphic(null);
                        item.setSelected(false); // 确保未选中
                    }
                }
            }
            
            private void handleCheckBoxChange(javafx.beans.value.ObservableValue<? extends Boolean> obs, Boolean oldVal, Boolean newVal) {
                if (currentItem != null && currentItem.isSelected() != newVal) {
                    currentItem.setSelected(newVal);
                }
            }
        });
        
        // 加载备份文件列表
        try {
            List<String> backupSources = backupService.getAvailableBackupSources();
            for (String path : backupSources) {
                File file = new File(path);
                if (file.exists()) {
                    restoreBackupItems.add(new BackupItem(path));
                }
            }
            if (restoreBackupItems.isEmpty()) {
                restoreStatusLabel.setText("没有备份记录，请先进行备份操作");
            }
        } catch (IOException e) {
            restoreStatusLabel.setText("加载备份记录失败: " + e.getMessage());
        }
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
                showAlert("提示", "该路径已存在");
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
                showAlert("提示", "该路径已存在");
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
            showAlert("错误", "请至少添加一个要备份的路径");
            return;
        }
        
        if (target.isEmpty()) {
            showAlert("错误", "请选择目标目录");
            return;
        }
        
        for (String source : sourcePaths) {
            if (source.equals(target)) {
                showAlert("错误", "源路径和目标目录不能相同: " + source);
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
        
        Task<BackupService.BackupResult> backupTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                ArrayList<String> actualPaths = new ArrayList<>();
                for (String displayPath : sourcePaths) {
                    String actualPath = displayPath.substring(displayPath.indexOf("] ") + 2);
                    actualPaths.add(actualPath);
                }
                return backupService.backupMultiple(actualPaths, target);
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
            
            // 备份成功后刷新还原列表
            refreshRestoreList();
        });
        
        backupTask.setOnFailed(event -> {
            Throwable exception = backupTask.getException();
            statusLabel.setText("备份失败: " + exception.getMessage());
            progressBar.setVisible(false);
            backupButton.setDisable(false);
        });
        
        new Thread(backupTask).start();
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
    private void handleRemoveRestoreSelected() {
        // 创建要删除的项目列表
        List<BackupItem> itemsToRemove = new ArrayList<>();
        for (BackupItem item : restoreBackupItems) {
            if (item.isSelected()) {
                itemsToRemove.add(item);
            }
        }
        
        if (itemsToRemove.isEmpty()) {
            showAlert("提示", "请先勾选要删除的备份项");
            return;
        }
        
        // 删除选中的项目
        restoreBackupItems.removeAll(itemsToRemove);
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
            showAlert("错误", "请至少勾选一个要还原的备份文件/文件夹");
            return;
        }
        
        if (target.isEmpty()) {
            showAlert("错误", "请选择还原目标目录");
            return;
        }
        
        performRestore(target);
    }
    
    private void performRestore(String target) {
        restoreButton.setDisable(true);
        restoreProgressBar.setVisible(true);
        restoreResultBox.setVisible(false);
        restoreStatusLabel.setText("正在还原...");
        
        Task<BackupService.BackupResult> restoreTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                ArrayList<String> actualPaths = new ArrayList<>();
                for (BackupItem item : restoreBackupItems) {
                    if (item.isSelected()) {
                        actualPaths.add(item.getPath());
                    }
                }
                return backupService.restoreMultiple(actualPaths, target);
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
        
        new Thread(restoreTask).start();
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
            List<String> backupSources = backupService.getAvailableBackupSources();
            Set<String> existingPaths = new HashSet<>();
            
            // 收集现有路径
            for (BackupItem item : restoreBackupItems) {
                existingPaths.add(item.getPath());
            }
            
            // 添加新的备份源
            for (String path : backupSources) {
                if (!existingPaths.contains(path)) {
                    restoreBackupItems.add(new BackupItem(path));
                }
            }
            
            // 检查并删除不存在的文件
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
}
