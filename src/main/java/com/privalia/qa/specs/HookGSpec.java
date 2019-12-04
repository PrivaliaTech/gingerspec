/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privalia.qa.specs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.privalia.qa.utils.ThreadProperty;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.remote.MobileBrowserType;
import io.appium.java_client.remote.MobileCapabilityType;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.restassured.http.ContentType;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.fail;

/**
 * This class contains functions that are executed before and after each test.
 * For this, it makes use of cucumber hooks
 *
 * @author Jose Fernandez
 * @see <a href="https://cucumber.io/docs/cucumber/api/#hooks">https://cucumber.io/docs/cucumber/api/#hooks</a>
 */
public class HookGSpec extends BaseGSpec {

    public static final int PAGE_LOAD_TIMEOUT = 120;

    public static final int IMPLICITLY_WAIT = 10;

    public static final int SCRIPT_TIMEOUT = 30;

    protected WebDriver driver;

    /**
     * Default constructor.
     *
     * @param spec commonG object
     */
    public HookGSpec(CommonG spec) {
        this.commonspec = spec;
    }


    /**
     * Clean the exception list before each scenario.
     */
    @Before(order = 0)
    public void globalSetup() {
        /*Removes unnecessary logging messages that are produced by dependencies that make use of java.util.logging.Logger*/
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.WARNING);

        /*Clears the exceptions stacktrace for the new test*/
        commonspec.getExceptions().clear();
    }


    /**
     * If the feature has the @web annotation, creates a new selenium web driver
     * before each scenario
     *
     * @throws MalformedURLException MalformedURLException
     */
    @Before(order = 10, value = "@web")
    public void seleniumSetup() throws IOException {

        String grid = System.getProperty("SELENIUM_GRID");
        String b = ThreadProperty.get("browser");
        ObjectMapper mapper = new ObjectMapper();

        if (grid == null) {
            fail("Selenium grid not available");
        }

        if ("".equals(b)) {
            fail("Non available browsers");
        }

        Map<String, String> capabilitiesMap = mapper.readValue(b, Map.class);

        commonspec.setBrowserName(capabilitiesMap.get("browserName"));
        commonspec.getLogger().debug("Setting up selenium for {}", capabilitiesMap.get("browserName"));

        if (grid.matches("local")) {
            this.useLocalDriver(capabilitiesMap.get("browserName"));
        } else {
            this.useRemoteGrid(grid, capabilitiesMap);
        }

    }

    /**
     * If the feature has the @mobile annotation, creates a new Appium driver
     * before each scenario
     *
     * @throws MalformedURLException MalformedURLException
     */
    @Before(order = 20, value = "@mobile")
    public void AppiumSetup() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        String grid = System.getProperty("SELENIUM_GRID");
        String b = ThreadProperty.get("browser");

        if (grid == null) {
            fail("Selenium grid not available");
        }

        if ("".equals(b)) {
            fail("No available nodes connected");
        }

        Map<String, String> capabilitiesMap = mapper.readValue(b, Map.class);

        commonspec.setBrowserName(capabilitiesMap.get("automationName"));
        commonspec.getLogger().debug("Setting up selenium for {}", capabilitiesMap.get("automationName"));

        grid = "http://" + grid + "/wd/hub";
        MutableCapabilities capabilities = null;
        capabilities = new DesiredCapabilities();

        //This capabilities are removed since they can cause problems when testing mobile apps
        capabilitiesMap.remove("platform");
        capabilitiesMap.remove("maxInstances");
        capabilitiesMap.remove("seleniumProtocol");

        //Assign all found capabilities found returned by the selenium node
        for (Map.Entry<String, String> entry : capabilitiesMap.entrySet()) {
            capabilities.setCapability(entry.getKey(), entry.getValue());
        }

        String app = capabilitiesMap.get("app");

        if (System.getProperty("APP") != null) {
            app = System.getProperty("APP");
        }

        if (app == null) {
            fail("No app specified (The absolute local path or remote http URL of an .apk or .ipa file). You can specify this in the node capabilities or using -DAPP=/path/to/file");
        }

        capabilities.setCapability("app", app);

        switch (capabilitiesMap.get("platformName").toLowerCase()) {

            case "android":
                commonspec.setDriver(new AndroidDriver(new URL(grid), capabilities));
                break;

            case "ios":
                commonspec.setDriver(new IOSDriver(new URL(grid), capabilities));
                break;

            default:
                commonspec.getLogger().error("Unknown platform: " + capabilitiesMap.get("platformName"));
                throw new WebDriverException("Unknown platform: " + capabilitiesMap.get("platformName"));
        }

    }

    /**
     * Connects to a remote selenium grid to execute the tests
     * @param grid                      Address of the remote selenium grid
     * @param capabilitiesMap           Capabilities of the node
     * @throws MalformedURLException    MalformedURLException
     */
    private void useRemoteGrid(String grid, Map<String, String> capabilitiesMap) throws MalformedURLException {

        MutableCapabilities capabilities = null;

        grid = "http://" + grid + "/wd/hub";

        capabilitiesMap.putIfAbsent("platform", "desktop");

        switch (capabilitiesMap.get("browserName").toLowerCase()) {
            case "chrome":

                commonspec.getLogger().debug("Setting up selenium for chrome in {}", capabilitiesMap.get("platform"));

                if ("android".matches(capabilitiesMap.get("platform").toLowerCase())) {
                    //Testing in chrome for android
                    capabilities = DesiredCapabilities.android();

                } else if ("ios".matches(capabilitiesMap.get("platform").toLowerCase())) {
                    //Testing in chrome for iphone
                    capabilities = DesiredCapabilities.iphone();

                } else {
                    //Testing in desktop version of chrome
                    ChromeOptions chromeOptions = new ChromeOptions();
                    chromeOptions.addArguments("--no-sandbox");
                    chromeOptions.addArguments("--ignore-certificate-errors");
                    capabilities = new ChromeOptions();
                    capabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
                }

                break;

            case "firefox":
                capabilities = new FirefoxOptions();
                break;

            case "phantomjs":
                capabilities = DesiredCapabilities.phantomjs();
                break;

            case "safari":

                commonspec.getLogger().debug("Setting up selenium for chrome in {}", capabilitiesMap.get("platform"));

                if ("ios".matches(capabilitiesMap.get("platform").toLowerCase())) {
                    //Testing in safari for iphone
                    capabilities = DesiredCapabilities.iphone();

                } else {
                    //Testing in Safari desktop browser
                    capabilities = DesiredCapabilities.safari();
                }

                break;


            default:
                commonspec.getLogger().error("Unknown browser: " + capabilitiesMap.get("browserName"));
                throw new WebDriverException("Unknown browser: " + capabilitiesMap.get("browserName"));
        }


        //Assign all found capabilities found returned by the selenium node
        for (Map.Entry<String, String> entry : capabilitiesMap.entrySet()) {
            capabilities.setCapability(entry.getKey(), entry.getValue());
        }

        commonspec.setDriver(new RemoteWebDriver(new URL(grid), capabilities));
        this.configureWebDriver(capabilities, capabilitiesMap.get("platform"));


    }

    /**
     * Makes use of WebDriverManager to automatically download the appropriate local driver
     *
     * @param browser Browser type to use (chrome/firefox)
     */
    private void useLocalDriver(String browser) {

        MutableCapabilities capabilities = null;

        switch (browser.toLowerCase()) {
            case "chrome":
                ChromeOptions chromeOptions = new ChromeOptions();
                chromeOptions.addArguments("--no-sandbox");
                chromeOptions.addArguments("--ignore-certificate-errors");
                capabilities = new ChromeOptions();
                capabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
                System.setProperty("webdriver.chrome.silentOutput", "true"); //removes logging messages
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(chromeOptions);
                break;

            case "firefox":
                capabilities = new FirefoxOptions();
                System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE, "true"); //removes logging messages
                System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null");  //removes logging messages
                WebDriverManager.firefoxdriver().setup();
                driver = new FirefoxDriver(capabilities);
                break;

            default:
                commonspec.getLogger().error("Unknown browser: " + browser);
                throw new WebDriverException("Unknown browser: " + browser);
        }

        commonspec.setDriver(driver);
        this.configureWebDriver(capabilities, "desktop");

    }

    private void configureWebDriver(MutableCapabilities capabilities, String platform) {

        commonspec.getDriver().manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT, TimeUnit.SECONDS);
        commonspec.getDriver().manage().timeouts().implicitlyWait(IMPLICITLY_WAIT, TimeUnit.SECONDS);
        commonspec.getDriver().manage().timeouts().setScriptTimeout(SCRIPT_TIMEOUT, TimeUnit.SECONDS);

        commonspec.getDriver().manage().deleteAllCookies();
        if (capabilities.getCapability("deviceName") == null) {
            commonspec.getDriver().manage().window().setSize(new Dimension(1440, 900));
        }

        //Doing a window.maximize in mobile could cause the browser to fail
        if ((!(platform.toLowerCase().matches("android") || platform.toLowerCase().matches("ios")))) {
            commonspec.getDriver().manage().window().maximize();
        }

    }


    /**
     * If the feature has the @web or @mobile annotation, closes selenium web driver after each scenario is completed.
     */
    @After(order = 20, value = {"@web or @mobile"})
    public void seleniumTeardown() {
        if (commonspec.getDriver() != null) {
            commonspec.getLogger().debug("Shutdown Selenium client");
            //commonspec.getDriver().close(); //causes the driver instance when using firefox
            commonspec.getDriver().quit();
        }
    }

    /**
     * Close logger.
     */
    @After(order = 0)
    public void teardown() {
    }

    /**
     * If the feature has the @rest annotation, creates a new REST client before each scenario
     *
     * @throws Exception Exception
     */
    @Before(order = 10, value = "@rest")
    public void restClientSetup() throws Exception {
        commonspec.getLogger().debug("Starting a REST client");

        commonspec.setClient(new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setAcceptAnyCertificate(true).setAllowPoolingConnections(false)
                .build()));

        commonspec.setRestRequest(given().contentType(ContentType.JSON));

    }

    /**
     * If the feature has the @rest annotation, closes the REST client after each scenario is completed
     *
     * @throws IOException IOException
     */
    @After(order = 10, value = "@rest")
    public void restClientTeardown() throws IOException {
        commonspec.getLogger().debug("Shutting down REST client");
        commonspec.getClient().close();

    }

    /**
     * Disconnect any remaining open SSH connection after each scenario is completed
     *
     * @throws Exception Exception
     */
    @After(order = 10)
    public void remoteSSHConnectionTeardown() throws Exception {
        if (commonspec.getRemoteSSHConnection() != null) {
            commonspec.getLogger().debug("Closing SSH remote connection");
            commonspec.getRemoteSSHConnection().getSession().disconnect();
        }
    }

    /**
     * If the feature has the @sql annotation, closes any open connection to a database after each scenario is completed
     *
     * @throws Exception Exception
     */
    @After(value = "@sql")
    public void sqlConnectionClose() throws Exception {
        if ((commonspec.getSqlClient() != null) && (commonspec.getSqlClient().connectionStatus())) {
            commonspec.getLogger().debug("Closing SQL remote connection");
            commonspec.getSqlClient().disconnect();
        }
    }
}
