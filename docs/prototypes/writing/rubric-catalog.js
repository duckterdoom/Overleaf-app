/* Tutor-authored teaching checklists; not official band descriptors or scoring formulas. */
const RUBRIC_CATALOG_VERSION='2026-11-07.1';
const checklistItem=(id,text,codes)=>({id,text,codes:codes.split('/')});
const ci=checklistItem;
const IELTS_PROFILES={
 line:{task:1,family:'academic',label:'Line graph',focus:['Phân biệt xu hướng chung với biến động cục bộ.','Đối chiếu điểm đầu/cuối, đỉnh/đáy hoặc giao nhau nếu có.']},
 bar:{task:1,family:'academic',label:'Bar chart',focus:['So sánh đúng các nhóm trên cùng thang đo.','Nếu có thời gian, phân biệt thay đổi theo năm với chênh lệch giữa nhóm.']},
 pie:{task:1,family:'academic',label:'Pie chart',focus:['Phân biệt phần trăm với số lượng thực.','So sánh cơ cấu/tỷ trọng giữa các hình, nếu đề có nhiều hình.']},
 table:{task:1,family:'academic',label:'Table',focus:['Chọn dữ liệu nổi bật từ hàng/cột thay vì đọc mọi ô.','Kiểm tra đơn vị, phân nhóm và ngoại lệ.']},
 mixed:{task:1,family:'academic',label:'Mixed / multiple visuals',focus:['Bao quát tất cả hình được giao.','Kết nối thông tin giữa các hình khi có căn cứ, không gán quan hệ nhân quả.']},
 process:{task:1,family:'academic',label:'Process / cycle',focus:['Theo dõi đúng trình tự, nhánh hoặc vòng lặp.','Mô tả đầu vào/đầu ra, biến đổi và tác nhân theo hình; không tự bịa số liệu.']},
 map:{task:1,family:'academic',label:'Map / plan',focus:['Mô tả thay đổi hoặc bố trí không gian theo mốc đề cho.','Phân biệt hạng mục giữ nguyên, xây mới, dỡ bỏ và chuyển vị trí khi có.']},
 diagram:{task:1,family:'academic',label:'Object / system diagram',focus:['Giải thích cấu phần và quan hệ/chức năng thể hiện trong sơ đồ.','Không ép ngôn ngữ tăng/giảm cho sơ đồ không có chuỗi thời gian.']},
 formal:{task:1,family:'general',label:'Formal letter',focus:['Xưng hô và kết thư phù hợp quan hệ trang trọng.','Yêu cầu hoặc đề nghị rõ ràng, lịch sự, tránh thân mật không phù hợp.']},
 semiformal:{task:1,family:'general',label:'Semi-formal letter',focus:['Giữ mức lịch sự phù hợp với người nhận có quan hệ quen biết nhất định.','Không đổi đột ngột giữa văn phong quá trang trọng và quá suồng sã.']},
 informal:{task:1,family:'general',label:'Informal letter',focus:['Giọng văn gần gũi phù hợp người nhận.','Duy trì mục đích thư, không biến thành essay trang trọng hoặc hội thoại rời rạc.']},
 opinion:{task:2,label:'Opinion / agree–disagree',focus:['Làm rõ mức độ đồng ý hoặc lập trường của người viết.','Lập luận trong thân bài hỗ trợ nhất quán cho lập trường; không buộc cân bằng hai phía.']},
 discussion:{task:2,label:'Discuss both views (+ opinion)',focus:['Thực sự giải thích cả hai quan điểm trong đề.','Thể hiện ý kiến riêng nếu đề yêu cầu; không chỉ lặp hai quan điểm.']},
 advantages:{task:2,label:'Advantages & disadvantages',focus:['Xử lý cả lợi ích và bất lợi theo phạm vi câu hỏi.','Không tự bắt buộc chọn bên thắng nếu đề không yêu cầu đánh giá outweigh.']},
 outweigh:{task:2,label:'Do advantages outweigh disadvantages?',focus:['Trình bày hai phía có liên quan.','Có cơ sở so sánh để kết luận bên nào nổi trội; không chỉ đếm số ý.']},
 cause_solution:{task:2,label:'Causes & solutions',focus:['Nêu và giải thích nguyên nhân liên quan.','Giải pháp có đối tượng thực hiện và phù hợp nguyên nhân đã nêu.']},
 problem_solution:{task:2,label:'Problems & solutions',focus:['Mô tả vấn đề/tác động được hỏi, phân biệt với nguyên nhân.','Đề xuất giải pháp có cơ chế xử lý vấn đề đã xác định.']},
 cause_effect:{task:2,label:'Causes & effects',focus:['Giải thích vì sao hiện tượng xảy ra.','Phân tích hệ quả được hỏi; không thay phần effects bằng solutions khi đề không hỏi.']},
 two_part:{task:2,label:'Two-part / direct questions',focus:['Ghi rõ câu hỏi A và câu hỏi B của đề.','Trả lời đủ cả hai câu hỏi; phân bổ phát triển ý tương xứng với yêu cầu.']},
 positive_negative:{task:2,label:'Positive / negative development',focus:['Có đánh giá rõ về chiều hướng phát triển theo câu hỏi.','Giải thích bằng tác động/đối tượng cụ thể thay vì chỉ liệt kê ưu nhược điểm.']},
 custom:{task:2,label:'Mixed / yêu cầu tùy chỉnh',focus:['Tách từng mệnh đề yêu cầu trong đề thật.','Kiểm tra từng yêu cầu; Tutor bổ sung checklist để phù hợp đề kết hợp.']},
};
const LETTER_PURPOSES={complaint:'Complaint / phàn nàn',request:'Request / hỏi thông tin',apology:'Apology / giải thích',invitation:'Invitation / mời',thanks:'Thanks / cảm ơn',advice:'Advice / đề nghị',application:'Application / ứng tuyển',other:'Khác / kết hợp'};
function profileOptions(task,variant){return Object.entries(IELTS_PROFILES).filter(([,p])=>p.task===task&&(task===2||p.family===variant));}
function sectionChecks(profileId,part){
 const p=IELTS_PROFILES[profileId],main=p.task===2?'TR':'TA';
 const language=[ci('precision','Từ/cụm từ dùng đúng nghĩa trong đoạn; ghi ví dụ nếu cần sửa.','LR'),ci('sentences','Câu đủ nghĩa, ngữ pháp và dấu câu được kiểm tra bằng minh chứng.','GRA')];
 let list=[];
 if(p.task===2&&part==='intro')list=[ci('scope','Giới thiệu đúng vấn đề/phạm vi, không đổi nghĩa đề.','TR'),ci('position','Lập trường hoặc hướng trả lời đủ rõ theo yêu cầu thực tế.','TR'),ci('relevance','Dẫn nhập liên quan, tránh mở đầu học thuộc không phục vụ bài.','TR/CC')];
 if(p.task===2&&part==='body')list=[ci('main-idea','Đoạn có trọng tâm, góp phần trả lời câu hỏi được giao.','TR/CC'),ci('development','Ý được giải thích, không dừng ở một khẳng định ngắn.','TR'),ci('support','Minh họa/lý giải cụ thể hỗ trợ ý; ví dụ là một cách, không bắt buộc ở mọi câu.','TR'),ci('flow','Các câu nối tiếp hợp lý, tránh lặp lại hoặc nhảy ý.','CC'),ci('consistency','Ý trong đoạn phù hợp với lập trường và các đoạn khác.','TR/CC')];
 if(p.task===2&&part==='conclusion')list=[ci('answer','Kết lại câu trả lời/đánh giá đúng câu hỏi đã giao.','TR'),ci('consistent','Kết luận nhất quán với lập luận trong bài.','TR/CC'),ci('synthesis','Tổng hợp được ý chính, không đưa lập luận mới chưa phát triển.','CC/TR')];
 if(p.task===1&&p.family==='academic'&&part==='intro')list=[ci('visual','Giới thiệu đúng đối tượng/hình được mô tả.','TA'),ci('context','Giữ đúng địa điểm, thời gian và đơn vị nếu hình có.','TA/LR'),ci('paraphrase','Diễn đạt rõ, không đổi nghĩa nhãn hoặc tiêu đề hình.','TA/LR')];
 if(p.task===1&&p.family==='academic'&&part==='overview')list=[ci('big-picture','Nêu được bức tranh chung phù hợp hình/đề.','TA'),ci('selection','Chọn đặc điểm nổi bật, không chỉ kể các chi tiết rời rạc.','TA'),ci('overview-role','Nhận diện được ý tổng quan dù nằm chung với đoạn khác.','TA/CC')];
 if(p.task===1&&p.family==='academic'&&part==='body')list=[ci('details','Chi tiết mô tả có thể đối chiếu với hình gốc.','TA'),ci('grouping','Nhóm thông tin theo logic thay vì liệt kê từng điểm máy móc.','CC/TA'),ci('relation','So sánh/trình tự/vị trí phù hợp loại hình, có minh chứng cụ thể.','TA'),ci('accuracy','Tên, đơn vị và mốc thời gian/quy trình không bị nhầm.','TA/LR')];
 if(p.task===1&&p.family==='general'&&part==='intro')list=[ci('recipient','Mở thư phù hợp người nhận và quan hệ.','TA/LR'),ci('purpose','Mục đích thư rõ, bám tình huống.','TA'),ci('opening-tone','Giọng điệu mở đầu phù hợp mức trang trọng.','TA/LR')];
 if(p.task===1&&p.family==='general'&&part==='body')list=[ci('bullet-coverage','Nội dung đoạn xử lý một hoặc nhiều ý yêu cầu trong đề; ghi rõ ý nào.','TA'),ci('bullet-development','Phần được hỏi được phát triển bằng chi tiết liên quan.','TA'),ci('tone','Giữ giọng văn và cách xưng hô phù hợp người nhận.','TA/LR'),ci('paragraph-flow','Các ý/đoạn liên kết hợp lý, không bắt mỗi bullet phải là một đoạn riêng.','CC')];
 if(p.task===1&&p.family==='general'&&part==='closing')list=[ci('next-step','Lời kết/đề nghị tiếp theo phù hợp mục đích thư.','TA'),ci('signoff','Kết thư và tên phù hợp giọng văn đã chọn.','TA/LR'),ci('no-new-task','Không phát sinh yêu cầu mới làm lệch mục đích thư.','CC/TA')];
 if(part==='whole')list=[ci('all-demands','Đối chiếu mọi yêu cầu của đề thật, không chỉ tên dạng bài. ',main),...(p.family==='general'?[ci('three-bullets','Kiểm đủ ba bullet và các ý nhỏ như and/why/plural trong từng bullet.','TA')]:[]),ci('progression','Nhìn toàn bài có sự phát triển và liên kết giữa các phần.','CC'),ci('lexical-patterns','Ghi mẫu dùng từ tốt/lỗi lặp lại trong toàn bài.','LR'),ci('grammar-patterns','Ghi phạm vi cấu trúc và lỗi lặp lại có ảnh hưởng nghĩa.','GRA')];
 const specific=['body','overview','whole'].includes(part)?p.focus.map((text,i)=>ci('type-'+i,text,main)):[];
 return [...list,...specific,...(part==='whole'?[]:language)];
}
