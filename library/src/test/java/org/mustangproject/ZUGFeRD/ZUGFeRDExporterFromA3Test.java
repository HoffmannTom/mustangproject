package org.mustangproject.ZUGFeRD;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.XmpConstants;
import org.apache.xmpbox.schema.PDFAExtensionSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.schema.XMPSchema;
import org.apache.xmpbox.type.ArrayProperty;
import org.apache.xmpbox.type.Attribute;
import org.apache.xmpbox.type.Cardinality;
import org.apache.xmpbox.type.DefinedStructuredType;
import org.apache.xmpbox.type.TextType;
import org.apache.xmpbox.xml.DomXmpParser;
import org.apache.xmpbox.xml.XmpSerializer;
import org.junit.jupiter.api.Test;

public class ZUGFeRDExporterFromA3Test {

	private static final String PRESET_DESCRIPTION = "DescriptionTest";
    private static final String PRESET_SUBJECT = "SubjectTest";
    private static final String PRESET_LABEL = "LabelTest";
    private static final String PRESET_ADOBE_KEYWORDS = "AdobeKeywordsTest";

	@Test
	public void testPrepareDocumentPreservesUntouchedMetadata() throws Exception {
		byte[] pdfBytes = createPdfWithPresetMetadata();

        try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
            exporter.load(pdfBytes);
            // test override behaviour
            exporter.overwrite = false;
            // the preset has no title, so only the title setter takes effect
            exporter.setTitle("My Invoice").setSubject("My Subject").setKeywords("Invoice, ZUGFeRD");
            exporter.prepareDocument();

            PDMetadata meta = exporter.doc.getDocumentCatalog().getMetadata();
            XMPMetadata xmp = new DomXmpParser().parse(meta.toByteArray());

            DublinCoreSchema dc = xmp.getDublinCoreSchema();
            assertEquals(PRESET_DESCRIPTION, dc.getDescription());
            assertTrue(dc.getSubjects().contains(PRESET_SUBJECT));

            XMPBasicSchema xsb = xmp.getXMPBasicSchema();
            assertEquals(PRESET_LABEL, xsb.getLabel());

            AdobePDFSchema adobePdf = xmp.getAdobePDFSchema();
            assertEquals(PRESET_ADOBE_KEYWORDS, adobePdf.getKeywords());

            assertDescriptiveMetadata(exporter, "My Invoice", PRESET_DESCRIPTION, PRESET_ADOBE_KEYWORDS);
        }
	}

	@Test
	public void testSettersOverrideDescriptiveMetadata() throws Exception {
		byte[] pdfBytes = createPdfWithXmpDescriptiveMetadata("Input Title", "Input Subject", "Input Keywords");

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.setTitle("My Invoice").setSubject("My Subject").setKeywords("Invoice, ZUGFeRD");
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, "My Invoice", "My Subject", "Invoice, ZUGFeRD");
		}
	}

	@Test
	public void testBlankSettersClearDescriptiveMetadata() throws Exception {
		byte[] pdfBytes = createPdfWithXmpDescriptiveMetadata("Input Title", "Input Subject", "Input Keywords");

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.setTitle("").setSubject(" ").setKeywords("");
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, null, null, null);
		}
	}

	@Test
	public void testBlankSettersDoNotClearDescriptiveMetadataWithoutOverwrite() throws Exception {
		byte[] pdfBytes = createPdfWithXmpDescriptiveMetadata("Input Title", "Input Subject", "Input Keywords");

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.overwrite = false;
			exporter.setTitle("").setSubject(" ").setKeywords("");
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, "Input Title", "Input Subject", "Input Keywords");
		}
	}

	@Test
	public void testPrepareDocumentRetainsXmpDescriptiveMetadata() throws Exception {
		byte[] pdfBytes = createPdfWithXmpDescriptiveMetadata("Input Title", "Input Subject", "Input Keywords");

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, "Input Title", "Input Subject", "Input Keywords");
		}
	}

	@Test
	public void testPrepareDocumentFallsBackToDocumentInformation() throws Exception {
		byte[] pdfBytes = createPdfWithDocumentInformation("Info Title", "Info Subject", "Info Keywords");

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, "Info Title", "Info Subject", "Info Keywords");
		}
	}

	@Test
	public void testPrepareDocumentIgnoresGhostscriptUntitled() throws Exception {
		byte[] pdfBytes = createPdfWithXmpDescriptiveMetadata("Untitled", null, null);

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, null, null, null);
		}
	}

	@Test
	public void testPrepareDocumentPrefersInfoTitleOverGhostscriptUntitled() throws Exception {
		byte[] pdfBytes;
		try (PDDocument doc = Loader.loadPDF(createPdfWithXmpDescriptiveMetadata("Untitled", null, null))) {
			doc.getDocumentInformation().setTitle("Invoice 4711");
			pdfBytes = save(doc);
		}

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, "Invoice 4711", null, null);
		}
	}

	@Test
	public void testPrepareDocumentIgnoresGhostscriptUntitledInDocumentInformation() throws Exception {
		byte[] pdfBytes = createPdfWithDocumentInformation("Untitled", null, null);

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(pdfBytes);
			exporter.prepareDocument();

			assertDescriptiveMetadata(exporter, null, null, null);
		}
	}

	@Test
	public void testRepeatedExportWithoutOverwriteDoesNotDuplicateXmpSchemas() throws Exception {
		byte[] firstPass;
		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(createPdfWithPresetMetadata());
			exporter.overwrite = false;
			exporter.prepareDocument();
			firstPass = save(exporter.doc);
		}

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(firstPass);
			exporter.overwrite = false;
			exporter.prepareDocument();

			PDMetadata meta = exporter.doc.getDocumentCatalog().getMetadata();
			DomXmpParser parser = new DomXmpParser();
			parser.setStrictParsing(false);
			XMPMetadata xmp = parser.parse(meta.toByteArray());

			assertEquals(1, countSchemas(xmp, PDFA_EXTENSION_NAMESPACE));
			assertEquals(1, countSchemas(xmp, exporter.getNamespaceForVersion(exporter.zfVersion)));

			String raw = new String(meta.toByteArray(), StandardCharsets.UTF_8);
			assertEquals(1, countOccurrences(raw, "<pdfaSchema:namespaceURI>"));
			assertEquals(1, countOccurrences(raw, "<" + exporter.getPrefixForVersion(exporter.zfVersion) + ":DocumentFileName>"));
		}
	}

	@Test
	public void testExportWithoutOverwriteKeepsForeignExtensionSchema() throws Exception {
		XMPMetadata input = XMPMetadata.createXMPMetadata();
		input.createAndAddDublinCoreSchema().setDescription(PRESET_DESCRIPTION);
		PDFAExtensionSchema foreign = new PDFAExtensionSchema(input);
		foreign.addNamespace("http://www.aiim.org/pdfa/ns/schema#", "pdfaSchema");
		foreign.addNamespace("http://www.aiim.org/pdfa/ns/property#", "pdfaProperty");
		ArrayProperty bag = new ArrayProperty(input, foreign.getNamespace(), foreign.getPrefix(), "schemas", Cardinality.Bag);
		DefinedStructuredType li = new DefinedStructuredType(input, foreign.getNamespace(), foreign.getPrefix(), XmpConstants.LIST_NAME);
		li.setAttribute(new Attribute(foreign.getNamespace(), XmpConstants.PARSE_TYPE, XmpConstants.RESOURCE_NAME));
		li.addProperty(new TextType(input, "http://www.aiim.org/pdfa/ns/schema#", "pdfaSchema", "schema", "Foreign Schema"));
		li.addProperty(new TextType(input, "http://www.aiim.org/pdfa/ns/schema#", "pdfaSchema", "namespaceURI", FOREIGN_NAMESPACE));
		li.addProperty(new TextType(input, "http://www.aiim.org/pdfa/ns/schema#", "pdfaSchema", "prefix", "foreign"));
		bag.addProperty(li);
		foreign.addProperty(bag);
		input.addSchema(foreign);

		try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
			exporter.load(createPdfWithXmp(input));
			exporter.overwrite = false;
			exporter.prepareDocument();

			PDMetadata meta = exporter.doc.getDocumentCatalog().getMetadata();
			DomXmpParser parser = new DomXmpParser();
			parser.setStrictParsing(false);
			XMPMetadata xmp = parser.parse(meta.toByteArray());
			assertEquals(1, countSchemas(xmp, PDFA_EXTENSION_NAMESPACE));

			String raw = new String(meta.toByteArray(), StandardCharsets.UTF_8);
			assertEquals(2, countOccurrences(raw, "<pdfaSchema:namespaceURI>"));
			assertTrue(raw.contains(FOREIGN_NAMESPACE));
			assertTrue(raw.contains(exporter.getNamespaceForVersion(exporter.zfVersion)));
		}
	}

	private static final String PDFA_EXTENSION_NAMESPACE = "http://www.aiim.org/pdfa/ns/extension/";
	private static final String FOREIGN_NAMESPACE = "http://example.org/foreign/ns#";

	private int countSchemas(XMPMetadata xmp, String namespace) {
		int count = 0;
		for (XMPSchema schema : xmp.getAllSchemas()) {
			if (namespace.equals(schema.getNamespace())) {
				count++;
			}
		}
		return count;
	}

	private int countOccurrences(String haystack, String needle) {
		int count = 0;
		for (int idx = haystack.indexOf(needle); idx >= 0; idx = haystack.indexOf(needle, idx + needle.length())) {
			count++;
		}
		return count;
	}

	private void assertDescriptiveMetadata(ZUGFeRDExporterFromA3 exporter, String title, String subject, String keywords) throws Exception {
		PDDocumentInformation info = exporter.doc.getDocumentInformation();
		assertEquals(title, info.getTitle());
		assertEquals(subject, info.getSubject());
		assertEquals(keywords, info.getKeywords());

		PDMetadata meta = exporter.doc.getDocumentCatalog().getMetadata();
		XMPMetadata xmp = new DomXmpParser().parse(meta.toByteArray());
		assertEquals(title, xmp.getDublinCoreSchema().getTitle());
		assertEquals(subject, xmp.getDublinCoreSchema().getDescription());
		assertEquals(keywords, xmp.getAdobePDFSchema().getKeywords());
	}

	private byte[] createPdfWithPresetMetadata() throws Exception {
		XMPMetadata xmp = XMPMetadata.createXMPMetadata();

		DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
		dc.setDescription(PRESET_DESCRIPTION);
		dc.addSubject(PRESET_SUBJECT);

		XMPBasicSchema xsb = xmp.createAndAddXMPBasicSchema();
		xsb.setLabel(PRESET_LABEL);

		AdobePDFSchema adobePdf = xmp.createAndAddAdobePDFSchema();
		adobePdf.setKeywords(PRESET_ADOBE_KEYWORDS);

		return createPdfWithXmp(xmp);
	}

	private byte[] createPdfWithXmpDescriptiveMetadata(String title, String description, String keywords) throws Exception {
		XMPMetadata xmp = XMPMetadata.createXMPMetadata();

		DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
		if (title != null) {
			dc.setTitle(title);
		}
		if (description != null) {
			dc.setDescription(description);
		}
		if (keywords != null) {
			xmp.createAndAddAdobePDFSchema().setKeywords(keywords);
		}

		return createPdfWithXmp(xmp);
	}

	private byte[] createPdfWithXmp(XMPMetadata xmp) throws Exception {
		try (PDDocument doc = createDocument()) {
			ByteArrayOutputStream xmpBytes = new ByteArrayOutputStream();
			new XmpSerializer().serialize(xmp, xmpBytes, true);

			PDMetadata metadata = new PDMetadata(doc);
			metadata.importXMPMetadata(xmpBytes.toByteArray());
			doc.getDocumentCatalog().setMetadata(metadata);

			return save(doc);
		}
	}

	private byte[] createPdfWithDocumentInformation(String title, String subject, String keywords) throws Exception {
		try (PDDocument doc = createDocument()) {
			PDDocumentInformation info = doc.getDocumentInformation();
			info.setTitle(title);
			info.setSubject(subject);
			info.setKeywords(keywords);
			return save(doc);
		}
	}

	private PDDocument createDocument() {
		PDDocument doc = new PDDocument();
		PDPage page = new PDPage();
		page.setResources(new PDResources());
		doc.addPage(page);
		return doc;
	}

	private byte[] save(PDDocument doc) throws Exception {
		ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
		doc.save(pdfBytes);
		return pdfBytes.toByteArray();
	}

}
