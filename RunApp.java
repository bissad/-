import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class RunApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main-view.fxml"));
        Parent root = loader.load();
        
        primaryStage.setTitle("文件备份软件 - 特殊文件支持版");
        primaryStage.setScene(new Scene(root, 900, 750));
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}