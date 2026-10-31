# Kết quả benchmark M4

> Đây là số liệu mẫu đo trên máy ảo 2 nhân CPU. Hãy chạy lại `BenchmarkM4` trên máy của nhóm và dùng số liệu của máy đó trong báo cáo.

- Ngày chạy: 2026-10-31T14:12:58
- Java: 21.0.12.1 (OpenJDK 64-Bit Server VM)
- Hệ điều hành: Linux 6.18.44-fc-v185
- Số nhân CPU hiển thị cho Java: 2
- Bộ nhớ tối đa cho Java: 1024 MB
- Lưu ý: đo trong một tiến trình, không qua mạng. Thời gian là từ lúc gửi thao tác đến lúc nhận ACK trong bộ nhớ.

## B1. Một phòng, nhiều người cùng gõ
Tổng 12 000 thao tác chia đều cho các người dùng. Mỗi người gửi một thao tác, chờ ACK rồi mới gửi tiếp.

| Số người | Thao tác/giây | Trung vị (ms) | p95 (ms) | p99 (ms) | Lớn nhất (ms) | Đủ ký tự |
|---|---|---|---|---|---|---|
| 1 | 2141 | 0.286 | 1.101 | 1.410 | 7.55 | đúng |
| 2 | 2401 | 0.660 | 1.836 | 2.538 | 7.67 | đúng |
| 4 | 3531 | 0.494 | 4.366 | 8.221 | 28.10 | đúng |
| 8 | 4467 | 0.991 | 2.751 | 12.640 | 331.94 | đúng |
| 16 | 4521 | 2.754 | 7.399 | 14.673 | 48.10 | đúng |

## B2. Nhiều phòng chạy song song
Mỗi phòng có 4 người, mỗi người gửi 1 500 thao tác.

| Số phòng | Tổng thao tác/giây | Trung vị (ms) | p95 (ms) | p99 (ms) | Đủ ký tự |
|---|---|---|---|---|---|
| 1 | 6104 | 0.307 | 2.010 | 5.787 | đúng |
| 2 | 9156 | 0.623 | 2.043 | 5.003 | đúng |
| 4 | 10677 | 1.269 | 2.885 | 4.265 | đúng |
| 8 | 12008 | 2.464 | 4.446 | 6.906 | đúng |

## B3. Một người gõ, nhiều người nhận
Một người gửi 1 000 thao tác, mỗi lần chờ cho đến khi TẤT CẢ người nhận đã nhận được.

| Số người nhận | Thao tác/giây | Trung vị (ms) | p95 (ms) | p99 (ms) | Đủ ký tự |
|---|---|---|---|---|---|
| 1 | 14052 | 0.064 | 0.094 | 0.152 | đúng |
| 5 | 10414 | 0.089 | 0.132 | 0.197 | đúng |
| 10 | 8334 | 0.105 | 0.148 | 0.176 | đúng |
| 25 | 4539 | 0.196 | 0.285 | 0.476 | đúng |
| 50 | 2919 | 0.308 | 0.494 | 0.863 | đúng |

## B4. Đồng bộ lại sau khi offline
Thời gian từ lúc gửi RESYNC đến lúc nhận CHECKPOINT (trung vị của 3 lần chạy).

| Thao tác Bob viết offline | Thao tác của người khác bỏ lỡ | Thời gian (ms) | Kết quả |
|---|---|---|---|
| 10 | 10 | 1.1 | đúng |
| 100 | 100 | 2.4 | đúng |
| 1000 | 100 | 18.7 | đúng |
| 100 | 1000 | 7.4 | đúng |
| 1000 | 1000 | 65.3 | đúng |

