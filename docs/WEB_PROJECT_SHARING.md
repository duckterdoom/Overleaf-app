# Chia sẻ dự án trên web (10/11/2026)

Cập nhật MongoDB: ACL/link/invitation hiện nằm trong collections MongoDB chính; SQLite chỉ còn nguồn migration. Giao diện, token và quyền giữ nguyên khi chuyển dữ liệu theo [MONGODB_PRIMARY.md](MONGODB_PRIMARY.md).

Nút **Chia sẻ / Share** trong editor web thay hộp thoại phòng TCP/TLS bằng `SharePanel.tsx`. Desktop cũ giữ NetworkPanel. Web không còn cho kết nối TCP/TLS qua các action connect/edit/cursor/reconnect.

## Sử dụng

### Riêng tư

Mặc định dự án riêng tư. Chủ sở hữu nhập một hoặc nhiều email phân cách bằng dấu phẩy, chọn **Chỉ xem** hoặc **Chỉnh sửa**, rồi bấm **Mời**. Danh sách hiển thị email, lời mời đang chờ/đã tham gia, quyền, nút sao chép liên kết lời mời và nút thu hồi.

**Chưa có dịch vụ gửi email tự động.** Chủ sở hữu sao chép liên kết lời mời ở hàng email và tự gửi cho người nhận. Người nhận phải mở đúng liên kết và đăng nhập đúng email. Không tự cấp quyền chỉ vì một tài khoản đăng ký cùng email; phải có liên kết lời mời không đoán được và chấp nhận. Nếu chưa có tài khoản, có thể đăng ký/chọn role hoặc đăng nhập Google trước; frontend giữ lời mời chờ trong sessionStorage để tiếp tục.

Tài liệu đã nhận xuất hiện trong **Được chia sẻ với bạn**, đồng thời thuộc danh sách Tất cả dự án. Chỉ cùng trỏ tới một vùng source của chủ sở hữu, không tạo bản sao riêng. Thẻ chia sẻ ghi người sở hữu và quyền; không có menu xóa/đổi tên dự án của chủ sở hữu.

### Bất kỳ ai có liên kết

Chủ sở hữu đổi chế độ thành **Bất kỳ ai có liên kết**, mặc định Chỉ xem. Có thể chọn Editor và sao chép link. **Người mở link vẫn cần đăng nhập Latex và hoàn tất chọn role**; đây không phải trang PDF anonymous hoặc đăng công khai để tìm kiếm.

Tắt link (chuyển Riêng tư) hoặc **Tạo liên kết mới** sẽ vô hiệu hóa link cũ và quyền có được từ link cũ ở các request sau. Thành viên đã nhận lời mời riêng vẫn có quyền của lời mời. Nếu một người có cả lời mời và link, quyền hiệu lực là quyền cao hơn; thu hồi lời mời không vô hiệu hóa một link public còn hoạt động.

Link chia sẻ có dạng `https://your-domain/#share=<random-token>`; token trong fragment được bootstrap giữ tạm rồi xóa khỏi thanh địa chỉ. Link không chứa đường dẫn thư mục server hoặc TCP access token. Secret chia sẻ được lưu trong database ACL để chủ sở hữu có thể sao chép lại, vì vậy cần bảo vệ database/backups như dữ liệu tài khoản.

## Quyền thực tế

| Quyền | Khả năng |
| --- | --- |
| Owner | Sửa tài liệu, quản lý dự án, mời/xóa thành viên, đổi quyền, bật/tắt/tạo lại link |
| Editor | Đọc và sửa source/file, compile, history, review và các endpoint tài liệu đã được phép; không quản lý ACL hoặc xóa/đổi tên dự án |
| Viewer | Xem source, history, preview/download và tự compile PDF; không sửa source/file/review/Word draft hoặc thiết lập chia sẻ |

Word preview dạng editor được vô hiệu hóa ở giao diện Viewer; dùng PDF preview. Backend vẫn kiểm mọi ghi dữ liệu độc lập với trạng thái nút. Link chỉ cấp quyền cho đúng project, không cấp quyền với các dự án khác của owner. Action có thể đọc chéo project như copy-file bị chặn đối với editor nhận chia sẻ, đến khi có cơ chế kiểm ACL cả source và destination riêng.

Các request shared được định tuyến về Store/Compiler của chủ sở hữu, sau khi gateway kiểm ACL. ContextVar mang người thao tác thực tế tới history/review; không thay phiên owner bằng phiên của người xem trên toàn process. Preferences, profile và avatar của mỗi tài khoản vẫn dùng workspace cá nhân của tài khoản đó.

## Code và lưu trữ

- `collaboration/sharing.py`: ACL/link/invitation trong MongoDB chính, collections shared_projects/invitations/link_visitors.
- `http/sharing_api.py`: GET/PATCH sharing, POST invitation, PATCH/DELETE member, POST accept, danh sách own + shared được phép.
- `http/web_accounts.py`: routing theo project owner, kiểm quyền HTTP/actions; chặn phòng TCP trên web.
- `auth/context.py`, `State.session`: actor theo request thay vì ghi đè phiên người khác.
- `components/SharePanel.tsx`: dialog theo theme, email/quyền, public/private, copy link, reset/revoke, mobile/focus/Escape.
- `lib/api.ts`: giữ link chờ qua login và gửi thay đổi permission từ response tới hook.
- `useParity.ts`: nhận link sau login/onboarding, readonly theo permission, mở Share trên web.
- `page.tsx` / `ProjectCard.tsx`: shared list thật, không dùng placeholder của mục 72 nữa, trạng thái quyền và nút phù hợp.

## Con trỏ và người đang chỉnh sửa qua WebSocket

Owner/Editor mở dự án sẽ kết nối `/api/projects/{id}/presence` bằng cookie đăng nhập cùng nguồn. Avatar cạnh trái nút Lịch sử chỉ hiển thị **người khác**, dùng ảnh profile hoặc chữ đầu tên; hover cho biết tên, file, trạng thái focus và cảnh báo nội dung chưa đồng bộ. Avatar bản thân đã có ở góc phải. Các tab cùng một tài khoản gộp thành một avatar. Nếu đã nối socket và chưa có người khác, báo “Chỉ bạn đang mở dự án”. Người chỉ xem không phát presence và không xuất hiện ở danh sách này.

Editor LaTeX vẽ caret màu và nhãn tên của người khác khi **cùng file và nội dung SHA-256 giống nhau**. Vùng bôi chọn chữ chỉ hiển thị ở tài khoản đang chọn, không vẽ vùng bôi remote. Client gửi caret thu gọn; server cũng thu gọn anchor=head cho cả client cũ. Chuyển cửa sổ giữ vị trí caret gần nhất. Đổi file/rời trang/mất kết nối sẽ bỏ dấu cũ. Buffer khác nhau tạm ẩn cursor để không đánh dấu nhầm; avatar vẫn thể hiện file đang mở. Khi hai bản hội tụ, cursor tự hiện lại. Có heartbeat/reconnect, kiểm session/quyền định kỳ và trên mỗi push; transport ping/pong phát hiện socket mất.

Backend hiện giữ presence trong RAM một worker, không thêm dữ liệu online vào MongoDB. Triển khai nhiều worker cần shared presence như Redis pub/sub. Không vẽ con trỏ lên PDF/Word. Kiểm chứng bằng `tests/test_presence.py` và browser `scripts/test-presence.mjs`, kết quả ở `target/presence-browser-04`.

## Nhận xét dùng chung và chọn chữ trong editor

Review của project được lưu cùng dữ liệu chủ sở hữu, người có quyền truy cập đọc qua API review. Sau create/reply/resolve/delete thành công, backend phát `review-changed` đúng phòng dự án; client tải lại review qua API kiểm ACL, không đưa toàn bộ nội dung comment vào presence frame. Khi nối lại WebSocket hoặc mở lại panel, review được tải mới. Panel mở có refresh dự phòng: 5 giây cho editor, 1,5 giây cho viewer; panel ẩn không poll nhưng vẫn nhận thông báo cập nhật. Draft và ô trả lời đang gõ được giữ. Viewer chỉ đọc; backend chặn ghi và UI không mở form reply/create cho viewer.

Click vị trí comment chỉ điều hướng/chọn đoạn **một lần**, không chạy lại khi có render, polling hay cập nhật con trỏ. Hàm dịch, extensions/basic setup và callback CodeMirror được giữ ổn định để không reconfigure xử lý chuột giữa lúc kéo chọn. Có thể kéo chọn một phần từ để thay/xóa hoặc gắn comment; quote của comment lấy đúng selection. Nếu đoạn gốc bị sửa hoặc không còn duy nhất, comment vẫn được giữ và app không giả vị trí.

CodeMirror vẽ màu vùng chọn dưới lớp chữ. CSS active-line nền đục trước đây che lớp này, khiến người đang chọn không thấy màu bôi dù selection đúng. Attribute cục bộ data-local-selection làm nền active-line trong suốt khi có vùng chọn, cùng màu selection rõ cho light/dark. Không dựa vào selection remote để hiển thị vùng bôi của chính mình.

Kiểm thử `test-shared-review-selection.mjs` dùng Edge/Chrome thực, kéo BRAVO trong từ dài ngay khi có remote OT update, thay/xóa đúng đoạn, tạo comment DELTA, kiểm tra tác giả thật, reply/resolve/delete từ xa và viewer polling. Test chụp ảnh dòng chữ và kiểm pixel xanh, không chỉ kiểm range/quote. Backend có `test_shared_review.py` kiểm shared authorship, notification và ACL; presence test kiểm server không phát range.

## Đồng bộ nội dung editor trực tiếp qua WebSocket

Owner/Editor hiện đồng bộ từng thao tác source trên cùng WebSocket presence, dùng `@codemirror/collab` (OT) và authority Python `collaboration/live_documents.py`. Xóa/chèn chữ được gom trong cửa sổ 16ms, không trì hoãn liên tục khi gõ và không cần nhấn Lưu. Máy chủ kiểm tra phiên/quyền trên từng push, áp dụng ChangeSet theo UTF-16 của JavaScript, ghi source nguyên tử + fsync và metadata/activity MongoDB trước khi xác nhận rồi broadcast cho các editor cùng file. Editor khác file không nhận nội dung nhầm file. Viewer giữ readonly, thấy bản lưu qua polling và không phát presence.

Để giảm độ trễ, lịch sử gom checkpoint tối đa một bản mới nhất mỗi tác giả/file trong khoảng 2 giây, giữ thứ tự sửa, flush khi rời dự án và khi server dừng bình thường. History là bước nền; lỗi/quota history không làm mất source đã lưu và có cảnh báo log không chứa URI. Hard-kill có thể mất checkpoint chưa ghi, source đã xác nhận vẫn được lưu. Khi OT hoạt động, client không gửi thêm HTTP editing/checkpoint cho từng thay đổi và chỉ poll file/PDF mỗi 3 giây (viewer vẫn 750ms); nội dung editor đi qua WebSocket. DB/file I/O chạy ở thread để không chặn event loop, authority vẫn tuần tự và hoàn thành commit/broadcast nếu socket ngắt giữa lúc ghi.

Authority đánh số version; push trên version cũ không ghi đè mà trả các thay đổi đã xác nhận. CodeMirror rebase phần sửa local chưa được xác nhận rồi gửi lại. Nút Lưu/compile chờ xác nhận live thay vì PUT cả file; polling không thay toàn bộ nội dung editor đang được OT quản lý. HTTP PUT vẫn giữ `expected`/409 cho đường dùng API và các công cụ cũ. Nội dung Word/DOCX, review/chat và cấu trúc cây file chưa dùng OT.

Phiên live có epoch và cửa sổ lịch sử giới hạn 4.096 thao tác/8 MiB mỗi file. Mất kết nối giữ thao tác trong editor; reconnect cùng epoch lấy phần còn thiếu và rebase. Nếu server restart, file bị công cụ khác ghi hoặc lịch sử đã bị cắt và không thể nối an toàn, app giữ bản local, báo lỗi để người dùng sao chép/đối chiếu, không tự ghi đè. Chưa lưu queue offline bền vững qua đóng/reload browser. Authority/log và presence vẫn nằm trong RAM của **một worker**; source đã xác nhận nằm trên volume server và metadata trong MongoDB. Chưa hỗ trợ nhiều worker/server hoặc cộng tác P2P.

## Thử local

1. Lưu bài, Ctrl+C dừng backend cũ tại terminal hiện đang chạy, sau đó chạy lại `scripts\run-python-web.bat` từ **cùng terminal đã cấu hình Google**. Không mở thêm một server dùng trùng cổng.
2. Reload frontend (Ctrl+F5 sau khi lưu bài).
3. Tài khoản A mở project → Chia sẻ → nhập email B, chọn quyền và Mời.
4. Sao chép link lời mời và mở trong browser profile/incognito khác, đăng nhập B. Sau chấp nhận, kiểm tra source và Được chia sẻ với bạn.
5. Thử public link, Viewer/Editor, tạo lại link và chuyển về Riêng tư.

`127.0.0.1` chỉ trỏ tới máy đang mở trình duyệt. Hai profile trên cùng máy thử được ngay; để gửi cho máy khác cần triển khai web đúng domain/HTTPS và hoàn thiện cấu hình host/proxy/compile isolation. Launcher hiện vẫn chỉ bind loopback; tính năng sharing không tự làm server truy cập được từ Internet.

## Kiểm chứng

Backend tests kiểm lời mời đúng/sai email, accept/revoke, viewer không ghi được, editor không quản lý ACL hoặc đọc dự án khác, link rotation/private, downgrade, session/ACL sau restart, và lưu source xung đột trả 409. Browser test dùng owner/member/visitor độc lập, lời mời giữ qua đăng ký/chọn role, clipboard link, Viewer không gõ được, compile PDF, nâng Editor lưu được, shared list, public link/revoke, mobile và Escape. Google OAuth vẫn cần credential thật của người vận hành; sharing không đổi cấu hình Google.
