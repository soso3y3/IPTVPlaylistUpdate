import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Selenide.*;

public class UpdateList {

    String vipDriveEmail = null;
    String vipDriverUserName = "soso3y3";
    String vipDrivePass = "DoNotChange";
    String verificationCode = null;
    String url = "https://tvlider.net";
    String downloadDir = "Playlist";
    //    List<String> channelGroups = Arrays.asList("1", "2", "4", "6", "7", "9", "12", "17", "24");
    List<String> channelGroupsToUnselect = Arrays.asList("3", "5", "10", "11", "16", "18", "19", "20", "21", "22", "23");

    @BeforeClass
    public void beforeClass() {
        Random rand = new Random();
        vipDriveEmail = "IPTVtestjava+" + rand.nextInt(99999999) + "@gmail.com";
        Configuration.downloadsFolder = "build/downloads";
        Configuration.timeout = 30000;
        System.out.println(vipDriveEmail);
    }

    @AfterClass
    public void afterClass() {
        closeWindow();
    }

    @Test(priority = 1)
    public void registerAccountOnVIPDriverNet() {
        System.out.println("Registering new account...");
        open(url + "/auth/signup");
        $("input[name='username']").sendKeys(vipDriverUserName);
        $("input[name='email']").sendKeys(vipDriveEmail);
        $("input[name='password']").sendKeys(vipDrivePass);
        $("input[name='repassword']").sendKeys(vipDrivePass);
        $("button[type='submit']").click();
    }

    @Test(priority = 2, dependsOnMethods = "registerAccountOnVIPDriverNet")
    public void getVerificationCodeFromGmail() {
        System.out.println("Retrieving Verification Code from the Email...");
        Selenide.sleep(60000);
        try {
            //create properties field
            Properties properties = new Properties();

            properties.put("mail.pop3.host", "pop.gmail.com");
            properties.put("mail.pop3.port", "995");
            properties.put("mail.pop3.starttls.enable", "true");

            Session emailSession = Session.getDefaultInstance(properties);

            //create the POP3 store object and connect with the pop server
            Store store = emailSession.getStore("pop3s");

            store.connect("pop.gmail.com", "IPTVtestjava", "klmbxlbqmzrxvqbe");

            //create the folder object and open it
            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_ONLY);

            // retrieve the messages from the folder in an array and print it
            Message[] messages = emailFolder.getMessages();
            int counter = 0;
            while (messages.length < 1 & counter < 3) {
                Selenide.sleep(15000);
                counter++;
            }
            for (Message msg : messages) {
                String bodyText = getTextFromMessage(msg);
//                System.out.println(bodyText);
                if (msg.getFrom()[0].toString().equals("support <support@tvlider.net>")) {
                    Pattern pattern = Pattern.compile("[-\\sa-zA-Z0-9]{11}");
                    Matcher matcher = pattern.matcher(bodyText);
                    if (matcher.find()) {
//                        System.out.println(matcher.group());
                        StringBuilder sb = new StringBuilder(matcher.group());
                        sb.delete(0, 2);
                        verificationCode = sb.toString();
                    }
                    break;
                }
            }
            //close the store and folder objects
            emailFolder.close(false);
            store.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(verificationCode);
    }

    @Test(priority = 3, dependsOnMethods = {"registerAccountOnVIPDriverNet"})
    public void authEmail() {
        getVerificationCodeFromGmail();
        $("input[name='email']").sendKeys(vipDriveEmail);
        $("input[name='code']").sendKeys(verificationCode);
        $("button[type='submit']").click();
        Selenide.sleep(2000);
        if($("div[class*='uk-notification']").isDisplayed() &
                $("div[class*='uk-notification']").getText().equals("Неверный код подтверждения")) {
            System.out.println("Retrieving Verification Code from the Email again...");
            Selenide.sleep(10000);
            getVerificationCodeFromGmail();
            $("input[name='email']").sendKeys(vipDriveEmail);
            $("input[name='code']").sendKeys(verificationCode);
            $("button[type='submit']").click();
        }
    }

    @Test(dependsOnMethods = {"registerAccountOnVIPDriverNet", "authEmail"})
    public void downloadList_VIPDriveNet() {
        System.out.println("Opening URL...");
        System.out.println("Authorization in progress...");
        $("input[name='email']").sendKeys(vipDriveEmail);
        $("input[name='password']").sendKeys(vipDrivePass);
        $("button[type='submit']").click();
        System.out.println("Opening Channel Groups...");
        open(url + "/playlist/groups");
        System.out.println("Selecting Channel groups...");
        for (String channelGroup : channelGroupsToUnselect) {
            $("input[type='checkbox'][value='" + channelGroup + "']").setSelected(false);
        }
        System.out.println("Saving...");
        $("button[type='submit']").click(ClickOptions.usingJavaScript());
        System.out.println("Downloading Channel list...");
        open(url + "/playlist/download");
        String firstURL = $("div[id='pllink']").getText();
        $("button[id='resetPlSrc']").click();
        while(firstURL.equals($("div[id='pllink']").getText())) {
            Selenide.sleep(5000);
            $("button[id='resetPlSrc']").click();
        }

        try {
            URL url = new URL($("div[id='pllink']").getText());
            File destination_file = new File(downloadDir +"\\playlist.m3u8");
            FileUtils.copyURLToFile(url, destination_file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Selenide.sleep(5000);
        boolean downloadedFile = true;
        while (downloadedFile) {
            if (Files.exists(Path.of(downloadDir +"\\playlist.m3u8")))
                downloadedFile = false;
        }
    }
    @Test()
    public void uploadChannelListToFTP() {
        String server = "files.000webhost.com";
        int port = 21;
        String user = "fa-training";
        String pass = "gaajvi123321";

        System.out.println("Uploading file to FTP...");
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            ftpClient.login(user, pass);
            ftpClient.enterLocalPassiveMode();

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            File firstLocalFile = new File(System.getProperty("user.dir")+ "/Playlist/playlist.m3u8");

            InputStream inputStream = new FileInputStream(firstLocalFile);

            System.out.println("Start uploading first file");
            boolean done = ftpClient.storeFile("public_html/playlist.m3u8", inputStream);
            inputStream.close();
            if (done) {
                System.out.println("The file is uploaded successfully.");
                System.out.println("Link to file -> https://fa-training.000webhostapp.com/playlist.m3u8");
            } else
                Assert.fail();

        } catch (IOException ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
            Assert.fail();
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String getTextFromMessage(Message message) throws MessagingException, IOException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
            if (bodyPart.isMimeType("text/plain")) {
                result.append("\n").append(bodyPart.getContent());
                break; // without break same text appears twice in my tests
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result.append("\n").append(org.jsoup.Jsoup.parse(html).text());
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }
        return result.toString();
    }
}
