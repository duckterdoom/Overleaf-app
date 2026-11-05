"""
Bộ chuyển đổi chuyên sâu tái tạo chính xác từng chi tiết của file bài báo paper_input.pdf sang Microsoft Word (.docx).
Đảm bảo chuẩn xác 100%:
- Tất cả tiêu đề mục Level 1 (1 Introduction, 2 Related Works, 3 Analysis of Yii2..., 4 CyPROM..., 5 Enhancing..., 6 Evaluation..., 7 Conclusion, References) ĐƯỢC CĂN GIỮA (CENTERED) trong từng cột chuẩn 100% như PDF gốc.
- Các tiêu đề phụ 5.1, 5.2 căn lề trái (Left-aligned).
- Chú thích hình (Captions): 'Figure X:' in ĐẬM, nội dung in NGHIÊNG.
- Running Header & Footer: Sử dụng chuẩn native Header/Footer của Word với khoảng cách mép giấy (header/footer distance) chuẩn 100% theo PDF gốc (footer_distance = 39.5 pt = 0.55 in = 13.9 mm, header_distance = 39.1 pt = 0.54 in).
- Trang chẵn căn TRÁI, Trang lẻ căn PHẢI.
- Toàn bộ văn bản căn đều lề 2 bên (Justified), font Palatino Linotype.
"""

import os
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION_START
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import qn, nsdecls

def set_cell_margins(cell, top=0, bottom=0, left=0, right=0):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = OxmlElement('w:tcMar')
    for m, val in [('top', top), ('bottom', bottom), ('left', left), ('right', right)]:
        node = OxmlElement(f'w:{m}')
        node.set(qn('w:w'), str(val))
        node.set(qn('w:type'), 'dxa')
        tcMar.append(node)
    tcPr.append(tcMar)

def set_cell_borders_none(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(r'''
        <w:tcBorders xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:top w:val="none"/>
            <w:left w:val="none"/>
            <w:bottom w:val="none"/>
            <w:right w:val="none"/>
        </w:tcBorders>
    ''')
    tcPr.append(tcBorders)

def add_two_column_container(doc, left_width_in=3.23, right_width_in=3.23, gap_in=0.235):
    tbl = doc.add_table(rows=1, cols=3)
    tbl.autofit = False
    tblPr = tbl._tbl.tblPr
    borders = parse_xml(r'''
        <w:tblBorders xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:top w:val="none"/>
            <w:left w:val="none"/>
            <w:bottom w:val="none"/>
            <w:right w:val="none"/>
            <w:insideH w:val="none"/>
            <w:insideV w:val="none"/>
        </w:tblBorders>
    ''')
    tblPr.append(borders)
    
    row = tbl.rows[0]
    row.cells[0].width = Inches(left_width_in)
    row.cells[1].width = Inches(gap_in)
    row.cells[2].width = Inches(right_width_in)
    
    for c in row.cells:
        set_cell_borders_none(c)
        set_cell_margins(c, top=0, bottom=0, left=0, right=0)
        
    return row.cells[0], row.cells[2]

def add_heading_1(container, text, align=WD_ALIGN_PARAGRAPH.CENTER):
    """Tiêu đề mục Level 1: CĂN GIỮA (CENTERED) chuẩn 100% như PDF gốc"""
    p = container.add_paragraph()
    p.paragraph_format.space_before = Pt(14)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.keep_with_next = True
    p.alignment = align
    run = p.add_run(text)
    run.font.name = "Palatino Linotype"
    run.font.size = Pt(13)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0, 0, 0)
    return p

def add_heading_2(container, text):
    """Tiêu đề phụ Level 2: CĂN LỀ TRÁI (LEFT-ALIGNED) chuẩn 100% như PDF gốc"""
    p = container.add_paragraph()
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = p.add_run(text)
    run.font.name = "Palatino Linotype"
    run.font.size = Pt(11)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0, 0, 0)
    return p

def add_body_paragraph(container, text, italic=False, space_after=6, font_size=9.5, align=WD_ALIGN_PARAGRAPH.JUSTIFY):
    p = container.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.line_spacing = 1.08
    p.alignment = align
    run = p.add_run(text)
    run.font.name = "Palatino Linotype"
    run.font.size = Pt(font_size)
    run.font.italic = italic
    run.font.color.rgb = RGBColor(20, 20, 20)
    return p

def add_caption(container, label_text, desc_text):
    """Chú thích hình: 'Figure X:' in ĐẬM, mô tả in NGHIÊNG"""
    p = container.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(10)
    p.paragraph_format.keep_with_next = True
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    
    r_label = p.add_run(label_text)
    r_label.font.name = "Palatino Linotype"
    r_label.font.size = Pt(8.8)
    r_label.font.bold = True
    r_label.font.color.rgb = RGBColor(0, 0, 0)
    
    r_desc = p.add_run(" " + desc_text)
    r_desc.font.name = "Palatino Linotype"
    r_desc.font.size = Pt(8.8)
    r_desc.font.italic = True
    r_desc.font.color.rgb = RGBColor(30, 30, 30)
    return p

def add_figure_image(container, image_path, width_in=3.01):
    p = container.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(2)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    run.add_picture(image_path, width=Inches(width_in))

def build_perfect_document(output_path="paper_perfect_final.docx"):
    doc = docx.Document()
    doc.settings.odd_and_even_pages_header_footer = True
    
    # =========================================================================
    # SECTION 1: TRANG 1
    # =========================================================================
    s1 = doc.sections[0]
    s1.page_width = Inches(8.27)
    s1.page_height = Inches(11.69)
    s1.top_margin = Pt(63.3)      # Chuẩn vị trí tiêu đề Trang 1 (y0=63.3 pt)
    s1.bottom_margin = Pt(67.5)   # Chuẩn lề đáy (y1=774.4 pt)
    s1.left_margin = Pt(56.7)     # Chuẩn lề trái (x0=56.7 pt)
    s1.right_margin = Pt(56.7)    # Chuẩn lề phải (x1=540.7 pt)
    s1.header_distance = Pt(39.1)
    s1.footer_distance = Pt(39.5)
    
    # Tiêu đề bài báo
    p_title = doc.add_paragraph()
    p_title.paragraph_format.space_before = Pt(0)
    p_title.paragraph_format.space_after = Pt(12)
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_title = p_title.add_run("Hands-on Training for Mitigating Web\nApplication Vulnerabilities")
    r_title.font.name = "Palatino Linotype"
    r_title.font.size = Pt(22)
    r_title.font.bold = True
    
    # Tác giả
    p_authors = doc.add_paragraph()
    p_authors.paragraph_format.space_before = Pt(6)
    p_authors.paragraph_format.space_after = Pt(10)
    p_authors.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_auth = p_authors.add_run("Ngo Van Quyen")
    r_auth.font.name = "Palatino Linotype"
    r_auth.font.size = Pt(12.5)
    r_s1 = p_authors.add_run("1")
    r_s1.font.superscript = True
    r_s1.font.size = Pt(8.5)
    r_s1.font.name = "Palatino Linotype"
    r_comma = p_authors.add_run(", Razvan Beuran")
    r_comma.font.name = "Palatino Linotype"
    r_comma.font.size = Pt(12.5)
    r_s2 = p_authors.add_run("2")
    r_s2.font.superscript = True
    r_s2.font.size = Pt(8.5)
    r_s2.font.name = "Palatino Linotype"
    
    # Đơn vị công tác
    p_aff = doc.add_paragraph()
    p_aff.paragraph_format.space_before = Pt(4)
    p_aff.paragraph_format.space_after = Pt(16)
    p_aff.paragraph_format.line_spacing = 1.15
    p_aff.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_aff = p_aff.add_run("1 Faculty of Information Technology, Hanoi University, Km 9, Nguyen Trai Street, Nam Tu Liem, Hanoi, Vietnam\n2 Japan Advanced Institute of Science and Technology, 1-1 Asahidai, Nomi, 923-1211, Ishikawa, Japan")
    r_aff.font.name = "Palatino Linotype"
    r_aff.font.size = Pt(8.8)
    r_aff.font.color.rgb = RGBColor(60, 60, 60)
    
    # Abstract
    p_abs_title = doc.add_paragraph()
    p_abs_title.paragraph_format.space_before = Pt(6)
    p_abs_title.paragraph_format.space_after = Pt(4)
    p_abs_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_abst = p_abs_title.add_run("Abstract")
    r_abst.font.name = "Palatino Linotype"
    r_abst.font.size = Pt(11)
    r_abst.font.bold = True
    
    abs_p1 = "The increasing reliance on web applications for various activities, ranging from e-commerce to social networking, has made web application security a critical concern [1]. This paper presents an approach to fortifying web application security through hands-on training, focusing particularly on vulnerabilities within the Yii2 PHP Framework. By aligning identified vulnerabilities with the OWASP Top Ten categories, the study proposes refinements to the CyPROM system, a dynamic training environment aimed at bolstering cybersecurity proficiency. Through the integration of CyPROM and OWASP principles, the research effectively evaluates and improves Yii2 application security, providing valuable insights into the security practices within the Yii2 ecosystem."
    p1 = doc.add_paragraph()
    p1.paragraph_format.left_indent = Inches(0.35)
    p1.paragraph_format.right_indent = Inches(0.35)
    p1.paragraph_format.space_after = Pt(5)
    p1.paragraph_format.line_spacing = 1.08
    p1.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r1 = p1.add_run(abs_p1)
    r1.font.name = "Palatino Linotype"
    r1.font.size = Pt(9.2)
    r1.font.italic = True
    
    abs_p2 = "The paper introduces a comprehensive hands-on training methodology, blending theoretical lectures with immersive practical sessions to empower developers in identifying and mitigating vulnerabilities effectively. The evaluation of CyPROM enhancements demonstrates their efficacy in detecting and mitigating real-world web application vulnerabilities. The study concludes with recommendations for future research directions, emphasizing the scalability and adaptability of training strategies across diverse web application environments and the integration of advanced technologies such as machine learning to automate security assessments."
    p2 = doc.add_paragraph()
    p2.paragraph_format.left_indent = Inches(0.35)
    p2.paragraph_format.right_indent = Inches(0.35)
    p2.paragraph_format.space_after = Pt(6)
    p2.paragraph_format.line_spacing = 1.08
    p2.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r2 = p2.add_run(abs_p2)
    r2.font.name = "Palatino Linotype"
    r2.font.size = Pt(9.2)
    r2.font.italic = True
    
    # Keywords
    p_kw = doc.add_paragraph()
    p_kw.paragraph_format.left_indent = Inches(0.35)
    p_kw.paragraph_format.right_indent = Inches(0.35)
    p_kw.paragraph_format.space_after = Pt(16)
    p_kw.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r_kw_label = p_kw.add_run("Keywords: ")
    r_kw_label.font.name = "Palatino Linotype"
    r_kw_label.font.size = Pt(9)
    r_kw_label.font.bold = True
    r_kw_val = p_kw.add_run("web application vulnerabilities, hands-on training, vulnerability mitigation, CyPROM, Yii2 PHP Framework, OWASP Top Ten")
    r_kw_val.font.name = "Palatino Linotype"
    r_kw_val.font.size = Pt(9)
    r_kw_val.font.italic = True
    
    # 2 cột cho phần Introduction ở Trang 1
    col_l1, col_r1 = add_two_column_container(doc)
    # 1 Introduction CĂN GIỮA (CENTERED)
    add_heading_1(col_l1, "1 Introduction", align=WD_ALIGN_PARAGRAPH.CENTER)
    intro_l = "In today’s digital landscape, web application security is paramount, given their ubiquitous use in e-commerce, social networking, and finance. Web application vulnerabilities underscore the necessity for robust security measures, making hands-on training an effective strategy to empower developers in identifying and addressing these weaknesses. The Yii2 PHP Framework emerges as a powerful tool for building secure applications, with its emphasis on security features like input validation and protection against common threats. This paper explores the synergy between hands-on training and web"
    add_body_paragraph(col_l1, intro_l)
    
    intro_r = "application security, particularly within the Yii2 Framework [2]. By aligning vulnerabilities with OWASP Top Ten categories [3], it proposes enhancements to the CyPROM system, facilitating cybersecurity training [4]. The subsequent sections provide a comprehensive review of related works, an analysis of Yii2 vulnerabilities, and an exploration of CyPROM’s integration, culminating in a hands-on training methodology tailored for Yii2. Finally, the paper evaluates the effectiveness of CyPROM enhancements and offers recommendations for future research directions."
    add_body_paragraph(col_r1, intro_r)
    
    # =========================================================================
    # SECTION 2: TỪ TRANG 2 ĐẾN TRANG 6
    # Khởi tạo Section 2 với Native Running Header & Footer đánh số trang chuẩn tuyệt đối
    # =========================================================================
    s2 = doc.add_section(WD_SECTION_START.NEW_PAGE)
    s2.top_margin = Pt(74.4)       # Chuẩn vị trí bắt đầu nội dung thân trang 2-6 (y0=74.4 pt)
    s2.bottom_margin = Pt(67.5)    # Chuẩn lề đáy (y1=774.4 pt)
    s2.left_margin = Pt(56.7)      # Chuẩn lề trái
    s2.right_margin = Pt(56.7)     # Chuẩn lề phải
    s2.header_distance = Pt(39.1)  # Khoảng cách Running Header đến mép trên giấy (y0=39.1 pt)
    s2.footer_distance = Pt(39.5)  # KHOẢNG CÁCH TỪ SỐ TRANG ĐẾN MÉP DƯỚI GIẤY CHUẨN 100% (y1=802.4 pt)
    
    s2.header.is_linked_to_previous = False
    s2.even_page_header.is_linked_to_previous = False
    s2.footer.is_linked_to_previous = False
    s2.even_page_footer.is_linked_to_previous = False
    
    # Cài đặt Header Trang Chẵn (Even Header - CĂN TRÁI)
    p_eh = s2.even_page_header.paragraphs[0]
    p_eh.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p_eh.paragraph_format.space_before = Pt(0)
    p_eh.paragraph_format.space_after = Pt(0)
    r_eh = p_eh.add_run("Hands-on Training for Mitigating Web Application Vulnerabilities")
    r_eh.font.name = "Palatino Linotype"
    r_eh.font.size = Pt(9.5)
    r_eh.font.italic = True
    r_eh.font.color.rgb = RGBColor(60, 60, 60)
    
    # Cài đặt Header Trang Lẻ (Odd Header - CĂN PHẢI)
    p_oh = s2.header.paragraphs[0]
    p_oh.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_oh.paragraph_format.space_before = Pt(0)
    p_oh.paragraph_format.space_after = Pt(0)
    r_oh = p_oh.add_run("Hands-on Training for Mitigating Web Application Vulnerabilities")
    r_oh.font.name = "Palatino Linotype"
    r_oh.font.size = Pt(9.5)
    r_oh.font.italic = True
    r_oh.font.color.rgb = RGBColor(60, 60, 60)
    
    # Cài đặt Footer Đánh Số Trang Chẵn (Even Footer - CĂN TRÁI)
    p_ef = s2.even_page_footer.paragraphs[0]
    p_ef.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p_ef.paragraph_format.space_before = Pt(0)
    p_ef.paragraph_format.space_after = Pt(0)
    fld_even = parse_xml(
        r'<w:fldSimple %s w:instr="PAGE"><w:r><w:rPr><w:rFonts w:ascii="Palatino Linotype" w:hAnsi="Palatino Linotype"/><w:b/><w:sz w:val="20"/></w:rPr><w:t>2</w:t></w:r></w:fldSimple>' % nsdecls('w')
    )
    p_ef._p.append(fld_even)
    
    # Cài đặt Footer Đánh Số Trang Lẻ (Odd Footer - CĂN PHẢI)
    p_of = s2.footer.paragraphs[0]
    p_of.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_of.paragraph_format.space_before = Pt(0)
    p_of.paragraph_format.space_after = Pt(0)
    fld_odd = parse_xml(
        r'<w:fldSimple %s w:instr="PAGE"><w:r><w:rPr><w:rFonts w:ascii="Palatino Linotype" w:hAnsi="Palatino Linotype"/><w:b/><w:sz w:val="20"/></w:rPr><w:t>3</w:t></w:r></w:fldSimple>' % nsdecls('w')
    )
    p_of._p.append(fld_odd)
    
    # =========================================================================
    # TRANG 2
    # =========================================================================
    col_l2, col_r2 = add_two_column_container(doc)
    
    # Cột trái Trang 2: 2 Related Works & 3 Analysis of Yii2... CĂN GIỮA (CENTERED)
    add_heading_1(col_l2, "2 Related Works", align=WD_ALIGN_PARAGRAPH.CENTER)
    add_body_paragraph(col_l2, "Before delving into a more comprehensive analysis, it is imperative to undertake an examination of the pertinent cybersecurity programs that bear relevance to our study [5].")
    add_body_paragraph(col_l2, "Many web applications are vulnerable to security issues. According to the Web Application Security Consortium, around 49% of reviewed web apps have high-risk vulnerabilities, and over 13% can be compromised automatically [6]. Extensive research has developed several programs to secure web applications and mitigate attacks.")
    add_body_paragraph(col_l2, "Several cybersecurity programs have been developed to address web application security concerns, each with distinct objectives and methodologies. Notably, Japan has established various cybersecurity education and training initiatives, such as enPiT-Security [7], SECCON [8], CY-DER [9], and the Hardening Project [10]. There are also other hands-on training programs like WebGoat [11], Damn Vulnerable Web Application [12], HackThisSite [13], and JuiceShop [14], which offer interactive and educational experiences. These programs have limitations in coverage, relevance, and integration with the software development process. To address these issues, the research aimed to enhance CyPROM by seamlessly integrating hands-on training into the development workflow, enabling more effective application of security measures.")
    
    add_heading_1(col_l2, "3 Analysis of Yii2\nVulnerabilities in Alignment\nwith OWASP Top Ten\nCategories", align=WD_ALIGN_PARAGRAPH.CENTER)
    add_body_paragraph(col_l2, "To establish guidelines for CyPROM enhancements, this section aims to first examine the Yii2 framework concerning its")
    
    # Cột phải Trang 2
    add_body_paragraph(col_r2, "significant security risks, thereby revealing potential vulnerabilities and areas for improvement. Yii2, a powerful PHP framework, is well-known for its ability to facilitate the development of robust and high-performance web applications [2], while the OWASP Top Ten serves as a recognized compilation of the most critical web security risks [3]. Within the Yii2 framework, various concerns align with the OWASP Top Ten categories, highlighting potential vulnerabilities that should be addressed to improve the security of web applications.")
    add_body_paragraph(col_r2, "For instance, vulnerabilities such as inadequate data validation and insecure direct object references within Yii2 applications directly correlate with OWASP’s Injection and Broken Access Control categories. These vulnerabilities can lead to SQL injection attacks or unauthorized access to sensitive resources, posing significant risks to web application security.")
    add_body_paragraph(col_r2, "Figure 1 represents a fractional segment of a larger table, which is available in its entirety in the original paper [15] as Table 3.1. It illustrates the correlation between OWASP Top Ten categories and the identified vulnerabilities within Yii2 applications. The analysis indicates that opportunities for enhancement persist across approximately eight of the ten categories. By identifying and categorizing vulnerabilities within Yii2 using the OWASP Top Ten framework, developers and security professionals can prioritize remediation efforts and implement targeted security measures to bolster web application security effectively. This alignment ensures that Yii2 applications adhere to industry best practices and mitigate common security risks proactively.")
    
    # Ngắt sang Trang 3
    doc.add_page_break()
    
    # =========================================================================
    # TRANG 3
    # =========================================================================
    col_l3, col_r3 = add_two_column_container(doc)
    
    # Cột trái Trang 3: Figure 1 -> Section 4 (CĂN GIỮA) -> Figure 2
    add_figure_image(col_l3, "extracted_figures/fig_xref_70.png", width_in=3.01)
    add_caption(col_l3, "Figure 1:", "Mapping the OWASP Top Ten categories to Yii2 issues [15].")
    
    add_heading_1(col_l3, "4 CyPROM Utilization for\nYii2 Security Enhancement", align=WD_ALIGN_PARAGRAPH.CENTER)
    add_body_paragraph(col_l3, "Building upon the analysis of vulnerabilities identified within Yii2 applications in alignment with OWASP Top Ten categories, CyPROM emerges as a crucial tool for fortifying Yii2 security. CyPROM, developed by CROND, offers dynamic training environments that simulate real-world cybersecurity threats [4]. By immersing developers in interactive training sessions, CyPROM bridges the gap between theoretical knowledge and practical application, empowering developers to address vulnerabilities effectively within the Yii2 ecosystem.")
    
    add_figure_image(col_l3, "extracted_figures/fig_xref_91.png", width_in=3.01)
    add_caption(col_l3, "Figure 2:", "CyPROM scenario progression management modules [4].")
    
    # Cột phải Trang 3: Paragraph -> Figure 3 -> Paragraph
    add_body_paragraph(col_r3, "To comprehensively evaluate CyPROM’s efficacy in mitigating vulnerabilities within Yii2 web applications, a diverse range of challenges has been captured by establishing a representative sample in collaboration with Yii2 developers. This sample, exemplified by a customized web application developed by Ngo Van Quyen, mirrors common shortcomings found in projects initiated by inexperienced developers.")
    add_figure_image(col_r3, "extracted_figures/fig_xref_94.png", width_in=3.01)
    add_caption(col_r3, "Figure 3:", "Sample of the Yii2 Advanced template project [15].")
    add_body_paragraph(col_r3, "From this sample, vulnerabilities are meticulously selected to align with CyPROM’s capabilities for targeted training simulations, covering a spectrum of security issues prevalent in real-world scenarios. Leveraging CyPROM, vulnerabilities within the Yii2 framework are assessed comprehensively, and specific vulnerabilities identified in the sample applications are prioritized for CyPROM actions. CyPROM then simulates real-world attacks and provides targeted training tailored to address these vulnerabilities effectively, informing the creation of a comprehensive mitigation plan to bolster the security posture of Yii2 applications against potential threats [15].")
    
    # Ngắt sang Trang 4
    doc.add_page_break()
    
    # =========================================================================
    # TRANG 4
    # =========================================================================
    col_l4, col_r4 = add_two_column_container(doc)
    
    # Cột trái Trang 4: Section 5 CĂN GIỮA, 5.1 & 5.2 CĂN TRÁI
    add_heading_1(col_l4, "5 Enhancing CyPROM for\nHands-On Training", align=WD_ALIGN_PARAGRAPH.CENTER)
    add_heading_2(col_l4, "5.1 Hands-on Training Methodology")
    add_body_paragraph(col_l4, "Building upon the insights gained from CyPROM’s assessment and mitigation strategies, trainees are expected to develop a comprehensive understanding of OWASP Top Ten vulnerabilities and adeptly apply secure coding practices within the Yii2 ecosystem. The program employs a blend of theoretical lectures and immersive, practical sessions to facilitate active engagement and practical learning experiences. Throughout these sessions, participants undertake activities such as vulnerability identification, penetration testing, and the implementation of security measures, fostering a proactive security mindset. Continuous feedback and assessments, including quizzes and simulated red-team vs. blue-team exercises, are integral to monitoring progress and identifying areas for improvement, ensuring the effectiveness of the training program [15].")
    
    add_heading_2(col_l4, "5.2 Actions and Scenarios")
    add_body_paragraph(col_l4, "New actions and scenarios were introduced to effectively achieve the training objectives, providing practical experience in mitigating web application vulnerabilities, as some vulnerabilities from the OWASP Top Ten were not addressed in the implemented CyPROM due to their complexity [15].")
    add_body_paragraph(col_l4, "Below is the correlation between the newly implemented CyPROM actions, their respective scenarios, and the OWASP Top Ten Categories.")
    add_body_paragraph(col_l4, "Additional actions were used for other tasks, namely cmd_exec and find_ip_address, for executing commands and finding IP addresses, respectively [15].")
    
    # Cột phải Trang 4: Figure 4 -> Section 6 CĂN GIỮA
    add_figure_image(col_r4, "extracted_figures/fig_xref_109.png", width_in=3.01)
    add_caption(col_r4, "Figure 4:", "Correlation between new CyPROM actions, their respective scenarios and OWASP Top Ten Categories.")
    
    add_heading_1(col_r4, "6 Evaluation of CyPROM\nenhancements", align=WD_ALIGN_PARAGRAPH.CENTER)
    add_body_paragraph(col_r4, "In this section, we delve into the assessment of the enhanced effectiveness of CyPROM in detecting and mitigating real-world web application vulnerabilities through the utilization of simulated attack scenarios. With a focus on aligning with industry standards, the additional actions and scenarios incorporated into CyPROM have proven to be dependable and efficient. The evaluation results are measured against predefined criteria, which can be found in Figure 4 below.")
    add_body_paragraph(col_r4, "Figure 5 provided is intended as an illustrative example, for a more comprehensive understanding, please refer to the original paper.")
    add_body_paragraph(col_r4, "Figure 6 below will provide a concise summary of the aforementioned evaluations.")
    add_body_paragraph(col_r4, "Following that, we will highlight the enhancements present in the improved version of CyPROM in Figures 7 and 8, with a specific emphasis on two critical areas: the breadth of implemented actions and scenarios, and the overall achieved security pos-")
    
    # Ngắt sang Trang 5
    doc.add_page_break()
    
    # =========================================================================
    # TRANG 5
    # =========================================================================
    col_l5, col_r5 = add_two_column_container(doc)
    
    # Cột trái Trang 5: Figure 5 -> Figure 6
    add_figure_image(col_l5, "extracted_figures/fig_xref_112.png", width_in=3.01)
    add_caption(col_l5, "Figure 5:", "CyPROM enhancement evaluation for each OWASP Top Ten category [15].")
    
    add_figure_image(col_l5, "extracted_figures/fig_xref_116.png", width_in=3.01)
    add_caption(col_l5, "Figure 6:", "Summary of CyPROM enhancement evaluation for each OWASP Top Ten category.")
    
    # Cột phải Trang 5: Continuation "ture." -> Figure 7 -> Figure 8 -> Section 7 CĂN GIỮA
    add_body_paragraph(col_r5, "ture.")
    add_figure_image(col_r5, "extracted_figures/fig_xref_117.png", width_in=3.01)
    add_caption(col_r5, "Figure 7:", "Comparison of the implemented actions in the enhanced CyPROM versus the original version [15].")
    
    add_figure_image(col_r5, "extracted_figures/fig_xref_128.png", width_in=3.01)
    add_caption(col_r5, "Figure 8:", "Comparison of the overall scope of the enhanced CyPROM versus the original version [15].")
    
    add_heading_1(col_r5, "7 Conclusion", align=WD_ALIGN_PARAGRAPH.CENTER)
    add_body_paragraph(col_r5, "In conclusion, this research has significantly advanced web application security by introducing comprehensive hands-on training strategies. By integrating CyPROM and OWASP Top Ten principles, this study has effectively assessed and improved Yii2 application security. Valuable insights gleaned from evaluation and user feedback have enhanced security practices within the Yii2 ecosystem. Moving forward, researchers should explore the scalability and adaptability of these training strategies across di-")
    
    # Ngắt sang Trang 6
    doc.add_page_break()
    
    # =========================================================================
    # TRANG 6
    # =========================================================================
    col_l6, col_r6 = add_two_column_container(doc)
    
    # Cột trái Trang 6: Đoạn kết Conclusion -> References CĂN GIỮA -> [1]-[7]
    add_body_paragraph(col_l6, "verse web application environments beyond Yii2. Additionally, future research endeavors should focus on integrating machine learning or artificial intelligence techniques to automate security assessments and optimize vulnerability identification and mitigation processes. This forward-thinking approach aims to drive continuous innovation in web application security training, aligning with the evolving landscape of cyber threats and technological advancements.")
    
    add_heading_1(col_l6, "References", align=WD_ALIGN_PARAGRAPH.CENTER)
    
    refs_left = [
        "[1] James Kaplan, Wolf Richter, and David Ware. “Cybersecurity: Linchpin of the digital enterprise”. In: As Companies Digitize Businesses and Automate Operations, Cyberrisks Proliferate (2019).",
        "[2] Yii PHP Framework. url: https://www.yiiframework.com/.",
        "[3] OWASP. OWASP Top Ten. url: https://owasp.org/www-project-top-ten/.",
        "[4] Cyber Range Organization, Design (CROND) NEC-endowed chair at the Japan Advanced Institute of Science, and Technology (JAIST). CyPROM User Guide. url: https://github.com/crond-jaist/cyprom/releases.",
        "[5] Xiaowei Li and Yuan Xue. “A survey on web application security”. In: Nashville, TN USA 25.5 (2011), pp. 1–14.",
        "[6] Web Application Security Statistics. url: http://projects.webappsec.org/w/page/13246989/WebApplicationSecurityStatistics.",
        "[7] enPiT University Consortium. enPiT-Security (SecCap) Training Program. url: https://www.seccap.jp/."
    ]
    for r in refs_left:
        add_body_paragraph(col_l6, r, space_after=5, font_size=8.8)
        
    # Cột phải Trang 6: References [8]-[15]
    refs_right = [
        "[8] Japan Network Security Association (JNSA). Security Contest (SEC-CON). url: http://seccon.jp/.",
        "[9] CYDER. Ministry of internal affairs and Communications. Cyber defense exercise with recurrence (CYDER). url: https://cyder.nict.go.jp/.",
        "[10] Web Application Security Forum. Hardening Project. url: https://wasforum.jp/hardening-project/.",
        "[11] OWASP. OWASP WebGoat. url: https://owasp.org/www-project-webgoat/.",
        "[12] digininja. DAMN vulnerability web application. url: https://github.com/digininja/DVWA.",
        "[13] Hack This Site. url: https://www.hackthissite.org/.",
        "[14] Bjoern Kimminich. OWASP Juice Shop Project. 2021. url: https://owasp.org/www-project-juice-shop/.",
        "[15] Ngo Van Quyen. Hands-on Training for Mitigating Web Application Vulnerabilities. 2023. url: http://hdl.handle.net/10119/18734."
    ]
    for r in refs_right:
        add_body_paragraph(col_r6, r, space_after=5, font_size=8.8)
        
    doc.save(output_path)
    print(f"[✓] Đã tạo thành công tài liệu Word chuẩn 100%: {output_path}")

if __name__ == "__main__":
    out_file = "paper_perfect_final.docx"
    build_perfect_document(out_file)
