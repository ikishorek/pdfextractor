/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tuwien.pdfprocessor.processor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tuwien.pdfprocessor.repository.DocumentRepository;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 *
 * @author amin
 */
@Service
public class Pdf2tableProcessor {

    @Autowired
    private DocumentRepository repository;

    private static final Logger LOGGER = Logger.getLogger(Pdf2tableProcessor.class.getName());

    private static final String MAINPATH = "/home/amin/Documents/amin/classification/pdf2tableresults/all/CLEF/CLEF-2003/";

    public void process() throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(MAINPATH))) {
            paths.forEach(filePath -> {
                if (Files.isRegularFile(filePath)) {

                    try {
                        File fXmlFile = new File(filePath.toString());
                        if (fXmlFile.toString().endsWith("output.xml")) {

                            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                            Document doc = dBuilder.parse(fXmlFile);
                            doc.getDocumentElement().normalize();

                            processDocument(doc, filePath.getParent().toString());
                        }

                    } catch (SAXException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    } catch (ParserConfigurationException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                }
            });
        }
    }

    /**
     * Reads xml document and convert it into the JSON format we require
     */
    private JSONArray processDocument(Document doc, String path) {

        JSONArray returnTables = new JSONArray();
        JSONObject tableObject = null;
        JSONArray jsonArr = null;

        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        int tableCounter = 0;

        NodeList nodeList = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {

            //We have encountered an <table> tag.
            Node node = nodeList.item(i);
            if ("table".equals(node.getNodeName())) {
                if (node instanceof Element) {
                    tableCounter++;
                    Map<Integer, String> headerList = new HashMap<>();

                    // Get title
                    Node titleNode = ((Element) node).getElementsByTagName("title").item(0);

                    String title = titleNode.getTextContent();

                    // GET HEADER ELEMENT
                    NodeList header = ((Element) node).getElementsByTagName("header");

                    // Get Only First Header
                    Node firstHeaderNode = header.item(0);

                    // GET First Header_Line
                    Node headerLineNode = ((Element) firstHeaderNode).getElementsByTagName("header_line").item(0);

                    // GET HEADER ELEMENT LISTS
                    NodeList headerElements = headerLineNode.getChildNodes();

                    // ITERATE ALL HEADE_ELEMENTS
                    int headerelementcounter = 0;
                    for (int c = 0; c < headerElements.getLength(); c++) {
                        Node headerElementNode = headerElements.item(c);
                        if (headerElementNode.getNodeName().equals("header_element")) {
                            headerelementcounter++;
                            String pathToNode = MessageFormat.format("/tables/table[{0}]/header[1]/header_line[1]/header_element[{1}]/@sh", tableCounter, headerelementcounter);
                            try {
                                String shVal = (String) xpath.evaluate(pathToNode, doc, XPathConstants.STRING);
                                headerList.put(Integer.valueOf(shVal.trim()), headerElementNode.getTextContent().replace("\n", "").replace("\r", ""));

                            } catch (XPathExpressionException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    // For each table create JSON
                    // Count data_rows
                    String datarowcounter = MessageFormat.format("/tables/table[{0}]/tbody[1]/data_row", tableCounter);
                    XPathExpression expr;
                    try {
                        expr = xpath.compile(datarowcounter);

                        NodeList datarows = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                        Integer rowCounts = datarows.getLength();

                        tableObject = new JSONObject();
                        jsonArr = new JSONArray();

                        for (int rowCounter = 0; rowCounter < rowCounts; rowCounter++) {

                            // For each Row Get Row Values
                            JSONObject tableDataObject = new JSONObject();

                            int headerCounter = 0;
                            for (Map.Entry<Integer, String> entry : headerList.entrySet()) {
                                headerCounter++;
                                Integer shVal = entry.getKey();
                                String headerText = entry.getValue();

                                // Find Cell
                                String propCell = MessageFormat.format("/tables/table[{0}]/tbody[1]/data_row[{1}]/cell[@sh={2}]", tableCounter, rowCounter + 1, shVal);
                                expr = xpath.compile(propCell);
                                NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                                if (nl.getLength() > 0) {
                                    Node cellItem = nl.item(0);
                                    String cellContent = cellItem.getTextContent().replace("\n", "").replace("\r", "");

                                    if (headerText.equals("")) {
                                        headerText = "header " + headerCounter;
                                    }
                                    tableDataObject.put(headerText, cellContent);
                                } else {
                                    tableDataObject.put(headerText, "no data");
                                }
                                // Add this cellText with the headername into JSON

//                                for (int cellcounter = 0; cellcounter < nl.getLength(); cellcounter++) {
//                                    
//                                    System.out.println(cellContent);
//                                }
                            }
                            // Add Row into array
                            jsonArr.put(tableDataObject);

                        }

                        // Add rows into tableObject
                        tableObject.put("rows", jsonArr);

                        String fileName = path.substring(path.lastIndexOf("/") + 1).replace(".pdf", "");

                        tableObject.put("fileid", fileName );
                        tableObject.put("tablecounter", tableCounter);
                        tableObject.put("header", title);
                        tableObject.toString();
                        
                        String fileNameJson = fileName + "_" + tableCounter + ".json";
                        try (FileWriter file = new FileWriter(path + "/" + fileNameJson)) {
                            file.write(tableObject.toString());
                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    } catch (XPathExpressionException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

            }

            if (tableObject != null) {
                returnTables.put(tableObject);

                // Save JSON INTO FILE
            }
        }

        return returnTables;
    }

}