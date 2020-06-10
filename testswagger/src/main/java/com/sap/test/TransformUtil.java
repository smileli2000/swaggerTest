package com.sap.test;

import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TransformUtil {

    private static final String V2_TO_V4_RULE_PATH = "V2-to-V4-CSDL.xsl";
    private static final String V4_TO_SWAGGER_RULE_PATH = "V4-CSDL-to-OpenAPI.xsl";
    private static final String OPEN_API_VERSION = "openapi-version";
    private static final String OPEN_API_VERSION_3 = "3.0";
    private static final String PRETTY = "pretty";
    private static final String DIAGRAM = "diagram";
    public static final String INFO = "info";
    public static final String DESCRIPTION = "description";
    public static final String EDMX_SCHEMA_XPATH = "/edmx:Edmx/edmx:DataServices/*[name()='Schema']";
    public static final String ANNOTATIONS = "Annotations";
    private static final Logger logger = LoggerFactory.getLogger(TransformUtil.class);
    private static final String PARAMETERS_SEARCH_PATTERN = "\\{\"\\$ref\":\"#/components/parameters/search\"},";

    private TransformUtil() {
    }


    public static String transfromEdmxToSwagger(String annotationsXml, String edmxXml) throws IOException {
        String convertedEdmx = combineAnnotaitonsIntoEdmx(annotationsXml, edmxXml);
        InputStream edxmInputStream = new ByteArrayInputStream(convertedEdmx.getBytes());
        StreamSource edmxStreamSource = new StreamSource(edxmInputStream);
        StreamResult transformV2ToV4Result = transformV2ToV4(edmxStreamSource);
        String ret = "";
        try (OutputStream transformV2ToV2OutPutStream = transformV2ToV4Result.getOutputStream()) {
            if (transformV2ToV2OutPutStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) transformV2ToV2OutPutStream;
                StreamSource v4ToSwaggerInputSreamSource = new StreamSource(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
                StreamResult swaggerStreamResult = transformV4ToSwagger(v4ToSwaggerInputSreamSource);
                ret = getSwaggerResult(swaggerStreamResult);
                ret = removeDescriptionOfSwagger(ret);
            }
        }
        return ret;
    }


    public static String transfromEdmxToSwagger(String edmxXml) throws IOException {
        InputStream edxmInputStream = new ByteArrayInputStream(edmxXml.getBytes());
        StreamSource edmxStreamSource = new StreamSource(edxmInputStream);
        StreamResult transformV2ToV4Result = transformV2ToV4(edmxStreamSource);
        String ret = "";
        try (OutputStream transformV2ToV2OutPutStream = transformV2ToV4Result.getOutputStream()) {
            if (transformV2ToV2OutPutStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) transformV2ToV2OutPutStream;
                StreamSource v4ToSwaggerInputSreamSource = new StreamSource(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
                StreamResult swaggerStreamResult = transformV4ToSwagger(v4ToSwaggerInputSreamSource);
                ret = getSwaggerResult(swaggerStreamResult);
                ret = removeDescriptionOfSwagger(ret);
                ret = removeSearchParameter(ret);
            }
        }
        return ret;
    }

    private static String combineAnnotaitonsIntoEdmx(String annotationsXmlStr, String edmxXmlStr) {
        Document annotationDoc = null;
        Document edmxWithoutAnnotationDoc = null;
        try {
            annotationDoc = DocumentHelper.parseText(annotationsXmlStr);
            edmxWithoutAnnotationDoc = DocumentHelper.parseText(edmxXmlStr);
        } catch (DocumentException e) {
            logger.warn("combineAnnotaitonsIntoEdmx warning, the annotation is not a valid document.");
            return edmxXmlStr;
        }
        Node dataServicesOfEdmx = edmxWithoutAnnotationDoc.selectSingleNode(EDMX_SCHEMA_XPATH);
        Element schemaOfEdmxElement = (Element) dataServicesOfEdmx;
        Node annotationSchemaNode = annotationDoc.selectSingleNode(EDMX_SCHEMA_XPATH);
        List<Element> annotaionsElements = ((Element) annotationSchemaNode).elements(ANNOTATIONS);
        for (Element annotationElement : annotaionsElements) {
            schemaOfEdmxElement.add((Element) annotationElement.clone());
        }
        return edmxWithoutAnnotationDoc.asXML();
    }

    private static String getSwaggerResult(StreamResult swaggerStreamResult) {
        String ret = "";
        try (OutputStream swaggerOutputStream = swaggerStreamResult.getOutputStream()) {
            if (swaggerOutputStream != null) {
                ByteArrayOutputStream outputByteArrayStream = (ByteArrayOutputStream) swaggerOutputStream;
                ret = new String(outputByteArrayStream.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return ret;
    }


    private static Transformer getTransformer(StreamSource ruleStream) {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        Transformer transformer;
        try {
            transformer = transformerFactory.newTransformer(ruleStream);
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        transformer.setParameter(OPEN_API_VERSION, OPEN_API_VERSION_3);
        transformer.setParameter(PRETTY, true);
        transformer.setParameter(DIAGRAM, true);
        return transformer;
    }

    private static String removeDescriptionOfSwagger(String swagger) {
        String convertedSwagger = swagger;
        if (StringUtils.isNotBlank(swagger)) {
            JsonObject swaggerObj = JsonUtils.generateJsonObjectFromJsonString(swagger);
            JsonObject swaggerInfo = swaggerObj.get(INFO).getAsJsonObject();
            swaggerInfo.remove(DESCRIPTION);
            convertedSwagger = swaggerObj.toString();
        }
        return convertedSwagger;
    }

    private static String removeSearchParameter(String swagger) {
        String convertedSwagger = swagger;
        return RegExUtils.replaceAll(convertedSwagger, PARAMETERS_SEARCH_PATTERN, "");
    }

    private static StreamResult transformV2ToV4(StreamSource inputStreamSource) throws IOException {
        StreamResult resultStream = new StreamResult(new ByteArrayOutputStream());
        if (inputStreamSource == null) {
            return resultStream;
        }
        try (InputStream inputStream = getFileStream(V2_TO_V4_RULE_PATH)) {
            if (inputStream != null) {
                StreamSource ruleStream = new StreamSource(inputStream);
                Transformer transformer = getTransformer(ruleStream);
                transformer.transform(inputStreamSource, resultStream);
            }
        } catch (TransformerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return resultStream;
    }

    private static StreamResult transformV4ToSwagger(StreamSource v4StreamSource) throws IOException {
        StreamResult streamResult = new StreamResult(new ByteArrayOutputStream());
        try (InputStream fileStream = getFileStream(V4_TO_SWAGGER_RULE_PATH)) {
            StreamSource ruleStream = new StreamSource(fileStream);
            Transformer transformer = getTransformer(ruleStream);
            transformer.transform(v4StreamSource, streamResult);
        } catch (TransformerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return streamResult;
    }

    private static InputStream getFileStream(String fileName) {
        ClassLoader classLoader = TransformUtil.class.getClassLoader();
        if (classLoader == null) {
            return null;
        }
        return classLoader.getResourceAsStream(fileName);
    }


    private String getFileContent(String edmxFileName) {
        String edmxFile = "";
        try {
            edmxFile = IOUtils.toString(JsonUtils.class.getClassLoader()
                    .getResourceAsStream(edmxFileName), Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return edmxFile;
    }

    public static void main(String arg[]) {

        //run time: 1.30 minutes:
        TransformUtil transformUtil = new TransformUtil();
        String edmxFileName = "edmxWithAnnotation.xml";
        String edmxFileContent = transformUtil.getFileContent(edmxFileName);
        try {
            String test = TransformUtil.transfromEdmxToSwagger(edmxFileContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
