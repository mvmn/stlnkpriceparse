package x.mvmn.util.stlnkpriceparse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.gson.GsonBuilder;

public class StarlinkPriceParser {

    private final ChromeDriver chrome;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Parameters: <chromedriver path> <addresses file> [<Chrome path>]");
            return;
        }

        List<String> addresses = FileUtils.readLines(new File(args[1]), StandardCharsets.UTF_8);

        System.setProperty("webdriver.chrome.driver", args[0]);

        ChromeOptions chromeOptions = new ChromeOptions().addArguments("--disable-blink-features=AutomationControlled");
        if (args.length > 2) {
            chromeOptions.setBinary(args[2]);
        }
        ChromeDriver chrome = new ChromeDriver(chromeOptions);

        try {
            Map<String, Map<String, String>> results = new LinkedHashMap<>();
            StarlinkPriceParser parser = new StarlinkPriceParser(chrome);
            for (String address : addresses) {
                if (!address.trim().isEmpty()) {
                    try {
                        results.put(address, parser.getPrices(address));
                    } catch (Exception e) {
                        System.err.println("Failed to parse for " + address);
                        e.printStackTrace();
                    }
                }
            }
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } finally {
            chrome.quit();
        }
    }

    public StarlinkPriceParser(ChromeDriver chrome) {
        this.chrome = chrome;
    }

    public Map<String, String> getPrices(String address) throws InterruptedException {
        chrome.get("https://www.starlink.com");
        chrome.findElement(By.id("service-input")).sendKeys(address);

        Thread.sleep(2000);

        try {
            WebDriverWait wait = new WebDriverWait(chrome, Duration.ofSeconds(30L));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".address-results-wrapper")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<WebElement> addressResults = chrome
                .findElements(By.cssSelector(".address-results-wrapper a.address-result"));
        if (addressResults != null && !addressResults.isEmpty()) {
            WebElement addressOption = addressResults.get(0);
            System.err.println(address + " => " + addressOption.getText());
            addressOption.click();
        }
        Thread.sleep(1000);
        // Translate in XPath is the poor man's "to lower case"
        chrome.findElement(By.xpath(
                "//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'order now')]"))
                .click();

        Thread.sleep(2000);
        WebDriverWait wait = new WebDriverWait(chrome, java.time.Duration.ofSeconds(30L));
        wait.until(webDriver -> {
            boolean result = false;
            try {
                List<WebElement> hardwareLine = webDriver.findElements(By.xpath(
                        "//*[contains(@class, 'price-details')]//div[contains(@class, 'order-line')]//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'hardware')]/parent::*/parent::*"));
                if (!hardwareLine.isEmpty()) {
                    Optional<WebElement> webElem = hardwareLine.get(0)
                            .findElements(By.xpath("//div[not(*)]"))
                            .stream()
                            .filter(el -> el.getText().matches(".*\\d+$"))
                            .findAny();
                    if (webElem.isPresent()) {
//                        System.err
//                                .println("Price tag :" + webElem.get().getTagName() + " // " + webElem.get().getText());
                        return true;
                    }
                }
            } catch (StaleElementReferenceException sere) {
                return false;
            }
            return result;
        });
        // Just in case
        Thread.sleep(1000);
        List<WebElement> orderLineDescriptionElements = chrome
                .findElements(By.cssSelector(".price-details div.order-line div.line-description"));

        Map<String, String> results = new LinkedHashMap<>();
        orderLineDescriptionElements.stream()
                .filter(it -> !it.getText().trim().isEmpty())
                .forEach(it -> results.put(it.getText(), getNextSiebling(it).getText()));
        System.err.println(address + ":\n\t" + results);
        return results;
    }

    private static final WebElement getNextSiebling(WebElement webElement) {
        return webElement.findElement(org.openqa.selenium.By.xpath("following-sibling::*[1]"));
    }
}
