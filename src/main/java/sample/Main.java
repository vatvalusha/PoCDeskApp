package sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.media.sound.InvalidDataException;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    private static final String TOKEN_URL = "https://login.salesforce.com/services/oauth2/token";


    private static final String client_id = "ENTER_YOUR_CLIENT_ID";
    private static final String client_secret = "ENTER_YOUR_CLIENT_SECRET";
    private static final String usernameSF = "ENTER_YOUR_SF_USERNAME";
    private static final String passwordSF = "ENTER_YOUR_PASSWORD_WITH_SECTOKEN";

    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final CloseableHttpClient httpclient = HttpClients.createDefault();
    private static HostServices hostServices;

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Proof of Concept");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        Text scenetitle = new Text("Proof of Concept");
        scenetitle.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        grid.add(scenetitle, 0, 0, 2, 1);

        Label urlName = new Label("Search param:");
        grid.add(urlName, 0, 1);

        final TextField url = new TextField();
        grid.add(url, 1, 1);
        final ComboBox<String> comboBox = new ComboBox<String>();
        comboBox.getItems().addAll("Contact                   ", "Case", "Account");
        Label b = new Label("Choose SObject:");
        grid.add(b, 0, 2);
        grid.add(comboBox, 1, 2);

        Button btn = new Button();
        btn.setText("Open");
        btn.setOnAction(new EventHandler<ActionEvent>() {

            public void handle(ActionEvent event) {
                String username = usernameSF;
                String password = passwordSF;
                String consumerKey = client_id;
                String consumerSecret = client_secret;
                String finalUrl = "%s/secur/frontdoor.jsp?sid=%s&retURL=/%s";
                String formattedString = null;
                try {
//                    Object  varName = (Object )comboBox.getItems();
                    String selected = comboBox.getValue();
                    final List<NameValuePair> loginParams = new ArrayList<NameValuePair>();
                    loginParams.add(new BasicNameValuePair("client_id", consumerKey));
                    loginParams.add(new BasicNameValuePair("client_secret", consumerSecret));
                    loginParams.add(new BasicNameValuePair("grant_type", "password"));
                    loginParams.add(new BasicNameValuePair("username", username));
                    loginParams.add(new BasicNameValuePair("password", password));

                    final HttpPost post = new HttpPost(TOKEN_URL);
                    post.setEntity(new UrlEncodedFormEntity(loginParams));

                    final HttpResponse loginResponse = httpclient.execute(post);

                    // parse
                    final JsonNode loginResult = mapper.readValue(loginResponse.getEntity().getContent(), JsonNode.class);
                    final String accessToken = loginResult.get("access_token").asText();
                    final String instanceUrl = loginResult.get("instance_url").asText();

                    String recordId = null;
                    String sessionId = getValueByQuery("SELECT Id, SessionId__c FROM TestSetting__c LIMIT 1", instanceUrl, accessToken, "SessionId__c").replaceAll("\"", "");
                    System.out.println("selected: " + selected);
                    if(selected.equals("Contact")) {
                        recordId = getValueByQuery("SELECT Id, Name, AccountId FROM Contact WHERE Phone =  '" + url.getText() + "'", instanceUrl, accessToken, "Id").replaceAll("\"", "");
                    } else if(selected.equals("Case")){
                        recordId = getValueByQuery("SELECT Id, CaseNumber FROM Case WHERE CaseNumber =  '" + url.getText() + "'", instanceUrl, accessToken, "Id").replaceAll("\"", "");
                    }else{
                        recordId = getValueByQuery("SELECT Id, Name FROM Account LIMIT 1", instanceUrl, accessToken, "Id").replaceAll("\"", "");
                    }
                    formattedString = String.format(finalUrl, instanceUrl, sessionId, recordId);
                    System.out.println(formattedString);

                } catch (Exception ex) {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setHeaderText("Input not valid");
                    errorAlert.setContentText(ex.getMessage());
                    errorAlert.showAndWait();
                } finally {

                }
                //String finalUrl = "http://[instance].salesforce.com/secur/frontdoor.jsp?sid=[access token]&retURL=/[start page]";

                new Main().getHostServices().showDocument(formattedString);
            }
        });

        HBox hbBtn = new HBox(10);
        hbBtn.setAlignment(Pos.BOTTOM_RIGHT);
        hbBtn.getChildren().add(btn);
        grid.add(hbBtn, 1, 4);

        Scene scene = new Scene(grid, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static String getValueByQuery(String query, String instanceUrl, String accessToken, String paramName) throws IOException, URISyntaxException {
        final URIBuilder builder = new URIBuilder(instanceUrl);
        builder.setPath("/services/data/v39.0/query/").setParameter("q", query);

        final HttpGet get = new HttpGet(builder.build());
        get.setHeader("Authorization", "Bearer " + accessToken);

        final HttpResponse queryResponse = httpclient.execute(get);

        final JsonNode queryResults = mapper.readValue(queryResponse.getEntity().getContent(), JsonNode.class);
        if (queryResults.get("records").get(0) == null) {
            throw new InvalidDataException("This phone number is not exist!");
        }
        System.out.println(queryResults);

        System.out.println(queryResults.get("records").get(0).get(paramName));
        return queryResults.get("records").get(0).get(paramName).toString();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
