package com.privalia.qa.specs;

import com.jayway.jsonpath.PathNotFoundException;
import com.privalia.qa.utils.ThreadProperty;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import gherkin.formatter.model.DataTableRow;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

import io.restassured.response.ResponseBody;
import io.restassured.specification.RequestSpecification;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class that contains all steps related to RESTFull API testing. This class rewrites some of the already existing steps from using com.ning.async-http-client to
 * the new io.rest-assured.rest-assured
 * @author Jose Fernandez
 */
public class RestSpec extends BaseGSpec {

    public RestSpec(CommonG spec) {
        this.commonspec = spec;
    }

    /**
     * Set app host and port {@code host, @code port}. This parameters will be used for all future
     * requests in the same scenario.
     *
     * The rest request is build within the {@link HookGSpec} class, so, dont forget to use the
     * @rest annotation at the beginning of your feature for a proper initialization.
     * @param isSecured     Indicates if https:// should be used (if false, defaults to http://)
     * @param restHost      Port where the API is running. Defaults to 80 if null
     * @param restPort      Remote host. Defaults to 'localhost' if null
     */
    @Given("^I( securely)? send requests to '([^:]+?)(:.+?)?'$")
    public void setupApp(String isSecured, String restHost, String restPort) {
        String restProtocol = "http://";

        if (isSecured != null) {
            restProtocol = "https://";
        }

        if (restHost == null) {
            restHost = "localhost";
        }

        if (restPort == null) {
            if (isSecured == null)  {
                restPort = "80";
            } else {
                restPort = "443";
            }
        }

        restPort = restPort.replace(":", "");
        assertThat(commonspec.getRestRequest()).as("No rest client initialized. Did you forget to use @rest annotation in your feature?").isNotNull();
        commonspec.setRestHost(restHost);
        commonspec.setRestPort(restPort);
        commonspec.setRestProtocol(restProtocol);

        if (restProtocol.matches("https://")) {
            commonspec.getRestRequest().relaxedHTTPSValidation();
        }

        commonspec.getRestRequest().baseUri(restProtocol + restHost).port(Integer.parseInt(restPort));
    }

    /**
     * Set app host and port {@code host, @code port}. This parameters will be used for all future
     * requests in the same scenario.
     *
     * The rest request is build within the {@link HookGSpec} class, so, dont forget to use the
     * @rest annotation at the beginning of your feature for a proper initialization.
     * @param host      Remote host. Defaults to 'localhost' if null
     * @param port      Port where the API is running. Defaults to 80 if null
     */
    @Given("^My app is running in '([^:]+?)(:.+?)?'$")
    public void setupApp(String host, String port) throws Exception {
        assertThat(host).isNotEmpty();

        String restProtocol = "http://";

        if (port == null) {
            port = ":80";
        }

        if ("443".equals(port.substring(1))) {
            restProtocol = "https://";
        }

        port = port.replace(":", "");

        if (commonspec.getRestRequest() == null && commonspec.getDriver() == null) {
            throw new Exception("Application was not initialized correctly. Did you forget to add @rest or @web at the top of your feature");
        }

        commonspec.setRestHost(host);
        commonspec.setRestPort(port);
        commonspec.setRestProtocol(restProtocol);
        commonspec.setWebHost(host);
        commonspec.setWebPort(port);

        if (commonspec.getRestRequest() != null) {
            if (restProtocol.matches("https://")) {
                commonspec.getRestRequest().relaxedHTTPSValidation();
            }

            commonspec.getRestRequest().baseUri(restProtocol + host).port(Integer.parseInt(port));
        }
    }

    /**
     * Check if expression defined by JSOPath (http://goessner.net/articles/JsonPath/index.html)
     * match in JSON stored in a environment variable.
     *
     * @param envVar environment variable where JSON is stored
     * @param table  data table in which each row stores one expression
     */
    @Then("^'(.+?)' matches the following cases:$")
    public void matchWithExpresion(String envVar, DataTable table) throws Exception {
        String jsonString = ThreadProperty.get(envVar);

        for (DataTableRow row : table.getGherkinRows()) {
            String expression = row.getCells().get(0);
            String condition = row.getCells().get(1);
            String result = row.getCells().get(2);

            String value = commonspec.getRestResponse().then().extract().path(expression);
            commonspec.evaluateJSONElementOperation(value, condition, result);
        }
    }

    /**
     * Generates a REST request of the type specified to the indicated endpoint
     * @param requestType   HTTP verb (type of request): POST, GET, PUT, PATCH, DELETE
     * @param endPoint      Endpoint (i.e /user/1). The base path used is the one indicated in a previous step
     * @param foo           parameter generated by cucumber because of the optional expression
     * @param loginInfo     User and password to use if the endpoints requires basic authentication (user:password)
     * @param bar           parameter generated by cucumber because of the optional expression
     * @param baseData      If specified, the content of the file will be loaded in the body of the request (POST, PUT, PATCH operations)
     * @param baz           parameter generated by cucumber because of the optional expression
     * @param type          If the content of the file should be read as string or json
     * @throws Exception
     */
    @When("^I send a '(.+?)' request to '(.+?)'( with user and password '(.+:.+?)')?( based on '([^:]+?)')?( as '(json|string)')?$")
    public void sendRequestNoDataTable(String requestType, String endPoint, String foo, String loginInfo, String bar, String baseData, String baz, String type) throws Exception {

        String retrievedData;
        String user = null;
        String password = null;

        if (loginInfo != null) {
            user = loginInfo.substring(0, loginInfo.indexOf(':'));
            password = loginInfo.substring(loginInfo.indexOf(':') + 1, loginInfo.length());
            commonspec.getRestRequest().auth().preemptive().basic(user, password);
        }

        if (baseData != null) {
            retrievedData = commonspec.retrieveData(baseData, type);
            commonspec.getRestRequest().given().body(retrievedData);
        }

        // Save response
        commonspec.generateRestRequest(requestType, endPoint);
        commonspec.getLogger().debug("Saving response");

    }

    /**
     * Send a request of the type specified
     *
     * @param requestType   type of request to be sent. Possible values:
     *                      GET|DELETE|POST|PUT|PATCH
     * @param endPoint      end point to be used
     * @param foo           parameter generated by cucumber because of the optional expression
     * @param baseData      path to file containing the schema to be used
     * @param type          element to read from file (element should contain a json)
     * @param modifications DataTable containing the modifications to be done to the
     *                      base schema element. Syntax will be:
     *                      {@code
     *                      | <key path> | <type of modification> | <new value> |
     *                      }
     *                      where:
     *                      key path: path to the key to be modified
     *                      type of modification: DELETE|ADD|UPDATE
     *                      new value: in case of UPDATE or ADD, new value to be used
     *                      for example:
     *                      if the element read is {"key1": "value1", "key2": {"key3": "value3"}}
     *                      and we want to modify the value in "key3" with "new value3"
     *                      the modification will be:
     *                      | key2.key3 | UPDATE | "new value3" |
     *                      being the result of the modification: {"key1": "value1", "key2": {"key3": "new value3"}}
     * @throws Exception
     */
    @When("^I send a '(.+?)' request to '(.+?)'( with user and password '(.+:.+?)')? based on '([^:]+?)'( as '(json|string)')? with:$")
    public void sendRequestDataTable(String requestType, String endPoint, String foo, String loginInfo, String baseData, String baz, String type, DataTable modifications) throws Exception {

        String user = null;
        String password = null;

        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);

        // Modify data
        commonspec.getLogger().debug("Modifying data {} as {}", retrievedData, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications).toString();
        commonspec.getRestRequest().given().body(modifiedData);


        if (loginInfo != null) {
            user = loginInfo.substring(0, loginInfo.indexOf(':'));
            password = loginInfo.substring(loginInfo.indexOf(':') + 1, loginInfo.length());
            commonspec.getRestRequest().auth().preemptive().basic(user, password);
        }

        // Save response
        commonspec.generateRestRequest(requestType, endPoint);
        commonspec.getLogger().debug("Saving response");

    }

    /**
     * Verifies the status response (HTTP response code) of a rest request.
     * Aditionally, the step can verify the response (body) lenght, and if the body contains a given character or matches a defined schema
     *
     * @param expectedStatus    Expected HTTP status code
     * @param foo               parameter generated by cucumber because of the optional expression
     * @param expectedLength    Expected body lenght
     * @param expectedText      Expected text to be present in the body
     * @param expectedSchema    file that contains a valid json schema to match against the response body
     */
    @Then("^the service response status must be '(.*?)'( and its response length must be '(.*?)'| and its response must contain the text '(.*?)'| and its response matches the schema in '(.*?)')?$")
    public void assertResponseStatusLength(Integer expectedStatus, String foo, Integer expectedLength, String expectedText, String expectedSchema) {

        commonspec.getRestResponse().then().statusCode(expectedStatus);

        if (foo == null) {
            return;
        }

        if (foo.contains("text")) {
            assertResponseMessage(expectedText);
        }

        if (foo.contains("schema")) {
            String schemaData = commonspec.retrieveData(expectedSchema, "json");
            commonspec.getRestResponse().then().assertThat().body(matchesJsonSchema(schemaData));
        }

        if (foo.contains("length")) {
            assertThat(commonspec.getRestResponse().getBody().asString().length()).as("The returned body does not have the expected length").isEqualTo(expectedLength);
        }

    }

    /**
     * Verifies if the response body contains an specific string
     * @param expectedText      String to find in the response body
     * @throws SecurityException
     * @throws IllegalArgumentException
     */
    @Then("^the service response must contain the text '(.*?)'$")
    public void assertResponseMessage(String expectedText) throws SecurityException, IllegalArgumentException {
        ResponseBody body = commonspec.getRestResponse().getBody();
        String bodyAsString = body.asString();
        assertThat(bodyAsString).as("Text '" + expectedText + "' was not found in response body").contains(expectedText);
    }


    /**
     * Save value for future use.
     * <p>
     * If element is a jsonpath expression (i.e. $.fragments[0].id), it will be
     * applied over the last httpResponse.
     * <p>
     * If element is a jsonpath expression preceded by some other string
     * (i.e. ["a","b",,"c"].$.[0]), it will be applied over this string.
     * This will help to save the result of a jsonpath expression evaluated over
     * previous stored variable.
     *
     * @param position position from a search result
     * @param element  key in the json response to be saved
     * @param envVar   thread environment variable where to store the value
     * @throws IllegalAccessException       exception
     * @throws IllegalArgumentException     exception
     * @throws SecurityException            exception
     * @throws NoSuchFieldException         exception
     * @throws ClassNotFoundException       exception
     * @throws InstantiationException       exception
     * @throws InvocationTargetException    exception
     * @throws NoSuchMethodException        exception
     */
    @Given("^I save element (in position \'(.+?)\' in )?\'(.+?)\' in environment variable \'(.+?)\'$")
    public void saveElementEnvironment(String foo, String position, String element, String envVar) throws Exception {

        Pattern pattern = Pattern.compile("^((.*)(\\.)+)(\\$.*)$");
        Matcher matcher = pattern.matcher(element);
        String json;
        String parsedElement;

        if (matcher.find()) {
            json = matcher.group(2);
            parsedElement = matcher.group(4);
        } else {
            json = commonspec.getRestResponse().getBody().asString();
            parsedElement = element;
        }

        String value = "";
        try {
            value = commonspec.getJSONPathString(json, parsedElement, position);
        } catch (PathNotFoundException pe) {
            commonspec.getLogger().error(pe.getLocalizedMessage());
        }

        assertThat(value).as("json result is empty").isNotEqualTo("");
        ThreadProperty.set(envVar, value);
    }

    /**
     * Specify a custom map of headers to be added to future requests
     * @param modifications DataTable containing the custom set of headers to be
     *                      added to the requests. Syntax will be:
     *                      {@code
     *                      | <key> | <value> |
     *                      }
     *                      where:
     *                      key: header key name
     *                      value: value for tue key
     *                      for example:
     *                      if we want to add the header "token" with value "12345678", to the request header
     *                      the modification will be:
     *                      | token | 12345678 |
     * @throws Exception
     */
    @Given("^I set headers:$")
    public void setHeaders(DataTable modifications) throws Throwable {

        Map<String, String> headers = new HashMap<>();

        LinkedHashMap jsonAsMap = new LinkedHashMap();
        for (int i = 0; i < modifications.raw().size(); i++) {
            String key = modifications.raw().get(i).get(0);
            String value = modifications.raw().get(i).get(1);
            headers.put(key, value);
            commonspec.getRestRequest().header(key, value);
        }

        commonspec.setHeaders(headers);
    }

    /**
     * Specify a custom map of cookies to be added to future requests
     * @param modifications DataTable containing the custom set of cookies to be
     *                      added to the requests. Syntax will be:
     *                      {@code
     *                      | <key> | <value> |
     *                      }
     *                      where:
     *                      key: cookie key name
     *                      value: cookie for tue key
     *                      for example:
     *                      if we want to add the cookie "token" with value "12345678", to the request cookie
     *                      the modification will be:
     *                      | token | 12345678 |
     * @throws Exception
     */
    @Given("^I set cookies:$")
    public void setCookies(DataTable modifications) throws Throwable {

        Map<String, String> cookies = new HashMap<>();

        LinkedHashMap jsonAsMap = new LinkedHashMap();
        for (int i = 0; i < modifications.raw().size(); i++) {
            String key = modifications.raw().get(i).get(0);
            String value = modifications.raw().get(i).get(1);
            cookies.put(key, value);
            commonspec.getRestRequest().cookie(key, value);
        }

        commonspec.setRestCookies(cookies);
    }

    /**
     * Clears the headers set by any previous request. A request will reuse the headers/cookies
     * that were set in any previous call within the same scenario
     * @throws Throwable
     */
    @Then("^I clear headers from previous request$")
    public void clearHeaders() throws Throwable {

        /**
         * Since there is no easy way to remove all headers from the request,
         * a new request object is created with the same configuration
         * */

        commonspec.getHeaders().clear();
        RequestSpecification spec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        commonspec.setRestRequest(given().header("Content-Type", "application/json").cookies(commonspec.getRestCookies()).spec(spec));
        this.setupApp(commonspec.getRestHost(), commonspec.getRestPort());

    }

    /**
     * Clears the cookies set by any previous request. A request will reuse the headers/cookies
     * that were set in any previous call within the same scenario
     * @throws Throwable
     */
    @Then("^I clear cookies from previous request$")
    public void clearCookies() throws Throwable {

        /**
         * Since there is no easy way to remove all cookies from the request,
         * a new request object is created with the same configuration
         * */
        commonspec.getRestCookies().clear();
        RequestSpecification spec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        commonspec.setRestRequest(given().header("Content-Type", "application/json").headers(commonspec.getHeaders()).spec(spec));
        this.setupApp(commonspec.getRestHost(), commonspec.getRestPort());

    }


    /**
     * SExecutes the given request to the REST endpont for the specified amount of time in regular intervals, until the response body contains
     * the specified text
     *
     * @param timeout       Maximum time to wait for the text to be present in the response body
     * @param wait          Time between retries
     * @param requestType   Type of request (POST, GET, PATCH, DELETE, PUT)
     * @param endPoint      Endpoint (i.e /user/1)
     * @param responseVal   Expected value to evaluate in the response body
     * @throws Exception
     */
    @When("^in less than '(\\d+?)' seconds, checking each '(\\d+?)' seconds, I send a '(.+?)' request to '(.+?)' so that the response( does not)? contains '(.+?)'$")
    public void sendRequestTimeout(Integer timeout, Integer wait, String requestType, String endPoint, String contains, String responseVal) throws Exception {

        Boolean searchUntilContains;
        if (contains == null || contains.isEmpty()) {
            searchUntilContains = Boolean.TRUE;
        } else {
            searchUntilContains = Boolean.FALSE;
        }
        Boolean found = !searchUntilContains;
        AssertionError ex = null;

        String type = "";
        Pattern pattern = CommonG.matchesOrContains(responseVal);
        for (int i = 0; (i <= timeout); i += wait) {
            if (found && searchUntilContains) {
                break;
            }

            commonspec.generateRestRequest(requestType, endPoint);
            commonspec.getLogger().debug("Checking response value");

            ResponseBody body = commonspec.getRestResponse().getBody();
            String bodyAsString = body.asString();

            try {
                if (searchUntilContains) {
                    assertThat(bodyAsString).containsPattern(pattern);
                    found = true;
                    timeout = i;
                } else {
                    assertThat(bodyAsString).doesNotContain(responseVal);
                    found = false;
                    timeout = i;
                }
            } catch (AssertionError e) {
                if (!found) {
                    commonspec.getLogger().info("Response value not found after " + i + " seconds");
                } else {
                    commonspec.getLogger().info("Response value found after " + i + " seconds");
                }
                Thread.sleep(wait * 1000);
                ex = e;
            }
            if (!found && !searchUntilContains) {
                break;
            }
        }
        if ((!found && searchUntilContains) || (found && !searchUntilContains)) {
            throw (ex);
        }
        if (searchUntilContains) {
            commonspec.getLogger().info("Success! Response value found after " + timeout + " seconds");
        } else {
            commonspec.getLogger().info("Success! Response value not found after " + timeout + " seconds");
        }
    }

    /**
     * Checks if the headers in the response matches the specified values
     * @param table DataTable containing the custom set of headers to be
     *                      added to the requests. Syntax will be:
     *                      {@code
     *                      | <header name> | <condition> | <expected value>
     *                      }
     *                      where:
     *                      header name: Header name
     *                      condition: Condition that is going to be evaluated (available: equal,
     *                      not equal, exists, does not exists, contains, does not contain, length, size)
     *                      expected value: Value used to verify the condition
     *                      for example:
     *                      If we want to verify that the header "Content-Encoding" is equal
     *                      to "application/json" we would do
     *                      | Content-Encoding | equal | application/json |
     * @throws Throwable
     */
    @And("^the service response headers match the following cases:$")
    public void checkHeaders(DataTable table) throws Throwable {

        for (DataTableRow row : table.getGherkinRows()) {
            String header = row.getCells().get(0);
            String condition = row.getCells().get(1);
            String result = row.getCells().get(2);

            String headerValue = commonspec.getRestResponse().getHeaders().getValue(header);
            commonspec.evaluateJSONElementOperation(headerValue, condition, result);

        }

    }

    /**
     * Checks if the cookies in the response matches the specified values
     * @param table DataTable containing the custom set of cookies to be
     *                      added to the requests. Syntax will be:
     *                      {@code
     *                      | <cookies name> | <condition> | <expected value>
     *                      }
     *                      where:
     *                      cookies name: Header name
     *                      condition: Condition that is going to be evaluated (available: equal,
     *                      not equal, exists, does not exists, contains, does not contain, length, size)
     *                      expected value: Value used to verify the condition
     *                      for example:
     *                      If we want to verify that the cookies "Content-Encoding" is equal
     *                      to "application/json" we would do
     *                      | Content-Encoding | equal | application/json |
     * @throws Throwable
     */
    @And("^the service response cookies match the following cases:$")
    public void checkCookies(DataTable table) throws Throwable {

        for (DataTableRow row : table.getGherkinRows()) {
            String cookie = row.getCells().get(0);
            String condition = row.getCells().get(1);
            String result = row.getCells().get(2);

            String cookieValue = commonspec.getRestResponse().getCookies().get(cookie);
            commonspec.evaluateJSONElementOperation(cookieValue, condition, result);

        }
    }

    /**
     * Saves the header value for future use
     * @param headerName  Header name
     * @param varName     Name of the environmental variable
     * @throws Throwable
     */
    @And("^I save the response header '(.+?)' in environment variable '(.+?)'$")
    public void saveHeaderValue(String headerName, String varName) throws Throwable {

        String headerValue = commonspec.getRestResponse().getHeaders().getValue(headerName);
        assertThat(headerValue).as("The header " + headerName + " is not present in the response").isNotNull();
        ThreadProperty.set(varName, headerValue);
    }

    /**
     * Saves the cookie value for future use
     * @param cookieName  Cookie name
     * @param varName     Name of the environmental variable
     * @throws Throwable
     */
    @And("^I save the response cookie '(.+?)' in environment variable '(.+?)'$")
    public void saveCookieValue(String cookieName, String varName) throws Throwable {

        String cookieValue = commonspec.getRestResponse().getCookies().get(cookieName);
        assertThat(cookieValue).as("The cookie " + cookieName + " is not present in the response").isNotNull();
        ThreadProperty.set(varName, cookieValue);
    }
}