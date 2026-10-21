package com.cruise.pos.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvoicePdfResultTest {
    @Test
    void testEqualsAndHashCode() {
        byte[] content1 = new byte[]{1, 2, 3};
        byte[] content2 = new byte[]{1, 2, 3};
        byte[] content3 = new byte[]{4, 5, 6};

        InvoicePdfService.InvoicePdfResult result1 = new InvoicePdfService.InvoicePdfResult(content1, "file1.pdf", "INV-001");
        InvoicePdfService.InvoicePdfResult result2 = new InvoicePdfService.InvoicePdfResult(content2, "file1.pdf", "INV-001");
        InvoicePdfService.InvoicePdfResult result3 = new InvoicePdfService.InvoicePdfResult(content3, "file1.pdf", "INV-001");
        InvoicePdfService.InvoicePdfResult result4 = new InvoicePdfService.InvoicePdfResult(content1, "file2.pdf", "INV-001");
        InvoicePdfService.InvoicePdfResult result5 = new InvoicePdfService.InvoicePdfResult(content1, "file1.pdf", "INV-002");

        // Equals
        assertEquals(result1, result1);
        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
        assertNotEquals(result1, result4);
        assertNotEquals(result1, result5);
        assertNotEquals(result1, null);
        assertNotEquals(result1, new Object());

        // HashCode
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1.hashCode(), result3.hashCode());
        assertNotEquals(result1.hashCode(), result4.hashCode());
        assertNotEquals(result1.hashCode(), result5.hashCode());

        // toString
        String toString = result1.toString();
        assertTrue(toString.contains("file1.pdf"));
        assertTrue(toString.contains("INV-001"));
        assertTrue(toString.contains("contentLength=3"));

        InvoicePdfService.InvoicePdfResult nullContent = new InvoicePdfService.InvoicePdfResult(null, "file.pdf", "INV");
        assertTrue(nullContent.toString().contains("contentLength=0"));
    }
}
