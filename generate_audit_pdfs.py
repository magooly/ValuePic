#!/usr/bin/env python3
"""
Generate PDF versions of audit reports from markdown files
"""

from reportlab.lib.pagesizes import letter, A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT, TA_JUSTIFY
import datetime
import os

def read_markdown_file(filepath):
    """Read markdown file"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        print(f"Error reading {filepath}: {e}")
        return None

def markdown_to_pdf(md_content, output_pdf, title):
    """Convert markdown content to PDF using reportlab"""
    try:
        doc = SimpleDocTemplate(output_pdf, pagesize=letter,
                               rightMargin=0.5*inch, leftMargin=0.5*inch,
                               topMargin=0.5*inch, bottomMargin=0.5*inch)

        story = []
        styles = getSampleStyleSheet()

        # Create custom styles
        title_style = ParagraphStyle(
            'CustomTitle',
            parent=styles['Heading1'],
            fontSize=16,
            textColor=colors.HexColor('#1f4788'),
            spaceAfter=12,
            alignment=TA_CENTER,
            fontName='Helvetica-Bold'
        )

        heading2_style = ParagraphStyle(
            'CustomHeading2',
            parent=styles['Heading2'],
            fontSize=12,
            textColor=colors.HexColor('#2d5aa8'),
            spaceAfter=8,
            fontName='Helvetica-Bold'
        )

        body_style = ParagraphStyle(
            'CustomBody',
            parent=styles['BodyText'],
            fontSize=9,
            alignment=TA_LEFT,
            spaceAfter=6,
            leading=11
        )

        # Add title
        story.append(Paragraph(title, title_style))
        story.append(Spacer(1, 0.2*inch))

        # Add metadata
        meta_text = f"Generated: {datetime.datetime.now().strftime('%B %d, %Y at %H:%M:%S')}"
        story.append(Paragraph(meta_text, styles['Normal']))
        story.append(Spacer(1, 0.3*inch))

        # Process content
        lines = md_content.split('\n')
        for line in lines:
            line = line.strip()

            if line.startswith('# '):
                # Main heading
                text = line[2:].strip()
                story.append(Paragraph(text, title_style))
                story.append(Spacer(1, 0.15*inch))

            elif line.startswith('## '):
                # Subheading
                text = line[3:].strip()
                story.append(Paragraph(text, heading2_style))
                story.append(Spacer(1, 0.1*inch))

            elif line.startswith('### '):
                # Sub-subheading
                text = line[4:].strip()
                h3_style = ParagraphStyle(
                    'Heading3',
                    parent=styles['Heading3'],
                    fontSize=10,
                    textColor=colors.HexColor('#444444'),
                    spaceAfter=6,
                    fontName='Helvetica-Bold'
                )
                story.append(Paragraph(text, h3_style))
                story.append(Spacer(1, 0.05*inch))

            elif line and not line.startswith('---'):
                # Regular paragraph
                if not line.startswith('|') and not line.startswith('['):
                    sanitized = line.replace('<', '&lt;').replace('>', '&gt;')
                    story.append(Paragraph(sanitized, body_style))
                    story.append(Spacer(1, 0.05*inch))

            elif line.startswith('---'):
                # Page break or separator
                story.append(Spacer(1, 0.15*inch))

        # Build PDF
        doc.build(story)
        return True

    except Exception as e:
        print(f"Error generating PDF: {e}")
        return False

def main():
    """Main function"""
    base_path = r"C:\wrhor\DataBase"

    reports = [
        ("AUDIT_EXECUTIVE_SUMMARY.txt", "AUDIT_EXECUTIVE_SUMMARY.pdf", "ValueFinder Code Audit - Executive Summary"),
        ("AUDIT_SUMMARY.md", "AUDIT_SUMMARY.pdf", "ValueFinder Code Audit - Quick Reference Summary"),
        ("AUDIT_INDEX.md", "AUDIT_INDEX.md.pdf", "ValueFinder Code Audit - Documentation Index"),
        ("CODE_AUDIT_REPORT.md", "CODE_AUDIT_REPORT.pdf", "ValueFinder Code Audit - Comprehensive Report"),
    ]

    print("=" * 70)
    print("GENERATING PDF VERSIONS OF AUDIT REPORTS")
    print("=" * 70)

    success_count = 0

    for input_file, output_file, title in reports:
        input_path = os.path.join(base_path, input_file)
        output_path = os.path.join(base_path, output_file)

        print(f"\n📄 Processing: {input_file}")

        if not os.path.exists(input_path):
            print(f"   ❌ File not found: {input_path}")
            continue

        # Read content
        content = read_markdown_file(input_path)
        if not content:
            print(f"   ❌ Failed to read file")
            continue

        # Generate PDF
        if markdown_to_pdf(content, output_path, title):
            file_size = os.path.getsize(output_path) / 1024
            print(f"   ✅ Created: {output_file} ({file_size:.1f} KB)")
            success_count += 1
        else:
            print(f"   ❌ Failed to generate PDF")

    print("\n" + "=" * 70)
    print(f"✅ COMPLETE: {success_count}/{len(reports)} PDFs generated successfully")
    print("=" * 70)

if __name__ == "__main__":
    main()

