# Kết quả kiểm tra — 05/10/2026

## Nâng cấp V3: phí và tài sản bảo đảm

**19/19 kiểm thử đạt**: 18 tích hợp HTTP + một migration. Bổ sung: giá tính cả ngày đầu/cuối và snapshot không đổi khi đổi giá asset; từ chối giá client sai/cũ; bắt buộc nhận tài sản kể cả phí 0; quyền nhận/thu/trả; không hủy khi đang giữ đồ; không trả đồ trước khi nhận trả thiết bị; hủy bàn giao kèm trả đồ; thu đúng tổng phí và từ chối phần lẻ VNĐ; hai request thu đồng thời chỉ một thành công; hoàn phí một lần theo ngày hoàn riêng; giá trị tài sản không cộng vào doanh thu. Migration giữ phí cũ 0, không tự tạo bản ghi thu tiền hoặc nhận đồ.

Chrome đã thực hiện từ giao diện: cấu hình 25.000đ/ngày → sinh viên mượn 3 ngày = 75.000đ → duyệt → nhận đồng hồ trị giá ước tính 2.000.000đ → bàn giao → xác nhận chuyển khoản thủ công → doanh thu 75.000đ → hoàn phí → nhận trả thiết bị → trả đồng hồ. Sinh viên thấy phí/đồ của mình, không có nút thu phí. Doanh thu sau hoàn về 0; không còn món đồ trong danh sách đang giữ. Giao diện doanh thu 390px không tràn ngang, không pageerror. Các khoản tiền và tài sản trên ảnh là dữ liệu kiểm thử trong bộ nhớ, không phải giao dịch thực.

Build Angular và JAR thành công. Đã dừng server thật và sao lưu H2 trước V3; so sánh trường cũ trong assets/loans/issues/audit trước–sau khớp. Server 8081 chạy H2 V3; server kiểm thử 8082 đã dừng. Chưa kiểm thử PostgreSQL thực tế.

## Nâng cấp V2: kết quả trước đó

**15/15 kiểm thử đạt**: 14 kiểm thử tích hợp HTTP và một kiểm thử migration V1 → V2. Bổ sung: không tự cấp quyền nhân viên qua payload tạo tài khoản; khóa/mở vẫn vô hiệu hóa session cũ; chặn duyệt cho sinh viên bị khóa; đổi mật khẩu thu hồi mọi phiên; giới hạn byte UTF-8; không ghi mật khẩu vào audit; sửa tài sản giữ snapshot tên/mã trên phiếu; ngừng dùng chặn mượn và bảo vệ phiếu đang xử lý; báo cáo đúng số liệu, phân quyền, ngày không hợp lệ và biên ngày UTC+7; quá hạn không bị giới hạn theo kỳ báo cáo.

Test migration tạo database V1 với tài khoản, asset và loan; nâng cấp V2 giữ password hash, mục đích, tài khoản và điền snapshot; chạy lại không áp dụng thêm migration.

Chrome xác minh lại luồng mượn–trả–báo hỏng cũ và thêm: sửa/ngừng dùng/mở lại asset, tạo/khóa/mở sinh viên, đổi mật khẩu và đăng nhập mật khẩu mới, báo cáo desktop/mobile 390px. Không có pageerror; báo cáo mobile không tràn ngang. Dữ liệu kiểm thử chỉ ở H2 trong bộ nhớ trên 8082. Ảnh báo cáo/tài khoản là dữ liệu kiểm thử minh họa.

Đã dừng server local trước khi sao lưu file H2 vào data/backups, build lại Angular và JAR, rồi khởi động 8081 với V2. So sánh dữ liệu API trước/sau (bỏ qua các trường mới) xác nhận assets, loans, issues, audit không thay đổi. Backend H2 local vẫn giữ dữ liệu cũ.

Bản build Angular mới khoảng 338 kB trước nén, 83 kB ước lượng truyền nén. Source ZIP mới loại toàn bộ database, backup và cache. PostgreSQL thật vẫn chưa được cài/kiểm thử.

Các mục dưới ghi lại kết quả bản V1.

## Môi trường thực tế

Windows, Java 21.0.11, Maven 3.9.16, Node 24.18.0, npm 11.16.0, Chrome. Angular 21.2.25 và Spring Boot 3.5.16.

## Backend

**9/9 kiểm thử tích hợp đạt**, không failure/error/skipped. Chạy HTTP server thật ở cổng ngẫu nhiên, database H2 trong bộ nhớ riêng, Flyway tạo schema, Hibernate kiểm tra schema. Không dùng database Tea House hoặc dữ liệu demo trên đĩa.

1. Xác thực, sai mật khẩu, chặn chưa đăng nhập, ẩn passwordHash, chặn sinh viên xem nhật ký, logout vô hiệu hóa phiên.
2. Chặn POST thiếu header bảo vệ và Origin ngoài danh sách cho phép.
3. Từ chối ngày quá khứ và yêu cầu trùng của cùng sinh viên.
4. Mượn → duyệt → bàn giao → nhận trả; chặn vai trò/sai trạng thái; ghi nhật ký.
5. Chặn lịch giao nhau ở cả ngày ranh giới; cho phép hai lịch cách nhau; không bàn giao sớm.
6. Hai lượt duyệt đồng thời trên cùng tài sản: đúng một thành công (200), một xung đột (409).
7. Chặn hủy phiếu người khác; lịch thiết bị không lộ email/mục đích người mượn.
8. Báo hỏng chặn bàn giao; phải xử lý mọi issue mới trở về AVAILABLE; trả tài sản không tự xóa trạng thái bảo trì.
9. Chỉ nhân viên được thêm thiết bị; mã trùng bị từ chối.

Chạy lại bằng `mvn.cmd test`. Khi đóng gói JAR, đã dùng kết quả kiểm thử vừa đạt; không chạy lại bộ test không thay đổi chỉ để đóng gói.

## Frontend và bản đóng gói

Angular production build thành công: khoảng 316 kB JavaScript + CSS trước nén, khoảng 79 kB ước lượng truyền nén. JAR đã đóng gói Angular và API chung một server.

Chrome headless kiểm tra trên server 8082 với H2 trong bộ nhớ:

- Đăng nhập sinh viên/nhân viên, đăng xuất; tải tám thiết bị mẫu.
- Đăng ký mượn từ giao diện → duyệt → bàn giao → báo hỏng → nhận trả → ghi kết quả sửa chữa.
- Kiểm tra nhãn trạng thái, nhật ký thao tác, thêm tài sản và tìm theo mã.
- Desktop 1440px; mobile 390px không tràn ngang; chụp ảnh giao diện.
- Không có lỗi JavaScript `pageerror` trong luồng hoàn tất.

Server kiểm thử dùng bộ nhớ, đã dừng sau kiểm tra. Server local thật chạy 8081 bằng H2 lưu file riêng. Health trả `ok=true, mode=demo`.

## Giới hạn

Chưa chạy trên PostgreSQL thật: không suy diễn kết quả H2 thành chứng nhận tương thích PostgreSQL. Chưa kiểm thử Safari/Firefox, tải lớn, nhiều instance, HTTPS công khai hoặc tích hợp dịch vụ ngoài. QR và quản lý tài khoản chưa triển khai. Lỗi trùng mã cố ý trong test có log SQL constraint violation; test đã xác minh API trả 409 như dự kiến.
