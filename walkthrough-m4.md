# M4 Collaboration Logic - Tiến độ công việc

Cập nhật: 31/10/2026. Người phụ trách: M4.

## 1. Tóm tắt nhanh
- Phần M4 theo kế hoạch ban đầu: **xong**, đã merge vào nhánh `main` (Pull Request #2).
- Phần bổ sung "sửa offline rồi tự đồng bộ lại" (mục A): **xong code và test**, chờ commit và mở Pull Request.
- Chưa chạy được toàn bộ dự án từ đầu đến cuối vì **M3 (mạng TCP) chưa có code**. Đây là phần chặn lớn nhất, không phải lỗi của M4.
- Mục C (benchmark): **xong**, đã chạy thử và có số liệu mẫu.
- Mục B (tua lại lịch sử): chưa làm.

## 2. Bảng tiến độ

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| 6 lớp dữ liệu dùng chung (Common) | Xong, đã merge | |
| CRDT Engine, Operation History, Collaboration Thread Pool | Xong, đã merge | |
| Sync Broadcaster, Document Sync Manager | Xong, đã merge | Pull Request #2 |
| Kiểm tra M4 Smoke (9 kiểm tra) | Đạt | Chưa phải JUnit |
| Chế độ offline và đồng bộ lại (mục A) | Xong, chưa commit | Mục 3 bên dưới |
| Sửa thứ tự gửi thông điệp trong Sync Broadcaster | Xong, chưa commit | Mục 3.4 |
| Kiểm tra Offline Resync Smoke (26 kiểm tra) | Đạt | |
| Chạy `mvn clean compile` trên máy thật | Chưa | Sandbox không tải được Maven |
| Chuyển test sang JUnit 5 | Chưa | Cần thêm `junit-jupiter` vào `overleaf-server/pom.xml` |
| Nối với M3 (mạng) | Chưa, M3 chưa có code | |
| Nối với client (M1/M2) | Chưa | |
| Mục C: benchmark (`BenchmarkM4`) | Xong, chưa commit | Mục 3b bên dưới |
| Mục B: tua lại lịch sử | Chưa bắt đầu | |
| Báo cáo phần M4 | Có bản nháp tiếng Việt, đã cập nhật phần offline và benchmark | Cần tự sửa lại bằng lời của mình, thay số liệu bằng số đo trên máy của nhóm, rồi đưa vào file báo cáo |

## 3. Chi tiết phần mới: offline và đồng bộ lại (mục A)

### 3.1 Vấn đề cần giải quyết
Overleaf web cần mạng liên tục. Với ứng dụng desktop, người dùng nên tiếp tục gõ khi mất mạng và không mất chữ nào khi nối lại, kể cả khi người khác đã sửa cùng lúc.

### 3.2 Cách hoạt động
1. Client giữ mọi thao tác trong danh sách "chưa được xác nhận" cho đến khi nhận ACK. Nhờ vậy cả thao tác viết lúc offline lẫn thao tác đã gửi nhưng rớt mạng trước khi nhận ACK đều không mất.
2. Client nhớ mã thao tác cuối cùng đã thấy từ server (gọi là "điểm đã thấy"). Server cập nhật điểm này bằng thông điệp CHECKPOINT khi vào phòng và khi đồng bộ lại xong.
3. Khi nối lại, client gửi thông điệp RESYNC gồm điểm đã thấy và các thao tác chưa xác nhận.
4. Server xử lý (hàm handle Resync trong Document Sync Manager):
   - Thao tác nào đã có trong lịch sử (đã áp dụng nhưng ACK bị mất): chỉ ACK lại, không áp dụng lần hai.
   - Tìm "phần còn thiếu": các thao tác của người khác nằm sau điểm đã thấy. Từng thao tác offline được dịch vị trí qua phần này, rồi áp dụng như thao tác thường và phát cho mọi người.
   - Nếu không tìm thấy điểm đã thấy (lịch sử đã bị xóa bớt), dùng cách dự phòng là đồng hồ Lamport như thao tác thường.
   - Cuối cùng gửi FULL_SYNC (văn bản chuẩn) và CHECKPOINT mới cho client.
5. Nếu phòng không còn (server khởi động lại), server trả lỗi `ROOM_UNKNOWN` và không đụng đến dữ liệu. Client vẫn giữ thao tác của mình.

Ví dụ đã kiểm tra: văn bản "Hello World". Bob mất mạng, gõ "!" ở cuối và ">" ở đầu. Alice thêm "Big " và "<<". Khi Bob nối lại, mọi máy cùng ra `<<>Hello Big World!`.

### 3.3 File thay đổi
| File | Thay đổi |
|---|---|
| `MessageType.java` (Common) | Thêm `RESYNC`, `CHECKPOINT` |
| `OperationCodec.java` (Common, mới) | Chuyển thao tác sang và từ JSON, định dạng nội dung RESYNC. Chuyển từ server sang Common để client dùng chung |
| `OfflineOperationQueue.java` (Common, mới) | Hàng đợi thao tác phía client |
| `OperationHistory.java` | Thêm `contains`, `getOperationsAfter`, `getLastOperationId`, `wasTrimmed` |
| `DocumentSyncManager.java` | Thêm `handleResync`, chống áp dụng trùng ở TEXT_EDIT, gửi CHECKPOINT khi vào phòng, tách hàm `applyAndPublish` |
| `SyncBroadcaster.java` | Mỗi client có hộp thư đi riêng để thông điệp đến đúng thứ tự |
| `OfflineResyncSmoke.java` (test, mới) | 5 tình huống, 26 kiểm tra |
| `BenchmarkM4.java` (test, mới) | Benchmark, xem mục 3b |

### 3.4 Lỗi tìm thấy và đã sửa
Trước đây Sync Broadcaster gửi qua một nhóm luồng co giãn nên hai thông điệp liên tiếp tới cùng một client có thể đến **đảo thứ tự** (ví dụ ACK đến sau thao tác kế tiếp). Điều này làm sai "điểm đã thấy". Đã sửa bằng hộp thư đi riêng cho mỗi client, và có test gửi 300 thông điệp để kiểm tra thứ tự.

### 3.5 Kết quả kiểm tra
| Tình huống | Kết quả |
|---|---|
| 1. Bob offline, Alice sửa, Bob nối lại | Đạt, 3 máy cùng văn bản |
| 2. ACK bị mất, Bob gửi lại | Đạt, không bị trùng thành "abcabc" |
| 3. Phòng không còn | Đạt, lỗi `ROOM_UNKNOWN`, client giữ nguyên thao tác |
| 4. Lịch sử bị xóa bớt | Đạt, không mất chữ nào (vị trí chèn có thể lệch, xem 3.6) |
| 5. Thứ tự 300 thông điệp | Đạt |

### 3.6 Giới hạn đã biết (cần nói thật khi báo cáo)
- Sau khi đồng bộ lại, văn bản của client bị **thay bằng bản từ server**, nên con trỏ có thể nhảy. Giao diện cần khóa ô soạn thảo trong lúc `isResyncing()` là true (thường vài trăm mili giây).
- Cách dự phòng (lịch sử bị xóa bớt) có thể đặt chữ sai vị trí so với ý người gõ, dù không mất chữ. Trong test 4, chữ "B" nằm giữa các chữ "x" thay vì ở cuối.
- Hai người cùng chèn đúng một vị trí: thứ tự quyết định bởi thứ tự server nhận. Hai người cùng xóa đoạn chồng nhau chưa được kiểm tra kỹ.
- Nếu ACK bị mất và đồng thời người khác sửa, vị trí của thao tác kế tiếp có thể lệch nhẹ.
- Nội dung phòng chỉ nằm trong bộ nhớ server. Server tắt thì mất, đây là lý do có lỗi `ROOM_UNKNOWN`.
- Đã chạy bằng `javac` trong một tiến trình, chưa chạy qua mạng thật.

## 3b. Benchmark (mục C)

### Đo cái gì và không đo cái gì
`BenchmarkM4` đo tốc độ xử lý của bộ máy M4 **trong một tiến trình Java**, không qua mạng. "Độ trễ" là thời gian từ lúc client gửi thao tác đến lúc nhận ACK trong bộ nhớ. **Không phải** độ trễ mạng thật giữa hai máy, vì M3 chưa có code. Khi viết báo cáo phải ghi đúng như vậy, và không dùng con số "<50ms" cũ vì nó chưa từng được đo.

### Bốn phép đo
| Phép đo | Nội dung |
|---|---|
| B1 | Một phòng, 1 đến 16 người cùng gõ (vòng lặp: gửi, chờ ACK, gửi tiếp) |
| B2 | 1 đến 8 phòng chạy song song, mỗi phòng 4 người |
| B3 | Một người gõ, 1 đến 50 người nhận (chờ đến khi tất cả đã nhận) |
| B4 | Thời gian đồng bộ lại sau offline với số thao tác offline và số thao tác bỏ lỡ khác nhau |

Mỗi phép đo chạy 1 lần khởi động rồi 3 lần chính thức, lấy lần ở giữa. Sau mỗi lần chạy, chương trình kiểm tra văn bản cuối có đúng số ký tự (không mất thao tác). Tất cả đều đúng.

### Số liệu mẫu (máy ảo 2 nhân CPU, Java 21, đã ghi trong `benchmark-results.md`)
- B1: từ khoảng 2 100 thao tác/giây (1 người) lên khoảng 4 500 thao tác/giây (8 đến 16 người). Độ trễ trung vị dưới 3 ms. Thông lượng không tăng thêm khi từ 8 lên 16 người vì máy chỉ có 2 nhân CPU.
- B2: tổng thông lượng tăng từ khoảng 6 100 lên khoảng 12 000 thao tác/giây khi tăng từ 1 lên 8 phòng (gấp đôi, không gấp tám). Độ trễ trung vị tăng từ 0,3 lên 2,5 ms vì 32 người dùng giả cùng chia nhau 2 nhân CPU.
- B3: phát cho 50 người nhận mất trung vị khoảng 0,3 ms, tăng gần tuyến tính theo số người nhận.
- B4: đồng bộ lại với 1000 thao tác offline và 1000 thao tác bỏ lỡ mất khoảng 65 ms.

Các số này chỉ có ý nghĩa trên máy ảo đã đo. Hãy chạy lại trên máy của nhóm (xem mục 5) và dùng số của máy đó.

### Điều đáng chú ý về code (không phải kết quả đo)
Mỗi lần xử lý một thao tác sửa, `getOperationsSince` duyệt toàn bộ lịch sử của phòng (tối đa 10 000 thao tác). Chi phí vì vậy tăng theo độ dài lịch sử. Chưa đo riêng ảnh hưởng này. Nếu cần tối ưu, có thể lập chỉ mục theo vị trí trong lịch sử.

## 4. Hướng dẫn nối vào client và mạng (gửi cho M1, M3)

Client (M1/M2), mỗi phòng tạo một `OfflineOperationQueue(roomId, userId, userName)`:
1. Mỗi khi người dùng gõ: `SyncMessage m = queue.prepareEdit(op);` rồi nếu `m != null` thì gửi `m`.
2. Mỗi thông điệp nhận từ server: gọi `queue.handleServerMessage(msg)` trước, rồi xử lý bình thường (FULL_SYNC thì thay văn bản, TEXT_EDIT thì áp dụng vào văn bản).
3. Khi socket đứt: `queue.onConnectionLost()`. Người dùng vẫn gõ được, `prepareEdit` trả về null.
4. Khi socket nối lại: gửi `queue.onReconnected()` đầu tiên (không cần JOIN_ROOM). Khóa ô soạn thảo cho đến khi `queue.isResyncing()` là false.
5. Nếu nhận lỗi `ROOM_UNKNOWN`: giữ văn bản local, báo người dùng lưu bản sao, và để họ vào lại phòng.

Mạng (M3): cài `ClientSession` trên mỗi socket, gọi `DocumentSyncManager.handleMessage(session, message)` cho mọi thông điệp nhận được (kể cả RESYNC), và `handleDisconnect(roomId, session)` khi socket đứt.

Quy ước đồng hồ Lamport: client tăng đồng hồ trước khi tạo thao tác và cập nhật `max(local, op.ts)` khi nhận thao tác từ server.

## 5. Cách chạy kiểm tra trong IntelliJ
1. Mở `overleaf-server/src/test/java/.../collaboration/`.
2. Bấm mũi tên xanh cạnh hàm `main` của `M4Smoke` hoặc `OfflineResyncSmoke`, chọn Run.
3. Cuối log phải có dòng `ALL PASSED`. Nếu chữ tiếng Việt hiện dấu `?`, vào Run, Edit Configurations, ở VM options nhập `-Dfile.encoding=UTF-8`.
4. Benchmark: chạy `BenchmarkM4.main()` tương tự (mất khoảng 2 phút, nên đóng bớt chương trình khác khi đo). Kết quả in ra màn hình và ghi vào file `benchmark-results.md` ở thư mục đang chạy.

## 6. Việc cần làm tiếp (theo thứ tự ưu tiên)
1. Chạy `mvn clean compile -pl overleaf-common,overleaf-server -am` trên máy, rồi commit và mở Pull Request cho mục A (nhánh gợi ý `feat/m4-offline-resync`).
2. Báo M3 và M1 nội dung mục 4. Đây là phần đang chặn việc chạy thử cả dự án.
3. Chuyển hai chương trình kiểm tra sang JUnit 5 để `mvn clean test` chạy được theo quy ước của nhóm.
4. Đưa phần M4 vào báo cáo (xem bản nháp tiếng Việt), thêm mục offline vào các phần Novelty, Limitations và Future Work.
5. Chạy `BenchmarkM4` trên máy của nhóm và thay số liệu trong báo cáo bằng số đo được.
6. Nếu còn thời gian: mục B (tua lại lịch sử).

## 7. Rủi ro ngoài phạm vi M4 (đã báo cho nhóm)
- File `database/src/main/resources/config.properties` chứa mật khẩu MongoDB thật và đã bị commit lên GitHub. Cần M5 đổi mật khẩu trên MongoDB Atlas, bỏ file khỏi Git, và sửa `.gitignore`.
- Nhiều file hiển thị là "modified" chỉ vì khác kiểu xuống dòng (CRLF và LF). Nên đặt `git config core.autocrlf true`.
