/**
 * @module api/pdf
 * @description PDF document generation using jsPDF.
 * Professional formatting with company header for job reports, estimates,
 * and invoices. All documents include branding and legal disclaimers.
 */

import { jsPDF } from 'jspdf';
import 'jspdf-autotable';

// ─── Constants ───────────────────────────────────────────────────────────────

const COMPANY = {
  name: 'Wildlife Whisperer LLC',
  slogan: 'Professional Wildlife Removal & Exclusion',
  phone: '(555) 123-4567',
  email: 'info@wildlifewhisperer.com',
  website: 'www.wildlifewhisperer.com',
  address: '123 Wildlife Way, Natureville, ST 12345',
  license: 'NWCO-LIC-2024-001',
  insurance: 'Insured & Bonded'
};

const COLORS = {
  primary: [34, 197, 94], // Green #22c55e
  dark: [18, 18, 18], // Near black #121212
  gray: [120, 120, 120], // Medium gray
  lightGray: [220, 220, 220], // Light gray for borders
  white: [255, 255, 255],
  red: [239, 68, 68], // Red for totals/due
  yellow: [251, 191, 36] // Yellow for warnings
};

const STATUS_COLORS = {
  Active: [34, 197, 94],
  Scheduled: [59, 130, 246],
  'In Progress': [251, 191, 36],
  'Needs Follow-up': [249, 115, 22],
  Closed: [120, 120, 120],
  Cancelled: [239, 68, 68]
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatCurrency(amount) {
  const num = parseFloat(amount) || 0;
  return `$${num.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`;
}

function formatDate(dateStr) {
  if (!dateStr) return 'N/A';
  try {
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  } catch {
    return dateStr;
  }
}

function addPageHeader(doc, title, subtitle = '') {
  const pageW = doc.internal.pageSize.getWidth();

  // Header background
  doc.setFillColor(...COLORS.dark);
  doc.rect(0, 0, pageW, 40, 'F');

  // Company name
  doc.setTextColor(...COLORS.primary);
  doc.setFontSize(18);
  doc.setFont('helvetica', 'bold');
  doc.text(COMPANY.name, 15, 18);

  // Slogan
  doc.setTextColor(...COLORS.gray);
  doc.setFontSize(9);
  doc.setFont('helvetica', 'italic');
  doc.text(COMPANY.slogan, 15, 25);

  // Contact info (right-aligned)
  doc.setTextColor(...COLORS.white);
  doc.setFontSize(8);
  doc.setFont('helvetica', 'normal');
  const rightX = pageW - 15;
  doc.text(COMPANY.phone, rightX, 14, { align: 'right' });
  doc.text(COMPANY.email, rightX, 19, { align: 'right' });
  doc.text(COMPANY.website, rightX, 24, { align: 'right' });
  doc.text(COMPANY.address, rightX, 29, { align: 'right' });

  // Document title
  doc.setTextColor(...COLORS.dark);
  doc.setFontSize(16);
  doc.setFont('helvetica', 'bold');
  doc.text(title, 15, 52);

  if (subtitle) {
    doc.setTextColor(...COLORS.gray);
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text(subtitle, 15, 59);
  }

  // Separator line
  doc.setDrawColor(...COLORS.primary);
  doc.setLineWidth(0.5);
  doc.line(15, subtitle ? 63 : 57, pageW - 15, subtitle ? 63 : 57);

  return subtitle ? 68 : 62;
}

function addFooter(doc, pageNum) {
  const pageW = doc.internal.pageSize.getWidth();
  const pageH = doc.internal.pageSize.getHeight();

  doc.setDrawColor(...COLORS.lightGray);
  doc.setLineWidth(0.3);
  doc.line(15, pageH - 20, pageW - 15, pageH - 20);

  doc.setTextColor(...COLORS.gray);
  doc.setFontSize(8);
  doc.setFont('helvetica', 'normal');
  doc.text(
    `${COMPANY.name} | ${COMPANY.phone} | ${COMPANY.website} | License: ${COMPANY.license} | ${COMPANY.insurance}`,
    pageW / 2,
    pageH - 12,
    { align: 'center' }
  );
  doc.text(`Page ${pageNum}`, pageW - 15, pageH - 12, { align: 'right' });
}

function addLegalDisclaimer(doc, y) {
  const pageW = doc.internal.pageSize.getWidth();
  doc.setTextColor(...COLORS.gray);
  doc.setFontSize(7);
  doc.setFont('helvetica', 'italic');

  const disclaimer =
    'This document is for service estimation purposes only. Final charges may vary based on actual ' +
    'field conditions. All wildlife services are subject to applicable state and federal regulations. ' +
    'Payment terms: Net 15 days. A 1.5% monthly late fee applies to overdue balances. ' +
    'Warranty applies only to sealed/repaired areas as documented. This is not an invoice.';

  const splitText = doc.splitTextToSize(disclaimer, pageW - 30);
  doc.text(splitText, 15, y);
  return y + splitText.length * 3 + 5;
}

// ─── Public API ──────────────────────────────────────────────────────────────

/**
 * Generate a complete job PDF report including customer info,
 * services, photos checklist, and status history.
 *
 * @param {Object} job - Job record
 * @param {Array} [services=[]] - Service line items
 * @param {Array} [photos=[]] - Photo records
 * @returns {jsPDF} PDF document (call .save() or .output() on it)
 */
export function generateJobPDF(job, services = [], photos = []) {
  if (!job) throw new Error('Job data is required');

  const doc = new jsPDF({ unit: 'mm', format: 'letter' });
  const pageW = doc.internal.pageSize.getWidth();
  let y = addPageHeader(doc, 'JOB REPORT', `Job #${job.id?.slice(0, 8) || 'N/A'} | ${formatDate(job.created_at)}`);
  let pageNum = 1;

  // ─── Customer Section ────────────────────────────────────────────────────
  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...COLORS.dark);
  doc.text('CUSTOMER INFORMATION', 15, y);
  y += 7;

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');

  const custFields = [
    ['Customer:', job.customer || 'N/A'],
    ['Phone:', job.phone || 'N/A'],
    ['Email:', job.email || 'N/A'],
    ['Address:', [job.address, job.town, job.state, job.zip].filter(Boolean).join(', ') || 'N/A']
  ];

  custFields.forEach(([label, value]) => {
    doc.setFont('helvetica', 'bold');
    doc.text(label, 20, y);
    doc.setFont('helvetica', 'normal');
    doc.text(value, 55, y);
    y += 5;
  });

  // ─── Job Details ─────────────────────────────────────────────────────────
  y += 5;
  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.text('JOB DETAILS', 15, y);
  y += 7;

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');

  const statusColor = STATUS_COLORS[job.status] || COLORS.gray;
  const jobFields = [
    ['Species:', job.species || 'N/A'],
    ['Status:', job.status || 'N/A'],
    ['Priority:', job.priority || 'Normal'],
    ['Assigned Tech:', job.assigned_tech || 'Unassigned'],
    ['Scope:', job.scope || 'N/A'],
    ['Warranty:', job.warranty || 'Not set'],
    ['Scheduled:', job.scheduled_start ? formatDate(job.scheduled_start) : 'Not scheduled']
  ];

  jobFields.forEach(([label, value]) => {
    doc.setFont('helvetica', 'bold');
    doc.text(label, 20, y);
    doc.setFont('helvetica', 'normal');

    if (label === 'Status:') {
      doc.setTextColor(...statusColor);
    }
    doc.text(value, 55, y);
    doc.setTextColor(...COLORS.dark);
    y += 5;
  });

  // ─── GPS ─────────────────────────────────────────────────────────────────
  if (job.latitude && job.longitude) {
    y += 2;
    doc.setFontSize(9);
    doc.setTextColor(...COLORS.gray);
    doc.text(`GPS: ${job.latitude}, ${job.longitude}${job.accuracy ? ` (accuracy: ${job.accuracy}m)` : ''}`, 20, y);
    y += 5;
  }

  // ─── Notes ───────────────────────────────────────────────────────────────
  if (job.notes || job.ai_notes) {
    y += 3;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...COLORS.dark);
    doc.text('NOTES', 15, y);
    y += 5;

    if (job.notes) {
      doc.setFont('helvetica', 'normal');
      const noteLines = doc.splitTextToSize(job.notes, pageW - 35);
      doc.text(noteLines, 20, y);
      y += noteLines.length * 4 + 3;
    }

    if (job.ai_notes) {
      doc.setFont('helvetica', 'bold');
      doc.text('AI Insights:', 20, y);
      y += 4;
      doc.setFont('helvetica', 'normal');
      const aiLines = doc.splitTextToSize(job.ai_notes, pageW - 35);
      doc.text(aiLines, 20, y);
      y += aiLines.length * 4 + 3;
    }
  }

  // ─── Services Table ──────────────────────────────────────────────────────
  if (services.length > 0) {
    y += 5;
    if (y > 230) {
      doc.addPage();
      y = addPageHeader(doc, 'JOB REPORT (continued)');
      pageNum++;
    }

    doc.setFontSize(11);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...COLORS.dark);
    doc.text('SERVICES', 15, y);
    y += 5;

    const tableHeaders = [['Service', 'Qty', 'Unit Price', 'Total', 'Notes']];
    const tableData = services.map(s => [
      s.service || '',
      String(s.qty || 1),
      formatCurrency(s.unit_price),
      formatCurrency(s.total || (s.qty || 1) * (s.unit_price || 0)),
      s.notes || ''
    ]);

    doc.autoTable({
      head: tableHeaders,
      body: tableData,
      startY: y,
      theme: 'striped',
      headStyles: { fillColor: COLORS.dark, textColor: COLORS.white, fontSize: 9 },
      bodyStyles: { fontSize: 9, textColor: COLORS.dark },
      alternateRowStyles: { fillColor: [245, 245, 245] },
      columnStyles: {
        0: { cellWidth: 60 },
        1: { cellWidth: 15, halign: 'center' },
        2: { cellWidth: 30, halign: 'right' },
        3: { cellWidth: 30, halign: 'right' },
        4: { cellWidth: 'auto' }
      },
      margin: { left: 15, right: 15 }
    });

    y = doc.lastAutoTable.finalY + 5;

    // Totals
    const subtotal = parseFloat(job.subtotal) || services.reduce((s, svc) => s + (parseFloat(svc.total) || 0), 0);
    const taxRate = parseFloat(job.tax_rate) || 0;
    const taxAmount = parseFloat(job.tax_amount) || subtotal * taxRate;
    const grandTotal = parseFloat(job.grand_total) || subtotal + taxAmount;
    const deposit = parseFloat(job.deposit_paid) || 0;
    const balance = parseFloat(job.balance_due) || grandTotal - deposit;

    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    const totalsX = pageW - 60;

    doc.text('Subtotal:', totalsX - 20, y, { align: 'right' });
    doc.text(formatCurrency(subtotal), totalsX + 15, y, { align: 'right' });
    y += 5;

    doc.text(`Tax (${(taxRate * 100).toFixed(2)}%):`, totalsX - 20, y, { align: 'right' });
    doc.text(formatCurrency(taxAmount), totalsX + 15, y, { align: 'right' });
    y += 5;

    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...COLORS.primary);
    doc.text('GRAND TOTAL:', totalsX - 20, y, { align: 'right' });
    doc.text(formatCurrency(grandTotal), totalsX + 15, y, { align: 'right' });
    y += 5;

    if (deposit > 0) {
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(...COLORS.dark);
      doc.text('Deposit Paid:', totalsX - 20, y, { align: 'right' });
      doc.text(formatCurrency(deposit), totalsX + 15, y, { align: 'right' });
      y += 5;

      doc.setFont('helvetica', 'bold');
      doc.setTextColor(...COLORS.red);
      doc.text('BALANCE DUE:', totalsX - 20, y, { align: 'right' });
      doc.text(formatCurrency(balance), totalsX + 15, y, { align: 'right' });
      y += 5;
    }
    doc.setTextColor(...COLORS.dark);
  }

  // ─── Photos Section ──────────────────────────────────────────────────────
  if (photos.length > 0) {
    y += 5;
    if (y > 230) {
      doc.addPage();
      y = addPageHeader(doc, 'JOB REPORT (continued)');
      pageNum++;
    }

    doc.setFontSize(11);
    doc.setFont('helvetica', 'bold');
    doc.text('PHOTOS', 15, y);
    y += 5;

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    photos.forEach((photo, i) => {
      const line = `${i + 1}. [${photo.tag || 'General'}] ${photo.notes || 'No notes'} — ${formatDate(photo.created_at)}`;
      const lines = doc.splitTextToSize(line, pageW - 35);
      doc.text(lines, 20, y);
      y += lines.length * 3.5 + 1;
    });
  }

  // ─── Footer ──────────────────────────────────────────────────────────────
  y += 8;
  addLegalDisclaimer(doc, y);
  addFooter(doc, pageNum);

  return doc;
}

/**
 * Generate an estimate PDF.
 *
 * @param {Object} job - Job record
 * @param {Object} [estimate={}] - Estimate details
 * @param {number} [estimate.basePrice] - Base price for species
 * @param {number} [estimate.severityMultiplier] - Severity adjustment
 * @param {string} [estimate.severity] - Severity level
 * @param {string} [estimate.notes] - Additional notes
 * @returns {jsPDF}
 */
export function generateEstimatePDF(job, estimate = {}) {
  if (!job) throw new Error('Job data is required');

  const doc = new jsPDF({ unit: 'mm', format: 'letter' });
  const pageW = doc.internal.pageSize.getWidth();
  let y = addPageHeader(doc, 'SERVICE ESTIMATE', `Valid for 30 days from ${formatDate(new Date())}`);
  let pageNum = 1;

  // ─── Estimate Info ───────────────────────────────────────────────────────
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');

  const estFields = [
    ['Customer:', job.customer || 'N/A'],
    ['Property:', [job.address, job.town].filter(Boolean).join(', ') || 'N/A'],
    ['Species:', job.species || 'Unknown'],
    ['Severity:', estimate.severity || 'Medium'],
    ['Date:', formatDate(new Date())]
  ];

  estFields.forEach(([label, value]) => {
    doc.setFont('helvetica', 'bold');
    doc.text(label, 20, y);
    doc.setFont('helvetica', 'normal');
    doc.text(value, 55, y);
    y += 6;
  });

  // ─── Pricing ─────────────────────────────────────────────────────────────
  y += 5;
  doc.setDrawColor(...COLORS.lightGray);
  doc.setLineWidth(0.3);
  doc.line(15, y, pageW - 15, y);
  y += 8;

  const basePrice = parseFloat(estimate.basePrice) || parseFloat(job.estimate) || 0;
  const multiplier = parseFloat(estimate.severityMultiplier) || 1.35;
  const subtotal = Math.round(basePrice * multiplier * 100) / 100;
  const taxRate = parseFloat(job.tax_rate) || 0;
  const tax = Math.round(subtotal * taxRate * 100) / 100;
  const total = Math.round((subtotal + tax) * 100) / 100;

  const pricingRows = [
    [`Base Service Fee (species: ${job.species || 'General'})`, formatCurrency(basePrice)],
    [`Complexity Adjustment (${estimate.severity || 'Medium'})`, `x ${multiplier.toFixed(2)}`],
    ['', ''],
    ['Subtotal', formatCurrency(subtotal)]
  ];

  if (taxRate > 0) {
    pricingRows.push([`Tax (${(taxRate * 100).toFixed(2)}%)`, formatCurrency(tax)]);
  }

  pricingRows.forEach(([label, value], i) => {
    if (label === '') {
      y += 3;
      return;
    }
    doc.setFont('helvetica', i >= 3 ? 'bold' : 'normal');
    doc.setTextColor(...(i >= 3 ? COLORS.dark : COLORS.gray));
    doc.setFontSize(i >= 3 ? 12 : 10);
    doc.text(label, 20, y);
    doc.text(value, pageW - 30, y, { align: 'right' });
    y += 7;
  });

  // Total box
  y += 3;
  doc.setFillColor(...COLORS.dark);
  doc.rect(15, y - 5, pageW - 30, 15, 'F');
  doc.setTextColor(...COLORS.primary);
  doc.setFontSize(14);
  doc.setFont('helvetica', 'bold');
  doc.text('ESTIMATED TOTAL', 25, y + 5);
  doc.text(formatCurrency(total), pageW - 25, y + 5, { align: 'right' });
  y += 20;

  doc.setTextColor(...COLORS.dark);

  // ─── Scope Description ───────────────────────────────────────────────────
  if (job.scope) {
    doc.setFontSize(11);
    doc.setFont('helvetica', 'bold');
    doc.text('SCOPE OF WORK', 15, y);
    y += 6;

    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    const scopeLines = doc.splitTextToSize(job.scope, pageW - 35);
    doc.text(scopeLines, 20, y);
    y += scopeLines.length * 4 + 5;
  }

  // ─── Estimate Notes ──────────────────────────────────────────────────────
  if (estimate.notes) {
    doc.setFontSize(11);
    doc.setFont('helvetica', 'bold');
    doc.text('ADDITIONAL NOTES', 15, y);
    y += 6;

    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    const noteLines = doc.splitTextToSize(estimate.notes, pageW - 35);
    doc.text(noteLines, 20, y);
    y += noteLines.length * 4 + 5;
  }

  // ─── Standard Inclusions ─────────────────────────────────────────────────
  y += 3;
  if (y > 220) {
    doc.addPage();
    y = addPageHeader(doc, 'SERVICE ESTIMATE (continued)');
    pageNum++;
  }

  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.text('ESTIMATE INCLUDES:', 15, y);
  y += 6;

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');

  const inclusions = [
    'Initial inspection and wildlife species identification',
    'Entry point assessment and exclusion plan',
    'Installation of professional-grade exclusion devices',
    'Humane trapping and removal services',
    'Sealing of all identified entry/exit points',
    'Damage repair to affected areas (as specified)',
    'Cleanup and sanitization of wildlife-affected zones',
    'Written warranty on all sealed areas',
    'Follow-up inspection to confirm resolution'
  ];

  inclusions.forEach(item => {
    doc.text(`\u2022 ${item}`, 22, y);
    y += 5;
  });

  // ─── Exclusions ──────────────────────────────────────────────────────────
  y += 3;
  doc.setFont('helvetica', 'bold');
  doc.text('NOT INCLUDED (unless specified above):', 15, y);
  y += 6;

  doc.setFont('helvetica', 'normal');
  const exclusions = [
    'Attic insulation replacement',
    'Major structural repairs beyond entry points',
    'Electrical or plumbing repairs',
    'Ongoing pest control for insects/rodents post-exclusion'
  ];

  exclusions.forEach(item => {
    doc.text(`\u2022 ${item}`, 22, y);
    y += 5;
  });

  // ─── Acceptance ──────────────────────────────────────────────────────────
  y += 8;
  doc.setDrawColor(...COLORS.lightGray);
  doc.setLineWidth(0.3);
  doc.line(15, y, pageW - 15, y);
  y += 8;

  doc.setFontSize(10);
  doc.setFont('helvetica', 'bold');
  doc.text('CUSTOMER ACCEPTANCE', 15, y);
  y += 8;

  doc.setFont('helvetica', 'normal');
  doc.text('I authorize Wildlife Whisperer LLC to perform the services outlined above.', 20, y);
  y += 8;

  doc.text('Customer Signature: _________________________________', 20, y);
  y += 8;
  doc.text('Date: _______________________', 20, y);
  y += 8;
  doc.text('Technician Signature: _________________________________', 20, y);

  // ─── Footer ──────────────────────────────────────────────────────────────
  addLegalDisclaimer(doc, doc.internal.pageSize.getHeight() - 30);
  addFooter(doc, pageNum);

  return doc;
}

/**
 * Generate an invoice PDF.
 *
 * @param {Object} job - Job record
 * @param {Object} [options={}] - Invoice options
 * @param {string} [options.invoiceNumber] - Custom invoice number
 * @param {string} [options.dueDate] - Payment due date
 * @param {Array} [options.lineItems] - Override line items
 * @param {string} [options.notes] - Invoice-specific notes
 * @returns {jsPDF}
 */
export function generateInvoicePDF(job, options = {}) {
  if (!job) throw new Error('Job data is required');

  const doc = new jsPDF({ unit: 'mm', format: 'letter' });
  const pageW = doc.internal.pageSize.getWidth();
  const invoiceNumber =
    options.invoiceNumber || `INV-${job.id?.slice(0, 8).toUpperCase() || Date.now().toString(36).toUpperCase()}`;
  const invoiceDate = formatDate(new Date());
  const dueDate = options.dueDate
    ? formatDate(options.dueDate)
    : formatDate(new Date(Date.now() + 15 * 24 * 60 * 60 * 1000));

  let y = addPageHeader(doc, 'INVOICE', `Invoice #: ${invoiceNumber}`);
  const pageNum = 1;

  // ─── Invoice Meta ────────────────────────────────────────────────────────
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');

  const metaFields = [
    ['Invoice Date:', invoiceDate],
    ['Due Date:', dueDate],
    ['Terms:', 'Net 15'],
    ['Job ID:', job.id?.slice(0, 8) || 'N/A']
  ];

  metaFields.forEach(([label, value]) => {
    doc.setFont('helvetica', 'bold');
    doc.text(label, pageW - 75, y);
    doc.setFont('helvetica', 'normal');
    doc.text(value, pageW - 40, y);
    y += 5;
  });

  // ─── Bill To ─────────────────────────────────────────────────────────────
  y += 5;
  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...COLORS.dark);
  doc.text('BILL TO', 15, y);
  y += 6;

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.text(job.customer || 'N/A', 15, y);
  y += 5;
  doc.text([job.address, job.town, job.state, job.zip].filter(Boolean).join(', ') || 'N/A', 15, y);
  y += 5;
  if (job.phone) doc.text(`Phone: ${job.phone}`, 15, y);
  y += 5;
  if (job.email) doc.text(`Email: ${job.email}`, 15, y);
  y += 8;

  // ─── Line Items ──────────────────────────────────────────────────────────
  doc.setDrawColor(...COLORS.lightGray);
  doc.line(15, y, pageW - 15, y);
  y += 5;

  const lineItems = options.lineItems || [];

  if (lineItems.length > 0) {
    doc.autoTable({
      head: [['Description', 'Qty', 'Rate', 'Amount']],
      body: lineItems.map(item => [
        item.description || '',
        String(item.qty || 1),
        formatCurrency(item.rate || item.unit_price || 0),
        formatCurrency((item.qty || 1) * (item.rate || item.unit_price || 0))
      ]),
      startY: y,
      theme: 'plain',
      headStyles: {
        fillColor: COLORS.dark,
        textColor: COLORS.white,
        fontSize: 9,
        fontStyle: 'bold'
      },
      bodyStyles: { fontSize: 9, textColor: COLORS.dark },
      columnStyles: {
        0: { cellWidth: 80 },
        1: { cellWidth: 25, halign: 'center' },
        2: { cellWidth: 35, halign: 'right' },
        3: { cellWidth: 35, halign: 'right' }
      },
      margin: { left: 15, right: 15 }
    });
    y = doc.lastAutoTable.finalY + 5;
  } else {
    // Auto-generate line items from job totals
    doc.autoTable({
      head: [['Description', 'Qty', 'Rate', 'Amount']],
      body: [
        [
          `Wildlife ${job.species || ''} Removal & Exclusion Services`,
          '1',
          formatCurrency(job.subtotal || job.estimate || 0),
          formatCurrency(job.subtotal || job.estimate || 0)
        ]
      ],
      startY: y,
      theme: 'plain',
      headStyles: {
        fillColor: COLORS.dark,
        textColor: COLORS.white,
        fontSize: 9,
        fontStyle: 'bold'
      },
      bodyStyles: { fontSize: 9, textColor: COLORS.dark },
      columnStyles: {
        0: { cellWidth: 80 },
        1: { cellWidth: 25, halign: 'center' },
        2: { cellWidth: 35, halign: 'right' },
        3: { cellWidth: 35, halign: 'right' }
      },
      margin: { left: 15, right: 15 }
    });
    y = doc.lastAutoTable.finalY + 5;
  }

  // ─── Totals ──────────────────────────────────────────────────────────────
  const subtotal = parseFloat(job.subtotal) || parseFloat(job.estimate) || 0;
  const taxRate = parseFloat(job.tax_rate) || 0;
  const tax = parseFloat(job.tax_amount) || subtotal * taxRate;
  const total = parseFloat(job.grand_total) || subtotal + tax;
  const deposit = parseFloat(job.deposit_paid) || 0;
  const balance = parseFloat(job.balance_due) || total - deposit;

  const totalsX = pageW - 25;

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.text('Subtotal:', totalsX - 35, y, { align: 'right' });
  doc.text(formatCurrency(subtotal), totalsX, y, { align: 'right' });
  y += 6;

  if (taxRate > 0) {
    doc.text(`Tax (${(taxRate * 100).toFixed(2)}%):`, totalsX - 35, y, { align: 'right' });
    doc.text(formatCurrency(tax), totalsX, y, { align: 'right' });
    y += 6;
  }

  doc.setFontSize(12);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...COLORS.dark);
  doc.text('TOTAL:', totalsX - 35, y, { align: 'right' });
  doc.text(formatCurrency(total), totalsX, y, { align: 'right' });
  y += 6;

  if (deposit > 0) {
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...COLORS.gray);
    doc.text('Deposit Paid:', totalsX - 35, y, { align: 'right' });
    doc.text(`(${formatCurrency(deposit)})`, totalsX, y, { align: 'right' });
    y += 6;

    doc.setFontSize(13);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...COLORS.red);
    doc.text('BALANCE DUE:', totalsX - 35, y, { align: 'right' });
    doc.text(formatCurrency(balance), totalsX, y, { align: 'right' });
    y += 6;
  }

  doc.setTextColor(...COLORS.dark);

  // ─── Payment Info ────────────────────────────────────────────────────────
  y += 10;
  doc.setFontSize(10);
  doc.setFont('helvetica', 'bold');
  doc.text('PAYMENT INFORMATION', 15, y);
  y += 6;

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  const paymentLines = [
    'Payment is due within 15 days of invoice date.',
    'Accepted payment methods: Check, Cash, Credit Card, ACH Transfer.',
    'Make checks payable to: Wildlife Whisperer LLC',
    `Credit card payments: Call (555) 123-4567 or pay online at ${COMPANY.website}`,
    'Late payments subject to 1.5% monthly service charge.'
  ];

  paymentLines.forEach(line => {
    doc.text(line, 20, y);
    y += 4.5;
  });

  // ─── Notes ───────────────────────────────────────────────────────────────
  if (options.notes || job.notes) {
    y += 5;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.text('NOTES', 15, y);
    y += 5;

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    const notes = options.notes || job.notes;
    const noteLines = doc.splitTextToSize(notes, pageW - 35);
    doc.text(noteLines, 20, y);
  }

  // ─── Footer ──────────────────────────────────────────────────────────────
  addFooter(doc, pageNum);

  return doc;
}

// ─── Convenience Functions ───────────────────────────────────────────────────

/**
 * Download a job report as PDF.
 *
 * @param {Object} job
 * @param {Array} [services=[]]
 * @param {Array} [photos=[]]
 */
export function downloadJobPDF(job, services, photos) {
  const doc = generateJobPDF(job, services, photos);
  const filename = `JobReport_${job.customer?.replace(/\s+/g, '_') || 'Unknown'}_${job.id?.slice(0, 8) || Date.now()}.pdf`;
  doc.save(filename);
}

/**
 * Download an estimate as PDF.
 *
 * @param {Object} job
 * @param {Object} [estimate={}]
 */
export function downloadEstimatePDF(job, estimate) {
  const doc = generateEstimatePDF(job, estimate);
  const filename = `Estimate_${job.customer?.replace(/\s+/g, '_') || 'Unknown'}_${Date.now()}.pdf`;
  doc.save(filename);
}

/**
 * Download an invoice as PDF.
 *
 * @param {Object} job
 * @param {Object} [options={}]
 */
export function downloadInvoicePDF(job, options) {
  const doc = generateInvoicePDF(job, options);
  const filename = `Invoice_${job.customer?.replace(/\s+/g, '_') || 'Unknown'}_${Date.now()}.pdf`;
  doc.save(filename);
}
