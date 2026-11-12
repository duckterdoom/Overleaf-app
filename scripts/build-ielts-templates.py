"""Regenerate the original, bundled IELTS practice projects (stdlib only).

Run from any directory with Python. Does not modify user projects or databases.
Question language stays English; project instructions are available in en/vi.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'apps/backend/src/latex_backend/resources'
TEMPLATES = RESOURCES / 'templates'
RUBRICS = json.loads((RESOURCES / 'writing/catalog.json').read_text(encoding='utf-8'))

# Original prompts and synthetic data. No downloaded exam questions or images.
QUESTIONS = {
    'line': ('Library visits', 'Lượt đến thư viện',
        'The line graph shows the average number of daily visits to three public libraries in the fictional city of Bellford between 2010 and 2025.',
        r'''\begin{tikzpicture}[x=0.115cm,y=0.008cm]
\draw[->] (0,0)--(52,0) node[right]{Year}; \draw[->] (0,0)--(0,550) node[above]{Visits per day};
\foreach \v in {0,100,200,300,400,500}{\draw[gray!25] (0,\v)--(45,\v);\node[left] at (0,\v){\v};}
\foreach \x/\v in {0/2010,15/2015,30/2020,45/2025}{\node[below] at (\x,0){\v};}
\draw[blue,thick] (0,200)--(15,280)--(30,360)--(45,480);
\draw[orange,thick,dashed] (0,420)--(15,360)--(30,300)--(45,260);
\draw[teal,thick,dotted] (0,120)--(15,180)--(30,290)--(45,350);
\node[blue,anchor=west] at (48,480){Central};\node[orange,anchor=west] at (48,260){West};\node[teal,anchor=west] at (48,350){Riverside};
\end{tikzpicture}''', 'year,Central,West,Riverside\n2010,200,420,120\n2015,280,360,180\n2020,360,300,290\n2025,480,260,350\n'),
    'bar': ('Commuting choices', 'Phương tiện đi làm',
        'The bar chart compares the percentages of commuters using four main forms of transport in Bellford in 2005 and 2025.',
        r'''\begin{tikzpicture}[x=0.85cm,y=0.07cm]
\draw[->] (0,0)--(9,0);\draw[->] (0,0)--(0,65) node[above]{Percent};
\foreach \v in {0,10,20,30,40,50,60}{\draw[gray!25] (0,\v)--(8.5,\v);\node[left] at (0,\v){\v};}
\foreach \x/\a/\b/\name in {1/55/35/Car,3/25/30/Bus,5/10/25/Bicycle,7/10/10/Walking}{
\fill[blue!65] (\x,0) rectangle +(0.55,\a);\fill[teal!65] (\x+0.6,0) rectangle +(0.55,\b);
\node[above] at (\x+0.275,\a){\a};\node[above] at (\x+0.875,\b){\b};\node[below] at (\x+0.55,0){\name};}
\node[blue] at (2,72){2005};\node[teal] at (5,72){2025};
\end{tikzpicture}''', 'mode,2005_percent,2025_percent\nCar,55,35\nBus,25,30\nBicycle,10,25\nWalking,10,10\n'),
    'pie': ('Household energy', 'Năng lượng hộ gia đình',
        'The pie charts show how household energy use was distributed among four purposes in a fictional country in 2000 and 2025.',
        r'''\begin{tikzpicture}
\begin{scope}[shift={(0,0)}]
\fill[blue!65] (0,0)--(0:1.7) arc (0:180:1.7)--cycle;
\fill[teal!65] (0,0)--(180:1.7) arc (180:270:1.7)--cycle;
\fill[orange!65] (0,0)--(270:1.7) arc (270:324:1.7)--cycle;
\fill[purple!45] (0,0)--(324:1.7) arc (324:360:1.7)--cycle;
\node at (0,2.2){2000};\node at (90:1.1){50\%};\node at (225:1.1){25\%};\node at (297:1.1){15\%};\node at (342:1.25){10\%};
\end{scope}
\begin{scope}[shift={(5,0)}]
\fill[blue!65] (0,0)--(0:1.7) arc (0:126:1.7)--cycle;
\fill[teal!65] (0,0)--(126:1.7) arc (126:216:1.7)--cycle;
\fill[orange!65] (0,0)--(216:1.7) arc (216:306:1.7)--cycle;
\fill[purple!45] (0,0)--(306:1.7) arc (306:360:1.7)--cycle;
\node at (0,2.2){2025};\node at (63:1.1){35\%};\node at (171:1.1){25\%};\node at (261:1.1){25\%};\node at (333:1.1){15\%};
\end{scope}
\foreach \x/\color/\name in {-0.5/blue/Heating,1.7/teal/Water,3.9/orange/Appliances,6.1/purple/Lighting}{\fill[\color!65] (\x,-2.4) rectangle +(0.2,0.2);\node[anchor=west,font=\small] at (\x+0.25,-2.3){\name};}
\end{tikzpicture}''', 'purpose,2000_percent,2025_percent\nHeating,50,35\nWater,25,25\nAppliances,15,25\nLighting,10,15\n'),
    'table': ('Community courses', 'Khóa học cộng đồng',
        'The table gives enrolment and completion figures for four community education courses in Bellford in 2024.',
        r'''\renewcommand{\arraystretch}{1.6}
\begin{tabular}{|l|r|r|r|}\hline
Course & Enrolled students & Completed students & Fee (USD)\\\hline
Languages & 240 & 204 & 120\\
Computing & 180 & 162 & 150\\
Cooking & 150 & 135 & 90\\
Photography & 100 & 70 & 180\\\hline
\end{tabular}''', 'course,enrolled,completed,fee_USD\nLanguages,240,204,120\nComputing,180,162,150\nCooking,150,135,90\nPhotography,100,70,180\n'),
    'mixed': ('Visitors and spending', 'Khách tham quan và chi tiêu',
        'The chart shows the numbers of visitors to three museums in 2024. The table gives their average spending per visit in the same year.',
        r'''\begin{tikzpicture}[x=1.8cm,y=0.035cm]
\draw[->] (0,0)--(4,0);\draw[->] (0,0)--(0,140) node[above]{Visitors (thousands)};
\foreach \v in {0,40,80,120}{\node[left] at (0,\v){\v};\draw[gray!25] (0,\v)--(3.7,\v);}
\foreach \x/\v/\name in {0.5/120/History,1.5/80/Science,2.5/60/Art}{\fill[teal!65] (\x,0) rectangle +(0.55,\v);\node[above] at (\x+0.275,\v){\v};\node[below] at (\x+0.275,0){\name};}
\end{tikzpicture}
\par\bigskip\begin{tabular}{|l|r|}\hline Museum & Spending per visitor (USD)\\\hline History & 8\\Science & 15\\Art & 20\\\hline\end{tabular}''', 'museum,visitors_thousands,spending_per_visitor_USD\nHistory,120,8\nScience,80,15\nArt,60,20\n'),
    'process': ('Rainwater reuse', 'Tái sử dụng nước mưa',
        'The diagram illustrates how rainwater is collected and prepared for non-drinking uses in a residential building.',
        r'''\begin{tikzpicture}[every node/.style={font=\small},box/.style={draw,rounded corners,align=center,minimum width=2.6cm,minimum height=1.1cm}]
\node[box,fill=blue!10] (a) at (0,0){Roof collection};\node[box] (b) at (4,0){Mesh filter};\node[box] (c) at (8,0){Storage tank};
\node[box] (d) at (8,-2.5){Pump};\node[box] (e) at (4,-2.5){Fine filter};\node[box,fill=teal!10] (f) at (0,-2.5){Toilets and\\garden taps};
\draw[->,thick] (a)--(b);\draw[->,thick] (b)--(c);\draw[->,thick] (c)--(d);\draw[->,thick] (d)--(e);\draw[->,thick] (e)--(f);
\node[font=\small,align=center] at (8,1.6){Rain enters at the roof.\\Excess tank water flows to a drain.};\draw[->,dashed] (c.east)--+(1.5,0) node[right]{Drain};
\end{tikzpicture}''', ''),
    'map': ('A neighbourhood square', 'Quảng trường khu dân cư',
        'The plans show a neighbourhood square in Bellford in 2000 and after redevelopment in 2025.',
        r'''\begin{tikzpicture}[x=0.8cm,y=0.8cm,every node/.style={font=\small,align=center}]
\foreach \x/\year in {0/2000,7/2025}{\draw (\x,0) rectangle +(5,5);\node at (\x+2.5,5.5){\year};\draw[gray,line width=5pt] (\x,0.3)--+(5,0);\node at (\x+2.5,-0.4){Main Street};\draw[->] (\x+4.5,4)--+(0,0.7) node[above]{N};}
\draw[fill=gray!15] (0.3,0.8) rectangle (2.4,2.6);\node at (1.35,1.7){Car park};
\draw[fill=green!15] (2.7,0.8) rectangle (4.7,3.1);\node at (3.7,1.9){Lawn};
\draw (0.3,3) rectangle (2.4,4.5);\node at (1.35,3.75){Shops};
\draw[fill=green!15] (7.3,0.8) rectangle (9.4,2.6);\node at (8.35,1.7){Playground};
\draw[fill=blue!10] (9.7,0.8) rectangle (11.7,3.1);\node at (10.7,1.9){Pond};
\draw (7.3,3) rectangle (9.4,4.5);\node at (8.35,3.75){Cafe};\draw (9.7,3.5) rectangle (11.7,4.5);\node at (10.7,4){Cycle racks};
\end{tikzpicture}''', ''),
    'diagram': ('A solar ventilation unit', 'Hệ thống thông gió năng lượng mặt trời',
        'The diagram shows the main components of a solar-powered ventilation unit and how air moves through it.',
        r'''\begin{tikzpicture}[every node/.style={font=\small,align=center}]
\draw[fill=gray!10] (0,0) rectangle (10,3);
\node[draw,fill=blue!10,minimum height=1.5cm] (filter) at (1.5,1.5){Dust\\filter};
\node[draw,circle,fill=teal!10] (fan) at (5,1.5){Fan};
\draw[->,very thick,blue] (-1,1.5)--(filter);\node at (-0.5,2.5){Outside air};
\draw[->,very thick,blue] (filter)--(fan);\draw[->,very thick,blue] (fan)--(11,1.5);\node at (10.7,2.5){Indoor air supply};
\node[draw,fill=yellow!20] (solar) at (3,5){Solar panel};\node[draw] (battery) at (7,5){Battery};
\draw[->,thick] (solar)--(battery);\draw[->,thick] (battery)--(7,3.6)--(5,3.6)--(fan) node[pos=0.2,right]{Electricity};
\node at (3,6){Sunlight};\draw[->] (3,5.7)--(solar);
\end{tikzpicture}''', ''),
    'formal': ('Request to a library manager', 'Thư trang trọng: thư viện',
        'You attend an evening study group at a public library. The library plans to close earlier, which would affect your group. Write a letter to the library manager. In your letter:',
        ['explain how your group uses the library', 'describe the difficulty the new hours would cause', 'suggest a practical alternative'], ''),
    'semiformal': ('A change to your work schedule', 'Thư bán trang trọng: lịch làm việc',
        'You work part-time in a local bookshop and know the owner well. You need to change your working hours for the next month. Write a letter to the owner. In your letter:',
        ['explain why you need different hours', 'suggest a new schedule', 'describe how you could help avoid disruption'], ''),
    'informal': ('An invitation to a friend', 'Thư thân mật: mời bạn',
        'A friend from another town has asked about visiting you for a weekend. Write a letter to your friend. In your letter:',
        ['suggest a suitable weekend', 'describe two activities you could do together', 'explain how your friend can reach your home'], ''),
    'opinion': ('Repair skills at school', 'Quan điểm: kỹ năng sửa chữa',
        'Secondary schools should teach students how to repair everyday household items, even if this reduces the time available for other subjects. To what extent do you agree or disagree?', '', ''),
    'discussion': ('The future of public libraries', 'Thảo luận hai phía: thư viện',
        'Some people believe public libraries should spend most of their budget on digital resources. Others think printed books and physical reading spaces should remain the priority. Discuss both views and give your own opinion.', '', ''),
    'advantages': ('Local weekend markets', 'Ưu và nhược điểm: chợ cuối tuần',
        'An increasing number of towns are closing central streets to traffic at weekends so that local markets can take place. What are the advantages and disadvantages of this approach?', '', ''),
    'outweigh': ('Four-day working weeks', 'Cân nhắc lợi hại: tuần làm việc',
        'Some employers are introducing a four-day working week without reducing weekly pay. Do the advantages of this change outweigh the disadvantages?', '', ''),
    'cause_solution': ('Unused sports facilities', 'Nguyên nhân và giải pháp: thể thao',
        'In some communities, public sports facilities are used by only a small proportion of residents. Why does this happen, and what could be done to encourage more people to use them?', '', ''),
    'problem_solution': ('Deliveries in residential streets', 'Vấn đề và giải pháp: giao hàng',
        'Online shopping has increased the number of delivery vehicles in residential streets. What problems can this create, and what measures could reduce these problems?', '', ''),
    'cause_effect': ('Moving away from small towns', 'Nguyên nhân và hệ quả: di cư',
        'Many young adults leave small towns to settle in larger cities. What are the main reasons for this trend, and what effects can it have on the towns they leave?', '', ''),
    'two_part': ('Learning outside the classroom', 'Hai câu hỏi trực tiếp: học tập',
        'Many adults choose to learn new practical skills after leaving formal education. Which skills are particularly useful in everyday life, and how can communities help adults learn them?', '', ''),
    'positive_negative': ('Cashless community services', 'Tích cực hay tiêu cực: thanh toán',
        'More local services, including swimming pools and community centres, now accept only electronic payments. Is this a positive or negative development?', '', ''),
    'custom': ('Shared urban gardens', 'Đề kết hợp: vườn cộng đồng',
        'Some city councils are replacing unused parking areas with shared gardens. Why is this policy becoming more common? Do you think its benefits outweigh its drawbacks?', '', ''),
}

PACKS = {
    'ielts-complete-bank': (list(QUESTIONS), 'IELTS Writing · Complete practice bank', 'IELTS Writing · Bộ đề đầy đủ 21 dạng', 'All 21 practice profiles, editable visuals and individual question files.', '21 dạng bài, hình vẽ sửa được và tệp đề riêng để giao Task 1/2.'),
    'ielts-academic-mock': (['bar', 'discussion'], 'IELTS Academic · 60-minute mock', 'IELTS Academic · Đề luyện 60 phút', 'Task 1 bar chart + Task 2 discussion; question paper and answer sheet.', 'Task 1 biểu đồ cột + Task 2 thảo luận; có đề và khung bài làm.'),
    'ielts-general-mock': (['formal', 'opinion'], 'IELTS General Training · 60-minute mock', 'IELTS General Training · Đề luyện 60 phút', 'Task 1 formal letter + Task 2 opinion; question paper and answer sheet.', 'Task 1 thư trang trọng + Task 2 quan điểm; có đề và khung bài làm.'),
}
LABELS_VI = ['Biểu đồ đường', 'Biểu đồ cột', 'Biểu đồ tròn', 'Bảng số liệu', 'Biểu đồ kết hợp', 'Quy trình / chu trình', 'Bản đồ / mặt bằng', 'Sơ đồ cấu tạo / hệ thống', 'Thư trang trọng', 'Thư bán trang trọng', 'Thư thân mật', 'Đồng ý / không đồng ý', 'Thảo luận hai quan điểm', 'Ưu điểm và nhược điểm', 'Lợi ích có vượt bất lợi?', 'Nguyên nhân và giải pháp', 'Vấn đề và giải pháp', 'Nguyên nhân và hệ quả', 'Hai câu hỏi trực tiếp', 'Phát triển tích cực / tiêu cực', 'Đề kết hợp / tùy chỉnh']
BASE_PACKAGES = ['iftex', 'inputenc', 'fontenc', 'lmodern', 'fontspec', 'babel', 'babel-vietnamese', 'vntex', 'amsmath', 'amssymb', 'amsthm', 'graphicx', 'xcolor', 'hyperref', 'geometry', 'tikz']


def slug(key):
    return 'task%d-%s' % (RUBRICS[key]['task'], key.replace('_', '-'))


def question(key, lang):
    profile, (_, _, prompt, visual, _) = RUBRICS[key], QUESTIONS[key]
    task = profile['task']
    header = f'WRITING TASK {task}'
    time, words = (20, 150) if task == 1 else (40, 250)
    result = rf'\section*{{{header}}}' + '\n'
    result += f'You should spend about {time} minutes on this task.\n\n'
    result += r'\begin{quote}\large ' + prompt + '\n'
    if isinstance(visual, list):
        result += '\\begin{itemize}\n' + '\n'.join('\\item ' + item for item in visual) + '\n\\end{itemize}\n'
    result += '\\end{quote}\n'
    if task == 1 and profile['family'] == 'academic':
        result += 'Describe the main features of the information, select relevant details, and make comparisons where appropriate.\n\n'
        result += '\\begin{center}\n' + visual + '\n\\end{center}\n'
    elif task == 1:
        result += 'You do not need to write any addresses.\n\n'
    else:
        result += 'Support your ideas with reasons and relevant examples.\n\n'
    result += f'Write at least {words} words.\n\\par\\bigskip\n'
    result += r'{\footnotesize\color{gray} Original practice material; fictional settings and data. Not an official IELTS examination paper.}' + '\n'
    return result


def document(body, title=True):
    return r'''% !TeX program = pdflatex
\documentclass[11pt,a4paper]{article}
\input{preamble}
\usepackage[margin=2.2cm]{geometry}
\usepackage{tikz}
\setlength{\parindent}{0pt}
\setlength{\parskip}{7pt}
\begin{document}
''' + (r'{\small\sffamily\color{teal} {{TITLE}}}\par\hrule\medskip' + '\n' if title else '') + body + '\n\\end{document}\n'


def guide(keys, lang):
    vi = lang == 'vi'
    text = ('# Hướng dẫn sử dụng bộ đề IELTS Writing\n\n' if vi else '# IELTS Writing practice project guide\n\n')
    text += ('Đây là đề luyện tự biên soạn, không phải đề thi chính thức hoặc bản chép lại từ IELTS CITY. Số liệu và địa danh là giả định. Câu hỏi giữ tiếng Anh ở cả hai ngôn ngữ dự án.\n\n'
             '1. Compile `main.tex` để xem cả bộ. Mỗi `question-taskN-*.tex` là đề độc lập, có thể compile và dùng riêng.\n'
             '2. Tutor vào IELTS Writing → Giao bài IELTS → chọn tệp đề Task 1/2 → Dùng tệp làm đề. Chọn đúng dạng bài và Academic/General Training; không dùng `answers.tex` làm đề.\n'
             '3. Với bộ đủ hai task, đặt tổng thời lượng 60 phút, Task 1 khoảng 20 phút/ít nhất 150 từ; Task 2 khoảng 40 phút/ít nhất 250 từ. Hạn nộp đặt ở form giao bài, không được tự đặt bởi template.\n'
             '4. Học viên viết ở module IELTS để lưu/nộp; `answers.tex` chỉ là khung soạn bài LaTeX tùy chọn, không tự nộp hoặc tự tính giờ.\n'
             '5. Đổi số liệu trong `questions/*.tex` (nguồn biểu đồ) rồi sửa CSV tương ứng nếu có. CSV là bản sao dữ liệu để đối chiếu, không tự điều khiển biểu đồ. Compile lại và lấy đề lại trước khi giao.\n'
             '6. Tutor đối chiếu checklist trong module. Tick không tự quy đổi thành band; không có bài mẫu hoặc điểm band giả định. Chia sẻ dự án cho Học viên với vai trò Học viên và quyền phù hợp.\n\n'
             if vi else
             'Original practice questions, not official exam papers or copies of IELTS CITY content. Places and data are fictional. Prompts remain in English in both project languages.\n\n'
             '1. Compile `main.tex` for the collection. Each `question-taskN-*.tex` is a standalone question paper.\n'
             '2. In IELTS Writing → Assign IELTS, choose a question file for each task and capture it. Set the matching profile and Academic/General Training variant; do not assign `answers.tex` as the question.\n'
             '3. A two-task mock uses 60 minutes: approximately 20 minutes/150 words for Task 1 and 40 minutes/250 words for Task 2. Set the actual duration and deadline in the assignment form. Templates do not start timers.\n'
             '4. Students write and submit in IELTS Writing. `answers.tex` is an optional LaTeX answer skeleton; it does not submit or start a timer.\n'
             '5. Edit visual data in `questions/*.tex`, then update the matching CSV if present. CSV files are reference copies, not live chart inputs. Recompile and recapture before assigning.\n'
             '6. Use the module checklist for feedback; ticks do not automatically produce a band. No fabricated model answers or bands are included. Share the project with the Student role and appropriate permission.\n\n')
    for key in keys:
        profile = RUBRICS[key]
        text += f"## {profile['label']} · `{key}`\n\n- `question-{slug(key)}.tex`\n"
        text += '\n'.join('- ' + line for line in profile['focus']) + '\n\n'
        for section, checks in profile['checks'].items():
            text += f'### {section}\n\n' + '\n'.join('- [ ] ' + check['text'] + ' (' + '/'.join(check['codes']) + ')' for check in checks) + '\n\n'
    text += '\n## References / Nguồn tham khảo\n\n'
    text += '- https://ieltscity.vn/de-thi-ielts-writing-2025/ (question-type reference only; no text or images copied)\n'
    text += '- https://ielts.org/take-a-test/test-types/ielts-academic-test/ielts-academic-format-writing\n'
    text += '- https://ielts.org/take-a-test/test-types/ielts-general-training-test/ielts-general-training-format-writing\n'
    text += '\nThe 21 profiles are teaching categories used by this app, not an official exhaustive IELTS taxonomy.\n'
    return text


def write_project(identifier, keys, title_en, title_vi, description_en, description_vi):
    folder = TEMPLATES / 'ielts' / identifier
    paths = set()
    for lang in ('en', 'vi'):
        assets = {}
        for key in keys:
            name = slug(key)
            assets[f'questions/{name}.tex'] = question(key, lang)
            assets[f'question-{name}.tex'] = document('\\input{questions/' + name + '}\n')
            if QUESTIONS[key][4]:
                assets[f'data/{name}.csv'] = QUESTIONS[key][4]
        if len(keys) == 1:
            body = '\\input{questions/' + slug(keys[0]) + '}\n'
        else:
            body = ('\\section*{IELTS Writing practice bank}\n' if len(keys) > 2 else '\\section*{IELTS Writing mock test}\nTotal writing time: 60 minutes. Complete both tasks.\n')
            body += ('\\textit{Đề luyện tự biên soạn; số liệu giả định.}\\par\n' if lang == 'vi' else '\\textit{Original practice questions; synthetic data.}\\par\n')
            body += '\\tableofcontents\n'
            for key in keys:
                label = RUBRICS[key]['label'].replace('&', r'\&').replace('–', '--')
                body += '\\clearpage\n\\phantomsection\n\\addcontentsline{toc}{section}{' + label + '}\n\\input{questions/' + slug(key) + '}\n'
        assets['main.tex'] = document(body)
        answer = '\\section*{' + ('Khung bài làm' if lang == 'vi' else 'Answer sheet') + '}\n'
        answer += ('% Viết bài của bạn bên dưới. Không phải bài mẫu hoặc câu trả lời đã nộp.\n' if lang == 'vi' else '% Write your own response below. Not a model answer or submitted response.\n')
        for key in keys:
            label = RUBRICS[key]['label'].replace('&', r'\&').replace('–', '--')
            answer += '\\section*{' + label + '}\n% Question: question-' + slug(key) + '.tex\n'
            sections = ['Opening', 'Bullet point 1', 'Bullet point 2', 'Bullet point 3', 'Closing'] if RUBRICS[key]['family'] == 'general' else ['Introduction', 'Overview', 'Body 1', 'Body 2'] if RUBRICS[key]['task'] == 1 else ['Introduction', 'Body 1', 'Body 2', 'Conclusion']
            answer += '% Suggested planning labels only; adapt the structure to the question.\n'
            answer += '\n'.join('\\subsection*{' + section + '}\n% Your response here.\n\\vspace{1cm}' for section in sections) + '\n\\clearpage\n'
        assets['answers.tex'] = document(answer)
        assets['GUIDE.md'] = guide(keys, lang)
        assets['ielts-bank.json'] = json.dumps({'version': 1, 'originalPractice': True, 'profiles': [{'id': key, 'task': RUBRICS[key]['task'], 'variant': 'general' if RUBRICS[key]['family'] == 'general' else 'academic' if RUBRICS[key]['task'] == 1 else 'either', 'questionFile': f'question-{slug(key)}.tex', 'bodyFile': f'questions/{slug(key)}.tex'} for key in keys]}, ensure_ascii=False, indent=2) + '\n'
        for name, content in assets.items():
            destination = folder / lang / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(content, encoding='utf-8')
        paths.update(assets)
    meta = {'id': identifier, 'category': 'ielts', 'title': {'en': title_en, 'vi': title_vi},
            'description': {'en': description_en, 'vi': description_vi},
            'tags': ['ielts', 'writing', *sorted({'task-' + str(RUBRICS[k]['task']) for k in keys}), *keys],
            'engine': 'pdflatex', 'mainFile': 'main.tex', 'requiredPackages': BASE_PACKAGES,
            'needsBiber': False, 'files': sorted(paths), 'sharedPreamble': True}
    (folder / 'template.json').write_text(json.dumps(meta, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return 'ielts/' + identifier


def main():
    assert set(QUESTIONS) == set(RUBRICS), 'Every checklist profile must have a practice question.'
    entries = [write_project('ielts-complete-bank', *PACKS['ielts-complete-bank'])]
    catalog_path = TEMPLATES / 'catalog.json'
    catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
    catalog['categories'] = [c for c in catalog['categories'] if c['id'] != 'ielts']
    catalog['categories'].insert(0, {'id': 'ielts', 'en': 'IELTS Writing', 'vi': 'IELTS Writing · Đề luyện viết'})
    catalog['templates'] = entries + [p for p in catalog['templates'] if not p.startswith('ielts/')]
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Generated {len(entries)} IELTS templates covering {len(QUESTIONS)} profiles (en/vi).')


if __name__ == '__main__':
    main()
