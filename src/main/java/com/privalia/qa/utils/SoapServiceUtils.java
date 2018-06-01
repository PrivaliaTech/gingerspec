package com.privalia.qa.utils;

import com.predic8.wsdl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.*;
import org.w3c.dom.Node;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Class to execute methods on any remote WebService. Verify the test class SoapServiceUtilsTest for
 * instructions on how to use it
 * @author José Fernández
 */
public class SoapServiceUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoapServiceUtils.class);

    private String wsdlAddress;

    private Definitions defs;

    /**
     * Returns the Port name for the first service found in the WSDL file
     * @return port name
     */
    public String getPortName() {
        return this.getDefs().getServices().get(0).getPorts().get(0).getName();
    }

    /**
     * Gets the address of the remote wsdl
     * @return
     */
    public String getWsdlAddress() {
        return wsdlAddress;
    }

    /**
     * Sets remote wsdl address
     * @param wsdlAddress
     */
    private void setWsdlAddress(String wsdlAddress) {
        this.wsdlAddress = wsdlAddress;
    }

    /**
     * Returns a {@link Definitions} object representing the WSDL
     * definition
     * @return {@link Definitions} object
     */
    private Definitions getDefs() {
        return defs;
    }

    /**
     * Sets the {@link Definitions} object representing the given WSDL definition
     * @param defs
     */
    private void setDefs(Definitions defs) {
        this.defs = defs;
    }

    /**
     * Returns name of the first service found in the WSDL description
     * @return
     */
    public String getServiceName() { return this.getDefs().getServices().get(0).getName(); }

    /**
     * Returns Target Namespace
     * @return
     */
    public String getTargetNameSpace() {
        return this.getDefs().getTargetNamespace();
    }

    /**
     * Parses the remote WSDL file and store its variables internally for easier access
     * @param url   Remote WSDL address
     */
    public void parseWsdl(String url) {

        WSDLParser parser = new WSDLParser();
        this.setDefs(parser.parse(url));
        this.setWsdlAddress(url);
    }

    /**
     * Returns a Map with all the posible SOAP operations for the the first service
     * found in the WSDL file
     * @return  Map containing action name -> corresponding soap action
     */
    public Map<String, String> getAvailableSoapActions() {

        Map<String, String> operations = new LinkedHashMap<>();
        String service = this.getDefs().getServices().get(0).getPorts().get(0).getBinding().getName();

        for (Binding bnd : defs.getBindings()) {
            if (bnd.getName().matches(service)) {

                for (BindingOperation bop : bnd.getOperations()) {
                    if (bnd.getBinding() instanceof AbstractSOAPBinding) {
                        operations.put(bop.getName(), bop.getOperation().getSoapAction());
                    }
                }

            }
        }
        return operations;
    }

    /**
     * Alter the given XML request with the given values in the Map
     * @param request       Request in XML format
     * @param variables     Map describing the list of variables and the corresponding value
     * @return              XML String with the changes
     */
    public String transformXml(String request, Map<String, String> variables) throws IOException, SAXException, ParserConfigurationException, TransformerException {

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new ByteArrayInputStream(request.getBytes()));

        for (Map.Entry<String, String> entry : variables.entrySet())
        {
            document.getElementsByTagName(entry.getKey()).item(0).setTextContent(entry.getValue());
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        String output = writer.getBuffer().toString().replaceAll("\n|\r", "");
        return  output;

    }

    /**
     * Given an String representing an XML object, returns the value of the given variable
     * @param xmlString      XML String
     * @param variable       Variable to look for in the body
     * @return               The value of the variable, or null if not found
     */
    public String evaluateXml(String xmlString, String variable) throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new ByteArrayInputStream(xmlString.getBytes()));

        Node node = document.getElementsByTagName(variable).item(0);

        if (node != null) {
            return node.getTextContent();
        }

        return null;

    }

    /**
     * Alter the given XML request with the given values in the Map before executing the given method
     * @param ActionName    Action name. This name with be mapped to the corresponding SOAPAction
     * @param request       XML message to send as string
     * @param variables     Map describing the list of variables and the corresponding value
     * @return              a {@link SOAPMessage} object
     * @throws Exception
     */
    public String executeMethodWithParams(String ActionName, String request, Map<String, String> variables) throws Exception {

        String transformedRequest = this.transformXml(request, variables);
        return this.executeMethod(ActionName, transformedRequest);
    }

    /**
     * Executes the given method in the remote webservice
     * @param ActionName    Action name. This name with be mapped to the corresponding SOAPAction
     * @param request       XML message to send as string
     * @return              a {@link SOAPMessage} object
     * @throws Exception
     */
    public String executeMethod(String ActionName, String request) throws Exception {

        QName serviceName = new QName(this.getTargetNameSpace(), this.getServiceName());
        QName portName = new QName(this.getTargetNameSpace(), this.getPortName());
        String SOAPAction = this.getAvailableSoapActions().get(ActionName);

        SOAPMessage response = invoke(serviceName, portName, this.getWsdlAddress(), SOAPAction, request);

        SOAPBody body = response.getSOAPBody();

        if (body.hasFault()) {
            return body.getFault().toString();
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            response.writeTo(baos);
            baos.flush();
            return new String(baos.toByteArray());
        }
    }

    /**
     * Performs the invoke operation with the parameters given and returns a {@link SOAPMessage} object
     * @param serviceName       Service name
     * @param portName          Port Name
     * @param endpointUrl       URL of the web service
     * @param soapActionUri     Action/Method in the remote webservice to execute
     * @param data              Request (as XML string data)
     * @return
     * @throws Exception
     */
    private SOAPMessage invoke(QName serviceName, QName portName, String endpointUrl,
                                     String soapActionUri, String data) throws Exception {
        Service service = Service.create(serviceName);
        service.addPort(portName, SOAPBinding.SOAP11HTTP_BINDING, endpointUrl);

        Dispatch dispatch = service.createDispatch(portName, SOAPMessage.class, Service.Mode.MESSAGE);

        // The soapActionUri is set here. otherwise we get a error on .net based services.
        dispatch.getRequestContext().put(Dispatch.SOAPACTION_USE_PROPERTY, true);
        dispatch.getRequestContext().put(Dispatch.SOAPACTION_URI_PROPERTY, soapActionUri);

        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();

        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        SOAPBody body = envelope.getBody();

        StreamSource preppedMsgSrc = new StreamSource(new StringReader(data));
        soapPart.setContent(preppedMsgSrc);

        message.saveChanges();

        System.out.println(message.getSOAPBody().getFirstChild().getTextContent());
        SOAPMessage response = (SOAPMessage) dispatch.invoke(message);

        return response;
    }

}
