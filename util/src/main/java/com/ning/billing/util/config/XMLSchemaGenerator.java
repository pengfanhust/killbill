/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

public class XMLSchemaGenerator {
    private static final int MAX_SCHEMA_SIZE_IN_BYTES = 100000;


    //Note: this main method is called by the maven build to generate the schema for the jar
    public static void main(final String[] args) throws IOException, TransformerException, JAXBException, ClassNotFoundException {
        if (args.length != 2) {
            printUsage();
            System.exit(0);
        }
        final Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass(args[1]);

        final JAXBContext context = JAXBContext.newInstance(clazz);
        String xsdFileName = "Schema.xsd";
        if (args.length != 0) {
            xsdFileName = args[0] + "/" + xsdFileName;
        }
        final FileOutputStream s = new FileOutputStream(xsdFileName);
        pojoToXSD(context, s);
    }

    private static void printUsage() {
        System.out.println(XMLSchemaGenerator.class.getName() + " <file> <class1>");

    }

    public static String xmlSchemaAsString(final Class<?> clazz) throws IOException, TransformerException, JAXBException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream(MAX_SCHEMA_SIZE_IN_BYTES);
        final JAXBContext context = JAXBContext.newInstance(clazz);
        pojoToXSD(context, output);
        return new String(output.toByteArray());
    }

    public static InputStream xmlSchema(final Class<?> clazz) throws IOException, TransformerException, JAXBException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream(MAX_SCHEMA_SIZE_IN_BYTES);
        final JAXBContext context = JAXBContext.newInstance(clazz);
        pojoToXSD(context, output);
        return new ByteArrayInputStream(output.toByteArray());
    }

    public static void pojoToXSD(final JAXBContext context, final OutputStream out)
            throws IOException, TransformerException {
        final List<DOMResult> results = new ArrayList<DOMResult>();

        context.generateSchema(new SchemaOutputResolver() {
            @Override
            public Result createOutput(final String ns, final String file)
                    throws IOException {
                final DOMResult result = new DOMResult();
                result.setSystemId(file);
                results.add(result);
                return result;
            }
        });

        final DOMResult domResult = results.get(0);
        final Document doc = (Document) domResult.getNode();

        // Use a Transformer for output
        final TransformerFactory tFactory = TransformerFactory.newInstance();
        final Transformer transformer = tFactory.newTransformer();

        final DOMSource source = new DOMSource(doc);
        final StreamResult result = new StreamResult(out);
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(source, result);
    }

}
