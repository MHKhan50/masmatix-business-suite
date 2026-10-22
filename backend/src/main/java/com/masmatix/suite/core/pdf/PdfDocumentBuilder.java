package com.masmatix.suite.core.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Small wrapper used for receipts, invoices and prescriptions:
 * a title block, a few key/value lines, one table and a totals block.
 */
public class PdfDocumentBuilder {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final Document doc;

    public PdfDocumentBuilder(boolean receiptWidth) {
        // 80 mm thermal receipt is about 226 points wide.
        doc = receiptWidth ? new Document(new com.lowagie.text.Rectangle(226, 600), 12, 12, 12, 12)
                           : new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(doc, out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not start PDF", e);
        }
        doc.open();
    }

    public PdfDocumentBuilder title(String text) {
        Paragraph p = new Paragraph(text, TITLE);
        p.setAlignment(Element.ALIGN_CENTER);
        add(p);
        return this;
    }

    public PdfDocumentBuilder line(String text) {
        add(new Paragraph(text, NORMAL));
        return this;
    }

    public PdfDocumentBuilder table(List<String> headers, List<List<String>> rows, float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        for (String h : headers) {
            t.addCell(cell(h, BOLD));
        }
        for (List<String> r : rows) {
            for (String c : r) {
                t.addCell(cell(c, NORMAL));
            }
        }
        add(t);
        return this;
    }

    public PdfDocumentBuilder total(String label, String value) {
        Paragraph p = new Paragraph(label + "  " + value, BOLD);
        p.setAlignment(Element.ALIGN_RIGHT);
        add(p);
        return this;
    }

    public byte[] build() {
        doc.close();
        return out.toByteArray();
    }

    private void add(Element element) {
        try {
            doc.add(element);
        } catch (Exception e) {
            throw new IllegalStateException("Could not write PDF", e);
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorderWidth(0.3f);
        c.setPadding(2);
        return c;
    }
}
