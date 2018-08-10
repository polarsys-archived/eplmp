/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server.indexer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.xmlbeans.XmlException;
import org.polarsys.eplmp.core.util.Tools;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility methods for Search & Index operations using Elasticsearch API.
 *
 * @author Taylor Labejof
 */
public class IndexerUtils {

    private static final Logger LOGGER = Logger.getLogger(IndexerUtils.class.getName());

    private IndexerUtils() {
    }

    /**
     * Convert the workspaceId to a Elastic Search index name
     *
     * @param workspaceId Id to convert
     * @return The workspaceId without uppercase and space
     */
    protected static String formatIndexName(String workspaceId) {
        try {
            return java.net.URLEncoder.encode(Tools.unAccent(workspaceId), "UTF-8").toLowerCase();
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.FINEST, null, e);
            return null;
        }
    }

    /**
     * Get Stream for a Bin Resource
     *
     * @param fullName    The full name of the resource
     * @param inputStream Stream of the resource
     * @return String to index
     */
    protected static String streamToString(String fullName, InputStream inputStream) {
        String strRet = " ";

        int lastDotIndex = fullName.lastIndexOf('.');

        String extension = "";

        if (lastDotIndex != -1) {
            extension = fullName.substring(lastDotIndex);
        }

        try {
            switch (extension) {
                case ".odt":
                case ".ods":
                case ".odp":
                case ".odg":
                case ".odc":
                case ".odf":
                case ".odb":
                case ".odi":
                case ".odm":
                    strRet = openOfficeDocumentToString(inputStream);
                    break;
                case ".doc":
                case ".docx":
                    strRet = microsoftWordDocumentToString(inputStream);
                    break;
                case ".ppt":
                case ".pps":
                case ".pptx":
                    strRet = microsoftPowerPointDocumentToString(inputStream);
                    break;
                case ".txt":
                case ".csv":
                    strRet = rawTextDocumentToString(inputStream);
                    break;
                case ".xls":
                case ".xlsx":
                    strRet = microsoftExcelDocumentToString(inputStream);
                    break;
                case ".pdf":
                    strRet = pdfDocumentToString(inputStream);
                    break;
                case ".html":
                case ".htm":
                case ".xml":
                case ".rtf":
                case ".msg":
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "The file " + fullName + " can't be indexed.", ex);
        }
        // todo : remove \n \r \t from string
        // text.replace("\n", "").replace("\r", "");
        return strRet;
    }

    private static String rawTextDocumentToString(InputStream inputStream) {
        return new Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();
    }

    private static String openOfficeDocumentToString(InputStream inputStream) throws IOException, SAXException, ParserConfigurationException {
        final StringBuilder text = new StringBuilder();

        try (ZipInputStream zipOpenDoc = new ZipInputStream(new BufferedInputStream(inputStream))) {
            ZipEntry zipEntry;

            while ((zipEntry = zipOpenDoc.getNextEntry()) != null) {
                if ("content.xml".equals(zipEntry.getName())) {
                    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                    SAXParser parser = saxParserFactory.newSAXParser();
                    parser.parse(zipOpenDoc, new DefaultHandler() {
                        @Override
                        public void characters(char[] ch,
                                               int start,
                                               int length)
                                throws SAXException {
                            for (int i = start; i < start + length; i++) {
                                text.append(ch[i]);
                            }
                            text.append("\r\n");
                        }
                    });
                    break;
                }
            }

        }

        return text.toString();
    }

    private static String microsoftWordDocumentToString(InputStream inputStream) throws IOException {
        String strRet;

        try (InputStream wordStream = new BufferedInputStream(inputStream)) {
            if (POIFSFileSystem.hasPOIFSHeader(wordStream)) {
                WordExtractor wordExtractor = new WordExtractor(wordStream);
                strRet = wordExtractor.getText();
                wordExtractor.close();
            } else {
                XWPFWordExtractor wordXExtractor = new XWPFWordExtractor(new XWPFDocument(wordStream));
                strRet = wordXExtractor.getText();
                wordXExtractor.close();
            }
        }

        return strRet;
    }

    private static String microsoftPowerPointDocumentToString(InputStream inputStream) throws IOException {
        String strRet;

        try (InputStream pptStream = new BufferedInputStream(inputStream)) {
            if (POIFSFileSystem.hasPOIFSHeader(pptStream)) {
                PowerPointExtractor pptExtractor = new PowerPointExtractor(pptStream);
                strRet = pptExtractor.getText(true, true);
                pptExtractor.close();
            } else {
                XSLFPowerPointExtractor pptExtractor = new XSLFPowerPointExtractor(new XMLSlideShow(pptStream));
                strRet = pptExtractor.getText(true, true, true);
                pptExtractor.close();
            }
        }

        return strRet;
    }

    private static String microsoftExcelDocumentToString(InputStream inputStream) throws IOException, OpenXML4JException, XmlException {
        StringBuilder sb = new StringBuilder();

        try (InputStream excelStream = new BufferedInputStream(inputStream)) {
            if (POIFSFileSystem.hasPOIFSHeader(excelStream)) { // Before 2007 format files
                POIFSFileSystem excelFS = new POIFSFileSystem(excelStream);
                ExcelExtractor excelExtractor = new ExcelExtractor(excelFS);
                sb.append(excelExtractor.getText());
                excelExtractor.close();
            } else { // New format
                XSSFWorkbook workBook = new XSSFWorkbook(excelStream);
                int numberOfSheets = workBook.getNumberOfSheets();
                for (int i = 0; i < numberOfSheets; i++) {
                    XSSFSheet sheet = workBook.getSheetAt(0);
                    Iterator<Row> rowIterator = sheet.rowIterator();
                    while (rowIterator.hasNext()) {
                        XSSFRow row = (XSSFRow) rowIterator.next();
                        Iterator<Cell> cellIterator = row.cellIterator();
                        while (cellIterator.hasNext()) {
                            XSSFCell cell = (XSSFCell) cellIterator.next();
                            sb.append(cell.toString());
                            sb.append(" ");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static String pdfDocumentToString(InputStream inputStream) throws IOException {
        PDDocument pdf = PDDocument.load(inputStream);
        String documentAsString = new PDFTextStripper().getText(pdf);
        pdf.close();
        return documentAsString;
    }
}
